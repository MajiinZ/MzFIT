package org.mz.mzfit.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mz.mzfit.data.ExerciseLibrary
import org.mz.mzfit.data.Muscle
import org.mz.mzfit.data.WorkoutRepository
import org.mz.mzfit.ui.components.ExerciseRow
import org.mz.mzfit.ui.components.LocalAppContainer
import org.mz.mzfit.ui.components.SurfaceCard
import org.mz.mzfit.ui.musclemap.MuscleMapPair
import org.mz.mzfit.ui.musclemap.loadColor
import org.mz.mzfit.ui.theme.MzColors
import kotlin.math.roundToInt

private enum class MapMode(val label: String) { LOAD("This week"), EXPLORE("Explore") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleMapScreen(contentPadding: PaddingValues, onExercise: (String) -> Unit) {
    val logs by LocalAppContainer.current.workouts.logs.collectAsStateWithLifecycle()
    val load = remember(logs) { WorkoutRepository.muscleLoad(logs) }
    var mode by rememberSaveable { mutableStateOf(MapMode.LOAD) }
    var selected by rememberSaveable { mutableStateOf<Muscle?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            MapMode.entries.forEachIndexed { i, m ->
                SegmentedButton(mode == m, { mode = m }, SegmentedButtonDefaults.itemShape(i, MapMode.entries.size)) { Text(m.label) }
            }
        }
        Spacer(Modifier.height(16.dp))

        val colorFor: (Muscle) -> androidx.compose.ui.graphics.Color = when (mode) {
            MapMode.LOAD -> { m -> loadColor(load[m] ?: 0f) }
            MapMode.EXPLORE -> { m -> if (m == selected) MzColors.Ember else if (selected != null && m.bodyPart == selected?.bodyPart) MzColors.Amber.copy(alpha = 0.45f) else MzColors.MuscleIdle }
        }
        MuscleMapPair(colorFor, Modifier.height(380.dp).padding(horizontal = 12.dp), selected = selected, onMuscleTap = { selected = if (selected == it) null else it })
        Spacer(Modifier.height(10.dp))

        if (mode == MapMode.LOAD) {
            HeatLegend(Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(6.dp))
            Text("Weekly volume per muscle over the last 7 days. Primary movers count as a full set, synergists as half. " +
                "Tap any muscle for details.",
                style = MaterialTheme.typography.bodySmall, color = MzColors.TextMuted, modifier = Modifier.padding(horizontal = 24.dp))
        } else {
            Text("Tap any muscle to see what it does and how to train it.",
                style = MaterialTheme.typography.bodySmall, color = MzColors.TextMuted, modifier = Modifier.padding(horizontal = 24.dp))
        }
        Spacer(Modifier.height(16.dp))

        AnimatedContent(selected, label = "muscle") { m ->
            if (m != null) MuscleDetail(m, load[m] ?: 0f, onExercise)
            else if (mode == MapMode.LOAD) LoadBreakdown(load) { selected = it }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HeatLegend(modifier: Modifier) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            .background(Brush.horizontalGradient(listOf(MzColors.MuscleIdle, loadColor(0.05f), loadColor(0.5f), loadColor(1f)))))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("Fresh", style = MaterialTheme.typography.labelSmall, color = MzColors.TextMuted, modifier = Modifier.weight(1f))
            Text("${WorkoutRepository.TARGET_WEEKLY_SETS.toInt()}+ sets", style = MaterialTheme.typography.labelSmall, color = MzColors.TextMuted)
        }
    }
}

@Composable
private fun LoadBreakdown(load: Map<Muscle, Float>, onSelect: (Muscle) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        val trained = Muscle.entries.sortedByDescending { load[it] ?: 0f }
        val needs = Muscle.entries.filter { (load[it] ?: 0f) < 0.3f }
        SurfaceCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Volume by muscle", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                trained.forEach { m ->
                    val v = load[m] ?: 0f
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onSelect(m) }.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(m.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp))
                        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MzColors.SurfaceHighest)) {
                            Box(Modifier.fillMaxWidth(v.coerceAtLeast(0.01f)).fillMaxHeight().background(loadColor(v)))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("${(v * WorkoutRepository.TARGET_WEEKLY_SETS).roundToInt()} sets", style = MaterialTheme.typography.bodySmall,
                            color = MzColors.TextMuted, modifier = Modifier.width(52.dp))
                    }
                }
            }
        }
        if (needs.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Needs attention: " + needs.joinToString { it.displayName },
                style = MaterialTheme.typography.bodySmall, color = MzColors.Amber)
        }
    }
}

@Composable
private fun MuscleDetail(m: Muscle, load: Float, onExercise: (String) -> Unit) {
    val exercises = remember(m) { ExerciseLibrary.targeting(m).take(6) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        SurfaceCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(m.bodyPart.displayName.uppercase(), style = MaterialTheme.typography.labelSmall, color = MzColors.Volt)
                Text(m.displayName, style = MaterialTheme.typography.headlineSmall)
                Text(m.anatomicalName, style = MaterialTheme.typography.bodySmall, color = MzColors.TextMuted)
                Spacer(Modifier.height(10.dp))
                Text(m.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text("This week: ${(load * WorkoutRepository.TARGET_WEEKLY_SETS).roundToInt()} of ${WorkoutRepository.TARGET_WEEKLY_SETS.toInt()} target sets",
                    style = MaterialTheme.typography.labelLarge, color = loadColor(load.coerceAtLeast(0.3f)))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Best exercises", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(8.dp))
        exercises.forEach { ExerciseRow(it, { onExercise(it.id) }, Modifier.padding(vertical = 5.dp)) }
    }
}
