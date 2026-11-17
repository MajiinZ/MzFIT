package com.mlpt.data

import com.mlpt.shared.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SavedRoutine(val plan: WorkoutPlan, val days: Set<Int> = emptySet())
enum class SetMetric { Reps, Seconds }
data class LiveSet(val amount: String = "", val load: String = "", val complete: Boolean = false)
data class LiveExercise(val prescription: Exercise, val metric: SetMetric, val sets: List<LiveSet>)
data class WorkoutDraft(val id: String, val plan: WorkoutPlan, val startedAt: Long, val pounds: Boolean,
    val exercises: List<LiveExercise>, val notes: String = "") {
    val completedSets get() = exercises.sumOf { e -> e.sets.count { it.complete } }
    val totalSets get() = exercises.sumOf { it.sets.size }
}
data class PerformanceSet(val workoutId: Int, val exercise: String, val completedAt: Long?, val number: Int,
    val reps: Int?, val seconds: Int?, val weightKg: Double?)
data class StudioSnapshot(val routines: List<SavedRoutine> = emptyList(), val draft: WorkoutDraft? = null,
    val performances: List<PerformanceSet> = emptyList())
data class StudioState(val ready: Boolean = false, val busy: Boolean = false, val draftSaving: Boolean = false,
    val unsaved: Boolean = false, val error: String? = null, val notice: String? = null,
    val routines: List<SavedRoutine> = emptyList(), val draft: WorkoutDraft? = null, val performances: List<PerformanceSet> = emptyList())
interface StudioStorage {
    suspend fun load(): StudioSnapshot
    suspend fun saveRoutine(routine: SavedRoutine)
    suspend fun deleteRoutine(id: String)
    suspend fun saveDraft(draft: WorkoutDraft)
    suspend fun discardDraft(id: String)
    /** Completion, actual sets, notes, idempotency marker and draft removal must commit together. */
    suspend fun finish(draft: WorkoutDraft, endedAt: Long): Int
}

fun exerciseKey(name: String) = name.trim().lowercase().split(Regex("\\s+")).joinToString(" ")
fun liveSetValid(set: LiveSet, metric: SetMetric, pounds: Boolean): Boolean {
    val amount = set.amount.toIntOrNull() ?: return false
    if (amount !in 1..(if (metric == SetMetric.Reps) 999 else 86400)) return false
    if (metric == SetMetric.Seconds) return set.load.isBlank()
    if (set.load.isBlank()) return true
    val weight = set.load.replace(',', '.').toDoubleOrNull()?.div(if (pounds) 2.20462262185 else 1.0) ?: return false
    return weight.isFinite() && weight in 0.0..2000.0
}
fun validateRoutine(routine: SavedRoutine) {
    val p = routine.plan
    require(p.id.isNotBlank() && p.title.isNotBlank() && p.title.length <= 80 && p.description.length <= 2000)
    require(p.bodyPart.isNotBlank() && p.category.isNotBlank())
    require(p.minutes in 1..240 && p.exercises.size in 1..30 && routine.days.all { it in 1..7 })
    require(p.exercises.sumOf { it.sets } <= 500)
    require(p.exercises.map { it.id }.distinct().size == p.exercises.size)
    require(p.exercises.all { it.name.isNotBlank() && it.name.length <= 120 && it.sets in 1..20 && it.reps.isNotBlank() && it.reps.length <= 100 })
}
fun validateDraft(draft: WorkoutDraft) {
    validateRoutine(SavedRoutine(draft.plan))
    require(draft.id.isNotBlank() && draft.startedAt > 0 && draft.notes.length <= 5000)
    require(draft.exercises.map { it.prescription } == draft.plan.exercises)
    require(draft.exercises.all { e -> e.sets.size in 1..100 && e.sets.all { it.amount.length <= 5 && it.load.length <= 12 && (!it.complete || liveSetValid(it, e.metric, draft.pounds)) } })
    require(draft.totalSets <= 500)
}

/** Durable, app-owned live logger. Edits publish immediately; coalesced checkpoints serialize with finish. */
class TrainingStudio(private val storage: StudioStorage, private val scope: CoroutineScope,
    private val clock: () -> Long, private val newId: () -> String, private val onFinished: () -> Unit = {}) {
    private val mutable = MutableStateFlow(StudioState())
    val state = mutable.asStateFlow()
    private val lock = Mutex()
    private val checkpoints = Channel<Unit>(Channel.CONFLATED)
    init {
        retry()
        scope.launch {
            for (unit in checkpoints) lock.withLock {
                val snapshot = mutable.value.draft ?: return@withLock
                mutable.value = mutable.value.copy(draftSaving = true)
                try {
                    storage.saveDraft(snapshot)
                    val changed = snapshot != mutable.value.draft
                    mutable.value = mutable.value.copy(draftSaving = false, unsaved = changed,
                        error = mutable.value.error?.takeUnless { it.startsWith("Your latest workout edits") })
                    if (changed) checkpoints.trySend(Unit)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    mutable.value = mutable.value.copy(draftSaving = false, unsaved = true, error = "Your latest workout edits haven’t saved. Keep the app open and retry.")
                }
            }
        }
    }
    fun retry() {
        if (mutable.value.ready) { checkpoints.trySend(Unit); return }
        scope.launch { lock.withLock {
            try { val saved = storage.load(); mutable.value = StudioState(ready = true, routines = saved.routines, draft = saved.draft, performances = saved.performances) }
            catch (e: Exception) { if (e is CancellationException) throw e; mutable.value = mutable.value.copy(error = "Couldn’t load routines and your active workout. Retry before training.") }
        } }
    }
    fun refreshHistory() { if (mutable.value.ready) scope.launch { lock.withLock {
        try { val saved = storage.load(); mutable.value = mutable.value.copy(routines = saved.routines, performances = saved.performances) }
        catch (e: Exception) { if (e is CancellationException) throw e; mutable.value = mutable.value.copy(error = "Couldn’t refresh your recorded performance. Try again.") }
    } } }
    fun start(plan: WorkoutPlan, pounds: Boolean) {
        if (!mutable.value.ready || mutable.value.busy || mutable.value.draft != null) return
        val exercises = plan.exercises.map { prescription ->
            val timed = prescription.reps.contains("second", true) || prescription.reps.contains("minute", true)
            LiveExercise(prescription, if (timed) SetMetric.Seconds else SetMetric.Reps, List(prescription.sets) { LiveSet() })
        }
        val draft = WorkoutDraft(newId(), plan, clock(), pounds, exercises)
        validateDraft(draft)
        mutable.value = mutable.value.copy(draft = draft, unsaved = true, notice = null, error = null)
        checkpoints.trySend(Unit)
    }
    private fun edit(change: (WorkoutDraft) -> WorkoutDraft) {
        val s = mutable.value
        if (s.busy || !s.ready) return
        val draft = s.draft ?: return
        val next = change(draft); validateDraft(next)
        mutable.value = s.copy(draft = next, unsaved = true, notice = null)
        checkpoints.trySend(Unit)
    }
    fun setNotes(notes: String) = edit { it.copy(notes = notes.take(5000)) }
    fun changeSet(exercise: Int, index: Int, set: LiveSet) = edit { draft -> draft.copy(exercises = draft.exercises.mapIndexed { i, e ->
        if (i != exercise) e else e.copy(sets = e.sets.mapIndexed { j, old -> if (j != index) old else set.copy(complete = false) })
    }) }
    fun completeSet(exercise: Int, index: Int): Boolean {
        val draft = mutable.value.draft ?: return false
        if (mutable.value.busy) return false
        val e = draft.exercises[exercise]; val set = e.sets[index]
        if (!set.complete && !liveSetValid(set, e.metric, draft.pounds)) return false
        edit { it.copy(exercises = it.exercises.mapIndexed { i, item -> if (i != exercise) item else item.copy(sets = item.sets.mapIndexed { j, value -> if (j != index) value else value.copy(complete = !value.complete) }) }) }
        return true
    }
    fun addSet(exercise: Int) = edit { draft -> draft.copy(exercises = draft.exercises.mapIndexed { i, e ->
        if (i != exercise || e.sets.size >= 100 || draft.totalSets >= 500) e else e.copy(sets = e.sets + LiveSet())
    }) }
    fun usePrevious(exercise: Int) = edit { draft -> draft.copy(exercises = draft.exercises.mapIndexed { i, e ->
        if (i != exercise) e else {
            val matching = mutable.value.performances.filter { exerciseKey(it.exercise) == exerciseKey(e.prescription.name) && (it.seconds != null) == (e.metric == SetMetric.Seconds) }
            val latest = matching.maxOfOrNull { it.workoutId }
            val previous = matching.filter { it.workoutId == latest }.sortedBy { it.number }
            e.copy(sets = e.sets.mapIndexed { index, set ->
                val old = previous.getOrNull(index)
                if (set.complete || old == null) set else LiveSet((old.seconds ?: old.reps)?.toString() ?: "",
                    old.weightKg?.let { (kotlin.math.round(it * (if (draft.pounds) 2.20462262185 else 1.0) * 1000) / 1000).toString() } ?: "")
            })
        }
    }) }
    fun removeSet(exercise: Int, index: Int) = edit { draft -> draft.copy(exercises = draft.exercises.mapIndexed { i, e ->
        if (i != exercise || e.sets.size <= 1) e else e.copy(sets = e.sets.filterIndexed { j, _ -> j != index })
    }) }
    fun finish() {
        val current = mutable.value
        if (current.busy || current.draft?.completedSets == null || current.draft.completedSets == 0) return
        mutable.value = current.copy(busy = true, error = null)
        scope.launch { lock.withLock {
            try {
                val draft = mutable.value.draft ?: return@withLock
                val id = storage.finish(draft, clock().coerceAtLeast(draft.startedAt))
                // Clear a successfully committed draft even if a subsequent history read fails.
                mutable.value = mutable.value.copy(draft = null, unsaved = false, draftSaving = false, notice = "Workout #$id saved. ${draft.completedSets} completed sets recorded.")
                onFinished()
                val saved = storage.load(); mutable.value = mutable.value.copy(routines = saved.routines, performances = saved.performances)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value = mutable.value.copy(error = if (mutable.value.draft == null) "Workout saved, but progress couldn’t refresh. Reopen Progress to retry." else "Workout couldn’t finish saving. Your sets are still here; retry Finish workout.")
            } finally { mutable.value = mutable.value.copy(busy = false) }
        } }
    }
    fun discard() {
        val draft = mutable.value.draft ?: return
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true)
        scope.launch { lock.withLock {
            try { storage.discardDraft(draft.id); mutable.value = mutable.value.copy(draft = null, unsaved = false, error = null, notice = "Workout draft discarded.") }
            catch (e: Exception) { if (e is CancellationException) throw e; mutable.value = mutable.value.copy(error = "Couldn’t discard this draft. Try again.") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        } }
    }
    fun saveRoutine(routine: SavedRoutine) = routineOperation {
        validateRoutine(routine); storage.saveRoutine(routine)
        mutable.value = mutable.value.copy(routines = mutable.value.routines.filterNot { it.plan.id == routine.plan.id } + routine, notice = "Routine saved.")
    }
    fun deleteRoutine(id: String) = routineOperation {
        storage.deleteRoutine(id); mutable.value = mutable.value.copy(routines = mutable.value.routines.filterNot { it.plan.id == id }, notice = "Routine deleted. Completed workouts are preserved.")
    }
    private fun routineOperation(block: suspend () -> Unit) {
        if (!mutable.value.ready || mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        scope.launch { lock.withLock {
            try { block() } catch (e: Exception) { if (e is CancellationException) throw e; mutable.value = mutable.value.copy(error = "Routine changes weren’t saved. Try again.") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        } }
    }
}
