package com.mlpt.shared

import kotlin.test.*

class ExerciseLibraryTest {
    @Test fun everyMuscleHasPrimaryTargetExercisesBeforeSupportingMatches() {
        MuscleGroup.entries.forEach { muscle ->
            val matches = exercisesFor(muscle)
            assertTrue(matches.count { muscle in it.primary } >= 2, muscle.label)
            assertTrue(matches.all { muscle in it.primary || muscle in it.secondary })
            val firstSupporting = matches.indexOfFirst { muscle !in it.primary }
            if (firstSupporting >= 0) assertTrue(matches.drop(firstSupporting).all { muscle !in it.primary })
        }
    }

    @Test fun directArmExercisesDoNotConfusePrimaryAndSupportingTargets() {
        assertEquals(setOf(MuscleGroup.Biceps), exerciseLibrary.first { it.id == "curl" }.primary)
        val bench = exerciseLibrary.first { it.id == "bench" }
        assertTrue(MuscleGroup.Chest in bench.primary)
        assertTrue(MuscleGroup.Triceps in bench.secondary)
        assertFalse(MuscleGroup.Triceps in bench.primary)
        assertTrue(exerciseLibrary.all { guide -> guide.primary.intersect(guide.secondary).isEmpty() })
    }

    @Test fun allMainLiftsHaveExactlyOneAnimationAndUsableGuidance() {
        assertEquals(LiftMotion.entries.toSet(), exerciseLibrary.mapNotNull { it.motion }.toSet())
        assertEquals(LiftMotion.entries.size, exerciseLibrary.count { it.motion != null })
        assertEquals(exerciseLibrary.size, exerciseLibrary.map { it.id }.toSet().size)
        assertTrue(exerciseLibrary.all { it.primary.isNotEmpty() && it.steps.size >= 3 && it.equipment.isNotBlank() })
    }
}
