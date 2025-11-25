package org.mz.mzfit.ml.pose


import com.google.mlkit.vision.pose.PoseLandmark

data class PoseResult(
    val landmarks: List<PoseLandmark>
)
