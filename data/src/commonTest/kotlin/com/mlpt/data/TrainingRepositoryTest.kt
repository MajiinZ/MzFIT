package com.mlpt.data

import kotlin.test.*
import kotlinx.coroutines.test.runTest

class TrainingRepositoryTest {
    @Test fun restoredHistoryContinuesWithoutLosingProfileOrSessions() = runTest {
        val original = InMemoryTrainingRepository()
        original.updateProfile("Sam", "Build strength", 4)
        val plan = original.state.value.plans.first()
        original.startWorkout(plan.id)
        plan.exercises.forEach { original.toggleExercise(it.id) }
        original.finishWorkout()
        val saved = original.state.value
        val restored = InMemoryTrainingRepository(saved.profile, saved.sessions)
        assertEquals(saved.profile, restored.state.value.profile)
        assertEquals(saved.sessions, restored.state.value.sessions)
        assertNull(restored.state.value.activePlanId)
        restored.startWorkout(plan.id)
        plan.exercises.forEach { restored.toggleExercise(it.id) }
        assertTrue(restored.finishWorkout())
        assertEquals(listOf(1, 2), restored.state.value.sessions.map { it.id })
    }

    @Test fun everyBodyPartHasAWorkoutThatCanBeCompleted() = runTest {
        val repo = InMemoryTrainingRepository()
        val bodyParts = setOf("Chest", "Back", "Legs", "Shoulders", "Arms", "Core")
        assertTrue(repo.state.value.plans.map { it.bodyPart }.containsAll(bodyParts))
        repo.state.value.plans.filter { it.bodyPart in bodyParts }.forEach { plan ->
            repo.startWorkout(plan.id)
            plan.exercises.forEach { repo.toggleExercise(it.id) }
            assertTrue(repo.finishWorkout())
        }
        assertEquals(bodyParts.size, repo.state.value.sessions.size)
        assertEquals(bodyParts, repo.state.value.sessions.map { session -> repo.state.value.plans.first { it.id == session.planId }.bodyPart }.toSet())
    }

    @Test fun onlyACompletedWorkoutCanBeLoggedAndCannotBeLoggedTwice() = runTest {
        val repo = InMemoryTrainingRepository()
        val plan = repo.state.value.plans.first()
        repo.startWorkout(plan.id)
        assertFalse(repo.finishWorkout())
        plan.exercises.forEach { repo.toggleExercise(it.id) }
        assertTrue(repo.finishWorkout())
        assertEquals(1, repo.state.value.sessions.size)
        assertNull(repo.state.value.activePlanId)
        assertFalse(repo.finishWorkout())
    }

    @Test fun switchingWorkoutsResetsChecklistAndRejectsUnrelatedExercises() = runTest {
        val repo = InMemoryTrainingRepository()
        repo.startWorkout("strength")
        repo.toggleExercise("squat")
        repo.startWorkout("mobility")
        assertTrue(repo.state.value.completedExerciseIds.isEmpty())
        assertFailsWith<IllegalArgumentException> { repo.toggleExercise("squat") }
        assertFailsWith<IllegalArgumentException> { repo.startWorkout("missing") }
    }

    @Test fun profileValidationPreservesPreviousValues() = runTest {
        val repo = InMemoryTrainingRepository()
        val original = repo.state.value.profile
        assertFailsWith<IllegalArgumentException> { repo.updateProfile("", "Strength", 3) }
        assertFailsWith<IllegalArgumentException> { repo.updateProfile("Alex", "Strength", 0) }
        assertEquals(original, repo.state.value.profile)
        repo.updateProfile(" Jo ", " Mobility ", 4)
        assertEquals("Jo", repo.state.value.profile.name)
        assertEquals(4, repo.state.value.profile.weeklyTarget)
    }
}
