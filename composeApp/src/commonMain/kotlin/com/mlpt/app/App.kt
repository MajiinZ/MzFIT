package com.mlpt.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.mlpt.di.AppContainer
import com.mlpt.feature.training.*
import com.mlpt.navigation.Destination
import com.mlpt.shared.*
import kotlinx.coroutines.launch

@Composable
fun App(
    container: AppContainer = remember { AppContainer() },
    storageNotice: String = "Demo workspace · Changes reset when the app closes.",
    formatCompletedAt: (Long) -> String = { "Saved workout" },
    workoutTools: @Composable () -> Unit = {},
    dashboardContent: (@Composable (TrainingState, () -> Unit, (String) -> Unit) -> Unit)? = null,
    workoutsContent: (@Composable (TrainingState, String, () -> Unit) -> Unit)? = null,
    progressContent: (@Composable (TrainingState) -> Unit)? = null,
    startWorkout: ((String) -> Unit)? = null,
    showBottomNavigation: Boolean = false,
    bottomContent: @Composable () -> Unit = {},
    toolsContent: @Composable (TrainingState) -> Unit = { Text("Training tools are available in the Android app.") },
    poseContent: @Composable () -> Unit = {
        Text("ML · Pose detection", style = MaterialTheme.typography.headlineLarge)
        Text("Live Google ML Kit pose detection is available in the Android and iOS apps. Open this tab on your phone to use the camera.", color = SecondaryText)
    },
) {
    val repository = container.trainingRepository
    val state by repository.state.collectAsState()
    var destination by remember { mutableStateOf(Destination.Home) }
    var workoutFocus by remember { mutableStateOf("All") }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    TrainingTheme {
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = drawer.isOpen,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = CardSurface) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("MzFit", style = MaterialTheme.typography.headlineLarge, color = Ink)
                        Text("YOUR PERSONAL TRAINING SPACE", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                    }
                    Destination.entries.forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(item.label) }, icon = { Text(item.symbol) },
                            selected = destination == item,
                            onClick = { destination = item; scope.launch { drawer.close() } },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text("A little stronger. Every single day.", Modifier.padding(24.dp), color = Ink)
                }
            },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().background(AppBackground).windowInsetsPadding(WindowInsets.safeDrawing)) {
                val wide = maxWidth >= 840.dp
                Column(Modifier.fillMaxSize()) {
                    Surface(color = CardSurface) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { scope.launch { drawer.open() } }) {
                                androidx.compose.foundation.Canvas(Modifier.size(24.dp).semanticsMenu()) {
                                    repeat(3) { line -> drawLine(Ink, androidx.compose.ui.geometry.Offset(2.dp.toPx(), (5 + line * 7).dp.toPx()), androidx.compose.ui.geometry.Offset(22.dp.toPx(), (5 + line * 7).dp.toPx()), 2.dp.toPx()) }
                                }
                            }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text("MzFit", style = MaterialTheme.typography.titleLarge, color = Ink)
                                Text(destination.label, style = MaterialTheme.typography.labelMedium, color = SecondaryText)
                            }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        key(destination) {
                            Column(Modifier.widthIn(max = 1120.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                                .padding(if (wide) 40.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                if (state.isLoading) {
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                    Text("Loading your saved workouts…")
                                } else if (!state.storageReady) {
                                    Text(state.storageError ?: "Workout history is unavailable.", color = MaterialTheme.colorScheme.error)
                                    Button(onClick = repository::retryLoading) { Text("Retry loading") }
                                } else {
                                state.storageError?.let { error ->
                                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                                        Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                                when (destination) {
                                    Destination.Home -> {
                                        val browse = { destination = Destination.Workouts }
                                        val start: (String) -> Unit = { id -> (startWorkout ?: repository::startWorkout)(id); destination = Destination.Workouts }
                                        if (dashboardContent != null) dashboardContent(state, browse, start)
                                        else Dashboard(state, wide, onBrowse = browse, onStart = start)
                                    }
                                    Destination.Workouts -> {
                                        if (workoutsContent != null) workoutsContent(state, workoutFocus) { destination = Destination.MuscleMap }
                                        else Workouts(state, repository, workoutFocus, onExploreMuscles = { destination = Destination.MuscleMap }, tools = workoutTools)
                                    }
                                    Destination.MuscleMap -> MuscleExplorer(onBrowseWorkouts = { bodyPart -> workoutFocus = bodyPart; destination = Destination.Workouts })
                                    Destination.Pose -> poseContent()
                                    Destination.Profile -> {
                                        if (progressContent != null) progressContent(state)
                                        ProfileProgress(state, formatCompletedAt)
                                        Profile(state.profile, repository::updateProfile)
                                    }
                                    Destination.Tools -> toolsContent(state)
                                }
                                }
                                Text(storageNotice, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    bottomContent()
                    if (showBottomNavigation) NavigationBar(containerColor = CardSurface, tonalElevation = 0.dp) {
                        listOf(Triple(Destination.Home, "Today", "⌂"), Triple(Destination.Workouts, "Train", "▤"), Triple(Destination.Profile, "Progress", "↗")).forEach { (target, label, symbol) ->
                            NavigationBarItem(selected = destination == target, onClick = { destination = target }, icon = { Text(symbol) }, label = { Text(label) })
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.semanticsMenu() = this.then(Modifier.semantics { contentDescription = "Open navigation menu" })
