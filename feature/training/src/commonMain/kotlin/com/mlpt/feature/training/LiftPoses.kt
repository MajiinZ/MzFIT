package com.mlpt.feature.training

import com.mlpt.shared.LiftMotion

data class LiftPoint(val x: Float, val y: Float)
data class LiftPose(
    val head: LiftPoint, val shoulder: LiftPoint, val hip: LiftPoint,
    val elbow: LiftPoint, val wrist: LiftPoint, val knee: LiftPoint, val ankle: LiftPoint,
    val rearElbow: LiftPoint, val rearWrist: LiftPoint, val rearKnee: LiftPoint, val rearAnkle: LiftPoint,
) {
    fun points() = listOf(head, shoulder, hip, elbow, wrist, knee, ankle, rearElbow, rearWrist, rearKnee, rearAnkle)
}
private fun p(x: Int, y: Int) = LiftPoint(x.toFloat(), y.toFloat())
private fun LiftPoint.back() = copy(x = x - 10f)
private fun frame(head: LiftPoint, shoulder: LiftPoint, hip: LiftPoint, elbow: LiftPoint, wrist: LiftPoint,
    knee: LiftPoint = p(155, 191), ankle: LiftPoint = p(155, 237),
    rearElbow: LiftPoint = elbow.back(), rearWrist: LiftPoint = wrist.back(),
    rearKnee: LiftPoint = knee.back(), rearAnkle: LiftPoint = ankle.back(),
) = LiftPose(head, shoulder, hip, elbow, wrist, knee, ankle, rearElbow, rearWrist, rearKnee, rearAnkle)

/** Original, simplified 2D movement illustrations in a 320 × 260 scene. */
fun liftKeyframes(motion: LiftMotion): Pair<LiftPose, LiftPose> {
    val standing = frame(p(155, 44), p(155, 73), p(155, 145), p(155, 111), p(155, 150))
    val hinge = frame(p(192, 72), p(178, 99), p(125, 150), p(178, 142), p(177, 188), p(159, 191))
    return when (motion) {
        LiftMotion.Squat -> standing.copy(elbow = p(180, 96), wrist = p(155, 80), rearElbow = p(171, 96), rearWrist = p(145, 80)) to
            frame(p(154, 80), p(145, 107), p(120, 176), p(174, 130), p(146, 115), p(168, 195))
        LiftMotion.Deadlift -> frame(p(190, 104), p(176, 131), p(125, 170), p(175, 174), p(175, 217), p(159, 199)) to standing
        LiftMotion.RomanianDeadlift -> standing to
            frame(p(200, 76), p(185, 102), p(123, 143), p(184, 141), p(179, 181), p(151, 190))
        LiftMotion.BenchPress -> frame(p(88, 170), p(119, 170), p(215, 170), p(119, 130), p(119, 90), p(249, 201), p(249, 237)) to
            frame(p(88, 170), p(119, 170), p(215, 170), p(150, 189), p(150, 143), p(249, 201), p(249, 237))
        LiftMotion.OverheadPress -> standing.copy(elbow = p(119, 112), wrist = p(119, 76), rearElbow = p(191, 112), rearWrist = p(191, 76)) to
            standing.copy(elbow = p(128, 43), wrist = p(140, 15), rearElbow = p(182, 43), rearWrist = p(170, 15))
        LiftMotion.Row -> hinge to hinge.copy(elbow = p(143, 105), wrist = p(160, 131), rearElbow = p(133, 105), rearWrist = p(150, 131))
        LiftMotion.PullUp -> frame(p(155, 75), p(155, 104), p(155, 176), p(117, 68), p(110, 32), p(155, 212), p(155, 245),
            p(193, 68), p(200, 32), p(145, 212), p(145, 245)) to
            frame(p(155, 17), p(155, 46), p(155, 118), p(118, 65), p(110, 32), p(155, 158), p(155, 198),
                p(192, 65), p(200, 32), p(145, 158), p(145, 198))
        LiftMotion.SplitSquat -> standing.copy(knee = p(182, 187), ankle = p(200, 237), rearKnee = p(119, 186), rearAnkle = p(89, 237)) to
            frame(p(143, 76), p(143, 105), p(143, 177), p(143, 143), p(143, 182), p(201, 190), p(200, 237),
                p(133, 143), p(133, 182), p(122, 222), p(89, 237))
    }
}

fun liftPoseAt(motion: LiftMotion, fraction: Float): LiftPose {
    val (start, end) = liftKeyframes(motion)
    val t = fraction.coerceIn(0f, 1f)
    fun mix(a: LiftPoint, b: LiftPoint) = LiftPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    return LiftPose(mix(start.head, end.head), mix(start.shoulder, end.shoulder), mix(start.hip, end.hip),
        mix(start.elbow, end.elbow), mix(start.wrist, end.wrist), mix(start.knee, end.knee), mix(start.ankle, end.ankle),
        mix(start.rearElbow, end.rearElbow), mix(start.rearWrist, end.rearWrist), mix(start.rearKnee, end.rearKnee), mix(start.rearAnkle, end.rearAnkle))
}

fun LiftMotion.positionLabels() = when (this) {
    LiftMotion.Squat, LiftMotion.SplitSquat -> "Standing" to "Lowered"
    LiftMotion.Deadlift -> "Floor setup" to "Standing"
    LiftMotion.RomanianDeadlift -> "Standing" to "Hip hinge"
    LiftMotion.BenchPress -> "Arms extended" to "Bar lowered"
    LiftMotion.OverheadPress -> "Shoulder height" to "Overhead"
    LiftMotion.Row -> "Arms extended" to "Bar at ribs"
    LiftMotion.PullUp -> "Controlled hang" to "Pulled up"
}
