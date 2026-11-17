package com.mlpt.data

data class LiftPoint(val workoutId: Int, val completedAt: Long?, val heaviestKg: Double, val volumeKg: Double)
data class LiftRecord(val key: String, val name: String, val heaviestKg: Double, val repsAtHeaviest: Int,
    val externalVolumeKg: Double, val points: List<LiftPoint>)

/** Recorded external-load volume only; body mass, machine ratios and timed holds are not inferred. */
fun liftRecords(sets: List<PerformanceSet>): List<LiftRecord> = sets.filter { it.reps != null && it.reps > 0 && it.weightKg != null && it.weightKg.isFinite() && it.weightKg > 0 }
    .groupBy { exerciseKey(it.exercise) }.map { (key, entries) ->
        val max = entries.maxOf { it.weightKg!! }
        val points = entries.groupBy { it.workoutId }.entries.sortedBy { it.key }.map { (id, values) ->
            LiftPoint(id, values.first().completedAt, values.maxOf { it.weightKg!! }, values.sumOf { it.weightKg!! * it.reps!! })
        }
        LiftRecord(key, entries.first().exercise, max, entries.filter { it.weightKg == max }.maxOf { it.reps!! },
            points.sumOf { it.volumeKg }, points)
    }.sortedBy { it.name.lowercase() }
