package com.mlpt.feature.training

import com.mlpt.shared.*
import kotlin.test.*

class MuscleMapTest {
    @Test fun frontMapRoutesTapsToTheCorrectExerciseTargets() {
        val taps = listOf(
            Triple(104f, 95f, MuscleGroup.Shoulders), Triple(130f, 110f, MuscleGroup.Chest),
            Triple(101f, 132f, MuscleGroup.Biceps), Triple(150f, 155f, MuscleGroup.Abs),
            Triple(124f, 165f, MuscleGroup.Obliques), Triple(128f, 260f, MuscleGroup.Quads),
            Triple(130f, 350f, MuscleGroup.Calves),
        )
        taps.forEach { (x, y, muscle) ->
            assertEquals(muscle, muscleAt(false, MapPoint(x, y)))
            assertEquals(muscle, muscleAt(false, MapPoint(300f - x, y)))
            assertTrue(exercisesFor(muscle).any { muscle in it.primary })
        }
    }

    @Test fun backMapIdentifiesPosteriorMusclesAndRejectsEmptySpace() {
        listOf(Triple(150f, 100f, MuscleGroup.UpperBack), Triple(130f, 151f, MuscleGroup.Lats),
            Triple(101f, 132f, MuscleGroup.Triceps), Triple(133f, 207f, MuscleGroup.Glutes),
            Triple(130f, 265f, MuscleGroup.Hamstrings)).forEach { (x, y, muscle) ->
            assertEquals(muscle, muscleAt(true, MapPoint(x, y)))
        }
        assertNull(muscleAt(false, MapPoint(0f, 0f)))
        assertNull(muscleAt(true, MapPoint(150f, 40f)))
        assertEquals(MuscleGroup.entries.toSet(), (muscleRegions(false) + muscleRegions(true)).map { it.muscle }.toSet())
    }

    @Test fun animationsStayInFrameAndChangeThroughTheMovement() {
        LiftMotion.entries.forEach { motion ->
            val (start, end) = liftKeyframes(motion)
            assertNotEquals(start, end)
            assertEquals(start, liftPoseAt(motion, 0f))
            assertEquals(end, liftPoseAt(motion, 1f))
            (0..20).forEach { step ->
                assertTrue(liftPoseAt(motion, step / 20f).points().all { it.x.isFinite() && it.y.isFinite() && it.x in 0f..320f && it.y in 0f..260f })
            }
        }
    }
}
