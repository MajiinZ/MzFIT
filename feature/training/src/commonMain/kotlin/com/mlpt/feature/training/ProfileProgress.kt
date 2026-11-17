package com.mlpt.feature.training

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mlpt.shared.*

@Composable
fun ProfileProgress(state: TrainingState, formatCompletedAt: (Long) -> String = { "Saved workout" }) {
    Text("See how far you’ve come.", style = MaterialTheme.typography.headlineLarge, color = Ink)
    Text("Progress from the workouts you complete. Each point represents a completed session.", color = SecondaryText)
    Surface(color = CardSurface, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Exercises completed over time", style = MaterialTheme.typography.titleLarge)
            Text("${state.sessions.sumOf { it.exerciseCount }} exercises · ${state.sessions.size} sessions", color = Ink)
            if (state.sessions.isEmpty()) {
                Text("Complete your first workout to start your progress graph.", color = SecondaryText)
            } else {
                val totals = state.sessions.runningFold(0) { total, session -> total + session.exerciseCount }.drop(1).takeLast(7)
                val labels = state.sessions.takeLast(7).map { "S${it.id}" }
                val summary = labels.zip(totals).joinToString { (label, total) -> "$label: $total exercises" }
                Canvas(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = "Cumulative exercises by session. $summary" }) {
                    val left = 10.dp.toPx()
                    val top = 12.dp.toPx()
                    val bottom = size.height - 12.dp.toPx()
                    val chartWidth = size.width - left * 2
                    repeat(4) { line ->
                        val y = top + (bottom - top) * line / 3
                        drawLine(Divider, Offset(left, y), Offset(size.width - left, y), 1.dp.toPx())
                    }
                    val points = totals.mapIndexed { index, total ->
                        Offset(if (totals.size == 1) size.width / 2 else left + chartWidth * index / (totals.size - 1),
                            bottom - (bottom - top) * total / totals.last().coerceAtLeast(1))
                    }
                    if (points.size > 1) {
                        val path = Path().apply { moveTo(points.first().x, points.first().y); points.drop(1).forEach { lineTo(it.x, it.y) } }
                        drawPath(path, PerformanceBlue, style = Stroke(width = 3.dp.toPx()))
                    }
                    points.forEach { drawCircle(PerformanceBlue, 5.dp.toPx(), it) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    labels.zip(totals).forEach { (label, count) ->
                        Column { Text(label, style = MaterialTheme.typography.labelSmall); Text("$count", color = SecondaryText, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Text("Last ${totals.size} completed sessions · Cumulative exercise count", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Surface(color = CardSurface, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Sessions by body part", style = MaterialTheme.typography.titleLarge)
            val counts = state.sessions.groupingBy { session -> session.bodyPart ?: if (session.importedLegacy) "Unspecified (imported)" else state.plans.find { it.id == session.planId }?.bodyPart ?: "Full body" }.eachCount()
            if (counts.isEmpty()) Text("Your completed workouts will show your training balance here.", color = SecondaryText)
            counts.forEach { (part, count) ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("$part · $count ${if (count == 1) "session" else "sessions"}")
                    LinearProgressIndicator(progress = { count.toFloat() / state.sessions.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().height(12.dp))
                }
            }
        }
    }
    WorkoutHistory(state.sessions, formatCompletedAt)
}

@Composable
private fun WorkoutHistory(sessions: List<TrainingSession>, formatCompletedAt: (Long) -> String) {
    var visibleCount by remember(sessions.size) { mutableStateOf(10) }
    Text("Completed workouts · ${sessions.size}", style = MaterialTheme.typography.headlineMedium)
    if (sessions.isEmpty()) Text("Complete a workout to start your workout history.", color = SecondaryText)
    sessions.asReversed().take(visibleCount).forEach { session ->
        var expanded by remember(session.id) { mutableStateOf(false) }
        Surface(color = CardSurface, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(session.title, style = MaterialTheme.typography.titleLarge)
                Text(session.completedAtEpochMillis?.let(formatCompletedAt) ?: if (session.importedLegacy) "Imported history · Completion date unavailable" else "Demo session", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Text("${session.exerciseCount} exercises completed" + (session.bodyPart?.let { " · $it" } ?: ""), color = PerformanceBlue)
                if (session.exercises.isNotEmpty()) {
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide exercises" else "View exercises") }
                    if (expanded) {
                        session.plannedMinutes?.let { Text("Planned duration: $it min", color = SecondaryText, style = MaterialTheme.typography.bodySmall) }
                        session.exercises.forEach { exercise -> Text("${exercise.name} · ${exercise.sets} sets · ${exercise.reps}") }
                    }
                } else if (session.importedLegacy) Text("Exercise details weren’t recorded in the earlier version.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (visibleCount < sessions.size) OutlinedButton(onClick = { visibleCount += 10 }) { Text("Show more workouts") }
}
