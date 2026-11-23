package org.mz.mzfit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRepositoryTest {
    private val now = 1_800_000_000_000L
    private fun log(id: String, daysAgo: Int, form: Int = 90) = WorkoutLog(
        exerciseId = id, timestamp = now - daysAgo * 86_400_000L, reps = 10, goodReps = 9,
        formScore = form, durationSec = 40, aiTracked = true,
    )

    @Test fun muscleLoadWeightsPrimaryAndSecondary() {
        val load = WorkoutRepository.muscleLoad(listOf(log("push_up", 1), log("push_up", 2)), now)
        // push-up: LOWER_CHEST primary (2 sets), TRICEPS secondary (2 × 0.5)
        assertEquals(2f / WorkoutRepository.TARGET_WEEKLY_SETS, load[Muscle.LOWER_CHEST]!!, 0.001f)
        assertEquals(1f / WorkoutRepository.TARGET_WEEKLY_SETS, load[Muscle.TRICEPS]!!, 0.001f)
        assertNull(load[Muscle.HAMSTRINGS])
    }

    @Test fun muscleLoadIgnoresOldSessions() {
        val load = WorkoutRepository.muscleLoad(listOf(log("push_up", 10)), now)
        assertTrue(load.isEmpty())
    }

    @Test fun weeklySummary() {
        val s = WorkoutRepository.weeklySummary(listOf(log("push_up", 0, 80), log("db_curl", 0, 100), log("plank", 3, -1)), now)
        assertEquals(3, s.sets)
        assertEquals(2, s.activeDays)
        assertEquals(90, s.avgForm)
    }

    @Test fun libraryIsConsistent() {
        val ids = ExerciseLibrary.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ExerciseLibrary.all.forEach { assertTrue(it.name, it.primary.isNotEmpty()) }
        BodyPart.entries.forEach { assertTrue(it.name, ExerciseLibrary.forBodyPart(it).isNotEmpty()) }
        // Every muscle drawn on the map can be trained by at least one exercise.
        Muscle.entries.filter { it != Muscle.NECK }.forEach { assertTrue(it.name, ExerciseLibrary.targeting(it).isNotEmpty()) }
    }
}
