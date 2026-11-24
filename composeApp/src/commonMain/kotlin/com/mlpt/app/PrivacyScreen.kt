package com.mlpt.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.mlpt.shared.*

internal val privacySections = listOf(
    "Your training data" to "MzFit is developed by Zak Falkenbach. Your profile name, goals, preferences, routines, workout drafts, completed sets, loads, notes and pose-session measurements are stored in app-private storage on your device. MzFit does not operate an account service or upload your workout history to a developer server.",
    "Camera and pose coaching" to "Camera access is optional and requested when you start pose coaching. Images are processed on your device by Google ML Kit. MzFit does not save or upload camera images or video. It saves derived measurements such as rep counts, joint-angle ranges, timing, visibility and feedback. The camera stops when you leave coaching or background the app.",
    "Google ML Kit services" to "Google ML Kit can collect device and app information, installation identifiers, performance measurements, feature usage and error diagnostics. Pose detection also uses Firebase services for remote configuration and installation diagnostics. Google states SDK diagnostic data is encrypted in transit. On-device image processing does not mean the SDK collects no data. See Google’s ML Kit disclosures and privacy policy below.",
    "Exports, retention and removal" to "Training tools lets you export your history to a file you choose. That file contains personal workout information; protect it and share it only intentionally. Local records remain until you clear MzFit storage in Android settings or uninstall the app. Android backup is disabled. Exported files and copies you share remain outside the app and must be deleted separately. Clearing storage cannot remove diagnostic data already handled by Google under its own policies.",
    "Exercise safety" to "MzFit supports general fitness and is not a medical device. It does not diagnose, treat, cure or prevent medical conditions. Consult a qualified healthcare professional for medical advice and before starting a new exercise program. Stop if you experience pain, dizziness or other concerning symptoms. Pose coaching is experimental: 2D camera angles and visibility can cause errors. Feedback and animations cannot establish that an exercise is safe or correctly performed. Use a qualified trainer for personalized technique guidance.",
    "Support and policy updates" to "Contact the developer through the MzFit GitHub issue tracker linked below. Issues are public: do not post workout exports, camera images or sensitive health information. This policy applies to MzFit 0.8.0 and will be updated when data practices change. Updated November 23, 2026.",
)

@Composable
fun PrivacyScreen() {
    val links = LocalUriHandler.current
    Text("Privacy & safety", style = MaterialTheme.typography.headlineLarge, color = Ink)
    Text("MzFit 0.8.0 · Updated November 23, 2026", color = SecondaryText)
    privacySections.forEach { (heading, text) ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(heading, style = MaterialTheme.typography.titleLarge, color = Ink)
            Text(text, color = SecondaryText)
        }
    }
    TextButton(onClick = { links.openUri("https://developers.google.com/ml-kit/android-data-disclosure") }) { Text("Google ML Kit data disclosures") }
    TextButton(onClick = { links.openUri("https://policies.google.com/privacy") }) { Text("Google privacy policy") }
    TextButton(onClick = { links.openUri("https://github.com/MajiinZ/MzFIT/issues") }) { Text("MzFit support & privacy questions") }
}
