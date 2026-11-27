package com.mlpt.data

import com.mlpt.shared.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingStudioTest {
    private val plan = WorkoutPlan("plan", "Strength", "Strength", 30, "Test plan", listOf(Exercise("bench", "Bench press", 2, "8–12"), Exercise("plank", "Plank", 1, "30 seconds")))
    private class Store : StudioStorage {
        var snapshot = StudioSnapshot()
        var failDraft = false
        var failFinish = false
        var gate: CompletableDeferred<Unit>? = null
        val completed = mutableMapOf<String, Int>()
        override suspend fun load() = snapshot
        override suspend fun saveRoutine(routine: SavedRoutine) { snapshot = snapshot.copy(routines = snapshot.routines.filterNot { it.plan.id == routine.plan.id } + routine) }
        override suspend fun deleteRoutine(id: String) { snapshot = snapshot.copy(routines = snapshot.routines.filterNot { it.plan.id == id }) }
        override suspend fun saveDraft(draft: WorkoutDraft) { gate?.await(); check(!failDraft); snapshot = snapshot.copy(draft = draft) }
        override suspend fun discardDraft(id: String) { snapshot = snapshot.copy(draft = null) }
        override suspend fun finish(draft: WorkoutDraft, endedAt: Long): Int {
            check(!failFinish)
            val id = completed.getOrPut(draft.id) { completed.size + 1 }; snapshot = snapshot.copy(draft = null); return id
        }
    }
    @Test fun checkpointsRestoreRawEditsAndCompletedTimedSetsAfterRestart() = runTest {
        val store = Store(); val studio = TrainingStudio(store, backgroundScope, { 1000 }, { "draft" }); runCurrent()
        studio.start(plan, false)
        studio.changeSet(0, 0, LiveSet("8", "42,5")); assertTrue(studio.completeSet(0, 0))
        studio.changeSet(1, 0, LiveSet("45")); assertTrue(studio.completeSet(1, 0)); studio.setNotes("Good session 🏋️")
        runCurrent()
        val restarted = TrainingStudio(store, backgroundScope, { 2000 }, { "other" }); runCurrent()
        assertEquals(studio.state.value.draft, restarted.state.value.draft)
        assertEquals(2, restarted.state.value.draft?.completedSets)
        assertFalse(restarted.state.value.unsaved)
    }
    @Test fun failedCheckpointKeepsEditsAndRetryPersistsLatestValue() = runTest {
        val store = Store(); val studio = TrainingStudio(store, backgroundScope, { 1000 }, { "draft" }); runCurrent()
        store.failDraft = true; studio.start(plan, false); studio.changeSet(0, 0, LiveSet("10", "50")); runCurrent()
        assertTrue(studio.state.value.unsaved); assertNotNull(studio.state.value.error)
        store.failDraft = false; studio.retry(); runCurrent()
        assertEquals("50", store.snapshot.draft?.exercises?.first()?.sets?.first()?.load)
        assertFalse(studio.state.value.unsaved); assertNull(studio.state.value.error)
    }
    @Test fun finishDuringCheckpointCommitsLatestSetsOnceAndCannotResurrectDraft() = runTest {
        val store = Store(); var notified = 0
        val studio = TrainingStudio(store, backgroundScope, { 1000 }, { "draft" }, { notified++ }); runCurrent()
        store.gate = CompletableDeferred(); studio.start(plan, false); runCurrent()
        studio.changeSet(0, 0, LiveSet("8", "50")); studio.completeSet(0, 0)
        studio.finish(); studio.finish(); runCurrent()
        assertTrue(studio.state.value.busy)
        store.gate!!.complete(Unit); runCurrent()
        assertEquals(1, store.completed.size); assertEquals(1, notified)
        assertNull(studio.state.value.draft); assertNull(store.snapshot.draft)
        assertFalse(studio.state.value.unsaved)
    }
    @Test fun failedFinishRetainsSetsAndNextAttemptCanCommit() = runTest {
        val store = Store(); val studio = TrainingStudio(store, backgroundScope, { 1000 }, { "draft" }); runCurrent()
        studio.start(plan, false); studio.changeSet(0, 0, LiveSet("8", "50")); studio.completeSet(0, 0); runCurrent()
        store.failFinish = true; studio.finish(); runCurrent()
        assertEquals(1, studio.state.value.draft?.completedSets); assertNotNull(studio.state.value.error)
        store.failFinish = false; studio.finish(); runCurrent(); assertNull(studio.state.value.draft); assertEquals(1, store.completed.size)
    }
    @Test fun invalidValuesCannotBeMarkedCompleteOrTurnIntoRecords() = runTest {
        val store = Store(); val studio = TrainingStudio(store, backgroundScope, { 1000 }, { "draft" }); runCurrent(); studio.start(plan, false)
        listOf(LiveSet("0", "50"), LiveSet("10", "NaN"), LiveSet("10", "-1"), LiveSet("1000", "50")).forEach {
            studio.changeSet(0, 0, it); assertFalse(studio.completeSet(0, 0))
        }
        studio.changeSet(1, 0, LiveSet("45", "10")); assertFalse(studio.completeSet(1, 0))
        studio.finish(); runCurrent(); assertTrue(store.completed.isEmpty())
    }
    @Test fun previousSessionCopiesCompatibleMetricsAndConvertsPoundsWithoutMarkingDone() = runTest {
        val store = Store().apply { snapshot = StudioSnapshot(performances = listOf(
            PerformanceSet(1, "Bench press", 100, 1, 5, null, 100.0), PerformanceSet(2, " BENCH   PRESS ", 200, 1, 8, null, 50.0),
            PerformanceSet(3, "Bench press", 300, 1, null, 30, null))) }
        val studio = TrainingStudio(store, backgroundScope, { 1000 }, { "draft" }); runCurrent(); studio.start(plan, true); studio.usePrevious(0)
        val set = studio.state.value.draft!!.exercises.first().sets.first()
        assertEquals("8", set.amount); assertEquals(110.231, set.load.toDouble(), .001); assertFalse(set.complete)
        runCurrent()
    }
    @Test fun aNewPlanCannotReplaceAnActiveDraftAndDiscardDoesNotDeleteRoutine() = runTest {
        val store = Store(); val studio = TrainingStudio(store, backgroundScope, { 1000 }, { "draft" }); runCurrent()
        studio.saveRoutine(SavedRoutine(plan, setOf(1, 3, 5))); runCurrent(); studio.start(plan, false); runCurrent()
        studio.start(plan.copy(id = "replacement"), true); assertEquals("plan", studio.state.value.draft?.plan?.id)
        studio.discard(); runCurrent(); assertNull(store.snapshot.draft); assertEquals(1, store.snapshot.routines.size)
    }
    @Test fun routineLimitsAndCompletedSetValidationRejectImpossibleDrafts() {
        assertFailsWith<IllegalArgumentException> { validateRoutine(SavedRoutine(plan, setOf(0))) }
        assertFailsWith<IllegalArgumentException> { validateRoutine(SavedRoutine(plan.copy(exercises = emptyList()))) }
        assertFailsWith<IllegalArgumentException> { validateRoutine(SavedRoutine(plan.copy(exercises = (1..30).map { Exercise("$it", "Lift $it", 20, "8") }))) }
        assertFalse(liveSetValid(LiveSet("8", "5000"), SetMetric.Reps, true))
        assertTrue(liveSetValid(LiveSet("30"), SetMetric.Seconds, false))
    }
}
