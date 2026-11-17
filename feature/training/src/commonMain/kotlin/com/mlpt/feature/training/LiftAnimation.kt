package com.mlpt.feature.training

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mlpt.shared.*
import kotlin.math.PI
import kotlin.math.cos

@Composable
fun LiftAnimation(motion: LiftMotion) {
    var playing by remember(motion) { mutableStateOf(false) }
    var slow by remember(motion) { mutableStateOf(false) }
    var position by remember(motion) { mutableStateOf(0f) }
    var direction by remember(motion) { mutableStateOf(1f) }
    val (start, end) = motion.positionLabels()
    LaunchedEffect(playing, slow, motion) {
        if (playing) {
            var previous = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val delta = ((now - previous) / 1_000_000_000f).coerceAtMost(0.05f)
                previous = now
                val next = position + delta * (if (slow) 0.22f else 0.44f) * direction
                if (next >= 1f) { position = 1f; direction = -1f }
                else if (next <= 0f) { position = 0f; direction = 1f }
                else position = next
            }
        }
    }
    val eased = ((1.0 - cos(position * PI)) / 2).toFloat()
    val pose = liftPoseAt(motion, eased)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(Modifier.fillMaxWidth().height(260.dp).background(AppBackground, RoundedCornerShape(18.dp))
            .semantics { contentDescription = "Simplified ${motion.name} animation, ${if (playing) "playing" else "paused"}. $start to $end. Use the position slider to inspect movement." }) {
            val ratio = minOf(size.width / 320f, size.height / 260f)
            translate((size.width - 320f * ratio) / 2, (size.height - 260f * ratio) / 2) {
                scale(ratio, ratio, Offset.Zero) { drawLiftScene(motion, pose) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { playing = !playing }) { Text(if (playing) "Pause" else "Play") }
            FilterChip(selected = slow, onClick = { slow = !slow }, label = { Text("0.5× speed") })
            TextButton(onClick = { playing = false; position = 0f; direction = 1f }) { Text("Reset") }
        }
        Text("Position", style = MaterialTheme.typography.labelMedium)
        Slider(value = position, onValueChange = { playing = false; position = it })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(start, style = MaterialTheme.typography.labelSmall)
            Text(end, style = MaterialTheme.typography.labelSmall)
        }
        Text("Simplified movement illustration · Not a personalized form assessment", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
    }
}

internal fun DrawScope.drawLiftScene(motion: LiftMotion, pose: LiftPose) {
    fun LiftPoint.offset() = Offset(x, y)
    fun limb(a: LiftPoint, b: LiftPoint, color: Color, width: Float = 13f) = drawLine(color, a.offset(), b.offset(), width, StrokeCap.Round)
    drawLine(Divider, Offset(36f, 250f), Offset(284f, 250f), 2f)
    if (motion == LiftMotion.BenchPress) {
        drawLine(HeroAccent, Offset(76f, 191f), Offset(233f, 191f), 13f, StrokeCap.Round)
        drawLine(HeroAccent, Offset(97f, 194f), Offset(97f, 247f), 7f)
        drawLine(HeroAccent, Offset(218f, 194f), Offset(218f, 247f), 7f)
    }
    if (motion == LiftMotion.PullUp) {
        drawLine(HeroAccent, Offset(76f, 32f), Offset(238f, 32f), 7f, StrokeCap.Round)
        drawLine(HeroAccent, Offset(76f, 32f), Offset(76f, 248f), 5f)
        drawLine(HeroAccent, Offset(238f, 32f), Offset(238f, 248f), 5f)
    }
    val rear = Color(0xFF94A8C8)
    limb(pose.shoulder, pose.rearElbow, rear)
    limb(pose.rearElbow, pose.rearWrist, rear, 11f)
    limb(pose.hip, pose.rearKnee, rear, 17f)
    limb(pose.rearKnee, pose.rearAnkle, rear, 13f)
    limb(pose.shoulder, pose.hip, PerformanceBlue, 29f)
    limb(pose.hip, pose.knee, Ink, 18f)
    limb(pose.knee, pose.ankle, Ink, 13f)
    limb(pose.shoulder, pose.elbow, PerformanceBlue, 14f)
    limb(pose.elbow, pose.wrist, Color(0xFF79A8F7), 11f)
    drawCircle(Ink, 14f, pose.head.offset())
    drawCircle(CardSurface, 3f, Offset(pose.head.x + 6f, pose.head.y - 2f))
    drawCircle(SoftBlue, 4f, pose.elbow.offset())
    drawCircle(SoftBlue, 4f, pose.knee.offset())
    limb(pose.ankle, LiftPoint(pose.ankle.x + 14f, pose.ankle.y), Ink, 10f)
    limb(pose.rearAnkle, LiftPoint(pose.rearAnkle.x + 14f, pose.rearAnkle.y), rear, 10f)
    if (motion != LiftMotion.PullUp && motion != LiftMotion.SplitSquat) {
        val center = if (motion == LiftMotion.OverheadPress) LiftPoint(155f, pose.wrist.y) else pose.wrist
        val plateHeight = if (motion == LiftMotion.Deadlift) 52f else 28f
        drawLine(HeroAccent, Offset(center.x - 47f, center.y), Offset(center.x + 47f, center.y), 5f)
        listOf(-37f, 32f).forEach { dx ->
            drawRoundRect(Ink, Offset(center.x + dx - 4f, center.y - plateHeight / 2), Size(9f, plateHeight), androidx.compose.ui.geometry.CornerRadius(3f))
        }
    }
}
