package org.mz.mzfit.ml.pose



import com.google.mlkit.vision.pose.Pose

class PoseUseCase {

    fun convertToResult(pose: Pose): PoseResult {
        return PoseResult(
            landmarks = pose.allPoseLandmarks
        )
    }
}
