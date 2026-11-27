package com.mlpt.feature.training

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mlpt.shared.*
import kotlinx.coroutines.launch

@Composable
private fun Heading(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(eyebrow.uppercase(), color = SecondaryText, style = MaterialTheme.typography.labelMedium)
        Text(title, style = MaterialTheme.typography.headlineLarge, color = Ink)
        Text(subtitle, color = SecondaryText)
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = CardSurface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
fun Dashboard(state: TrainingState, wide: Boolean, onBrowse: () -> Unit, onStart: (String) -> Unit) {
    Heading("Your space to grow", "Let’s move, ${state.profile.name}.", "Small steps. Strong foundations. A practice built around you.")
    Surface(color = Ink, contentColor = CardSurface, shape = RoundedCornerShape(26.dp)) {
        Row(Modifier.fillMaxWidth().padding(if (wide) 36.dp else 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("YOUR NEXT SESSION", style = MaterialTheme.typography.labelMedium, color = SoftBlue)
                Text(state.plans.first().title, style = MaterialTheme.typography.headlineLarge)
                Text("${state.plans.first().minutes} min / ${state.plans.first().bodyPart} / ${state.plans.first().exercises.size} exercises", color = SoftBlue)
                Text("Show up for yourself.\nWe’ll take it one rep at a time.")
                Button(onClick = { onStart(state.plans.first().id) },
                    colors = ButtonDefaults.buttonColors(containerColor = PerformanceBlue, contentColor = CardSurface)) { Text("Start your session  →") }
            }
            if (wide) Box(Modifier.size(150.dp).background(HeroAccent, RoundedCornerShape(75.dp)), contentAlignment = Alignment.Center) {
                Text("01", style = MaterialTheme.typography.displayMedium, color = SoftBlue)
            }
        }
    }
    Panel {
        Text("Your practice, at a glance", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("${state.sessions.size}", "Sessions logged")
            Stat("${state.profile.weeklyTarget}", "Weekly goal")
            Stat("${state.sessions.sumOf { it.exerciseCount }}", "Exercises done")
        }
        Text(state.profile.goal, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Made for your momentum", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onBrowse) { Text("View plans →") }
    }
    state.plans.drop(1).forEach { plan -> PlanCard(plan) { onStart(plan.id) } }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.headlineLarge, color = Ink)
        Text(label, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PlanCard(plan: WorkoutPlan, enabled: Boolean = true, onStart: () -> Unit) {
    Panel {
        Text("${plan.bodyPart.uppercase()}  /  ${plan.category}  /  ${plan.minutes} MIN", style = MaterialTheme.typography.labelMedium, color = SecondaryText)
        Text(plan.title, style = MaterialTheme.typography.headlineMedium)
        Text(plan.description, color = SecondaryText)
        Text("${plan.exercises.size} exercises · Guided checklist", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        OutlinedButton(onClick = onStart, enabled = enabled) { Text("Start workout →") }
    }
}

@Composable
fun Workouts(state: TrainingState, repository: TrainingRepository, initialBodyPart: String = "All", onExploreMuscles: () -> Unit = {}, tools: @Composable () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(initialBodyPart) }
    var notice by remember { mutableStateOf("") }
    var pendingPlan by remember { mutableStateOf<String?>(null) }
    Heading("Workouts by body part", "Choose your focus.", "Explore full-body sessions or train a specific muscle group.")
    OutlinedButton(onClick = onExploreMuscles) { Text("Explore muscle map & lift animations →") }
    tools()
    if (notice.isNotEmpty()) Text(notice, color = Ink)
    val active = state.plans.find { it.id == state.activePlanId }
    if (active != null) Panel {
        Text("IN PROGRESS", color = Ink, style = MaterialTheme.typography.labelMedium)
        Text(active.title, style = MaterialTheme.typography.headlineMedium)
        Text("Complete every set before checking off an exercise.", color = SecondaryText)
        active.exercises.forEach { exercise ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = exercise.id in state.completedExerciseIds, enabled = !state.isSaving, onCheckedChange = { repository.toggleExercise(exercise.id) })
                Column(Modifier.weight(1f)) {
                    Text(exercise.name, fontWeight = FontWeight.Medium)
                    Text("${exercise.sets} sets · ${exercise.reps}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        LinearProgressIndicator(progress = { state.completedExerciseIds.size.toFloat() / active.exercises.size }, modifier = Modifier.fillMaxWidth())
        Button(enabled = !state.isSaving && active.exercises.all { it.id in state.completedExerciseIds }, onClick = {
            scope.launch { notice = if (repository.finishWorkout()) "Session saved. Your progress has been updated." else "Session wasn’t saved. Please retry." }
        }) { Text(if (state.isSaving) "Saving…" else "Complete session") }
    }
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf("All") + state.plans.map { it.bodyPart }.distinct()).forEach { category ->
            FilterChip(selected = filter == category, onClick = { filter = category }, label = { Text(category) })
        }
    }
    state.plans.filter { filter == "All" || it.bodyPart == filter }.forEach { plan ->
        PlanCard(plan, enabled = !state.isSaving) {
            if (active != null) pendingPlan = plan.id else { repository.startWorkout(plan.id); notice = "" }
        }
    }
    pendingPlan?.let { planId ->
        AlertDialog(onDismissRequest = { pendingPlan = null }, title = { Text("Replace your active session?") },
            text = { Text("Your current exercise checklist will reset. Completed sessions stay in your progress history.") },
            confirmButton = { TextButton(onClick = { repository.startWorkout(planId); pendingPlan = null; notice = "" }) { Text("Start new session") } },
            dismissButton = { TextButton(onClick = { pendingPlan = null }) { Text("Keep current") } })
    }
}

@Composable
fun Progress(state: TrainingState) {
    Heading("The bigger picture", "Progress is a practice.", "Every completed session is another step forward.")
    Panel {
        Text("Session goal", style = MaterialTheme.typography.titleLarge)
        Text("${state.sessions.size} of ${state.profile.weeklyTarget} sessions", style = MaterialTheme.typography.headlineMedium)
        LinearProgressIndicator(progress = { (state.sessions.size.toFloat() / state.profile.weeklyTarget).coerceAtMost(1f) }, modifier = Modifier.fillMaxWidth())
        Text("Preview of your weekly target using this demo’s session history. Calendar-based tracking will come with persistent storage.", color = SecondaryText)
    }
    Text("Your session journal", style = MaterialTheme.typography.headlineMedium)
    if (state.sessions.isEmpty()) Panel {
        Text("Your story starts here.", style = MaterialTheme.typography.titleLarge)
        Text("Complete a workout from the Workouts tab to see your first session here.", color = SecondaryText)
    }
    state.sessions.asReversed().forEach { session -> Panel {
        Text("SESSION ${session.id}", style = MaterialTheme.typography.labelMedium, color = SecondaryText)
        Text(session.title, style = MaterialTheme.typography.titleLarge)
        Text("${session.exerciseCount} exercises completed", color = Ink)
    } }
}

@Composable
fun Profile(profile: ClientProfile, onSave: suspend (String, String, Int) -> Boolean) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var name by remember(profile) { mutableStateOf(profile.name) }
    var goal by remember(profile) { mutableStateOf(profile.goal) }
    var target by remember(profile) { mutableStateOf(profile.weeklyTarget) }
    var saved by remember { mutableStateOf(false) }
    Heading("Make it yours", "Built around you.", "Set an intention and a pace you can keep coming back to.")
    Panel {
        OutlinedTextField(value = name, onValueChange = { name = it; saved = false }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = goal, onValueChange = { goal = it; saved = false }, label = { Text("Your training goal") }, modifier = Modifier.fillMaxWidth())
        Text("Weekly session target: $target", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { target--; saved = false }, enabled = target > 1) { Text("Fewer") }
            Text("$target / week")
            OutlinedButton(onClick = { target++; saved = false }, enabled = target < 7) { Text("More") }
        }
        Button(enabled = !saving && name.isNotBlank() && goal.isNotBlank(), onClick = {
            saving = true
            scope.launch { try { saved = onSave(name, goal, target) } finally { saving = false } }
        }) { Text(if (saving) "Saving…" else "Save profile") }
        if (saved) Text("Profile saved.", color = Ink)
    }
    Panel {
        Text("Make consistency your goal", style = MaterialTheme.typography.titleLarge)
        Text("Choose a weekly target that fits your schedule. Your calendar shows completed sessions; your workout history keeps the details you logged.", color = SecondaryText)
    }
}
