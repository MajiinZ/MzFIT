package com.mlpt.shared

import kotlin.math.*

enum class PoseExercise(val key: String, val label: String, val instructions: String, val checks: String) {
    Squat("bodyweight_squat", "Bodyweight squat", "Face sideways to a steady camera at hip height. Keep your shoulder, hip, knee and ankle in view.", "Knee movement range and projected trunk lean"),
    PushUp("push_up", "Push-up", "Place the camera side-on near floor level. Keep your shoulder, elbow, wrist, hip and ankle in view. Standard full-body push-ups only.", "Elbow movement range and shoulder–hip–ankle alignment"),
    Curl("biceps_curl", "Biceps curl", "Stand side-on to a steady camera. Keep your shoulder, elbow, wrist and hip visible. Track one arm per session.", "Elbow movement range, upper-arm drift and trunk sway"),
}
enum class PoseJoint { LeftShoulder, RightShoulder, LeftElbow, RightElbow, LeftWrist, RightWrist, LeftHip, RightHip, LeftKnee, RightKnee, LeftAnkle, RightAnkle }
data class PosePoint(val x: Float, val y: Float, val confidence: Float)
data class CoachingFrame(val timeMs: Long, val width: Float, val height: Float, val points: Map<PoseJoint, PosePoint>)
enum class PoseQuality { Setup, NotVisible, CameraAngle, WithinChecks, Adjust }
enum class PosePhase { FindStart, Ready, Moving, TargetReached }
enum class PoseCue(val message: String) {
    Visibility("Keep the required joints fully visible in good lighting."),
    SideView("Turn side-on to the camera; this view is too frontal."),
    StandingSetup("Stand upright and keep the camera level for this exercise."),
    PushUpSetup("Use a side-on floor view with your whole body visible."),
    ReturnToStart("Return to the start position and hold briefly to begin."),
    TrackingReset("Tracking was interrupted. Return to the start position."),
    TorsoLean("Keep your torso controlled; substantial forward lean was observed."),
    HipAlignment("Keep your hips aligned with your shoulders and ankles."),
    UpperArmDrift("Keep your upper arm steadier beside your torso."),
    TrunkSway("Keep your torso steady rather than swinging with the curl."),
    RangeNotReached("The tracked movement-range target was not reached."),
    TooFast("Slow the movement down; this attempt was very quick."),
}
data class PoseRep(
    val number: Int, val elapsedMs: Long, val durationMs: Long, val completedRange: Boolean,
    val minAngle: Float, val maxAngle: Float, val minSecondary: Float, val maxSecondary: Float,
    val cues: List<String>,
) { val matchedChecks: Boolean get() = completedRange && cues.isEmpty() }
data class PoseObservation(
    val number: Int, val elapsedMs: Long, val quality: String, val phase: String,
    val angle: Float?, val secondary: Float?, val confidence: Float?, val cues: List<String>,
)
data class PoseAssessment(
    val quality: PoseQuality = PoseQuality.Setup, val phase: PosePhase = PosePhase.FindStart,
    val cues: List<PoseCue> = listOf(PoseCue.ReturnToStart), val angle: Float? = null,
    val secondary: Float? = null, val confidence: Float? = null, val side: String? = null,
    val fullReps: Int = 0, val matchedReps: Int = 0, val partialAttempts: Int = 0,
    val analyzedSamples: Int = 0, val usableSamples: Int = 0, val newRep: PoseRep? = null,
)
data class PoseSessionLog(
    val id: String, val exerciseKey: String, val exerciseName: String, val startedAt: Long,
    val endedAt: Long? = null, val status: String = "active", val algorithmVersion: String = "angle-checks-v1",
    val analyzedSamples: Int = 0, val usableSamples: Int = 0,
    val reps: List<PoseRep> = emptyList(), val observations: List<PoseObservation> = emptyList(),
) {
    val fullReps get() = reps.count { it.completedRange }
    val matchedReps get() = reps.count { it.matchedChecks }
    val partialAttempts get() = reps.count { !it.completedRange }
}
data class PoseSessionSummary(
    val id: String, val exerciseName: String, val startedAt: Long, val endedAt: Long?, val status: String,
    val fullReps: Int, val matchedReps: Int, val partialAttempts: Int, val analyzedSamples: Int, val usableSamples: Int,
)

fun jointAngle(a: PosePoint, b: PosePoint, c: PosePoint): Float? {
    val ax = (a.x - b.x).toDouble(); val ay = (a.y - b.y).toDouble()
    val cx = (c.x - b.x).toDouble(); val cy = (c.y - b.y).toDouble()
    val denominator = hypot(ax, ay) * hypot(cx, cy)
    if (!denominator.isFinite() || denominator < 0.001) return null
    return (acos(((ax * cx + ay * cy) / denominator).coerceIn(-1.0, 1.0)) * 180 / PI).toFloat()
}

/** Transparent prototype angle rules, not a trained or clinically validated form classifier. */
class PoseCoach(private val exercise: PoseExercise, private val startedMonoMs: Long) {
    private var phase = PosePhase.FindStart
    private var side: Boolean? = null // true = left; locked until tracking resets
    private var lastTime: Long? = null
    private var band = ""
    private var bandSince = 0L
    private var bandSamples = 0
    private var baselineLean = 0f
    private var attemptStart = 0L
    private var targetReached = false
    private val attemptCues = linkedSetOf<PoseCue>()
    private var minAngle = 180f; private var maxAngle = 0f
    private var minSecondary = 180f; private var maxSecondary = -180f
    private var attempts = 0
    var assessment = PoseAssessment(); private set

    fun resetTracking() {
        phase = PosePhase.FindStart; side = null; band = ""; bandSamples = 0
        attemptCues.clear(); targetReached = false
        assessment = assessment.copy(quality = PoseQuality.NotVisible, phase = phase,
            cues = listOf(PoseCue.TrackingReset), angle = null, secondary = null, confidence = null, newRep = null)
    }

    fun analyze(frame: CoachingFrame): PoseAssessment {
        if (lastTime != null && frame.timeMs <= lastTime!!) return assessment.copy(newRep = null)
        if (lastTime != null && frame.timeMs - lastTime!! > 1_000) resetTracking()
        lastTime = frame.timeMs
        val analyzed = assessment.analyzedSamples + 1
        fun unavailable(quality: PoseQuality, cue: PoseCue): PoseAssessment {
            resetTracking()
            assessment = assessment.copy(quality = quality, cues = listOf(cue), analyzedSamples = analyzed)
            return assessment
        }
        if (!frame.width.isFinite() || !frame.height.isFinite() || frame.width <= 0 || frame.height <= 0) return unavailable(PoseQuality.NotVisible, PoseCue.Visibility)
        fun joints(left: Boolean) = if (left) listOf(PoseJoint.LeftShoulder, PoseJoint.LeftElbow, PoseJoint.LeftWrist, PoseJoint.LeftHip, PoseJoint.LeftKnee, PoseJoint.LeftAnkle)
            else listOf(PoseJoint.RightShoulder, PoseJoint.RightElbow, PoseJoint.RightWrist, PoseJoint.RightHip, PoseJoint.RightKnee, PoseJoint.RightAnkle)
        fun needed(left: Boolean): List<PoseJoint> { val j = joints(left); return when (exercise) {
            PoseExercise.Squat -> listOf(j[0], j[3], j[4], j[5])
            PoseExercise.PushUp -> listOf(j[0], j[1], j[2], j[3], j[5])
            PoseExercise.Curl -> listOf(j[0], j[1], j[2], j[3])
        } }
        fun visible(point: PosePoint?) = point != null && point.confidence.isFinite() && point.confidence in 0.75f..1f &&
            point.x.isFinite() && point.y.isFinite() && point.x in (frame.width * 0.01f)..(frame.width * 0.99f) && point.y in (frame.height * 0.01f)..(frame.height * 0.99f)
        fun confidence(left: Boolean): Float = needed(left).minOf { key -> frame.points[key]?.takeIf(::visible)?.confidence ?: 0f }
        val left = side ?: (confidence(true) >= confidence(false))
        if (confidence(left) < 0.75f) return unavailable(PoseQuality.NotVisible, PoseCue.Visibility)
        val ids = joints(left)
        fun point(index: Int) = frame.points.getValue(ids[index])
        val shoulder = point(0); val hip = point(3)
        val torso = hypot(shoulder.x - hip.x, shoulder.y - hip.y)
        if (torso < frame.height * 0.05f) return unavailable(PoseQuality.NotVisible, PoseCue.Visibility)
        val other = frame.points[joints(!left)[0]]
        if (visible(other) && abs(shoulder.x - other!!.x) / torso > 0.65f) return unavailable(PoseQuality.CameraAngle, PoseCue.SideView)
        val lean = (atan2((shoulder.x - hip.x).toDouble(), (hip.y - shoulder.y).toDouble()) * 180 / PI).toFloat()
        val primary = when (exercise) {
            PoseExercise.Squat -> jointAngle(hip, point(4), point(5))
            else -> jointAngle(shoulder, point(1), point(2))
        } ?: return unavailable(PoseQuality.NotVisible, PoseCue.Visibility)
        val secondary = when (exercise) {
            PoseExercise.Squat -> abs(lean)
            PoseExercise.PushUp -> jointAngle(shoulder, hip, point(5)) ?: return unavailable(PoseQuality.NotVisible, PoseCue.Visibility)
            PoseExercise.Curl -> abs(lean - baselineLean)
        }
        if (exercise != PoseExercise.PushUp && hip.y - shoulder.y < torso * 0.25f) return unavailable(PoseQuality.CameraAngle, PoseCue.StandingSetup)
        if (exercise == PoseExercise.PushUp && abs(shoulder.y - point(5).y) > abs(shoulder.x - point(5).x) * 0.8f) return unavailable(PoseQuality.CameraAngle, PoseCue.PushUpSetup)
        val cues = mutableListOf<PoseCue>()
        when (exercise) {
            PoseExercise.Squat -> if (secondary > 55f) cues += PoseCue.TorsoLean
            PoseExercise.PushUp -> if (secondary < 160f) cues += PoseCue.HipAlignment
            PoseExercise.Curl -> {
                if (abs(point(1).x - shoulder.x) / torso > 0.35f) cues += PoseCue.UpperArmDrift
                if (phase != PosePhase.FindStart && secondary > 12f) cues += PoseCue.TrunkSway
            }
        }
        val startThreshold = if (exercise == PoseExercise.Squat) 160f else 155f
        val targetThreshold = when (exercise) { PoseExercise.Squat -> 100f; PoseExercise.PushUp -> 95f; PoseExercise.Curl -> 60f }
        val currentBand = when { primary >= startThreshold -> "start"; primary <= targetThreshold -> "target"; else -> "middle" }
        if (currentBand == band) bandSamples++ else { band = currentBand; bandSince = frame.timeMs; bandSamples = 1 }
        val stable = bandSamples >= 3 && frame.timeMs - bandSince >= 200
        var newRep: PoseRep? = null
        if (phase == PosePhase.FindStart && band == "start" && stable) {
            phase = PosePhase.Ready; side = left; baselineLean = lean
        } else if (phase == PosePhase.Ready && primary < startThreshold - 15f) {
            phase = PosePhase.Moving; attemptStart = frame.timeMs; targetReached = false; attemptCues.clear()
            minAngle = primary; maxAngle = primary; minSecondary = secondary; maxSecondary = secondary
        }
        if (phase == PosePhase.Moving || phase == PosePhase.TargetReached) {
            attemptCues.addAll(cues)
            minAngle = minOf(minAngle, primary); maxAngle = maxOf(maxAngle, primary)
            minSecondary = minOf(minSecondary, secondary); maxSecondary = maxOf(maxSecondary, secondary)
            if (band == "target" && stable) { targetReached = true; phase = PosePhase.TargetReached }
            if (band == "start" && stable) {
                if (!targetReached) attemptCues += PoseCue.RangeNotReached
                if (frame.timeMs - attemptStart < 800) attemptCues += PoseCue.TooFast
                newRep = PoseRep(++attempts, frame.timeMs - startedMonoMs, frame.timeMs - attemptStart,
                    targetReached, minAngle, maxAngle, minSecondary, maxSecondary, attemptCues.map { it.name })
                phase = PosePhase.Ready; baselineLean = lean
            }
        }
        assessment = PoseAssessment(
            quality = if (phase == PosePhase.FindStart) PoseQuality.Setup else if (cues.isEmpty()) PoseQuality.WithinChecks else PoseQuality.Adjust,
            phase = phase, cues = if (phase == PosePhase.FindStart) listOf(PoseCue.ReturnToStart) else cues,
            angle = primary, secondary = secondary, confidence = confidence(left), side = if (left) "Left" else "Right",
            fullReps = assessment.fullReps + if (newRep?.completedRange == true) 1 else 0,
            matchedReps = assessment.matchedReps + if (newRep?.matchedChecks == true) 1 else 0,
            partialAttempts = assessment.partialAttempts + if (newRep != null && !newRep.completedRange) 1 else 0,
            analyzedSamples = analyzed, usableSamples = assessment.usableSamples + 1, newRep = newRep,
        )
        return assessment
    }
}
