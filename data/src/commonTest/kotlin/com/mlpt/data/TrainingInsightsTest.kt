package com.mlpt.data

import kotlin.test.*

class TrainingInsightsTest {
    @Test fun heaviestLoadAndVolumeUseOnlyValidRecordedExternalLoads() {
        val records = liftRecords(listOf(
            PerformanceSet(1, "Bench press", 1000, 1, 10, null, 40.0),
            PerformanceSet(1, "Bench press", 1000, 2, 8, null, 45.0),
            PerformanceSet(2, " bench   press ", 2000, 1, 10, null, 45.0),
            PerformanceSet(2, "Bench press", 2000, 2, 10, null, null),
            PerformanceSet(2, "Plank", 2000, 1, null, 60, null),
            PerformanceSet(2, "Bad entry", 2000, 1, 10, null, Double.NaN)))
        val record = records.single()
        assertEquals(45.0, record.heaviestKg); assertEquals(10, record.repsAtHeaviest)
        assertEquals(1210.0, record.externalVolumeKg); assertEquals(2, record.points.size)
        assertEquals(760.0, record.points.first().volumeKg)
    }
    @Test fun exercisesAreNeverMergedBySimilarNamesOrEstimatedBodyMass() {
        val records = liftRecords(listOf(
            PerformanceSet(1, "Dumbbell press", null, 1, 8, null, 20.0),
            PerformanceSet(2, "Barbell press", 2000, 1, 5, null, 40.0),
            PerformanceSet(3, "Push-up", 3000, 1, 20, null, null)))
        assertEquals(2, records.size); assertEquals(setOf("dumbbell press", "barbell press"), records.map { it.key }.toSet())
    }
}
