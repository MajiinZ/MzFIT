package com.mlpt.data

import com.mlpt.shared.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PersistentTrainingRepositoryTest {
    private class Store : TrainingStorage {
        var profile: ClientProfile? = null
        val sessions = mutableListOf<TrainingSession>()
        var failLoad = false
        var failSave = false
        var insertCount = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun load(): SavedTrainingData {
            if (failLoad) error("Unavailable")
            return SavedTrainingData(profile, sessions.toList())
        }
        override suspend fun insertCompletedWorkout(workout: TrainingSession): TrainingSession {
            gate?.await()
            if (failSave) error("Disk full")
            insertCount++
            return workout.copy(id = (sessions.maxOfOrNull { it.id } ?: 0) + 1).also { sessions += it }
        }
        override suspend fun saveProfile(profile: ClientProfile) {
            if (failSave) error("Disk full")
            this.profile = profile
        }
    }
    private fun checkOff(repo: TrainingRepository) {
        val plan = repo.state.value.plans.first()
        repo.startWorkout(plan.id)
        plan.exercises.forEach { repo.toggleExercise(it.id) }
    }

    @Test fun committedWorkoutsReloadWithTheirOriginalExercisesAndTimestamp() = runTest {
        val store = Store()
        val repo = PersistentTrainingRepository(store, backgroundScope) { 1_800_000_000_000L }
        runCurrent()
        checkOff(repo)
        assertTrue(repo.finishWorkout())
        val saved = repo.state.value.sessions.single()
        assertEquals(1_800_000_000_000L, saved.completedAtEpochMillis)
        assertEquals(repo.state.value.plans.first().exercises, saved.exercises)
        assertEquals(saved.exerciseCount, saved.exercises.size)
        assertFalse(repo.finishWorkout())
        val reopened = PersistentTrainingRepository(store, backgroundScope) { 1L }
        runCurrent()
        assertEquals(listOf(saved), reopened.state.value.sessions)
        assertNull(reopened.state.value.activePlanId)
    }

    @Test fun failedSaveKeepsTheCompletedChecklistForRetry() = runTest {
        val store = Store()
        val repo = PersistentTrainingRepository(store, backgroundScope) { 100L }
        runCurrent()
        checkOff(repo)
        val before = repo.state.value
        store.failSave = true
        assertFalse(repo.finishWorkout())
        assertTrue(repo.state.value.sessions.isEmpty())
        assertEquals(before.activePlanId, repo.state.value.activePlanId)
        assertEquals(before.completedExerciseIds, repo.state.value.completedExerciseIds)
        assertNotNull(repo.state.value.storageError)
        store.failSave = false
        assertTrue(repo.finishWorkout())
        assertEquals(1, store.sessions.size)
        assertNull(repo.state.value.storageError)
    }

    @Test fun concurrentCompletionRequestsCommitOnlyOnceAndDoNotPublishEarly() = runTest {
        val store = Store().apply { gate = CompletableDeferred() }
        val repo = PersistentTrainingRepository(store, backgroundScope) { 100L }
        runCurrent()
        checkOff(repo)
        val first = async { repo.finishWorkout() }
        val second = async { repo.finishWorkout() }
        runCurrent()
        assertTrue(repo.state.value.isSaving)
        assertTrue(repo.state.value.sessions.isEmpty())
        val active = repo.state.value.activePlanId
        repo.startWorkout("mobility")
        assertEquals(active, repo.state.value.activePlanId)
        store.gate!!.complete(Unit)
        assertTrue(first.await())
        assertFalse(second.await())
        assertEquals(1, store.insertCount)
    }

    @Test fun loadFailureBlocksChangesAndRetryRestoresExistingHistory() = runTest {
        val store = Store().apply { failLoad = true; sessions += TrainingSession(5, "old", "Earlier workout", 2, importedLegacy = true) }
        val repo = PersistentTrainingRepository(store, backgroundScope) { 100L }
        runCurrent()
        assertFalse(repo.state.value.storageReady)
        repo.startWorkout("strength")
        assertNull(repo.state.value.activePlanId)
        assertFalse(repo.finishWorkout())
        store.failLoad = false
        repo.retryLoading()
        runCurrent()
        assertTrue(repo.state.value.storageReady)
        assertEquals(store.sessions, repo.state.value.sessions)
    }

    @Test fun failedProfileSaveDoesNotChangeTheSavedProfile() = runTest {
        val store = Store()
        val repo = PersistentTrainingRepository(store, backgroundScope) { 100L }
        runCurrent()
        val original = repo.state.value.profile
        store.failSave = true
        assertFalse(repo.updateProfile("Jo", "Strength", 4))
        assertEquals(original, repo.state.value.profile)
        store.failSave = false
        assertTrue(repo.updateProfile("Jo", "Strength", 4))
        assertEquals(store.profile, repo.state.value.profile)
    }
}
