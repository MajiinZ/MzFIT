package org.mz.mzfit.ml.pose

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/** ML Kit BlazePose landmark indices (mirrors com.google.mlkit.vision.pose.PoseLandmark). */
object Lm {
    const val NOSE = 0
    const val LEFT_EAR = 7
    const val RIGHT_EAR = 8
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_INDEX = 19
    const val RIGHT_INDEX = 20
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val LEFT_HEEL = 29
    const val RIGHT_HEEL = 30
    const val LEFT_FOOT = 31
    const val RIGHT_FOOT = 32

    /** Bones rendered by the overlay. Face points are intentionally omitted for a cleaner look. */
    val BONES: List<Pair<Int, Int>> = listOf(
        LEFT_SHOULDER to RIGHT_SHOULDER, LEFT_HIP to RIGHT_HIP,
        LEFT_SHOULDER to LEFT_HIP, RIGHT_SHOULDER to RIGHT_HIP,
        LEFT_SHOULDER to LEFT_ELBOW, LEFT_ELBOW to LEFT_WRIST, LEFT_WRIST to LEFT_INDEX,
        RIGHT_SHOULDER to RIGHT_ELBOW, RIGHT_ELBOW to RIGHT_WRIST, RIGHT_WRIST to RIGHT_INDEX,
        LEFT_HIP to LEFT_KNEE, LEFT_KNEE to LEFT_ANKLE, LEFT_ANKLE to LEFT_HEEL, LEFT_HEEL to LEFT_FOOT, LEFT_ANKLE to LEFT_FOOT,
        RIGHT_HIP to RIGHT_KNEE, RIGHT_KNEE to RIGHT_ANKLE, RIGHT_ANKLE to RIGHT_HEEL, RIGHT_HEEL to RIGHT_FOOT, RIGHT_ANKLE to RIGHT_FOOT,
    )

    val BODY_JOINTS: Set<Int> = BONES.flatMap { listOf(it.first, it.second) }.toSet()
}

enum class Side(
    val shoulder: Int, val elbow: Int, val wrist: Int, val hip: Int,
    val knee: Int, val ankle: Int, val heel: Int, val foot: Int,
) {
    LEFT(Lm.LEFT_SHOULDER, Lm.LEFT_ELBOW, Lm.LEFT_WRIST, Lm.LEFT_HIP, Lm.LEFT_KNEE, Lm.LEFT_ANKLE, Lm.LEFT_HEEL, Lm.LEFT_FOOT),
    RIGHT(Lm.RIGHT_SHOULDER, Lm.RIGHT_ELBOW, Lm.RIGHT_WRIST, Lm.RIGHT_HIP, Lm.RIGHT_KNEE, Lm.RIGHT_ANKLE, Lm.RIGHT_HEEL, Lm.RIGHT_FOOT);

    val other: Side get() = if (this == LEFT) RIGHT else LEFT
    val all: List<Int> get() = listOf(shoulder, elbow, wrist, hip, knee, ankle)
}

data class Joint(val x: Float, val y: Float, val z: Float = 0f, val confidence: Float = 1f)

/**
 * A single smoothed pose frame in view coordinates (y grows downward).
 * All geometry helpers return null when the required joints aren't confidently visible.
 */
class Skeleton(val joints: Map<Int, Joint>, val timestampMs: Long) {

    fun visible(id: Int, min: Float = MIN_CONFIDENCE): Boolean = (joints[id]?.confidence ?: 0f) >= min

    fun allVisible(ids: Collection<Int>): Boolean = ids.all { visible(it) }

    operator fun get(id: Int): Joint? = joints[id]?.takeIf { it.confidence >= MIN_CONFIDENCE }

    /** Interior angle at [b] in degrees (0–180). */
    fun angle(a: Int, b: Int, c: Int): Float? {
        val pa = this[a] ?: return null
        val pb = this[b] ?: return null
        val pc = this[c] ?: return null
        return angleOf(pa, pb, pc)
    }

    /** Average visibility of the joints on one side — used to pick the side facing the camera. */
    fun sideConfidence(side: Side): Float = side.all.map { joints[it]?.confidence ?: 0f }.average().toFloat()

    fun bestSide(): Side = if (sideConfidence(Side.LEFT) >= sideConfidence(Side.RIGHT)) Side.LEFT else Side.RIGHT

    /** Angle of segment a→b relative to straight up (0 = vertical, 90 = horizontal). */
    fun fromVertical(a: Int, b: Int): Float? {
        val pa = this[a] ?: return null
        val pb = this[b] ?: return null
        val dx = pb.x - pa.x
        val dy = pa.y - pb.y // invert y so "up" is positive
        return Math.toDegrees(atan2(abs(dx), dy).toDouble()).toFloat()
    }

    /** Acute angle of segment a→b relative to horizontal (0 = flat, 90 = vertical). */
    fun fromHorizontal(a: Int, b: Int): Float? {
        val pa = this[a] ?: return null
        val pb = this[b] ?: return null
        return Math.toDegrees(atan2(abs(pb.y - pa.y), abs(pb.x - pa.x)).toDouble()).toFloat()
    }

    /** Forward/backward torso lean from vertical, measured hip → shoulder. */
    fun torsoLean(side: Side): Float? = fromVertical(side.hip, side.shoulder)

    fun distance(a: Int, b: Int): Float? {
        val pa = this[a] ?: return null
        val pb = this[b] ?: return null
        return hypot(pb.x - pa.x, pb.y - pa.y)
    }

    /**
     * Signed perpendicular offset of [mid] from the line [a]→[c], normalized by the line length.
     * Positive = below the line in screen space (e.g. sagging hips in a plank).
     */
    fun offsetFromLine(a: Int, mid: Int, c: Int): Float? {
        val pa = this[a] ?: return null
        val pm = this[mid] ?: return null
        val pc = this[c] ?: return null
        val len = hypot(pc.x - pa.x, pc.y - pa.y)
        if (len < 1e-3f) return null
        val t = ((pm.x - pa.x) * (pc.x - pa.x) + (pm.y - pa.y) * (pc.y - pa.y)) / (len * len)
        val projY = pa.y + t * (pc.y - pa.y)
        return (pm.y - projY) / len
    }

    companion object {
        const val MIN_CONFIDENCE = 0.5f

        fun angleOf(a: Joint, b: Joint, c: Joint): Float {
            val v1x = a.x - b.x; val v1y = a.y - b.y
            val v2x = c.x - b.x; val v2y = c.y - b.y
            val n = sqrt(v1x * v1x + v1y * v1y) * sqrt(v2x * v2x + v2y * v2y)
            if (n < 1e-6f) return 180f
            val cos = ((v1x * v2x + v1y * v2y) / n).coerceIn(-1f, 1f)
            return Math.toDegrees(acos(cos).toDouble()).toFloat()
        }
    }
}
