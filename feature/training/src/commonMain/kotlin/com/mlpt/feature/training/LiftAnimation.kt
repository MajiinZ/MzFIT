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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
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
    val skin = Color(0xFFCF9977)
    val skinLight = Color(0xFFE9B995)
    val rear = Color(0xFF896B62)
    val shorts = Color(0xFF263A56)
    fun mix(a: LiftPoint, b: LiftPoint, t: Float) = LiftPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    // Continuous curved silhouettes and cross-body lighting avoid visible joint seams.
    fun segment(a: LiftPoint, b: LiftPoint, start: Float, end: Float, color: Color) {
        val delta = b.offset() - a.offset()
        val length = delta.getDistance().coerceAtLeast(1f)
        val axis = delta / length
        val normal = Offset(-axis.y, axis.x)
        fun point(t: Float, width: Float) = a.offset() + delta * t + normal * width
        val outline = Path().apply {
            val p = point(0f, start); moveTo(p.x, p.y)
            val c1 = point(0.32f, start * 1.12f); val c2 = point(0.7f, end * 1.3f); val q = point(1f, end)
            cubicTo(c1.x, c1.y, c2.x, c2.y, q.x, q.y)
            val tip = b.offset() + axis * end
            val r = point(1f, -end)
            cubicTo(tip.x, tip.y, tip.x, tip.y, r.x, r.y)
            val c3 = point(0.7f, -end * 1.3f); val c4 = point(0.32f, -start * 1.12f); val t = point(0f, -start)
            cubicTo(c3.x, c3.y, c4.x, c4.y, t.x, t.y)
            val base = a.offset() - axis * start
            cubicTo(base.x, base.y, base.x, base.y, p.x, p.y); close()
        }
        drawPath(outline, Brush.linearGradient(
            listOf(color.copy(red = color.red * 0.76f, green = color.green * 0.76f, blue = color.blue * 0.76f), color, color),
            a.offset() + normal * start, a.offset() - normal * start))
    }
    fun leg(knee: LiftPoint, ankle: LiftPoint, back: Boolean) {
        segment(pose.hip, knee, 13f, 8f, if (back) rear else skin)
        segment(pose.hip, mix(pose.hip, knee, 0.55f), 15f, 12f, if (back) Ink else shorts)
        segment(knee, ankle, 8f, 5f, if (back) rear else skinLight)
        val toe = LiftPoint(ankle.x + 13f, ankle.y + 1f)
        limb(LiftPoint(ankle.x, ankle.y - 6f), ankle, if (back) Color(0xFF91A1B5) else Color(0xFFDAE5EF), 9f)
        limb(ankle, toe, if (back) shorts else Ink, 11f)
        limb(LiftPoint(ankle.x + 1f, ankle.y - 2f), LiftPoint(toe.x - 3f, toe.y - 2f), if (back) Color(0xFF60738C) else Color(0xFF718AA9), 3f)
        limb(LiftPoint(ankle.x - 3f, ankle.y + 5f), LiftPoint(toe.x + 2f, toe.y + 4f), CardSurface, 3f)
    }
    fun arm(elbow: LiftPoint, wrist: LiftPoint, back: Boolean) {
        segment(pose.shoulder, elbow, 10f, 7f, if (back) rear else skin)
        segment(elbow, wrist, 7.5f, 4.5f, if (back) rear else skinLight)
        segment(wrist, LiftPoint(wrist.x + 2f, wrist.y + 3f), 4.5f, 4f, if (back) rear else skinLight)
        if (!back) {
            val highlight = mix(elbow, wrist, 0.4f)
            limb(mix(elbow, wrist, 0.15f), highlight, Color.White.copy(alpha = 0.18f), 2f)
            limb(mix(elbow, wrist, 0.87f), mix(elbow, wrist, 0.96f), shorts, 10f)
        }
    }
    drawOval(Ink.copy(alpha = 0.09f), Offset(95f, 242f), Size(135f, 10f))
    // Overlap the head and shoulder anchors so the neck stays connected even
    // in horizontal bench/hinge poses. Clothing and the face cover both ends.
    segment(pose.head, mix(pose.shoulder, pose.hip, 0.12f), 6.5f, 9f, skin)
    leg(pose.rearKnee, pose.rearAnkle, true)
    arm(pose.rearElbow, pose.rearWrist, true)
    val torso = pose.hip.offset() - pose.shoulder.offset()
    val torsoLength = torso.getDistance().coerceAtLeast(1f)
    val side = Offset(-torso.y / torsoLength, torso.x / torsoLength)
    fun body(t: Float, width: Float) = pose.shoulder.offset() + torso * t + side * width
    val jersey = Path().apply {
        val a = body(0f, 18f); moveTo(a.x, a.y)
        val b = body(0.3f, 22f); val c = body(0.65f, 11f); val d = body(1f, 14f)
        cubicTo(b.x, b.y, c.x, c.y, d.x, d.y)
        val e = body(1f, -14f); lineTo(e.x, e.y)
        val f = body(0.65f, -11f); val g = body(0.3f, -22f); val h = body(0f, -18f)
        cubicTo(f.x, f.y, g.x, g.y, h.x, h.y); close()
    }
    drawPath(jersey, Brush.linearGradient(listOf(Color(0xFF123E52), Color(0xFF238A98), Color(0xFF57B7B3)), body(0f, 20f), body(0f, -20f)))
    val seamStart = body(0.22f, -12f); val seamEnd = body(0.87f, -9f)
    drawLine(Color.White.copy(alpha = 0.22f), seamStart, seamEnd, 1.2f, StrokeCap.Round)
    val logo = body(0.27f, -5f)
    drawLine(Color(0xFFCBF2CD), logo, logo + torso / torsoLength * 8f, 3f, StrokeCap.Round)
    limb(mix(pose.shoulder, pose.hip, 0.94f), pose.hip, shorts, 27f)
    leg(pose.knee, pose.ankle, false)
    drawOval(skinLight, Offset(pose.head.x - 12f, pose.head.y - 15f), Size(26f, 32f))
    drawArc(Ink, 160f, 210f, true, Offset(pose.head.x - 13f, pose.head.y - 16f), Size(27f, 23f))
    drawCircle(skin, 4f, Offset(pose.head.x - 5f, pose.head.y + 2f))
    drawCircle(Ink, 1.3f, Offset(pose.head.x + 8f, pose.head.y - 1f))
    drawLine(Ink.copy(alpha = 0.6f), Offset(pose.head.x + 5f, pose.head.y - 5f), Offset(pose.head.x + 10f, pose.head.y - 4f), 1.3f, StrokeCap.Round)
    drawLine(Color(0xFF9A624E), Offset(pose.head.x + 7f, pose.head.y + 9f), Offset(pose.head.x + 11f, pose.head.y + 8f), 1.2f, StrokeCap.Round)
    arm(pose.elbow, pose.wrist, false)
    if (motion != LiftMotion.PullUp && motion != LiftMotion.SplitSquat) {
        val center = if (motion == LiftMotion.OverheadPress) LiftPoint(155f, pose.wrist.y) else pose.wrist
        val plateHeight = if (motion == LiftMotion.Deadlift) 52f else 28f
        drawLine(Color(0xFF8C9BA9), Offset(center.x - 51f, center.y), Offset(center.x + 51f, center.y), 5f, StrokeCap.Round)
        drawLine(Color(0xFFD9E3EA), Offset(center.x - 50f, center.y - 1f), Offset(center.x + 50f, center.y - 1f), 1f)
        listOf(-37f, 32f).forEach { dx ->
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF1F3044), Color(0xFF4C6279), Color(0xFF172536))), Offset(center.x + dx - 5f, center.y - plateHeight / 2), Size(11f, plateHeight), androidx.compose.ui.geometry.CornerRadius(3f))
            drawLine(Color(0xFF7F94A8), Offset(center.x + dx - 2f, center.y - plateHeight / 2 + 4f), Offset(center.x + dx - 2f, center.y + plateHeight / 2 - 4f), 1f)
        }
    }
}
