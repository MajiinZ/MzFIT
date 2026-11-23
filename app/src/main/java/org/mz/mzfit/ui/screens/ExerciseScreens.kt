package org.mz.mzfit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mz.mzfit.data.BodyPart
import org.mz.mzfit.data.Equipment
import org.mz.mzfit.data.Exercise
import org.mz.mzfit.data.ExerciseLibrary
import org.mz.mzfit.data.WorkoutLog
import org.mz.mzfit.ui.components.AiBadge
import org.mz.mzfit.ui.components.ExerciseRow
import org.mz.mzfit.ui.components.Legend
import org.mz.mzfit.ui.components.LocalAppContainer
import org.mz.mzfit.ui.components.Pill
import org.mz.mzfit.ui.components.SurfaceCard
import org.mz.mzfit.ui.musclemap.MuscleMapPair
import org.mz.mzfit.ui.musclemap.exerciseColors
import org.mz.mzfit.ui.theme.MzColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyPartScreen(part: BodyPart, contentPadding: PaddingValues, onExercise: (String) -> Unit) {
    val all = remember(part) { ExerciseLibrary.forBodyPart(part) }
    val equipment = remember(all) { all.map { it.equipment }.distinct() }
    var filter by remember { mutableStateOf<Equipment?>(null) }
    var aiOnly by remember { mutableStateOf(false) }
    val list = all.filter { (filter == null || it.equipment == filter) && (!aiOnly || it.isAiTracked) }
    val muscles = remember(part) {
        if (part == BodyPart.FULL_BODY) all.flatMap { it.primary }.toSet() else part.muscles.toSet()
    }

    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(part.tagline, style = MaterialTheme.typography.bodyMedium, color = MzColors.TextMuted)
                Spacer(Modifier.height(12.dp))
                MuscleMapPair({ if (it in muscles) MzColors.Ember else MzColors.MuscleIdle }, Modifier.height(240.dp))
                Spacer(Modifier.height(8.dp))
                Text(muscles.joinToString(" · ") { it.displayName }, style = MaterialTheme.typography.bodySmall, color = MzColors.Ember)
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(aiOnly, { aiOnly = !aiOnly }, label = { Text("AI Coach") },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp)) })
                }
                item { FilterChip(filter == null, { filter = null }, label = { Text("All") }) }
                items(equipment) { e -> FilterChip(filter == e, { filter = if (filter == e) null else e }, label = { Text(e.displayName) }) }
            }
        }
        items(list, key = { it.id }) { ex ->
            ExerciseRow(ex, { onExercise(ex.id) }, Modifier.padding(horizontal = 16.dp))
        }
        if (list.isEmpty()) item {
            Text("No exercises match these filters.", Modifier.padding(20.dp), color = MzColors.TextMuted)
        }
    }
}

@Composable
fun ExerciseDetailScreen(ex: Exercise, contentPadding: PaddingValues, onStartCoach: (String) -> Unit, onExercise: (String) -> Unit) {
    val container = LocalAppContainer.current
    var showLog by remember { mutableStateOf(false) }
    var logged by remember { mutableStateOf(false) }
    val similar = remember(ex) {
        ExerciseLibrary.all.filter { it.id != ex.id && it.primary.any { m -> m in ex.primary } }.take(4)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (ex.isAiTracked) AiBadge()
            Pill(ex.bodyPart.displayName.uppercase())
            Pill(ex.equipment.displayName.uppercase())
            Pill(ex.level.displayName.uppercase())
        }
        Spacer(Modifier.height(16.dp))
        SurfaceCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                MuscleMapPair(exerciseColors(ex.primary, ex.secondary), Modifier.height(280.dp))
                Spacer(Modifier.height(12.dp))
                Legend(listOf(MzColors.MusclePrimary to "Primary", MzColors.MuscleSecondary to "Secondary", MzColors.MuscleIdle to "Not targeted"))
            }
        }
        Spacer(Modifier.height(16.dp))
        MuscleList("Primary movers", ex.primary.map { it.displayName to it.anatomicalName }, MzColors.MusclePrimary)
        if (ex.secondary.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            MuscleList("Synergists & stabilizers", ex.secondary.map { it.displayName to it.anatomicalName }, MzColors.MuscleSecondary)
        }

        Spacer(Modifier.height(20.dp))
        Text("How to", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        ex.steps.forEachIndexed { i, step ->
            Row(Modifier.padding(vertical = 6.dp)) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(MzColors.SurfaceHighest), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = MzColors.Volt)
                }
                Spacer(Modifier.width(12.dp))
                Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Coaching cues", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        ex.cues.forEach { Text("•  $it", style = MaterialTheme.typography.bodyLarge, color = MzColors.TextMuted, modifier = Modifier.padding(vertical = 3.dp)) }

        Spacer(Modifier.height(16.dp))
        SurfaceCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                Target("${ex.defaultSets}", "Sets"); Target("${ex.defaultReps}${if (ex.isHold) "s" else ""}", if (ex.isHold) "Hold" else "Reps")
                Target(if (ex.level.ordinal == 0) "60s" else "90s", "Rest")
            }
        }

        Spacer(Modifier.height(20.dp))
        if (ex.isAiTracked) {
            Button({ onStartCoach(ex.id) }, Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Start with AI Coach")
            }
            ex.pattern?.let {
                Text(it.cameraSetup, style = MaterialTheme.typography.bodySmall, color = MzColors.TextMuted,
                    modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(10.dp))
        }
        OutlinedButton({ showLog = true }, Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Default.Edit, null); Spacer(Modifier.width(8.dp)); Text(if (logged) "Set logged — log another" else "Log a set manually")
        }

        if (similar.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Similar exercises", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            similar.forEach { ExerciseRow(it, { onExercise(it.id) }, Modifier.padding(vertical = 5.dp)) }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showLog) {
        ManualLogDialog(ex, onDismiss = { showLog = false }) { reps, kg ->
            container.workouts.add(
                WorkoutLog(exerciseId = ex.id, timestamp = System.currentTimeMillis(), reps = reps, goodReps = reps,
                    formScore = -1, durationSec = 0, weightKg = kg, aiTracked = false)
            )
            logged = true; showLog = false
        }
    }
}

@Composable
private fun MuscleList(title: String, items: List<Pair<String, String>>, color: androidx.compose.ui.graphics.Color) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MzColors.TextMuted)
    Spacer(Modifier.height(6.dp))
    items.forEach { (name, latin) ->
        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
            Spacer(Modifier.width(10.dp))
            Text(name, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(8.dp))
            Text(latin, style = MaterialTheme.typography.bodySmall, color = MzColors.TextMuted)
        }
    }
}

@Composable
private fun Target(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MzColors.TextMuted)
    }
}

@Composable
private fun ManualLogDialog(ex: Exercise, onDismiss: () -> Unit, onSave: (Int, Float) -> Unit) {
    var reps by remember { mutableStateOf(ex.defaultReps.toString()) }
    var kg by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log ${ex.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(reps, { reps = it.filter(Char::isDigit).take(4) },
                    label = { Text(if (ex.isHold) "Seconds" else "Reps") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(kg, { kg = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                    label = { Text("Weight (kg, optional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            TextButton({ reps.toIntOrNull()?.takeIf { it > 0 }?.let { onSave(it, kg.toFloatOrNull() ?: 0f) } }) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
