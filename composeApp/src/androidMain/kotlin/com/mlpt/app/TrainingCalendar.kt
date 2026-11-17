package com.mlpt.app

import com.mlpt.shared.TrainingSession
import java.time.*
import java.time.temporal.TemporalAdjusters

data class CalendarDay(val date: LocalDate, val count: Int)
data class TrainingCalendar(val weekStart: LocalDate, val thisWeek: Int, val days: List<CalendarDay>, val undated: Int)

fun trainingCalendar(sessions: List<TrainingSession>, now: Instant, zone: ZoneId): TrainingCalendar {
    val today = now.atZone(zone).toLocalDate()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val dates = sessions.mapNotNull { s -> s.completedAtEpochMillis?.let { Instant.ofEpochMilli(it) }?.takeIf { !it.isAfter(now) }?.atZone(zone)?.toLocalDate() }
    val counts = dates.groupingBy { it }.eachCount()
    return TrainingCalendar(weekStart, dates.count { !it.isBefore(weekStart) },
        (27L downTo 0L).map { CalendarDay(today.minusDays(it), counts[today.minusDays(it)] ?: 0) }, sessions.count { it.completedAtEpochMillis == null })
}
