package com.mlpt.shared

data class Exercise(val id: String, val name: String, val sets: Int, val reps: String) {
    init { require(sets > 0) }
}
data class WorkoutPlan(
    val id: String, val title: String, val category: String,
    val minutes: Int, val description: String, val exercises: List<Exercise>,
    val bodyPart: String = "Full body",
)
data class TrainingSession(
    val id: Int, val planId: String, val title: String, val exerciseCount: Int,
    val completedAtEpochMillis: Long? = null, val bodyPart: String? = null,
    val plannedMinutes: Int? = null, val exercises: List<Exercise> = emptyList(),
    val importedLegacy: Boolean = false,
)
data class ClientProfile(val name: String, val goal: String, val weeklyTarget: Int)
data class TrainingState(
    val profile: ClientProfile, val plans: List<WorkoutPlan>,
    val sessions: List<TrainingSession> = emptyList(), val completedExerciseIds: Set<String> = emptySet(),
    val activePlanId: String? = null,
    val isLoading: Boolean = false, val storageReady: Boolean = true,
    val isSaving: Boolean = false, val storageError: String? = null,
)

interface TrainingRepository {
    val state: kotlinx.coroutines.flow.StateFlow<TrainingState>
    fun startWorkout(planId: String)
    fun toggleExercise(exerciseId: String)
    suspend fun finishWorkout(): Boolean
    suspend fun updateProfile(name: String, goal: String, weeklyTarget: Int): Boolean
    fun retryLoading() {}
    fun refreshHistory() {}
}
