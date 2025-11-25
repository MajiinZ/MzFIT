package org.mz.mzfit.ml.pose

import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.roundToInt

object AngleUtils {

    fun angle(a: PoseLandmark, b: PoseLandmark, c: PoseLandmark): Int {
        val ab = floatArrayOf(a.position.x - b.position.x, a.position.y - b.position.y)
        val cb = floatArrayOf(c.position.x - b.position.x, c.position.y - b.position.y)

        val angle = Math.toDegrees(
            atan2(cb[1], cb[0]) - atan2(ab[1], ab[0]).toDouble()
        )

        val normalized = abs(angle)
        return if (normalized > 180) (360 - normalized).roundToInt() else normalized.roundToInt()
    }
}
