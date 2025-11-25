package org.mz.mzfit.ml.pose

import com.google.mlkit.vision.pose.PoseLandmark

class RepCounter {

    private var squatDown = false
    private var pushDown = false

    var squatReps = 0
    var pushupReps = 0

    fun processSquat(leftKnee: Int, rightKnee: Int, map: Map<Int, PoseLandmark>) {
        val left = map[leftKnee] ?: return
        val right = map[rightKnee] ?: return

        val angle = AngleUtils.angle(
            map[PoseLandmark.LEFT_HIP]!!,
            map[PoseLandmark.LEFT_KNEE]!!,
            map[PoseLandmark.LEFT_ANKLE]!!
        )

        if (angle < 60 && !squatDown) {
            squatDown = true
        }
        if (angle > 160 && squatDown) {
            squatReps++
            squatDown = false
        }
    }

    fun processPushup(map: Map<Int, PoseLandmark>) {
        val angle = AngleUtils.angle(
            map[PoseLandmark.LEFT_SHOULDER]!!,
            map[PoseLandmark.LEFT_ELBOW]!!,
            map[PoseLandmark.LEFT_WRIST]!!
        )

        if (angle < 70 && !pushDown) pushDown = true
        if (angle > 150 && pushDown) {
            pushupReps++
            pushDown = false
        }
    }
}
