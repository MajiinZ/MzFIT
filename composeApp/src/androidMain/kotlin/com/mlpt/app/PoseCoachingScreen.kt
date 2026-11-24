package com.mlpt.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mlpt.shared.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoseDetectionScreen() {
    val context = LocalContext.current
    val recorder = remember { (context.applicationContext as TrainingApplication).poseRecorder }
    val state by recorder.state.collectAsState()
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var exercise by remember { mutableStateOf(PoseExercise.Squat) }
    var confirmed by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf(true) }
    var cameraStatus by remember { mutableStateOf("Camera is off") }
    var detail by remember { mutableStateOf<PoseSessionLog?>(null) }
    var detailError by remember { mutableStateOf(false) }
    val running = state.current?.status == "active"
    LaunchedEffect(running) { if (running) while (true) { delay(500); recorder.checkTrackingFreshness() } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) recorder.start(exercise)
    }
    DisposableEffect(owner, recorder) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) recorder.stop(interrupted = true) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); recorder.stop(interrupted = true) }
    }
    Text("Live movement coach", style = MaterialTheme.typography.headlineLarge, color = Ink)
    Text("Experimental 2D angle checks. “Within tracked checks” is not a guarantee of correct or safe technique. Use bodyweight or a light practice load.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PoseExercise.entries.forEach { option -> FilterChip(selected = exercise == option, enabled = !running,
            onClick = { exercise = option; confirmed = false }, label = { Text(option.label) }) }
    }
    Text("Camera frames are analyzed on this device. Derived workout measurements are saved locally. Google ML Kit can collect SDK usage and diagnostic data; see Privacy & safety in the menu.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Text("General fitness only. Not a medical device or a substitute for professional advice.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Text(exercise.instructions, color = SecondaryText)
    Text("Checks: ${exercise.checks}", style = MaterialTheme.typography.bodySmall, color = PerformanceBlue)
    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let { error ->
        Text(error, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = recorder::retryStorage) { Text(if (state.ready) "Retry save" else "Retry loading") }
    }
    if (running) {
        PoseFeedbackPanel(state.feedback, state.lastRep, exercise)
        key(front) { LivePoseCamera(front, { cameraStatus = it }, recorder::onFrame, recorder::resetTracking, state.feedback.quality) }
        Text(cameraStatus, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { recorder.stop() }) { Text("Stop & save log") }
            OutlinedButton(onClick = { recorder.resetTracking(); front = !front }) { Text("Switch camera") }
        }
        Text("Logs include rep angles, feedback and visibility samples. Camera images and full landmark coordinates are not saved or uploaded.", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
    } else {
        Row {
            Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
            Text("My camera is steady and side-on to the exercise.", Modifier.padding(top = 12.dp))
        }
        Button(enabled = confirmed && state.ready && !state.unsaved && !state.saving, onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) recorder.start(exercise)
            else permission.launch(Manifest.permission.CAMERA)
        }) { Text("Start exercise & log") }
        if (denied) {
            Text("Allow camera access in app settings, then start again.", color = SecondaryText)
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Open app settings") }
        }
        if (state.current != null) {
            Text(if (state.saving || state.unsaved) "Saving pose-session data…" else "Pose session saved locally.", color = SecondaryText)
            PoseFeedbackPanel(state.feedback, state.lastRep, PoseExercise.entries.firstOrNull { it.key == state.current?.exerciseKey } ?: exercise)
        }
        Text("Pose-session history", style = MaterialTheme.typography.headlineMedium)
        if (state.history.isEmpty()) Text("Your recorded pose sessions will appear here.", color = SecondaryText)
        state.history.forEach { log ->
            Surface(color = CardSurface, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(log.exerciseName, style = MaterialTheme.typography.titleLarge)
                    Text("${formatPoseDate(log.startedAt)} · ${log.status}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    Text("${log.fullReps} full-range reps · ${log.matchedReps} within checks · ${log.partialAttempts} partial attempts")
                    Text("Usable samples: ${log.usableSamples}/${log.analyzedSamples}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { scope.launch { try { detail = recorder.details(log.id); detailError = detail == null } catch (_: Exception) { detailError = true } } }) { Text("View rep & feedback log") }
                }
            }
        }
        if (detailError) Text("Couldn’t load this pose log. Try again.", color = MaterialTheme.colorScheme.error)
        if (state.history.size >= 20) TextButton(onClick = recorder::loadMoreHistory) { Text("Load older pose sessions") }
    }
    detail?.let { log ->
        ModalBottomSheet(onDismissRequest = { detail = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = CardSurface) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(log.exerciseName, style = MaterialTheme.typography.headlineMedium)
                Text("${formatPoseDate(log.startedAt)} · ${log.status}")
                Text("${log.fullReps} full-range reps · ${log.matchedReps} within tracked checks · ${log.partialAttempts} partial attempts")
                Text("Usable samples: ${log.usableSamples}/${log.analyzedSamples}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                Text("Rep attempts", style = MaterialTheme.typography.titleLarge)
                if (log.reps.isEmpty()) Text("No complete movement attempts were tracked.")
                log.reps.forEach { rep ->
                    Text("Attempt ${rep.number} · ${if (rep.matchedChecks) "Within checks" else if (!rep.completedRange) "Partial range" else "Adjustment flagged"}", style = MaterialTheme.typography.titleMedium)
                    Text("Angle range ${rep.minAngle.roundToInt()}–${rep.maxAngle.roundToInt()}° · ${rep.durationMs / 1000f} s", color = SecondaryText)
                    rep.cues.forEach { code -> Text(poseCueMessage(code), color = SecondaryText) }
                }
                Text("Recent measurements & feedback", style = MaterialTheme.typography.titleLarge)
                Text("Sampled about once per second, plus feedback and phase changes. Up to 100 recent observations are shown.", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                log.observations.asReversed().forEach { event ->
                    Text("${event.elapsedMs / 1000}s · ${poseQualityLabel(event.quality)} · ${posePhaseLabel(event.phase)}" + (event.angle?.let { " · ${it.roundToInt()}°" } ?: ""), style = MaterialTheme.typography.bodySmall)
                    event.cues.forEach { Text(poseCueMessage(it), style = MaterialTheme.typography.bodySmall, color = SecondaryText) }
                }
                TextButton(onClick = { detail = null }) { Text("Close log") }
            }
        }
    }
}

@Composable
private fun PoseFeedbackPanel(feedback: PoseAssessment, lastRep: PoseRep?, exercise: PoseExercise) {
    val label = when (feedback.quality) {
        PoseQuality.WithinChecks -> "Within tracked checks"
        PoseQuality.Adjust -> "Adjust movement"
        PoseQuality.NotVisible -> "Need a clearer view"
        PoseQuality.CameraAngle -> "Adjust camera setup"
        PoseQuality.Setup -> "Hold the start position"
    }
    val color = if (feedback.quality == PoseQuality.Adjust) Color(0xFFFFE6BD) else SoftBlue
    Surface(color = color, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = Ink)
            feedback.cues.forEach { Text(it.message, style = MaterialTheme.typography.bodySmall, color = Ink) }
            if (feedback.cues.isEmpty()) Text(if (feedback.phase == PosePhase.TargetReached) "Range target reached. Return smoothly to the start." else "Move with control through your comfortable range.", style = MaterialTheme.typography.bodySmall)
            feedback.angle?.let { Text("Joint angle: ${it.roundToInt()}° · ${feedback.side} side", style = MaterialTheme.typography.bodySmall) }
            feedback.secondary?.let { Text("${when (exercise) { PoseExercise.Squat -> "Trunk lean"; PoseExercise.PushUp -> "Body alignment"; PoseExercise.Curl -> "Trunk sway" }}: ${it.roundToInt()}°", style = MaterialTheme.typography.bodySmall) }
            Text("${feedback.fullReps} full reps  ·  ${feedback.matchedReps} within checks  ·  ${feedback.partialAttempts} partial", style = MaterialTheme.typography.labelMedium)
            lastRep?.let { rep ->
                Text("Last attempt: ${if (rep.matchedChecks) "matched tracked checks" else "review feedback"}", style = MaterialTheme.typography.labelMedium)
                rep.cues.forEach { Text(poseCueMessage(it), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
private fun poseCueMessage(code: String) = PoseCue.entries.firstOrNull { it.name == code }?.message ?: code
private fun poseQualityLabel(value: String) = when(value) { "WithinChecks" -> "Within checks"; "NotVisible" -> "Need clearer view"; "CameraAngle" -> "Camera setup"; "Adjust" -> "Adjustment flagged"; else -> "Setup" }
private fun posePhaseLabel(value: String) = when(value) { "FindStart" -> "Find start"; "TargetReached" -> "Target reached"; else -> value }
private fun formatPoseDate(time: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))
