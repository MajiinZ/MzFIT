package com.mlpt.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mlpt.data.*
import com.mlpt.shared.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.util.UUID

private data class RoutineRow(val id: String, val name: String, val sets: String = "3", val target: String = "8–12", val timed: Boolean = false)
private fun encodeRoutineRows(rows: List<RoutineRow>) = JSONArray().apply { rows.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("sets", it.sets).put("target", it.target).put("timed", it.timed)) } }.toString()
private fun decodeRoutineRows(raw: String): List<RoutineRow> = JSONArray(raw).let { j -> (0 until j.length()).map { j.getJSONObject(it).let { v -> RoutineRow(v.getString("id"), v.getString("name"), v.getString("sets"), v.getString("target"), v.getBoolean("timed")) } } }

@Composable internal fun RoutineEditor(initial: SavedRoutine, studio: TrainingStudio, onClose: () -> Unit) {
    val state by studio.state.collectAsState()
    val p = initial.plan
    var title by rememberSaveable(p.id) { mutableStateOf(p.title) }
    var minutes by rememberSaveable(p.id) { mutableStateOf(p.minutes.toString()) }
    var part by rememberSaveable(p.id) { mutableStateOf(p.bodyPart) }
    var description by rememberSaveable(p.id) { mutableStateOf(p.description) }
    var daysRaw by rememberSaveable(p.id) { mutableStateOf(initial.days.sorted().joinToString(",")) }
    var rowsRaw by rememberSaveable(p.id) { mutableStateOf(encodeRoutineRows(p.exercises.map { e ->
        val timed = e.reps.contains("second", true) || e.reps.contains("minute", true)
        val target = if (timed) ((Regex("\\d+").find(e.reps)?.value?.toIntOrNull() ?: 30) * if (e.reps.contains("minute", true)) 60 else 1).toString() else e.reps
        RoutineRow(e.id, e.name, e.sets.toString(), target, timed)
    })) }
    var search by rememberSaveable(p.id) { mutableStateOf("") }
    var libraryPart by rememberSaveable(p.id) { mutableStateOf("All") }
    var showLibrary by rememberSaveable(p.id) { mutableStateOf(false) }
    var customName by rememberSaveable(p.id) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf<SavedRoutine?>(null) }
    val rows = remember(rowsRaw) { decodeRoutineRows(rowsRaw) }
    val days = daysRaw.split(',').mapNotNull { it.toIntOrNull() }.toSet()
    val originalSignature = remember(p.id) { "$title|$minutes|$part|$description|$daysRaw|$rowsRaw" }
    val dirty = originalSignature != "$title|$minutes|$part|$description|$daysRaw|$rowsRaw"
    fun close() { if (state.busy) return; if (dirty) discard = true else onClose() }
    fun update(index: Int, row: RoutineRow) { rowsRaw = encodeRoutineRows(rows.toMutableList().apply { set(index, row) }) }
    fun add(name: String) {
        if (rows.size >= 30) { error = "A routine supports up to 30 exercises."; return }
        if (rows.any { exerciseKey(it.name) == exerciseKey(name) }) { error = "That exercise is already in this routine."; return }
        rowsRaw = encodeRoutineRows(rows + RoutineRow(UUID.randomUUID().toString(), name)); error = null; showLibrary = false; search = ""
    }
    LaunchedEffect(state.busy, state.routines, state.error, submitted) {
        submitted?.let { saved -> if (!state.busy && state.error == null && state.routines.any { it == saved }) onClose() }
    }
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), color = AppBackground) {
          Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Build your routine", style = MaterialTheme.typography.headlineLarge)
                TextButton(onClick = ::close, enabled = !state.busy) { Text("Close editor") }
                OutlinedTextField(title, { title = it.take(80) }, label = { Text("Routine name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.busy)
                OutlinedTextField(minutes, { minutes = it.take(3) }, label = { Text("Planned minutes (1–240)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !state.busy)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Full body", "Chest", "Back", "Legs", "Shoulders", "Arms", "Core").forEach { body -> FilterChip(selected = part == body, onClick = { part = body }, label = { Text(body) }, enabled = !state.busy) }
                }
                OutlinedTextField(description, { description = it.take(2000) }, label = { Text("Routine notes / equipment") }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
                Text("Training days (optional)", style = MaterialTheme.typography.titleMedium)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { day -> FilterChip(selected = day in days, onClick = { daysRaw = (if (day in days) days - day else days + day).sorted().joinToString(",") }, label = { Text(DayOfWeek.of(day).name.take(3)) }, enabled = !state.busy) }
                }
                Text("Scheduled routines appear on Today. This does not create a notification.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Text("${rows.size} exercises", style = MaterialTheme.typography.headlineMedium)
                rows.forEachIndexed { index, row -> StudioCard {
                    Text("${index + 1}. ${row.name}", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(row.sets, { update(index, row.copy(sets = it.take(2))) }, label = { Text("Sets (1–20)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), enabled = !state.busy)
                        OutlinedTextField(row.target, { update(index, row.copy(target = it.take(90))) }, label = { Text(if (row.timed) "Seconds / set" else "Rep target") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = if (row.timed) KeyboardType.Number else KeyboardType.Text), modifier = Modifier.weight(1f), enabled = !state.busy)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !row.timed, onClick = { update(index, row.copy(timed = false, target = "8–12")) }, label = { Text("Reps") }, enabled = !state.busy)
                        FilterChip(selected = row.timed, onClick = { update(index, row.copy(timed = true, target = "30")) }, label = { Text("Timed hold") }, enabled = !state.busy)
                    }
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = !state.busy && index > 0, onClick = { rowsRaw = encodeRoutineRows(rows.toMutableList().apply { val old = this[index - 1]; this[index - 1] = row; this[index] = old }) }) { Text("Move up") }
                        TextButton(enabled = !state.busy && index < rows.lastIndex, onClick = { rowsRaw = encodeRoutineRows(rows.toMutableList().apply { val old = this[index + 1]; this[index + 1] = row; this[index] = old }) }) { Text("Move down") }
                        TextButton(enabled = !state.busy, onClick = { rowsRaw = encodeRoutineRows(rows.filterIndexed { i, _ -> i != index }) }) { Text("Remove exercise") }
                    }
                } }
                Text("Add from the exercise library", style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = { showLibrary = !showLibrary }) { Text(if (showLibrary) "Hide exercise list" else "Browse ${exerciseLibrary.size} guided exercises") }
                OutlinedTextField(search, { search = it }, label = { Text("Search name or equipment") }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf("All") + MuscleGroup.entries.map { it.bodyPart }.distinct()).forEach { body -> FilterChip(selected = libraryPart == body, onClick = { libraryPart = body }, label = { Text(body) }) }
                }
                val guides = exerciseLibrary.filter { (libraryPart == "All" || it.primary.any { m -> m.bodyPart == libraryPart }) && (it.name.contains(search, true) || it.equipment.contains(search, true)) }
                if (guides.isEmpty()) Text("No exercises match. Search again or add your own below.")
                if (showLibrary || search.isNotBlank()) guides.forEach { guide -> OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(guide.name, style = MaterialTheme.typography.titleMedium)
                        Text(guide.equipment, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        Text(guide.primary.joinToString { it.label }, color = PerformanceBlue, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { add(guide.name) }, enabled = !state.busy && rows.size < 30 && rows.none { exerciseKey(it.name) == exerciseKey(guide.name) }) { Text("+ Add exercise") }
                    }
                } }
                OutlinedTextField(customName, { customName = it.take(120) }, label = { Text("Or name your own exercise") }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
                OutlinedButton(enabled = !state.busy && customName.isNotBlank() && rows.size < 30, onClick = { add(customName.trim()); customName = "" }) { Text("+ Add custom exercise") }
            }
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (error ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = !state.busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    val duration = minutes.toIntOrNull()
                    if (title.isBlank() || duration == null || duration !in 1..240 || rows.isEmpty() || rows.sumOf { it.sets.toIntOrNull() ?: 0 } > 500 || rows.any { (it.sets.toIntOrNull() ?: 0) !in 1..20 || it.target.isBlank() || it.timed && (it.target.toIntOrNull() ?: 0) !in 1..86400 }) {
                        error = "Add a name, 1–240 minutes, and at least one exercise. Check the sets and rep/second targets."
                    } else {
                        val routine = SavedRoutine(WorkoutPlan(p.id, title.trim(), "Custom", duration, description.trim(), rows.map { Exercise(it.id, it.name, it.sets.toInt(), if (it.timed) "${it.target} seconds" else it.target.trim()) }, part), days)
                        error = null; submitted = routine; studio.saveRoutine(routine)
                    }
                }) { Text(if (state.busy) "Saving routine…" else "Save routine") }
            }
          }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard routine edits?") }, text = { Text("Your unsaved changes will be lost.") },
        confirmButton = { TextButton(onClick = onClose) { Text("Discard edits") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
}
