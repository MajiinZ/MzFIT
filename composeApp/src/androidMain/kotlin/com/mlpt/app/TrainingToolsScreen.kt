package com.mlpt.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mlpt.shared.*
import com.mlpt.app.storage.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun RestTimerCard(timer: RestTimer, defaultSeconds: Int) {
    val state by timer.state.collectAsState()
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Rest between sets", style = MaterialTheme.typography.titleLarge)
            Text(if (state.finished) "Rest finished · Ready for your next set" else "%d:%02d".format(state.remaining / 60, state.remaining % 60), style = MaterialTheme.typography.headlineMedium)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { timer.start(defaultSeconds) }) { Text("Start ${defaultSeconds}s") }
                if (state.running) OutlinedButton(onClick = timer::pause) { Text("Pause") }
                else if (state.remaining > 0) OutlinedButton(onClick = timer::resume) { Text("Resume") }
                if (state.remaining > 0 || state.finished) TextButton(onClick = timer::reset) { Text("Reset") }
            }
            Text("Continues while the screen sleeps. Return here to see when rest is finished; background alerts aren’t enabled.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun TrainingToolsScreen(state: TrainingState, app: TrainingApplication) {
    val prefs by app.preferences.state.collectAsState()
    val export by app.exportState.collectAsState()
    val pose by app.poseRecorder.state.collectAsState()
    val studio by app.studio.state.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf<Int?>(null) }
    var visible by rememberSaveable { mutableStateOf(10) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) app.exportHistory(uri) }
    Text("Tools for your training", style = MaterialTheme.typography.headlineLarge)
    RestTimerCard(app.restTimer, prefs.restSeconds)
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your preferences", style = MaterialTheme.typography.titleLarge)
            Text("Default rest: ${prefs.restSeconds} seconds")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = prefs.restSeconds > 15, onClick = { app.preferences.update(prefs.copy(restSeconds = (prefs.restSeconds - 15).coerceAtLeast(15))) }) { Text("−15s") }
                OutlinedButton(enabled = prefs.restSeconds < 600, onClick = { app.preferences.update(prefs.copy(restSeconds = (prefs.restSeconds + 15).coerceAtMost(600))) }) { Text("+15s") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = !prefs.pounds, onClick = { app.preferences.update(prefs.copy(pounds = false)) }, label = { Text("Kilograms") })
                FilterChip(selected = prefs.pounds, onClick = { app.preferences.update(prefs.copy(pounds = true)) }, label = { Text("Pounds") })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Keep screen awake while using app", Modifier.weight(1f))
                Switch(prefs.keepAwake, { app.preferences.update(prefs.copy(keepAwake = it)) })
            }
        }
    }
    Text("Workout journal", style = MaterialTheme.typography.headlineMedium)
    Text("After completing a workout, record actual reps and loads here. Your original plan stays alongside your journal.")
    if (state.sessions.isEmpty()) Text("Complete a workout to add your first journal entry.")
    val dates = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()) }
    state.sessions.asReversed().take(visible).forEach { session ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(session.title, style = MaterialTheme.typography.titleMedium)
                Text(session.completedAtEpochMillis?.let { dates.format(Instant.ofEpochMilli(it)) } ?: "Imported session · Date unavailable")
                Button(onClick = { selectedId = session.id }, enabled = !export.busy) { Text("View / edit sets & notes") }
            }
        }
    }
    if (visible < state.sessions.size) TextButton(onClick = { visible += 10 }) { Text("Show more workouts") }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your local data", style = MaterialTheme.typography.titleLarge)
            Text("${state.sessions.size} completed workouts · Profile, journal and pose logs stored on this device. Uninstalling or clearing app storage removes them.")
            Text("Export saves a readable JSON file with all workout, journal and pose measurements. Camera images aren’t saved. Import and automatic cloud sync aren’t available.")
            Button(enabled = !export.busy && !state.isSaving && studio.ready && !studio.busy && !studio.unsaved && !studio.draftSaving && pose.ready && !pose.saving && !pose.unsaved && pose.current?.status != "active", onClick = {
                picker.launch("mlpt-history-${java.time.LocalDate.now()}.json")
            }) { Text(if (export.busy) "Exporting…" else "Export all history") }
            export.message?.let { Text(it) }
            Text("MzFit · Version 0.7.0", style = MaterialTheme.typography.bodySmall)
        }
    }
    state.sessions.find { it.id == selectedId }?.let { session ->
        JournalEditor(session, app.journal, prefs.pounds, onDismiss = { selectedId = null })
    }
}

private data class SetDraft(val position: Int, val reps: String = "", val weight: String = "", val seconds: Boolean = false)
private fun encode(rows: List<SetDraft>) = JSONArray().apply { rows.forEach { put(JSONObject().put("position", it.position).put("reps", it.reps).put("weight", it.weight).put("seconds", it.seconds)) } }.toString()
private fun decode(raw: String): List<SetDraft> = JSONArray(raw).let { a -> (0 until a.length()).map { a.getJSONObject(it).let { SetDraft(it.getInt("position"), it.getString("reps"), it.getString("weight"), it.optBoolean("seconds", false)) } } }
private fun formatted(value: Double) = String.format(Locale.ROOT, "%.6f", value).trimEnd('0').trimEnd('.')

@Composable
private fun JournalEditor(session: TrainingSession, store: JournalDatabase, pounds: Boolean, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loaded by rememberSaveable(session.id) { mutableStateOf(false) }
    var notes by rememberSaveable(session.id) { mutableStateOf("") }
    var rawRows by rememberSaveable(session.id) { mutableStateOf("[]") }
    var baseline by rememberSaveable(session.id) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val rows = remember(rawRows) { decode(rawRows) }
    val factor = if (pounds) 2.20462262185 else 1.0
    LaunchedEffect(session.id, retry) {
        if (loaded) return@LaunchedEffect
        try {
            val journal = store.load(session.id)
            notes = journal.notes
            rawRows = encode((journal.sets.map { SetDraft(it.exercisePosition, it.reps.toString(), it.weightKg?.let { kg -> formatted(kg * factor) } ?: "") } + journal.timedSets.map { SetDraft(it.exercisePosition, it.seconds.toString(), seconds = true) }).sortedBy { it.position })
            baseline = notes + "\u0000" + rawRows; loaded = true; error = null
        } catch (_: Exception) { error = "Couldn’t load this journal. Retry before editing." }
    }
    fun close() { if (saving) return; if (loaded && baseline != notes + "\u0000" + rawRows) discard = true else onDismiss() }
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(session.title, style = MaterialTheme.typography.headlineMedium)
                Text("Actual sets · ${if (pounds) "lb" else "kg"}. Leave load blank for bodyweight or unrecorded load. Reps: 1–999; timed sets: 1–86400 seconds.")
                TextButton(onClick = ::close, enabled = !saving) { Text("Close journal") }
                if (!loaded && error == null) CircularProgressIndicator()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!loaded && error != null) OutlinedButton(onClick = { retry++ }) { Text("Retry loading") }
                if (loaded) {
                    OutlinedTextField(notes, { notes = it.take(5000) }, label = { Text("Session notes") }, enabled = !saving, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    if (session.exercises.isEmpty()) Text("Exercise details weren’t recorded for this imported session. You can still save notes.")
                    session.exercises.forEachIndexed { position, exercise ->
                        HorizontalDivider()
                        Text(exercise.name, style = MaterialTheme.typography.titleLarge)
                        Text("Planned: ${exercise.sets} sets · ${exercise.reps}")
                        rows.forEachIndexed { index, row -> if (row.position == position) {
                            val number = rows.take(index + 1).count { it.position == position }
                            Text("Set $number")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(row.reps, { value -> rawRows = encode(rows.toMutableList().apply { set(index, row.copy(reps = value.take(if (row.seconds) 5 else 3))) }) },
                                    label = { Text(if (row.seconds) "Actual seconds" else "Actual reps") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !saving, singleLine = true, modifier = Modifier.weight(1f))
                                if (!row.seconds) OutlinedTextField(row.weight, { value -> rawRows = encode(rows.toMutableList().apply { set(index, row.copy(weight = value.take(12))) }) },
                                    label = { Text("Load (${if (pounds) "lb" else "kg"})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !saving, singleLine = true, modifier = Modifier.weight(1f))
                            }
                            TextButton(onClick = { rawRows = encode(rows.filterIndexed { i, _ -> i != index }) }, enabled = !saving) { Text("Remove set $number") }
                        } }
                        OutlinedButton(enabled = !saving && rows.count { it.position == position } < 100 && rows.size < 500, onClick = { rawRows = encode(rows + SetDraft(position, seconds = exercise.reps.contains("second", true) || exercise.reps.contains("minute", true))) }) { Text("Add actual set") }
                    }
                    Button(enabled = !saving, onClick = {
                        val counters = mutableMapOf<Int, Int>()
                        val sets = mutableListOf<JournalSet>()
                        val timedSets = mutableListOf<TimedJournalSet>()
                        var valid = true
                        rows.forEach { row ->
                            val reps = row.reps.toIntOrNull()
                            val weight = row.weight.trim().replace(',', '.').toDoubleOrNull()?.div(factor)
                            if (reps == null || reps !in 1..(if (row.seconds) 86400 else 999) || row.weight.isNotBlank() && (weight == null || !weight.isFinite() || weight !in 0.0..2000.0)) valid = false
                            else { val n = (counters[row.position] ?: 0) + 1; counters[row.position] = n; if (row.seconds) timedSets += TimedJournalSet(row.position, n, reps) else sets += JournalSet(row.position, n, reps, weight) }
                        }
                        if (!valid) error = "Check every set: enter reps from 1–999 and a non-negative load up to ${if (pounds) "4409.24 lb" else "2000 kg"}, or leave load blank."
                        else {
                            saving = true; error = null
                            scope.launch {
                                try { store.save(WorkoutJournal(session.id, notes.trim(), sets, timedSets)); onDismiss() }
                                catch (_: Exception) { error = "Journal wasn’t saved. Your entries are still here; retry saving." }
                                finally { saving = false }
                            }
                        }
                    }) { Text(if (saving) "Saving…" else "Save actual sets & notes") }
                }
            }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard journal edits?") }, text = { Text("Your unsaved changes will be lost.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Discard changes") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
}
