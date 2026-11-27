package com.mlpt.data

import com.mlpt.shared.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface PoseLogStorage {
    suspend fun recoverInterruptedSessions()
    suspend fun checkpoint(log: PoseSessionLog)
    suspend fun history(limit: Int): List<PoseSessionSummary>
    suspend fun details(id: String): PoseSessionLog?
}
data class PoseRecorderState(
    val ready: Boolean = false, val loading: Boolean = true, val saving: Boolean = false,
    val unsaved: Boolean = false, val error: String? = null,
    val current: PoseSessionLog? = null, val feedback: PoseAssessment = PoseAssessment(),
    val lastRep: PoseRep? = null, val history: List<PoseSessionSummary> = emptyList(),
)

/** App-owned recorder: camera disposal does not cancel outstanding local log writes. */
class PoseSessionRecorder(
    private val storage: PoseLogStorage, private val scope: CoroutineScope,
    private val wallClock: () -> Long, private val monotonicClock: () -> Long, private val newId: () -> String,
) {
    private val mutable = MutableStateFlow(PoseRecorderState())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    private val checkpoints = Channel<Unit>(Channel.CONFLATED)
    private var coach: PoseCoach? = null
    private var startedMono = 0L
    private var lastFrame = Long.MIN_VALUE
    private var lastObservation = -1_000L
    private var lastSignature = ""
    private var historyLimit = 20

    init {
        retryStorage()
        scope.launch {
            for (ignored in checkpoints) mutex.withLock {
                val snapshot = mutable.value.current ?: return@withLock
                mutable.value = mutable.value.copy(saving = true)
                try {
                    storage.checkpoint(snapshot)
                    val history = if (snapshot.status != "active") storage.history(historyLimit) else mutable.value.history
                    val changed = mutable.value.current != snapshot
                    mutable.value = mutable.value.copy(saving = false, unsaved = changed, error = null, history = history)
                    if (changed) checkpoints.trySend(Unit)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    mutable.value = mutable.value.copy(saving = false, unsaved = true, error = "Pose log hasn’t been saved. Keep the app open and tap Retry save.")
                }
            }
        }
    }

    fun retryStorage() {
        scope.launch { mutex.withLock {
            if (mutable.value.ready) {
                if (mutable.value.current != null) checkpoints.trySend(Unit)
                else try { mutable.value = mutable.value.copy(history = storage.history(historyLimit), error = null) }
                catch (error: Exception) { if (error is CancellationException) throw error }
                return@withLock
            }
            mutable.value = mutable.value.copy(loading = true, error = null)
            try {
                storage.recoverInterruptedSessions()
                mutable.value = mutable.value.copy(ready = true, loading = false, history = storage.history(historyLimit))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.value = mutable.value.copy(loading = false, error = "Couldn’t open pose-session history. Tap Retry loading.")
            }
        } }
    }

    fun start(exercise: PoseExercise): Boolean {
        val current = mutable.value
        if (!current.ready || current.current?.status == "active" || current.unsaved || current.saving) return false
        startedMono = monotonicClock(); coach = PoseCoach(exercise, startedMono)
        lastFrame = Long.MIN_VALUE; lastObservation = -1_000; lastSignature = ""
        mutable.value = current.copy(current = PoseSessionLog(newId(), exercise.key, exercise.label, wallClock()),
            feedback = PoseAssessment(), lastRep = null, unsaved = true, error = null)
        checkpoints.trySend(Unit)
        return true
    }

    fun onFrame(frame: CoachingFrame) {
        val current = mutable.value.current?.takeIf { it.status == "active" } ?: return
        if (lastFrame != Long.MIN_VALUE && frame.timeMs - lastFrame < 80) return
        if (frame.timeMs < startedMono) return
        if (monotonicClock() - frame.timeMs > 1_000) { resetTracking(); return }
        lastFrame = frame.timeMs
        val feedback = coach?.analyze(frame) ?: return
        val elapsed = frame.timeMs - startedMono
        val signature = "${feedback.quality}/${feedback.phase}/${feedback.cues}"
        val logObservation = elapsed - lastObservation >= 1_000 || feedback.newRep != null || (signature != lastSignature && elapsed - lastObservation >= 250)
        val observations = if (logObservation) current.observations + observation(current, feedback, elapsed) else current.observations
        val reps = feedback.newRep?.let { current.reps + it } ?: current.reps
        mutable.value = mutable.value.copy(current = current.copy(analyzedSamples = feedback.analyzedSamples, usableSamples = feedback.usableSamples, observations = observations, reps = reps),
            feedback = feedback, lastRep = feedback.newRep ?: mutable.value.lastRep, unsaved = true)
        if (logObservation) { lastObservation = elapsed; lastSignature = signature; checkpoints.trySend(Unit) }
    }

    fun resetTracking() {
        val log = mutable.value.current?.takeIf { it.status == "active" } ?: return
        coach?.resetTracking()
        val feedback = coach?.assessment ?: return
        val elapsed = (monotonicClock() - startedMono).coerceAtLeast(0)
        mutable.value = mutable.value.copy(current = log.copy(observations = log.observations + observation(log, feedback, elapsed)), feedback = feedback, unsaved = true)
        checkpoints.trySend(Unit)
    }

    fun checkTrackingFreshness() {
        if (mutable.value.current?.status != "active") return
        val latest = if (lastFrame == Long.MIN_VALUE) startedMono else lastFrame
        if (monotonicClock() - latest > 1_000 && mutable.value.feedback.quality != PoseQuality.NotVisible) resetTracking()
    }

    fun stop(interrupted: Boolean = false) {
        val log = mutable.value.current?.takeIf { it.status == "active" } ?: return
        mutable.value = mutable.value.copy(current = log.copy(endedAt = wallClock().coerceAtLeast(log.startedAt), status = if (interrupted) "interrupted" else "completed"), unsaved = true)
        coach = null
        checkpoints.trySend(Unit)
    }

    private fun observation(log: PoseSessionLog, feedback: PoseAssessment, elapsed: Long) = PoseObservation(log.observations.size + 1,
        elapsed, feedback.quality.name, feedback.phase.name, feedback.angle, feedback.secondary, feedback.confidence, feedback.cues.map { it.name })

    fun loadMoreHistory() { historyLimit += 20; scope.launch { mutex.withLock {
        try { mutable.value = mutable.value.copy(history = storage.history(historyLimit)) }
        catch (error: Exception) { if (error is CancellationException) throw error; mutable.value = mutable.value.copy(error = "Couldn’t load older pose sessions. Try again.") }
    } } }
    suspend fun details(id: String): PoseSessionLog? = mutex.withLock { storage.details(id) }
}
