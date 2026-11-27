package com.mlpt.data

import com.mlpt.shared.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Demo only: changes last for the current app process. Replace at the DI boundary. */
class InMemoryTrainingRepository(savedProfile: ClientProfile? = null, savedSessions: List<TrainingSession> = emptyList()) : TrainingRepository {
    private val mutable = MutableStateFlow(TrainingState(
        profile = savedProfile ?: ClientProfile("Alex", "Build strength & consistency", 3),
        sessions = savedSessions,
        plans = listOf(
            WorkoutPlan("strength", "Foundations of strength", "Strength", 45,
                "A balanced full-body session. Move with intention and make every rep count.", listOf(
                    Exercise("squat", "Goblet squat", 3, "10–12"),
                    Exercise("row", "Dumbbell row", 3, "10 each side"),
                    Exercise("press", "Dumbbell floor press", 3, "10–12"),
                    Exercise("plank", "Forearm plank", 3, "30 seconds"),
                )),
            WorkoutPlan("mobility", "Room to move", "Mobility", 20,
                "Slow down with a gentle mobility session for your hips, spine and shoulders.", listOf(
                    Exercise("cat", "Cat–cow", 2, "8 slow reps"),
                    Exercise("lunge", "Low lunge stretch", 2, "30 seconds each side"),
                    Exercise("rotation", "Thoracic rotation", 2, "8 each side"),
                )),
            WorkoutPlan("conditioning", "Find your rhythm", "Conditioning", 30,
                "Steady, accessible conditioning. Choose a pace that feels right for you.", listOf(
                    Exercise("walk", "Brisk walk warm-up", 1, "5 minutes"),
                    Exercise("step", "Step-ups", 3, "45 seconds"),
                    Exercise("march", "Standing march", 3, "60 seconds"),
                )),
        ) + bodyPartPlans(),
    ))
    override val state = mutable.asStateFlow()

    override fun startWorkout(planId: String) {
        require(mutable.value.plans.any { it.id == planId }) { "Unknown workout" }
        mutable.update { it.copy(activePlanId = planId, completedExerciseIds = emptySet()) }
    }

    override fun toggleExercise(exerciseId: String) {
        mutable.update { current ->
            val plan = current.plans.find { it.id == current.activePlanId } ?: return@update current
            require(plan.exercises.any { it.id == exerciseId }) { "Exercise is not in this workout" }
            val checked = current.completedExerciseIds
            current.copy(completedExerciseIds = if (exerciseId in checked) checked - exerciseId else checked + exerciseId)
        }
    }

    override suspend fun finishWorkout(): Boolean {
        val current = mutable.value
        val plan = current.plans.find { it.id == current.activePlanId } ?: return false
        if (!plan.exercises.all { it.id in current.completedExerciseIds }) return false
        mutable.value = current.copy(
            sessions = current.sessions + TrainingSession((current.sessions.maxOfOrNull { it.id } ?: 0) + 1, plan.id, plan.title, plan.exercises.size,
                bodyPart = plan.bodyPart, plannedMinutes = plan.minutes, exercises = plan.exercises.toList()),
            activePlanId = null, completedExerciseIds = emptySet(),
        )
        return true
    }

    override suspend fun updateProfile(name: String, goal: String, weeklyTarget: Int): Boolean {
        require(name.isNotBlank() && goal.isNotBlank() && weeklyTarget in 1..7)
        mutable.update { it.copy(profile = ClientProfile(name.trim(), goal.trim(), weeklyTarget)) }
        return true
    }
}

private fun bodyPartPlans() = listOf(
    Triple("Chest", "Chest foundations", listOf("Incline push-up", "Dumbbell floor press", "Wall push-up")),
    Triple("Back", "A stronger back", listOf("Dumbbell row", "Band pull-apart", "Bird dog")),
    Triple("Legs", "Lower-body foundations", listOf("Goblet squat", "Glute bridge", "Standing calf raise")),
    Triple("Shoulders", "Shoulder strength", listOf("Seated dumbbell press", "Lateral raise", "Wall slide")),
    Triple("Arms", "Arms in motion", listOf("Dumbbell curl", "Triceps kickback", "Hammer curl")),
    Triple("Core", "Steady at the center", listOf("Dead bug", "Forearm plank", "Side plank")),
).map { (bodyPart, title, exercises) ->
    val id = bodyPart.lowercase()
    WorkoutPlan(id, title, "Strength", 25, "A focused session for ${bodyPart.lowercase()}. Choose a comfortable resistance and controlled pace.",
        exercises.mapIndexed { index, name -> Exercise("$id-$index", name, 3, if (name.contains("plank")) "20–30 seconds" else "8–12") }, bodyPart)
}
