package org.mz.mzfit.ml.pose

import kotlin.math.PI
import kotlin.math.abs

/**
 * One Euro filter (Casiez et al., CHI 2012): adaptive low-pass filter that removes jitter when
 * a joint is still, while staying responsive (low lag) when it moves fast. This is the same
 * family of filter used by MediaPipe for landmark smoothing.
 */
class OneEuroFilter(
    private val minCutoff: Double = 1.0,
    private val beta: Double = 0.02,
    private val dCutoff: Double = 1.0,
) {
    private var xPrev: Double? = null
    private var dxPrev = 0.0
    private var tPrev = 0L

    private fun alpha(cutoff: Double, dt: Double): Double {
        val tau = 1.0 / (2 * PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }

    fun filter(x: Double, timestampMs: Long): Double {
        val prev = xPrev
        if (prev == null) {
            xPrev = x; tPrev = timestampMs
            return x
        }
        val dt = ((timestampMs - tPrev).coerceAtLeast(1)) / 1000.0
        tPrev = timestampMs
        val dx = (x - prev) / dt
        val aD = alpha(dCutoff, dt)
        val dxHat = aD * dx + (1 - aD) * dxPrev
        dxPrev = dxHat
        val cutoff = minCutoff + beta * abs(dxHat)
        val a = alpha(cutoff, dt)
        val xHat = a * x + (1 - a) * prev
        xPrev = xHat
        return xHat
    }

    fun reset() { xPrev = null; dxPrev = 0.0 }
}

/** Smooths every landmark of a pose stream independently on x and y. */
class SkeletonSmoother {
    private val fx = HashMap<Int, OneEuroFilter>()
    private val fy = HashMap<Int, OneEuroFilter>()
    private val conf = HashMap<Int, Float>()

    fun smooth(raw: Map<Int, Joint>, timestampMs: Long): Skeleton {
        val out = HashMap<Int, Joint>(raw.size)
        raw.forEach { (id, j) ->
            val x = fx.getOrPut(id) { OneEuroFilter() }.filter(j.x.toDouble(), timestampMs).toFloat()
            val y = fy.getOrPut(id) { OneEuroFilter() }.filter(j.y.toDouble(), timestampMs).toFloat()
            // Light smoothing on confidence too, so joints don't flicker in and out.
            val c = (conf[id] ?: j.confidence) * 0.6f + j.confidence * 0.4f
            conf[id] = c
            out[id] = Joint(x, y, j.z, c)
        }
        return Skeleton(out, timestampMs)
    }

    fun reset() { fx.clear(); fy.clear(); conf.clear() }
}
