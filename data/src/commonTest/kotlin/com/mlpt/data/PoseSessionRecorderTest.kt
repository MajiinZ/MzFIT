package com.mlpt.data

import com.mlpt.shared.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PoseSessionRecorderTest {
    private class Store : PoseLogStorage {
        val logs = mutableMapOf<String,PoseSessionLog>()
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun recoverInterruptedSessions() {}
        override suspend fun checkpoint(log: PoseSessionLog) { gate?.await();if(fail) error("Disk full");logs[log.id]=log }
        override suspend fun history(limit: Int) = logs.values.map { PoseSessionSummary(it.id,it.exerciseName,it.startedAt,it.endedAt,it.status,it.fullReps,it.matchedReps,it.partialAttempts,it.analyzedSamples,it.usableSamples) }.take(limit)
        override suspend fun details(id: String) = logs[id]
    }
    @Test fun failedCheckpointCanBeRetriedWithoutLosingDataOrStartingAnotherSession() = runTest {
        val store=Store();var now=0L;var id=0
        val recorder=PoseSessionRecorder(store,backgroundScope,{10_000+now},{now},{"session-${++id}"})
        runCurrent();store.fail=true
        assertTrue(recorder.start(PoseExercise.Squat))
        now=200;recorder.onFrame(CoachingFrame(now,1000f,1000f,emptyMap()));recorder.stop()
        runCurrent()
        assertTrue(recorder.state.value.unsaved);assertNotNull(recorder.state.value.error)
        assertFalse(recorder.start(PoseExercise.Curl))
        store.fail=false;recorder.retryStorage();runCurrent()
        assertFalse(recorder.state.value.unsaved)
        assertEquals(1,store.logs.size)
        assertEquals(1,store.logs.values.single().analyzedSamples)
        assertEquals("completed",store.logs.values.single().status)
        assertTrue(recorder.start(PoseExercise.Curl));runCurrent()
        assertEquals(2,store.logs.size)
    }
    @Test fun stoppingDuringAnOutstandingWriteEventuallyCommitsTheFinalSnapshot() = runTest {
        val store=Store().apply { gate=CompletableDeferred() };var now=0L
        val recorder=PoseSessionRecorder(store,backgroundScope,{10_000+now},{now},{"session"})
        runCurrent();recorder.start(PoseExercise.PushUp);runCurrent()
        assertTrue(recorder.state.value.saving)
        now=300;recorder.onFrame(CoachingFrame(now,1000f,1000f,emptyMap()))
        recorder.stop(interrupted=true);store.gate!!.complete(Unit);runCurrent()
        val saved=store.logs.getValue("session")
        assertEquals("interrupted",saved.status)
        assertEquals(10_300,saved.endedAt)
        assertEquals(1,saved.analyzedSamples)
        assertFalse(recorder.state.value.unsaved)
        assertEquals(1,recorder.state.value.history.size)
    }
    @Test fun oldInferenceResultsAreDiscardedAndLoggedAsTrackingReset() = runTest {
        val store=Store();var now=0L
        val recorder=PoseSessionRecorder(store,backgroundScope,{10_000+now},{now},{"session"})
        runCurrent();recorder.start(PoseExercise.Squat);runCurrent()
        now=2_000;recorder.onFrame(CoachingFrame(100,1000f,1000f,emptyMap()))
        runCurrent()
        assertEquals(PoseQuality.NotVisible,recorder.state.value.feedback.quality)
        assertEquals(0,recorder.state.value.feedback.analyzedSamples)
        assertTrue(store.logs.getValue("session").observations.single().cues.contains(PoseCue.TrackingReset.name))
    }
}
