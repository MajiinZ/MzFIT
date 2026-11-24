package com.mlpt.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mlpt.data.*
import com.mlpt.shared.*
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun amountText(value: Double): String = String.format(Locale.getDefault(), "%.1f", value).removeSuffix(".0")
internal fun loadText(kg: Double, pounds: Boolean) = "${amountText(kg * if (pounds) 2.20462262185 else 1.0)} ${if (pounds) "lb" else "kg"}"

@Composable fun CompactRestBar(app: TrainingApplication) {
    val timer by app.restTimer.state.collectAsState()
    val studio by app.studio.state.collectAsState()
    if (studio.draft != null && (timer.remaining > 0 || timer.finished)) Surface(color = SoftBlue) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (timer.finished) "Rest finished" else "Rest ${timer.remaining / 60}:${(timer.remaining % 60).toString().padStart(2, '0')}", Modifier.weight(1f), color = PerformanceBlue, fontWeight = FontWeight.Bold)
            if (timer.remaining > 0) TextButton(onClick = { if (timer.running) app.restTimer.pause() else app.restTimer.resume() }) { Text(if (timer.running) "Pause" else "Resume") }
            TextButton(onClick = app.restTimer::reset) { Text(if (timer.finished) "Dismiss" else "Skip") }
        }
    }
}

@Composable internal fun StudioCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = CardSurface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}
@Composable private fun currentInstant(): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { now = Instant.now(); delay(30_000) } }
    return now
}

@Composable fun StudioHome(training: TrainingState, app: TrainingApplication, onBrowse: () -> Unit, onStart: (String) -> Unit) {
    val studio by app.studio.state.collectAsState()
    val prefs by app.preferences.state.collectAsState()
    val now = currentInstant()
    val calendar = trainingCalendar(training.sessions, now, ZoneId.systemDefault())
    val today = now.atZone(ZoneId.systemDefault()).dayOfWeek.value
    val scheduled = studio.routines.filter { today in it.days }
    LaunchedEffect(Unit) { app.studio.refreshHistory() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(now.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEEE, MMM d")), color = SecondaryText, style = MaterialTheme.typography.labelLarge)
            Text("Let’s train, ${training.profile.name}.", style = MaterialTheme.typography.headlineLarge)
        }
        Surface(color = SoftBlue, shape = RoundedCornerShape(18.dp)) { Text("LOCAL\n& PRIVATE", Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall, color = PerformanceBlue) }
    }
    val plan = studio.draft?.plan ?: scheduled.firstOrNull()?.plan ?: studio.routines.firstOrNull()?.plan ?: training.plans.first()
    Surface(color = BrandNavy, contentColor = CardSurface, shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (studio.draft != null) "PICK UP WHERE YOU LEFT OFF" else if (scheduled.isNotEmpty()) "ON YOUR SCHEDULE TODAY" else "YOUR NEXT WORKOUT", color = BrandLime, style = MaterialTheme.typography.labelLarge)
            Text(plan.title, style = MaterialTheme.typography.headlineLarge)
            Text("${plan.exercises.size} exercises · ${plan.exercises.sumOf { it.sets }} planned sets · ${plan.minutes} min", color = CardSurface.copy(alpha = .8f))
            Text(if (studio.draft != null) "${studio.draft!!.completedSets} of ${studio.draft!!.totalSets} sets logged. Your draft is saved on this phone." else "Log each set, see your previous session, and build a history you can use.", color = CardSurface.copy(alpha = .8f))
            Button(onClick = { onStart(plan.id) }, enabled = studio.ready && !studio.busy,
                colors = ButtonDefaults.buttonColors(containerColor = BrandLime, contentColor = BrandNavy)) { Text(if (studio.draft != null) "Resume workout →" else "Start workout →", fontWeight = FontWeight.Bold) }
            TextButton(onClick = onBrowse, colors = ButtonDefaults.textButtonColors(contentColor = CardSurface)) { Text("Your routines & workout library") }
        }
    }
    WeekProgress(calendar, training.profile.weeklyTarget)
    if (scheduled.size > 1) StudioCard {
        Text("Also on your schedule", style = MaterialTheme.typography.titleLarge)
        scheduled.drop(1).forEach { routine -> OutlinedButton(onClick = { onStart(routine.plan.id) }, enabled = studio.draft == null) { Text(routine.plan.title) } }
    }
    val records = liftRecords(studio.performances)
    if (records.isNotEmpty()) StudioCard {
        Text("Your strength, on record", style = MaterialTheme.typography.titleLarge)
        records.take(3).forEach { Text("${it.name} · ${loadText(it.heaviestKg, prefs.pounds)} × ${it.repsAtHeaviest}") }
        Text("Heaviest loads you recorded. More details in Progress.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    }
    StudioCard {
        Text("Know the movement", style = MaterialTheme.typography.titleLarge)
        Text("Explore the muscle map for exercise guides and lift animations. Use ML pose coaching for experimental squat, push-up and curl feedback.", color = SecondaryText)
        OutlinedButton(onClick = onBrowse) { Text("Explore your training options") }
    }
}

@Composable internal fun WeekProgress(calendar: TrainingCalendar, target: Int) {
    StudioCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("This week", style = MaterialTheme.typography.titleLarge)
            Text("${calendar.thisWeek} / $target", color = PerformanceBlue, style = MaterialTheme.typography.titleLarge)
        }
        LinearProgressIndicator(progress = { (calendar.thisWeek.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), gapSize = 0.dp)
        Text("Week of ${calendar.weekStart.format(DateTimeFormatter.ofPattern("MMM d"))} · Monday–Sunday · Local time", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        Text("28 days of showing up", style = MaterialTheme.typography.titleMedium)
        calendar.days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1f).background(if (day.count > 0) PerformanceBlue else SoftBlue, RoundedCornerShape(8.dp))
                        .semantics { contentDescription = "${day.date}: ${day.count} completed workouts" }, contentAlignment = Alignment.Center) {
                        Text(day.date.dayOfMonth.toString(), color = if (day.count > 0) CardSurface else SecondaryText, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (calendar.undated > 0) Text("${calendar.undated} older sessions have no date and aren’t included in the calendar.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable fun StudioWorkouts(training: TrainingState, app: TrainingApplication, initialBodyPart: String, onMuscles: () -> Unit) {
    val state by app.studio.state.collectAsState()
    val prefs by app.preferences.state.collectAsState()
    var filter by rememberSaveable { mutableStateOf(initialBodyPart) }
    var query by rememberSaveable { mutableStateOf("") }
    var editor by remember { mutableStateOf<SavedRoutine?>(null) }
    var deleting by remember { mutableStateOf<SavedRoutine?>(null) }
    var finish by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { app.studio.refreshHistory() }
    Text(if (state.draft != null) "Make every set count." else "Your training. Your way.", style = MaterialTheme.typography.headlineLarge)
    if (!state.ready) {
        if (state.error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else { Text(state.error!!, color = MaterialTheme.colorScheme.error); Button(onClick = app.studio::retry) { Text("Retry loading") } }
        return
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); OutlinedButton(onClick = app.studio::retry) { Text("Retry saving") } }
    state.notice?.let { Text(it, color = PerformanceBlue) }
    state.draft?.let { draft ->
        LiveWorkout(draft, state, app, onFinish = { finish = true }, onDiscard = { discard = true })
        if (finish) AlertDialog(onDismissRequest = { finish = false }, title = { Text("Finish this workout?") },
            text = { Text("${draft.completedSets} of ${draft.totalSets} sets will be saved. Unchecked sets won’t count toward your progress.") },
            confirmButton = { TextButton(onClick = { finish = false; app.studio.finish() }) { Text("Save completed sets") } }, dismissButton = { TextButton(onClick = { finish = false }) { Text("Keep training") } })
        if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard this workout?") }, text = { Text("This removes the active draft and its sets. Completed workout history stays saved.") },
            confirmButton = { TextButton(onClick = { discard = false; app.studio.discard(); app.restTimer.reset() }) { Text("Discard draft") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep training") } })
        return
    }
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { editor = SavedRoutine(WorkoutPlan(java.util.UUID.randomUUID().toString(), "", "Strength", 45, "", emptyList())) }, enabled = !state.busy) { Text("+ Create routine") }
        OutlinedButton(onClick = onMuscles) { Text("Muscle map & guides") }
    }
    Text("My routines", style = MaterialTheme.typography.headlineMedium)
    if (state.routines.isEmpty()) StudioCard {
        Text("A plan that fits your gym.", style = MaterialTheme.typography.titleLarge)
        Text("Build a routine from the exercise library, choose sets and targets, and assign training days. Or customize a starter below.", color = SecondaryText)
    }
    state.routines.forEach { routine ->
        StudioCard {
            Text(routine.plan.title, style = MaterialTheme.typography.titleLarge)
            Text("${routine.plan.exercises.size} exercises · ${routine.plan.exercises.sumOf { it.sets }} sets · ${routine.plan.minutes} min", color = SecondaryText)
            Text(if (routine.days.isEmpty()) "Train whenever it suits you" else routine.days.sorted().joinToString(" · ") { DayOfWeek.of(it).name.take(3).lowercase().replaceFirstChar(Char::titlecase) }, color = PerformanceBlue)
            Button(onClick = { app.studio.start(routine.plan, prefs.pounds) }, enabled = !state.busy) { Text("Start routine") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { editor = routine }, enabled = !state.busy) { Text("Edit") }
                TextButton(onClick = { deleting = routine }, enabled = !state.busy) { Text("Delete") }
            }
        }
    }
    Text("Starter workouts", style = MaterialTheme.typography.headlineMedium)
    OutlinedTextField(query, { query = it }, label = { Text("Search workouts or exercises") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf("All") + training.plans.map { it.bodyPart }.distinct()).forEach { part -> FilterChip(selected = filter == part, onClick = { filter = part }, label = { Text(part) }) }
    }
    val plans = training.plans.filter { (filter == "All" || it.bodyPart == filter) && (it.title.contains(query, true) || it.exercises.any { e -> e.name.contains(query, true) }) }
    if (plans.isEmpty()) Text("No workouts match. Try another search or body part.", color = SecondaryText)
    plans.forEach { plan -> StudioCard {
        Text("${plan.bodyPart.uppercase()} · ${plan.minutes} MIN", color = PerformanceBlue, style = MaterialTheme.typography.labelLarge)
        Text(plan.title, style = MaterialTheme.typography.titleLarge)
        Text(plan.exercises.joinToString(" · ") { it.name }, color = SecondaryText)
        Button(onClick = { app.studio.start(plan, prefs.pounds) }, enabled = !state.busy) { Text("Start workout") }
        TextButton(onClick = { editor = SavedRoutine(plan.copy(id = java.util.UUID.randomUUID().toString(), title = "My ${plan.title}".take(80))) }, enabled = !state.busy) { Text("Customize & save routine") }
    } }
    editor?.let { RoutineEditor(it, app.studio, onClose = { editor = null }) }
    deleting?.let { routine -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${routine.plan.title}?") }, text = { Text("Completed workouts and their exercise snapshots will stay in your history.") },
        confirmButton = { TextButton(onClick = { app.studio.deleteRoutine(routine.plan.id); deleting = null }) { Text("Delete routine") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep routine") } }) }
}

@Composable private fun LiveWorkout(draft: WorkoutDraft, state: StudioState, app: TrainingApplication, onFinish: () -> Unit, onDiscard: () -> Unit) {
    val prefs by app.preferences.state.collectAsState()
    var inputError by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val now = currentInstant()
    val minutes = ((now.toEpochMilli() - draft.startedAt).coerceAtLeast(0) / 60_000)
    StudioCard {
        Text(draft.plan.title, style = MaterialTheme.typography.headlineMedium)
        Text("${draft.completedSets} / ${draft.totalSets} sets · ${minutes} min since start", color = PerformanceBlue)
        Text(if (state.unsaved) "${if (state.error == null) "Saving your draft…" else "Unsaved edits · Retry above"}" else "Draft saved · Safe to leave and resume", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(progress = { draft.completedSets.toFloat() / draft.totalSets.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
        Text("Loads in ${if (draft.pounds) "pounds" else "kilograms"} for this workout. Blank load means bodyweight or unrecorded load.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    }
    RestTimerCard(app.restTimer, prefs.restSeconds)
    inputError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    draft.exercises.forEachIndexed { position, exercise -> StudioCard {
        Text("${position + 1}. ${exercise.prescription.name}", style = MaterialTheme.typography.titleLarge)
        Text("Target: ${exercise.prescription.sets} sets · ${exercise.prescription.reps}", color = SecondaryText)
        val previous = state.performances.filter { exerciseKey(it.exercise) == exerciseKey(exercise.prescription.name) && (it.seconds != null) == (exercise.metric == SetMetric.Seconds) }.let { rows -> rows.filter { it.workoutId == rows.maxOfOrNull { r -> r.workoutId } } }
        if (previous.isNotEmpty()) {
            Text("Last session: " + previous.sortedBy { it.number }.joinToString(" · ") { p ->
                if (p.seconds != null) "${p.seconds}s" else "${p.reps} reps${p.weightKg?.let { " @ ${loadText(it, draft.pounds)}" } ?: ""}"
            }, style = MaterialTheme.typography.bodySmall, color = PerformanceBlue)
            TextButton(onClick = { app.studio.usePrevious(position) }, enabled = !state.busy) { Text("Use previous values in unchecked sets") }
        }
        val oldBest = previousBest(state.performances, exercise.prescription.name)
        if (oldBest != null) Text("Recorded best: ${loadText(oldBest, draft.pounds)}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        exercise.sets.forEachIndexed { index, set ->
            val valid = liveSetValid(set, exercise.metric, draft.pounds)
            Surface(color = if (set.complete) SoftBlue else AppBackground, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Set ${index + 1}", fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (set.complete) "Logged" else "Log set", style = MaterialTheme.typography.labelMedium)
                            Checkbox(set.complete, onCheckedChange = {
                                if (app.studio.completeSet(position, index)) { inputError = null; if (!set.complete) app.restTimer.start(prefs.restSeconds) }
                                else inputError = "Enter ${if (exercise.metric == SetMetric.Seconds) "seconds from 1–86400" else "reps from 1–999 and a valid optional load"} before logging this set."
                            }, enabled = !state.busy, modifier = Modifier.semantics { contentDescription = "Log ${exercise.prescription.name}, set ${index + 1}" })
                            if (exercise.sets.size > 1) IconButton(onClick = { if (set.amount.isNotBlank() || set.complete || set.load.isNotBlank()) removing = position to index else app.studio.removeSet(position, index) }, enabled = !state.busy,
                                modifier = Modifier.semantics { contentDescription = "Remove ${exercise.prescription.name}, set ${index + 1}" }) { Text("×") }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(set.amount, { app.studio.changeSet(position, index, set.copy(amount = it.take(5))); inputError = null },
                            label = { Text(if (exercise.metric == SetMetric.Seconds) "Seconds" else "Reps") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !state.busy && !set.complete, modifier = Modifier.weight(1f))
                        if (exercise.metric == SetMetric.Reps) OutlinedTextField(set.load, { app.studio.changeSet(position, index, set.copy(load = it.take(12))); inputError = null },
                            label = { Text(if (draft.pounds) "Load (lb)" else "Load (kg)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !state.busy && !set.complete, modifier = Modifier.weight(1f))
                    }
                    val kg = set.load.replace(',', '.').toDoubleOrNull()?.div(if (draft.pounds) 2.20462262185 else 1.0)
                    if (set.complete && valid && kg != null && kg > 0 && (oldBest == null || kg > oldBest)) Text("New heaviest recorded load", color = PerformanceBlue, fontWeight = FontWeight.Bold)
                }
            }
        }
        OutlinedButton(onClick = { app.studio.addSet(position) }, enabled = !state.busy && exercise.sets.size < 100 && draft.totalSets < 500) { Text("+ Add set") }
    } }
    StudioCard {
        OutlinedTextField(draft.notes, app.studio::setNotes, label = { Text("Workout notes") }, minLines = 2, modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
        Button(onClick = onFinish, enabled = !state.busy && draft.completedSets > 0, modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "Saving workout…" else "Finish workout · ${draft.completedSets} sets") }
        TextButton(onClick = onDiscard, enabled = !state.busy) { Text("Discard active workout") }
    }
    removing?.let { (exercise, index) -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remove this set?") }, text = { Text("The values you entered for this set will be removed from the draft.") },
        confirmButton = { TextButton(onClick = { app.studio.removeSet(exercise, index); removing = null }) { Text("Remove set") } }, dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep set") } }) }
}

private fun previousBest(sets: List<PerformanceSet>, name: String) = sets.filter { exerciseKey(it.exercise) == exerciseKey(name) && it.reps != null && it.weightKg != null }.maxOfOrNull { it.weightKg!! }

@Composable fun StudioProgress(training: TrainingState, app: TrainingApplication) {
    val state by app.studio.state.collectAsState()
    val prefs by app.preferences.state.collectAsState()
    val now = currentInstant()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { app.studio.refreshHistory() }
    WeekProgress(trainingCalendar(training.sessions, now, ZoneId.systemDefault()), training.profile.weeklyTarget)
    val records = liftRecords(state.performances)
    StudioCard {
        Text("Strength you can see", style = MaterialTheme.typography.headlineMedium)
        if (records.isEmpty()) Text("Log reps and loads during a workout to build your lift trends. Bodyweight and timed sets count as training, but don’t create external-load records.", color = SecondaryText)
        else {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                records.forEach { record -> FilterChip(selected = (selected ?: records.first().key) == record.key, onClick = { selected = record.key }, label = { Text(record.name) }) }
            }
            val record = records.find { it.key == selected } ?: records.first()
            Text("${loadText(record.heaviestKg, prefs.pounds)} × ${record.repsAtHeaviest}", style = MaterialTheme.typography.headlineLarge, color = PerformanceBlue)
            Text("Heaviest recorded load · ${record.points.size} sessions logged", color = SecondaryText)
            val points = record.points.takeLast(12)
            val change = points.last().heaviestKg - points.first().heaviestKg
            Text(if (points.size < 2) "Your starting point · Log another session to see your trend"
                else "${if (change > 0) "+" else if (change < 0) "−" else ""}${loadText(kotlin.math.abs(change), prefs.pounds)} since first session shown",
                color = PerformanceBlue, style = MaterialTheme.typography.titleMedium)
            val maximum = points.maxOf { it.heaviestKg }.coerceAtLeast(1.0) * 1.1
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.height(190.dp).padding(vertical = 3.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    listOf(maximum, maximum * 2 / 3, maximum / 3, 0.0).forEach {
                        Text(loadText(it, prefs.pounds), style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                    }
                }
                Canvas(Modifier.weight(1f).height(190.dp).semantics { contentDescription = "Heaviest load per workout for ${record.name}: " + points.joinToString { "Workout ${it.workoutId}: ${loadText(it.heaviestKg, prefs.pounds)}" } }) {
                    val top = 10.dp.toPx(); val left = 10.dp.toPx(); val bottom = size.height - top
                    repeat(4) { val y = top + (bottom - top) * it / 3; drawLine(Divider, Offset(left, y), Offset(size.width - left, y), 1.dp.toPx()) }
                    val max = maximum
                    val positions = points.mapIndexed { index, p -> Offset(if (points.size == 1) size.width / 2 else left + (size.width - left * 2) * index / (points.size - 1), (bottom - (bottom - top) * p.heaviestKg / max).toFloat()) }
                    if (positions.size > 1) {
                        val line = Path().apply { moveTo(positions.first().x, positions.first().y); positions.drop(1).forEach { lineTo(it.x, it.y) } }
                        val fill = Path().apply { addPath(line); lineTo(positions.last().x, bottom); lineTo(positions.first().x, bottom); close() }
                        drawPath(fill, Brush.verticalGradient(listOf(PerformanceBlue.copy(alpha = 0.22f), PerformanceBlue.copy(alpha = 0.01f))))
                        drawPath(line, PerformanceBlue, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    }
                    positions.forEach { drawCircle(CardSurface, 6.dp.toPx(), it); drawCircle(PerformanceBlue, 4.dp.toPx(), it) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Workout #${points.first().workoutId}", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                if (points.size > 1) Text("Workout #${points.last().workoutId}", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
            }
            Text("Last ${points.size} recorded sessions · Heaviest external load", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Text("Recorded load × reps: ${loadText(record.externalVolumeKg, prefs.pounds)} total", style = MaterialTheme.typography.titleMedium)
            Text("External-load volume excludes bodyweight, timed holds and unrecorded loads. Log total external load consistently for dumbbell and barbell exercises.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            points.asReversed().take(5).forEach { Text("Workout #${it.workoutId} · ${loadText(it.heaviestKg, prefs.pounds)} · Volume ${loadText(it.volumeKg, prefs.pounds)}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
