package org.mz.mzfit.ml.pose

data class PoseLandmarkModel(
    val id: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val likelihood: Float
)
