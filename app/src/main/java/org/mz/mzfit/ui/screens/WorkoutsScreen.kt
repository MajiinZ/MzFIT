package org.mz.mzfit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mz.mzfit.data.BodyPart
import org.mz.mzfit.data.BodySide
import org.mz.mzfit.data.ExerciseLibrary
import org.mz.mzfit.data.WorkoutRepository
import org.mz.mzfit.ui.components.AiBadge
import org.mz.mzfit.ui.components.LocalAppContainer
import org.mz.mzfit.ui.components.SectionHeader
import org.mz.mzfit.ui.components.SurfaceCard
import org.mz.mzfit.ui.musclemap.FigureView
import org.mz.mzfit.ui.musclemap.MuscleFigure
import org.mz.mzfit.ui.theme.MzColors
import java.util.Calendar

@Composable
fun WorkoutsScreen(
    contentPadding: PaddingValues,
    onBodyPart: (BodyPart) -> Unit,
    onExercise: (String) -> Unit,
    onStartCoach: () -> Unit,
) {
    val logs by LocalAppContainer.current.workouts.logs.collectAsStateWithLifecycle()
    val summary = remember(logs) { WorkoutRepository.weeklySummary(logs) }
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 4..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening"
        }
    }

    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(greeting.uppercase(), style = MaterialTheme.typography.labelSmall, color = MzColors.TextMuted)
                Text("What are we training?", style = MaterialTheme.typography.headlineMedium)
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WeekStat("Sets", "${summary.sets}", Modifier.weight(1f))
                WeekStat("Reps", "${summary.reps}", Modifier.weight(1f))
                WeekStat("Days", "${summary.activeDays}/7", Modifier.weight(1f))
                WeekStat("Form", summary.avgForm?.toString() ?: "—", Modifier.weight(1f))
            }
        }
        item { CoachHero(onStartCoach, Modifier.padding(horizontal = 16.dp)) }
        item { SectionHeader("Train by body part") }
        items(BodyPart.entries.chunked(2)) { row ->
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { part -> BodyPartCard(part, Modifier.weight(1f)) { onBodyPart(part) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { SectionHeader("AI-coached moves") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(ExerciseLibrary.aiTracked.distinctBy { it.pattern }) { ex ->
                    SurfaceCard(Modifier.width(150.dp), onClick = { onExercise(ex.id) }) {
                        Box(Modifier.fillMaxWidth().height(120.dp).background(MzColors.Background), contentAlignment = Alignment.Center) {
                            val back = ex.primary.count { it.side == BodySide.BACK } > ex.primary.count { it.side == BodySide.FRONT }
                            MuscleFigure(if (back) FigureView.BACK else FigureView.FRONT,
                                org.mz.mzfit.ui.musclemap.exerciseColors(ex.primary, ex.secondary),
                                Modifier.padding(8.dp).fillMaxHeight(), animate = false)
                        }
                        Column(Modifier.padding(12.dp)) {
                            Text(ex.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Spacer(Modifier.height(6.dp))
                            AiBadge()
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun WeekStat(label: String, value: String, modifier: Modifier) {
    SurfaceCard(modifier) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MzColors.TextMuted)
        }
    }
}

@Composable
private fun CoachHero(onStart: () -> Unit, modifier: Modifier) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1F2A0A), Color(0xFF0E1A1A), MzColors.Surface)))
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = MzColors.Volt)
                Spacer(Modifier.width(8.dp))
                Text("AI FORM COACH", style = MaterialTheme.typography.labelSmall, color = MzColors.Volt)
            }
            Spacer(Modifier.height(8.dp))
            Text("Real-time rep counting & form correction", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text("Joint-angle tracking, depth meter and voice cues — 100% on-device.",
                style = MaterialTheme.typography.bodyMedium, color = MzColors.TextMuted)
            Spacer(Modifier.height(14.dp))
            Button(onStart) { Text("Start a coached set") }
        }
    }
}

@Composable
private fun BodyPartCard(part: BodyPart, modifier: Modifier, onClick: () -> Unit) {
    val muscles = remember(part) {
        if (part == BodyPart.FULL_BODY) ExerciseLibrary.forBodyPart(part).flatMap { it.primary }.toSet() else part.muscles.toSet()
    }
    val back = part == BodyPart.BACK || part == BodyPart.GLUTES
    val count = remember(part) { ExerciseLibrary.forBodyPart(part).size }
    SurfaceCard(modifier.height(150.dp), onClick = onClick) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).padding(14.dp)) {
                Text(part.displayName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(part.tagline, style = MaterialTheme.typography.bodySmall, color = MzColors.TextMuted, maxLines = 2)
                Spacer(Modifier.weight(1f))
                Text("$count EXERCISES", style = MaterialTheme.typography.labelSmall, color = MzColors.Volt)
            }
            MuscleFigure(
                if (back) FigureView.BACK else FigureView.FRONT,
                { m -> if (m in muscles) MzColors.Ember else MzColors.MuscleIdle },
                Modifier.fillMaxHeight().padding(vertical = 10.dp).padding(end = 10.dp), animate = false,
            )
        }
    }
}
