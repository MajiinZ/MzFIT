package org.mz.mzfit.ui.screens.session

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mz.mzfit.camera.PoseCameraView
import org.mz.mzfit.ml.pose.Cue
import org.mz.mzfit.ml.pose.CueLevel
import org.mz.mzfit.ml.pose.Phase
import org.mz.mzfit.ui.musclemap.FigureView
import org.mz.mzfit.ui.musclemap.MuscleFigure
import org.mz.mzfit.ui.musclemap.MuscleMapPair
import org.mz.mzfit.ui.musclemap.exerciseColors
import org.mz.mzfit.ui.theme.MzColors

@Composable
fun TrainingSessionScreen(vm: TrainingViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    if (!granted) {
        PermissionGate(onRequest = { launcher.launch(Manifest.permission.CAMERA) }, onClose = onClose)
        return
    }

    val ui by vm.ui.collectAsStateWithLifecycle()
    val skeleton by vm.skeleton.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ex = vm.exercise

    BackHandler {
        if (ui.status == SessionStatus.ACTIVE || ui.status == SessionStatus.PAUSED) vm.finishSet() else onClose()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PoseCameraView(
            facing = ui.facing,
            highAccuracy = settings.highAccuracyModel,
            onPose = vm::onPose,
            modifier = Modifier.fillMaxSize(),
        )
        org.mz.mzfit.ui.components.PoseOverlay(
            skeleton = skeleton,
            faultJoints = if (ui.status == SessionStatus.ACTIVE) ui.tracker.faultJoints else emptySet(),
            readouts = if (ui.status == SessionStatus.ACTIVE) ui.tracker.readouts else emptyList(),
            showAngles = settings.showJointAngles,
            modifier = Modifier.fillMaxSize(),
        )

        // Top and bottom scrims for legibility over any background.
        Box(Modifier.fillMaxWidth().height(180.dp).align(Alignment.TopCenter)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))))
        Box(Modifier.fillMaxWidth().height(300.dp).align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))))

        TopHud(ui, ex.name, onClose = { if (ui.status == SessionStatus.ACTIVE) vm.finishSet() else onClose() }, onFlip = vm::flipCamera,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding())

        if (ui.status == SessionStatus.ACTIVE || ui.status == SessionStatus.PAUSED) {
            Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp, start = 16.dp, end = 16.dp)) {
                CueBanner(ui.tracker.cue)
            }
            if (!ex.isHold) RangeMeter(ui.tracker.progress, ui.tracker.activeThreshold, ui.tracker.phase,
                Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
            BottomHud(ui, vm, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }

        when (ui.status) {
            SessionStatus.WAITING -> WaitingCard(ex.pattern?.cameraSetup ?: "", ex.name, ui.setNumber, Modifier.align(Alignment.Center))
            SessionStatus.COUNTDOWN -> Countdown(ui.countdown, Modifier.align(Alignment.Center))
            SessionStatus.PAUSED -> PausedBadge(Modifier.align(Alignment.Center))
            SessionStatus.FINISHED -> ui.summary?.let { SummarySheet(it, ui.setNumber, vm, onClose) }
            else -> Unit
        }
    }
}

@Composable
private fun TopHud(ui: SessionUi, name: String, onClose: () -> Unit, onFlip: () -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassIcon(Icons.Default.Close, "Close", onClose)
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(name, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot(ui.status == SessionStatus.ACTIVE)
                Spacer(Modifier.width(6.dp))
                Text(
                    "SET ${ui.setNumber}  ·  ${formatTime(ui.elapsedSec)}  ·  ${ui.fps} FPS",
                    style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
        GlassIcon(Icons.Default.FlipCameraAndroid, "Switch camera", onFlip)
    }
}

@Composable
private fun LiveDot(live: Boolean) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(if (live) MzColors.Danger else MzColors.TextMuted))
}

@Composable
private fun GlassIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
        Icon(icon, desc, tint = Color.White)
    }
}

@Composable
private fun CueBanner(cue: Cue?) {
    AnimatedContent(cue, transitionSpec = {
        (fadeIn() + slideInVertically { -it / 3 }) togetherWith fadeOut()
    }, label = "cue") { c ->
        if (c == null) return@AnimatedContent
        val (color, icon) = when (c.level) {
            CueLevel.GOOD -> MzColors.Volt to Icons.Default.CheckCircle
            CueLevel.INFO -> Color.White to Icons.Default.Info
            CueLevel.WARN -> MzColors.Amber to Icons.Default.Warning
            CueLevel.BAD -> MzColors.Danger to Icons.Default.Error
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(c.text, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Vertical range-of-motion gauge: fill = current depth, notch = the depth that counts a rep. */
@Composable
private fun RangeMeter(progress: Float, threshold: Float, phase: Phase, modifier: Modifier) {
    val p by animateFloatAsState(progress, spring(stiffness = 600f), label = "rom")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("ROM", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.width(14.dp).height(220.dp)) {
            val r = size.width / 2
            drawRoundRect(Color.White.copy(alpha = 0.15f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
            val h = size.height * p.coerceIn(0f, 1f)
            val col = if (p >= 0.92f) MzColors.Volt else if (p >= threshold) MzColors.Cyan else Color.White
            drawRoundRect(col, topLeft = Offset(0f, size.height - h), size = Size(size.width, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
            val ty = size.height * (1f - threshold)
            drawLine(MzColors.Amber, Offset(-6f, ty), Offset(size.width + 6f, ty), 3f)
            val fy = size.height * (1f - 0.92f)
            drawLine(MzColors.Volt, Offset(-6f, fy), Offset(size.width + 6f, fy), 3f)
        }
        Spacer(Modifier.height(6.dp))
        Text("${(p * 100).toInt().coerceAtMost(100)}%", style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Composable
private fun BottomHud(ui: SessionUi, vm: TrainingViewModel, modifier: Modifier) {
    val ex = vm.exercise
    val t = ui.tracker
    val count = if (ex.isHold) (t.holdMs / 1000).toInt() else t.reps
    Column(modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RepRing(count, ui.targetReps, if (ex.isHold) "SEC" else "REPS", Modifier.size(118.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Stat("FORM", "${t.formScore}", scoreColor(t.formScore))
                Spacer(Modifier.height(10.dp))
                if (ex.isHold) Stat("CLEAN HOLD", "${t.goodHoldMs / 1000}s", Color.White)
                else Stat("CLEAN REPS", "${t.goodReps}/${t.reps}", Color.White)
            }
            // Live mini muscle map: which muscles this exercise is loading.
            val colors = exerciseColors(ex.primary, ex.secondary)
            val showBack = ex.primary.count { it.side == org.mz.mzfit.data.BodySide.BACK } >
                ex.primary.count { it.side == org.mz.mzfit.data.BodySide.FRONT }
            MuscleFigure(if (showBack) FigureView.BACK else FigureView.FRONT, colors, Modifier.height(118.dp), animate = false)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = vm::togglePause, modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Icon(if (ui.status == SessionStatus.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                Spacer(Modifier.width(6.dp))
                Text(if (ui.status == SessionStatus.PAUSED) "Resume" else "Pause")
            }
            Button(onClick = vm::finishSet, modifier = Modifier.weight(1f).height(52.dp)) {
                Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text("Finish set")
            }
        }
    }
}

@Composable
private fun RepRing(count: Int, target: Int, label: String, modifier: Modifier) {
    val frac by animateFloatAsState((count.toFloat() / target).coerceIn(0f, 1f), spring(stiffness = 300f), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            drawArc(Color.White.copy(alpha = 0.15f), -90f, 360f, false, style = Stroke(stroke))
            drawArc(
                Brush.sweepGradient(listOf(MzColors.Cyan, MzColors.Volt, MzColors.Cyan)), -90f, 360f * frac, false,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(count, transitionSpec = { scaleIn(initialScale = 1.4f) + fadeIn() togetherWith fadeOut() }, label = "count") {
                Text("$it", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
            Text("$label / $target", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Black, color = color)
    }
}

fun scoreColor(score: Int): Color = when {
    score >= 85 -> MzColors.Volt
    score >= 65 -> MzColors.Amber
    else -> MzColors.Danger
}

@Composable
private fun WaitingCard(setup: String, name: String, set: Int, modifier: Modifier) {
    Column(
        modifier.padding(28.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(alpha = 0.6f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Videocam, null, tint = MzColors.Volt, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(10.dp))
        Text("Step into frame", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(setup, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("$name · Set $set starts automatically when I can see you",
            style = MaterialTheme.typography.bodySmall, color = MzColors.TextMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Countdown(n: Int, modifier: Modifier) {
    AnimatedContent(n, modifier, transitionSpec = { scaleIn(initialScale = 2f) + fadeIn() togetherWith fadeOut() }, label = "cd") {
        Text("$it", fontSize = 140.sp, fontWeight = FontWeight.Black, color = MzColors.Volt)
    }
}

@Composable
private fun PausedBadge(modifier: Modifier) {
    Text("PAUSED", modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 24.dp, vertical = 12.dp),
        color = Color.White, style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun SummarySheet(s: SessionSummary, setNumber: Int, vm: TrainingViewModel, onClose: () -> Unit) {
    val ex = vm.exercise
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(true, enter = slideInVertically { it } + fadeIn()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MzColors.Surface).verticalScroll(rememberScrollState())
                    .navigationBarsPadding().padding(24.dp),
            ) {
                Text("SET $setNumber COMPLETE", style = MaterialTheme.typography.labelSmall, color = MzColors.Volt)
                Text(ex.name, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    SummaryStat(if (ex.isHold) "Hold" else "Reps", if (ex.isHold) "${s.holdSec}s" else "${s.reps}")
                    SummaryStat("Clean", if (ex.isHold) "—" else "${s.goodReps}")
                    SummaryStat("Form", "${s.formScore}", scoreColor(s.formScore))
                    SummaryStat("Time", formatTime(s.durationSec))
                }
                Spacer(Modifier.height(20.dp))
                Text("MUSCLES WORKED", style = MaterialTheme.typography.labelSmall, color = MzColors.TextMuted)
                Spacer(Modifier.height(10.dp))
                MuscleMapPair(exerciseColors(ex.primary, ex.secondary), Modifier.height(220.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    "Primary: " + ex.primary.joinToString { it.displayName } +
                        if (ex.secondary.isNotEmpty()) "\nSecondary: " + ex.secondary.joinToString { it.displayName } else "",
                    style = MaterialTheme.typography.bodySmall, color = MzColors.TextMuted,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClose, Modifier.weight(1f).height(52.dp)) { Text("Done") }
                    Button(vm::nextSet, Modifier.weight(1f).height(52.dp)) { Text("Next set") }
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, color: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Black, color = color)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MzColors.TextMuted)
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MzColors.Background).statusBarsPadding().padding(24.dp),
    ) {
        IconButton(onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.Videocam, null, tint = MzColors.Volt, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Camera access needed", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "The AI Coach watches your movement to count reps and correct your form. " +
                "All pose analysis happens on your device — video is never recorded or uploaded.",
            style = MaterialTheme.typography.bodyLarge, color = MzColors.TextMuted,
        )
        Spacer(Modifier.height(24.dp))
        Button(onRequest, Modifier.fillMaxWidth().height(52.dp)) { Text("Allow camera") }
        Spacer(Modifier.weight(1f))
    }
}

fun formatTime(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
