package com.mlpt.data

import com.mlpt.shared.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SavedTrainingData(val profile: ClientProfile?, val sessions: List<TrainingSession>)

interface TrainingStorage {
    suspend fun load(): SavedTrainingData
    suspend fun insertCompletedWorkout(workout: TrainingSession): TrainingSession
    suspend fun saveProfile(profile: ClientProfile)
}

/** Publishes success only after the local transaction commits. Platform stores own disk dispatch. */
class PersistentTrainingRepository(
    private val storage: TrainingStorage, private val scope: CoroutineScope,
    private val clockMillis: () -> Long,
) : TrainingRepository {
    private val mutable = MutableStateFlow(InMemoryTrainingRepository().state.value.copy(isLoading = true, storageReady = false))
    override val state = mutable.asStateFlow()
    private val mutex = Mutex()

    init { retryLoading() }

    override fun retryLoading() {
        scope.launch {
            mutex.withLock {
                if (mutable.value.storageReady) return@withLock
                mutable.update { it.copy(isLoading = true, storageError = null) }
                try {
                    val saved = storage.load()
                    mutable.update { it.copy(profile = saved.profile ?: it.profile, sessions = saved.sessions,
                        isLoading = false, storageReady = true, storageError = null) }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    mutable.update { it.copy(isLoading = false, storageReady = false,
                        storageError = "Couldn’t open your saved workout history. Retry to load it before making changes.") }
                }
            }
        }
    }

    override fun refreshHistory() {
        scope.launch { mutex.withLock {
            if (!mutable.value.storageReady) return@withLock
            try {
                val saved = storage.load()
                mutable.update { it.copy(profile = saved.profile ?: it.profile, sessions = saved.sessions, storageError = null) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.update { it.copy(storageError = "Couldn’t refresh saved workouts. Reopen the app to retry.") }
            }
        } }
    }

    override fun startWorkout(planId: String) {
        val current = mutable.value
        if (!current.storageReady || current.isSaving) return
        require(current.plans.any { it.id == planId }) { "Unknown workout" }
        mutable.update { it.copy(activePlanId = planId, completedExerciseIds = emptySet()) }
    }

    override fun toggleExercise(exerciseId: String) {
        mutable.update { current ->
            if (!current.storageReady || current.isSaving) return@update current
            val plan = current.plans.find { it.id == current.activePlanId } ?: return@update current
            require(plan.exercises.any { it.id == exerciseId }) { "Exercise is not in this workout" }
            current.copy(completedExerciseIds = if (exerciseId in current.completedExerciseIds)
                current.completedExerciseIds - exerciseId else current.completedExerciseIds + exerciseId)
        }
    }

    // A navigation cancellation must not interrupt the commit/publication boundary.
    override suspend fun finishWorkout(): Boolean = withContext(NonCancellable) {
        mutex.withLock {
            val current = mutable.value
            if (!current.storageReady) return@withLock false
            val plan = current.plans.find { it.id == current.activePlanId } ?: return@withLock false
            if (!plan.exercises.all { it.id in current.completedExerciseIds }) return@withLock false
            mutable.update { it.copy(isSaving = true, storageError = null) }
            try {
                val draft = TrainingSession(0, plan.id, plan.title, plan.exercises.size, clockMillis(), plan.bodyPart, plan.minutes, plan.exercises.toList())
                val saved = storage.insertCompletedWorkout(draft)
                mutable.update { it.copy(sessions = it.sessions + saved, activePlanId = null,
                    completedExerciseIds = emptySet(), isSaving = false, storageError = null) }
                true
            } catch (error: Exception) {
                mutable.update { it.copy(isSaving = false, storageError = "Workout wasn’t saved. Your checklist is still here. Tap Complete session to retry.") }
                if (error is CancellationException) throw error
                false
            }
        }
    }

    override suspend fun updateProfile(name: String, goal: String, weeklyTarget: Int): Boolean = withContext(NonCancellable) {
        require(name.isNotBlank() && goal.isNotBlank() && weeklyTarget in 1..7)
        mutex.withLock {
            if (!mutable.value.storageReady) return@withLock false
            val profile = ClientProfile(name.trim(), goal.trim(), weeklyTarget)
            mutable.update { it.copy(isSaving = true, storageError = null) }
            try {
                storage.saveProfile(profile)
                mutable.update { it.copy(profile = profile, isSaving = false) }
                true
            } catch (error: Exception) {
                mutable.update { it.copy(isSaving = false, storageError = "Profile wasn’t saved. Tap Save profile to retry.") }
                if (error is CancellationException) throw error
                false
            }
        }
    }
}
