package org.mz.mzfit.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mz.mzfit.BuildConfig
import org.mz.mzfit.data.CameraFacing
import org.mz.mzfit.data.WorkoutLog
import org.mz.mzfit.ui.components.AiBadge
import org.mz.mzfit.ui.components.LocalAppContainer
import org.mz.mzfit.ui.components.SurfaceCard
import org.mz.mzfit.ui.screens.session.formatTime
import org.mz.mzfit.ui.screens.session.scoreColor
import org.mz.mzfit.ui.theme.MzColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(contentPadding: PaddingValues, onExercise: (String) -> Unit) {
    val repo = LocalAppContainer.current.workouts
    val logs by repo.logs.collectAsStateWithLifecycle()
    val dayFmt = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val grouped = remember(logs) { logs.groupBy { dayFmt.format(Date(it.timestamp)) } }

    if (logs.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(contentPadding).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.History, null, tint = MzColors.TextMuted, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("No workouts yet", style = MaterialTheme.typography.titleLarge)
            Text("Finish a coached set or log one manually and it'll show up here.",
                style = MaterialTheme.typography.bodyMedium, color = MzColors.TextMuted)
        }
        return
    }

    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        grouped.forEach { (day, list) ->
            item {
                Text(day.uppercase(), style = MaterialTheme.typography.labelSmall, color = MzColors.TextMuted,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
            }
            items(list, key = { it.id }) { log -> LogRow(log, timeFmt, { onExercise(log.exerciseId) }, { repo.delete(log.id) }) }
        }
    }
}

@Composable
private fun LogRow(log: WorkoutLog, timeFmt: SimpleDateFormat, onClick: () -> Unit, onDelete: () -> Unit) {
    val ex = log.exercise
    SurfaceCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ex?.name ?: log.exerciseId, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                val unit = if (ex?.isHold == true) "s hold" else " reps"
                val weight = if (log.weightKg > 0f) " · ${"%.1f".format(log.weightKg)} kg" else ""
                Text("${log.reps}$unit$weight · ${timeFmt.format(Date(log.timestamp))}" +
                    if (log.durationSec > 0) " · ${formatTime(log.durationSec)}" else "",
                    style = MaterialTheme.typography.bodySmall, color = MzColors.TextMuted)
                if (log.aiTracked) { Spacer(Modifier.height(6.dp)); AiBadge() }
            }
            if (log.formScore >= 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${log.formScore}", style = MaterialTheme.typography.headlineSmall, color = scoreColor(log.formScore))
                    Text("FORM", style = MaterialTheme.typography.labelSmall, color = MzColors.TextMuted)
                }
            }
            IconButton(onDelete) { Icon(Icons.Default.Delete, "Delete", tint = MzColors.TextMuted) }
        }
    }
}

@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    val container = LocalAppContainer.current
    val s by container.settings.settings.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp)) {
        Group("AI Coach") {
            ToggleRow("Voice coaching", "Spoken rep counts and form corrections", s.voiceCoaching) { v -> container.settings.update { it.copy(voiceCoaching = v) } }
            ToggleRow("Haptic feedback", "Vibrate on every rep and correction", s.haptics) { v -> container.settings.update { it.copy(haptics = v) } }
            ToggleRow("Show joint angles", "Overlay live angle readouts on tracked joints", s.showJointAngles) { v -> container.settings.update { it.copy(showJointAngles = v) } }
            ToggleRow("High-accuracy model", "More precise landmarks; uses more battery. Best on recent phones.", s.highAccuracyModel) { v -> container.settings.update { it.copy(highAccuracyModel = v) } }
            ToggleRow("Use front camera", "Turn off to use the rear camera by default", s.cameraFacing == CameraFacing.FRONT) { v ->
                container.settings.update { it.copy(cameraFacing = if (v) CameraFacing.FRONT else CameraFacing.BACK) }
            }
            Column(Modifier.padding(vertical = 10.dp)) {
                Text("Countdown: ${s.countdownSeconds}s", style = MaterialTheme.typography.titleSmall)
                Slider(s.countdownSeconds.toFloat(), { v -> container.settings.update { it.copy(countdownSeconds = v.toInt()) } },
                    valueRange = 0f..10f, steps = 9)
            }
        }
        Spacer(Modifier.height(16.dp))
        Group("Data") {
            TextButton({ confirmClear = true }) { Text("Clear workout history", color = MzColors.Danger) }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear all history?") },
        text = { Text("This permanently deletes every logged set and resets your muscle map.") },
        confirmButton = { TextButton({ container.workouts.clear(); confirmClear = false }) { Text("Delete", color = MzColors.Danger) } },
        dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } },
    )
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MzColors.TextMuted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
    SurfaceCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), content = content) }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MzColors.TextMuted)
        }
        Switch(checked, onChange)
    }
}

@Composable
fun AboutScreen(contentPadding: PaddingValues) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 20.dp)) {
        Text("MzFIT", style = MaterialTheme.typography.headlineLarge, color = MzColors.Volt)
        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MzColors.TextMuted)
        Spacer(Modifier.height(16.dp))
        Text("Your AI personal trainer. MzFIT uses on-device pose estimation to count reps, score your form and coach you " +
            "in real time, plus a detailed anatomical muscle map so you always know what you're training.",
            style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Text("Privacy", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("• Camera frames are analyzed on your device by Google ML Kit and are never recorded, stored or uploaded.\n" +
            "• Workout history is stored only on this device.\n" +
            "• MzFIT has no accounts, ads or third-party analytics.",
            style = MaterialTheme.typography.bodyMedium, color = MzColors.TextMuted)
        Spacer(Modifier.height(24.dp))
        Text("Safety", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("MzFIT provides general fitness guidance and is not medical advice. Consult a professional before starting a new " +
            "program, and stop if you feel pain. Form feedback is an estimate based on 2D camera tracking.",
            style = MaterialTheme.typography.bodyMedium, color = MzColors.TextMuted)
        Spacer(Modifier.height(24.dp))
    }
}
