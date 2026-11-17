package com.mlpt.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mlpt.shared.TrainingSession
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*

@RunWith(AndroidJUnit4::class)
class TrainingCalendarTest {
    private fun session(id: Int, date: String?) = TrainingSession(id, "p", "Test", 1, date?.let { Instant.parse(it).toEpochMilli() })
    @Test fun weekUsesLocalMondayAndExcludesFutureAndUndatedSessions() {
        val now = Instant.parse("2026-11-17T00:30:00Z")
        val calendar = trainingCalendar(listOf(session(1, "2026-11-16T08:05:00Z"), session(2, "2026-11-16T07:50:00Z"), session(3, "2026-11-18T08:00:00Z"), session(4, null)), now, ZoneId.of("America/Los_Angeles"))
        assertEquals(LocalDate.of(2026, 11, 16), calendar.weekStart)
        assertEquals(1, calendar.thisWeek); assertEquals(1, calendar.undated)
        assertEquals(28, calendar.days.size); assertEquals(1, calendar.days.last().count)
    }
    @Test fun dstBoundaryAndMultipleSameDaySessionsAreCountedByLocalDate() {
        val calendar = trainingCalendar(listOf(session(1, "2026-11-01T08:30:00Z"), session(2, "2026-11-01T09:30:00Z")), Instant.parse("2026-11-01T12:00:00Z"), ZoneId.of("America/Los_Angeles"))
        assertEquals(2, calendar.thisWeek); assertEquals(2, calendar.days.last().count)
    }
}
