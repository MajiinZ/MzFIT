package org.mz.mzfit.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.mz.mzfit.BuildConfig
import org.mz.mzfit.data.BodyPart
import org.mz.mzfit.data.ExerciseLibrary
import org.mz.mzfit.data.WorkoutRepository
import org.mz.mzfit.ui.components.LocalAppContainer
import org.mz.mzfit.ui.screens.*
import org.mz.mzfit.ui.screens.session.TrainingSessionScreen
import org.mz.mzfit.ui.screens.session.TrainingViewModel
import org.mz.mzfit.ui.theme.MzColors

private object Routes {
    const val WORKOUTS = "workouts"
    const val COACH = "coach"
    const val MUSCLES = "muscles"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val BODY_PART = "bodypart/{part}"
    const val EXERCISE = "exercise/{id}"
    const val SESSION = "session/{id}"
    fun bodyPart(p: BodyPart) = "bodypart/${p.name}"
    fun exercise(id: String) = "exercise/$id"
    fun session(id: String) = "session/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.WORKOUTS, "Workouts", Icons.Outlined.FitnessCenter, Icons.Filled.FitnessCenter),
    Tab(Routes.COACH, "AI Coach", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    Tab(Routes.MUSCLES, "Muscle Map", Icons.Outlined.Accessibility, Icons.Filled.Accessibility),
)

private val drawerRoutes = setOf(Routes.HISTORY, Routes.SETTINGS, Routes.ABOUT)
private val topLevel = tabs.map { it.route }.toSet() + drawerRoutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MzFitApp() {
    val nav = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val isSession = route == Routes.SESSION
    // Detail screens keep the tab they were opened from highlighted.
    var lastTab by rememberSaveable { mutableStateOf(Routes.WORKOUTS) }
    LaunchedEffect(route) { if (route != null && tabs.any { it.route == route }) lastTab = route }
    val currentTab = when (route) { in drawerRoutes -> null; else -> if (tabs.any { it.route == route }) route else lastTab }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isSession,
        drawerContent = {
            AppDrawer(route) { target ->
                scope.launch { drawerState.close() }
                nav.navigateTopLevel(target)
            }
        },
    ) {
        Scaffold(
            containerColor = MzColors.Background,
            topBar = {
                if (!isSession) {
                    TopAppBar(
                        title = { Text(titleFor(route, entry?.arguments?.getString("part"), entry?.arguments?.getString("id")),
                            maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            if (route in topLevel || route == null) {
                                IconButton({ scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Open menu") }
                            } else {
                                IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MzColors.Background),
                    )
                }
            },
            bottomBar = {
                if (!isSession) {
                    NavigationBar(containerColor = MzColors.Surface, tonalElevation = 0.dp) {
                        tabs.forEach { tab ->
                            val selected = currentTab == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { nav.navigateTopLevel(tab.route) },
                                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MzColors.Background,
                                    indicatorColor = MzColors.Volt,
                                    selectedTextColor = MzColors.Volt,
                                    unselectedIconColor = MzColors.TextMuted,
                                    unselectedTextColor = MzColors.TextMuted,
                                ),
                            )
                        }
                    }
                }
            },
        ) { inner ->
            val pad = if (isSession) PaddingValues() else inner
            NavHost(nav, startDestination = Routes.WORKOUTS) {
                composable(Routes.WORKOUTS) {
                    WorkoutsScreen(pad,
                        onBodyPart = { nav.navigate(Routes.bodyPart(it)) },
                        onExercise = { nav.navigate(Routes.exercise(it)) },
                        onStartCoach = { nav.navigateTopLevel(Routes.COACH) })
                }
                composable(Routes.COACH) { CoachScreen(pad) { nav.navigate(Routes.exercise(it)) } }
                composable(Routes.MUSCLES) { MuscleMapScreen(pad) { nav.navigate(Routes.exercise(it)) } }
                composable(Routes.HISTORY) { HistoryScreen(pad) { nav.navigate(Routes.exercise(it)) } }
                composable(Routes.SETTINGS) { SettingsScreen(pad) }
                composable(Routes.ABOUT) { AboutScreen(pad) }
                composable(Routes.BODY_PART) { e ->
                    val part = e.arguments?.getString("part")?.let { runCatching { BodyPart.valueOf(it) }.getOrNull() } ?: BodyPart.CHEST
                    BodyPartScreen(part, pad) { nav.navigate(Routes.exercise(it)) }
                }
                composable(Routes.EXERCISE) { e ->
                    val ex = e.arguments?.getString("id")?.let(ExerciseLibrary::get)
                    if (ex != null) ExerciseDetailScreen(ex, pad,
                        onStartCoach = { nav.navigate(Routes.session(it)) },
                        onExercise = { nav.navigate(Routes.exercise(it)) })
                }
                composable(Routes.SESSION) { e ->
                    val ex = e.arguments?.getString("id")?.let(ExerciseLibrary::get)?.takeIf { it.isAiTracked }
                    if (ex == null) { LaunchedEffect(Unit) { nav.popBackStack() }; return@composable }
                    val app = LocalContext.current.applicationContext as Application
                    val vm: TrainingViewModel = viewModel(key = "session-${ex.id}",
                        factory = viewModelFactory { initializer { TrainingViewModel(app, ex) } })
                    TrainingSessionScreen(vm, onClose = { nav.popBackStack() })
                }
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun titleFor(route: String?, part: String?, id: String?): String = when (route) {
    Routes.WORKOUTS -> "MzFIT"
    Routes.COACH -> "AI Coach"
    Routes.MUSCLES -> "Muscle Map"
    Routes.HISTORY -> "History"
    Routes.SETTINGS -> "Settings"
    Routes.ABOUT -> "About & Privacy"
    Routes.BODY_PART -> part?.let { runCatching { BodyPart.valueOf(it).displayName }.getOrNull() } ?: ""
    Routes.EXERCISE -> id?.let { ExerciseLibrary.get(it)?.name } ?: ""
    else -> "MzFIT"
}

@Composable
private fun AppDrawer(current: String?, onNavigate: (String) -> Unit) {
    val logs by LocalAppContainer.current.workouts.logs.collectAsStateWithLifecycle()
    val summary = remember(logs) { WorkoutRepository.weeklySummary(logs) }
    ModalDrawerSheet(drawerContainerColor = MzColors.Surface) {
        Column(Modifier.padding(24.dp)) {
            Text("MzFIT", style = MaterialTheme.typography.headlineLarge, color = MzColors.Volt)
            Text("AI Personal Trainer", style = MaterialTheme.typography.bodyMedium, color = MzColors.TextMuted)
            Spacer(Modifier.height(16.dp))
            Text("${summary.sets} sets · ${summary.activeDays} active days this week",
                style = MaterialTheme.typography.labelLarge)
        }
        HorizontalDivider(color = MzColors.Outline)
        Spacer(Modifier.height(8.dp))
        val item: @Composable (String, String, ImageVector) -> Unit = { route, label, icon ->
            NavigationDrawerItem(
                label = { Text(label) }, selected = current == route, onClick = { onNavigate(route) },
                icon = { Icon(icon, null) }, modifier = Modifier.padding(horizontal = 12.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MzColors.Volt.copy(alpha = 0.14f),
                    selectedIconColor = MzColors.Volt, selectedTextColor = MzColors.Volt,
                    unselectedContainerColor = MzColors.Surface,
                ),
            )
        }
        item(Routes.WORKOUTS, "Workouts", Icons.Default.FitnessCenter)
        item(Routes.COACH, "AI Coach", Icons.Default.AutoAwesome)
        item(Routes.MUSCLES, "Muscle Map", Icons.Default.Accessibility)
        HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 24.dp), color = MzColors.Outline)
        item(Routes.HISTORY, "Workout History", Icons.Default.History)
        item(Routes.SETTINGS, "Settings", Icons.Default.Settings)
        item(Routes.ABOUT, "About & Privacy", Icons.Default.Info)
        Spacer(Modifier.weight(1f))
        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MzColors.TextMuted,
            modifier = Modifier.padding(24.dp))
    }
}
