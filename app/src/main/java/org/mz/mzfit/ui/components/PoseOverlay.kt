package org.mz.mzfit.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mz.mzfit.ml.pose.AngleReadout
import org.mz.mzfit.ml.pose.Lm
import org.mz.mzfit.ml.pose.Skeleton
import org.mz.mzfit.ui.theme.MzColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Broadcast-style skeleton overlay. Coordinates are already in view space (see PoseCameraView).
 */
@Composable
fun PoseOverlay(
    skeleton: Skeleton?,
    faultJoints: Set<Int>,
    readouts: List<AngleReadout>,
    showAngles: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = MzColors.BoneGood,
) {
    val measurer = rememberTextMeasurer()
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "pulse",
    )

    Canvas(modifier) {
        val sk = skeleton ?: return@Canvas
        val minC = 0.35f
        val core = 4.dp.toPx()
        val glow = 14.dp.toPx()

        fun pt(id: Int): Offset? = sk.joints[id]?.takeIf { it.confidence >= minC }?.let { Offset(it.x, it.y) }
        fun alphaOf(id: Int) = ((sk.joints[id]?.confidence ?: 0f) - minC).div(1f - minC).coerceIn(0.25f, 1f)

        // ---- Head marker ----
        val le = pt(Lm.LEFT_EAR); val re = pt(Lm.RIGHT_EAR); val nose = pt(Lm.NOSE)
        if (nose != null) {
            val r = if (le != null && re != null) (le - re).getDistance() * 0.75f else 22.dp.toPx()
            val center = if (le != null && re != null) (le + re) / 2f else nose
            drawCircle(accent.copy(alpha = 0.18f), r.coerceAtLeast(14.dp.toPx()), center, style = Stroke(glow / 2))
            drawCircle(accent.copy(alpha = 0.9f), r.coerceAtLeast(14.dp.toPx()), center, style = Stroke(2.dp.toPx()))
        }

        // ---- Spine (mid-shoulder → mid-hip) ----
        val ls = pt(Lm.LEFT_SHOULDER); val rs = pt(Lm.RIGHT_SHOULDER)
        val lh = pt(Lm.LEFT_HIP); val rh = pt(Lm.RIGHT_HIP)
        if (ls != null && rs != null && lh != null && rh != null) {
            val a = (ls + rs) / 2f; val b = (lh + rh) / 2f
            drawLine(Color.White.copy(alpha = 0.35f), a, b, 2.dp.toPx(), StrokeCap.Round,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 10f)))
        }

        // ---- Bones ----
        Lm.BONES.forEach { (i, j) ->
            val a = pt(i) ?: return@forEach
            val b = pt(j) ?: return@forEach
            val bad = i in faultJoints && j in faultJoints || (i in faultJoints && j !in Lm.BODY_JOINTS)
            val col = if (bad || (i in faultJoints || j in faultJoints)) MzColors.BoneBad else accent
            val alpha = minOf(alphaOf(i), alphaOf(j))
            drawLine(col.copy(alpha = 0.22f * alpha), a, b, glow, StrokeCap.Round)
            drawLine(
                Brush.linearGradient(listOf(col.copy(alpha = alpha), col.copy(alpha = alpha * 0.75f)), a, b),
                a, b, core, StrokeCap.Round,
            )
        }

        // ---- Joints ----
        Lm.BODY_JOINTS.forEach { id ->
            if (id == Lm.LEFT_INDEX || id == Lm.RIGHT_INDEX || id == Lm.LEFT_FOOT || id == Lm.RIGHT_FOOT ||
                id == Lm.LEFT_HEEL || id == Lm.RIGHT_HEEL) return@forEach
            val p = pt(id) ?: return@forEach
            val bad = id in faultJoints
            val a = alphaOf(id)
            val r = 7.dp.toPx() * if (bad) pulse * 1.25f else 1f
            if (bad) drawCircle(MzColors.BoneBad.copy(alpha = 0.3f), r * 2f, p)
            drawCircle(MzColors.Background.copy(alpha = 0.85f * a), r, p)
            drawCircle((if (bad) MzColors.BoneBad else MzColors.Joint).copy(alpha = a), r, p, style = Stroke(2.5.dp.toPx()))
            drawCircle((if (bad) MzColors.BoneBad else accent).copy(alpha = a), r * 0.42f, p)
        }

        // ---- Angle arcs + labels ----
        if (showAngles) readouts.forEach { ro -> drawAngle(sk, ro, measurer, faultJoints) }
    }
}

private fun DrawScope.drawAngle(
    sk: Skeleton, ro: AngleReadout, measurer: androidx.compose.ui.text.TextMeasurer, faults: Set<Int>,
) {
    val v = sk[ro.vertex] ?: return
    val a = sk[ro.from] ?: return
    val c = sk[ro.to] ?: return
    val center = Offset(v.x, v.y)
    val r = 30.dp.toPx()
    val start = Math.toDegrees(atan2((a.y - v.y).toDouble(), (a.x - v.x).toDouble())).toFloat()
    val end = Math.toDegrees(atan2((c.y - v.y).toDouble(), (c.x - v.x).toDouble())).toFloat()
    var sweep = end - start
    while (sweep > 180f) sweep -= 360f
    while (sweep < -180f) sweep += 360f
    val col = if (ro.vertex in faults) MzColors.BoneBad else MzColors.Volt

    drawArc(col.copy(alpha = 0.18f), start, sweep, useCenter = true, topLeft = center - Offset(r, r), size = Size(r * 2, r * 2))
    drawArc(col, start, sweep, useCenter = false, topLeft = center - Offset(r, r), size = Size(r * 2, r * 2),
        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))

    // Label sits on the bisector, just outside the arc.
    val mid = Math.toRadians((start + sweep / 2f).toDouble())
    val labelCenter = center + Offset((cos(mid) * r * 1.9f).toFloat(), (sin(mid) * r * 1.9f).toFloat())
    val text = "${ro.degrees.roundToInt()}°"
    val layout = measurer.measure(text, TextStyle(color = MzColors.Background, fontSize = 13.sp, fontWeight = FontWeight.Black))
    val pad = 6.dp.toPx()
    val w = layout.size.width + pad * 2; val h = layout.size.height + pad * 0.8f
    val tl = labelCenter - Offset(w / 2, h / 2)
    drawRoundRect(col, tl, Size(w, h), CornerRadius(h / 2))
    drawText(layout, topLeft = tl + Offset(pad, pad * 0.4f))
}
