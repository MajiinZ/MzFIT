package org.mz.mzfit.ml.pose

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

data class PosePoint(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val type: Int
)

fun Pose.toPosePoints(): List<PosePoint> {
    return this.allPoseLandmarks.map { lm ->
        PosePoint(
            x = lm.position.x,
            y = lm.position.y,
            z = lm.position3D.z,
            type = lm.landmarkType
        )
    }
}
