package com.mlpt.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.remember
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import com.mlpt.di.AppContainer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val app = application as TrainingApplication
            val container = remember { AppContainer(app.trainingRepository) }
            val preferences by app.preferences.state.collectAsState()
            val view = LocalView.current
            DisposableEffect(view, preferences.keepAwake) {
                view.keepScreenOn = preferences.keepAwake
                onDispose { view.keepScreenOn = false }
            }
            val dates = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()) }
            App(container = container, storageNotice = "Saved privately on your phone · Export from Training tools",
                formatCompletedAt = { dates.format(Instant.ofEpochMilli(it)) }, poseContent = { PoseDetectionScreen() },
                workoutTools = { RestTimerCard(app.restTimer, preferences.restSeconds) },
                dashboardContent = { training, browse, start -> StudioHome(training, app, browse, start) },
                workoutsContent = { training, bodyPart, muscles -> StudioWorkouts(training, app, bodyPart, muscles) },
                progressContent = { StudioProgress(it, app) },
                startWorkout = { id ->
                    if (app.studio.state.value.draft == null) {
                        val plan = app.studio.state.value.routines.find { it.plan.id == id }?.plan
                            ?: app.trainingRepository.state.value.plans.find { it.id == id }
                        if (plan != null) app.studio.start(plan, preferences.pounds)
                    }
                }, showBottomNavigation = true, bottomContent = { CompactRestBar(app) },
                toolsContent = { TrainingToolsScreen(it, app) })
        }
    }
}
