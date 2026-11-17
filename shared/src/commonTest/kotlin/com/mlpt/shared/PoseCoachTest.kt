package com.mlpt.shared

import kotlin.math.*
import kotlin.test.*

class PoseCoachTest {
    @Test fun controlledPushupsAndCurlsCanMatchTheirTrackedChecks() {
        listOf(PoseExercise.PushUp,PoseExercise.Curl).forEach { exercise ->
            val run=Run(PoseCoach(exercise,0))
            fun points(angle:Float)=if(exercise==PoseExercise.PushUp) pushup(angle) else curl(angle)
            run.feed(points(170f));run.feed(points(130f));run.feed(points(if(exercise==PoseExercise.PushUp) 85f else 45f));run.feed(points(130f));run.feed(points(170f))
            assertEquals(1,run.coach.assessment.matchedReps)
        }
    }
    @Test fun aSingleLowAngleOutlierCannotQualifyTheMovementRange() {
        val run=Run(PoseCoach(PoseExercise.Squat,0))
        run.feed(squat(170f));run.feed(squat(130f));run.feed(squat(90f),1);run.feed(squat(130f));run.feed(squat(170f))
        assertEquals(0,run.coach.assessment.fullReps)
        assertEquals(1,run.coach.assessment.partialAttempts)
    }
    private fun point(x: Float, y: Float) = PosePoint(x,y,0.99f)
    private fun squat(angle: Float): Map<PoseJoint, PosePoint> {
        val rad = angle * PI / 180
        val hip = point(500f - 250f * sin(rad).toFloat(),600f + 250f * cos(rad).toFloat())
        return mapOf(PoseJoint.LeftShoulder to point(hip.x,hip.y-220f),PoseJoint.LeftHip to hip,
            PoseJoint.LeftKnee to point(500f,600f),PoseJoint.LeftAnkle to point(500f,850f))
    }
    private fun curl(angle: Float, shoulderX: Float = 500f, drift: Float = 0f): Map<PoseJoint, PosePoint> {
        val shoulder = point(shoulderX,200f); val elbow = point(shoulderX+drift,400f)
        val heading = atan2(shoulder.y-elbow.y,shoulder.x-elbow.x) + angle * PI.toFloat()/180f
        return mapOf(PoseJoint.LeftShoulder to shoulder,PoseJoint.LeftHip to point(500f,600f),PoseJoint.LeftElbow to elbow,
            PoseJoint.LeftWrist to point(elbow.x+150f*cos(heading),elbow.y+150f*sin(heading)))
    }
    private fun pushup(angle: Float, sag: Float = 0f): Map<PoseJoint,PosePoint> {
        val arm = curl(angle)
        return mapOf(PoseJoint.LeftShoulder to point(250f,300f),PoseJoint.LeftElbow to point(250f,500f),
            PoseJoint.LeftWrist to arm.getValue(PoseJoint.LeftWrist).let { point(it.x-250f,it.y+100f) },
            PoseJoint.LeftHip to point(550f,300f+sag),PoseJoint.LeftAnkle to point(850f,300f))
    }
    private class Run(val coach: PoseCoach) {
        var time = 0L
        val reps = mutableListOf<PoseRep>()
        fun feed(points: Map<PoseJoint, PosePoint>, count: Int = 4) { repeat(count) {
            time += 100
            coach.analyze(CoachingFrame(time,1000f,1000f,points)).newRep?.let { reps += it }
        } }
    }
    @Test fun countsOnlyACompleteStableSquatCycleAndDoesNotCountStandingStill() {
        val run = Run(PoseCoach(PoseExercise.Squat,0))
        run.feed(squat(170f));run.feed(squat(135f));run.feed(squat(90f));run.feed(squat(135f));run.feed(squat(170f))
        assertEquals(1,run.coach.assessment.fullReps)
        assertTrue(run.reps.single().matchedChecks)
        run.feed(squat(170f),20)
        assertEquals(1,run.coach.assessment.fullReps)
    }
    @Test fun shallowMovementIsLoggedAsPartialInsteadOfCorrectRep() {
        val run = Run(PoseCoach(PoseExercise.Squat,0))
        run.feed(squat(170f));run.feed(squat(130f),8);run.feed(squat(170f))
        assertEquals(0,run.coach.assessment.fullReps)
        assertEquals(1,run.coach.assessment.partialAttempts)
        assertTrue(PoseCue.RangeNotReached.name in run.reps.single().cues)
    }
    @Test fun lowConfidenceAndTrackingGapsCannotCompleteAnUnseenRep() {
        listOf(false,true).forEach { gap ->
            val run = Run(PoseCoach(PoseExercise.Squat,0))
            run.feed(squat(170f));run.feed(squat(135f))
            if (gap) run.time += 2_000 else run.feed(squat(90f).mapValues { it.value.copy(confidence=0.2f) })
            run.feed(squat(90f));run.feed(squat(170f))
            assertEquals(0,run.coach.assessment.fullReps)
            assertTrue(run.reps.isEmpty())
        }
    }
    @Test fun frontalAndCroppedViewsAreNotGradedAsGoodForm() {
        val coach = PoseCoach(PoseExercise.Squat,0)
        val front = squat(170f) + (PoseJoint.RightShoulder to point(800f,150f))
        assertEquals(PoseQuality.CameraAngle,coach.analyze(CoachingFrame(100,1000f,1000f,front)).quality)
        val cropped = squat(170f) + (PoseJoint.LeftAnkle to point(500f,1200f))
        assertEquals(PoseQuality.NotVisible,coach.analyze(CoachingFrame(200,1000f,1000f,cropped)).quality)
    }
    @Test fun pushupsFlagHipAlignmentEvenWhenFullRangeIsReached() {
        val run = Run(PoseCoach(PoseExercise.PushUp,0))
        run.feed(pushup(170f));run.feed(pushup(130f,150f));run.feed(pushup(85f,150f));run.feed(pushup(130f));run.feed(pushup(170f))
        assertEquals(1,run.coach.assessment.fullReps)
        assertEquals(0,run.coach.assessment.matchedReps)
        assertTrue(PoseCue.HipAlignment.name in run.reps.single().cues)
    }
    @Test fun curlsTrackRangeAndFlagTorsoSwing() {
        val run = Run(PoseCoach(PoseExercise.Curl,0))
        run.feed(curl(170f));run.feed(curl(110f,650f));run.feed(curl(45f,650f));run.feed(curl(110f));run.feed(curl(170f))
        assertEquals(1,run.coach.assessment.fullReps)
        assertTrue(PoseCue.TrunkSway.name in run.reps.single().cues)
        assertEquals(0,run.coach.assessment.matchedReps)
    }
    @Test fun degenerateAndNonFiniteLandmarksDoNotProduceAFormScore() {
        assertNull(jointAngle(point(1f,1f),point(1f,1f),point(2f,2f)))
        val invalid = squat(170f) + (PoseJoint.LeftHip to point(Float.NaN,400f))
        assertEquals(PoseQuality.NotVisible,PoseCoach(PoseExercise.Squat,0).analyze(CoachingFrame(100,1000f,1000f,invalid)).quality)
    }
    @Test fun duplicateFramesCannotIncrementCountersOrReturnAnOldRepAgain() {
        val run = Run(PoseCoach(PoseExercise.Squat,0))
        run.feed(squat(170f));run.feed(squat(135f));run.feed(squat(90f));run.feed(squat(135f));run.feed(squat(170f))
        val before = run.coach.assessment
        val repeated = run.coach.analyze(CoachingFrame(run.time,1000f,1000f,squat(170f)))
        assertEquals(before.analyzedSamples,repeated.analyzedSamples)
        assertEquals(1,repeated.fullReps)
        assertNull(repeated.newRep)
    }
}
