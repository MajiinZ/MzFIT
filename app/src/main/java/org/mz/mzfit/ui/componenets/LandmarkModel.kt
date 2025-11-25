package org.mz.mzfit.ui.componenets

data class LandmarkModel(
    val id: Int,          // ML Kit landmark type (e.g., PoseLandmark.LEFT_ELBOW)
    val x: Float,         // Smoothed X
    val y: Float,         // Smoothed Y
    val z: Float,         // Smoothed Z (3D only)
    val likelihood: Float // ML Kit confidence
)