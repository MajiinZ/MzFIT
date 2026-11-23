package org.mz.mzfit.ml.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mz.mzfit.data.MotionPattern
import kotlin.math.cos
import kotlin.math.sin

class ExerciseTrackerTest {

    private var t = 0L

    /** Side-view squatter (left side to camera) with a given knee angle and torso lean. */
    private fun squatFrame(kneeAngle: Float, leanDeg: Float = 20f, conf: Float = 0.95f): Skeleton {
        val ankle = Joint(500f, 1000f, 0f, conf)
        val knee = Joint(500f, 800f, 0f, conf)
        val bend = Math.toRadians((180f - kneeAngle).toDouble())
        val hip = Joint((500 - 200 * sin(bend)).toFloat(), (800 - 200 * cos(bend)).toFloat(), 0f, conf)
        val lean = Math.toRadians(leanDeg.toDouble())
        val shoulder = Joint((hip.x + 300 * sin(lean)).toFloat(), (hip.y - 300 * cos(lean)).toFloat(), 0f, conf)
        val heel = Joint(490f, 1010f, 0f, conf)
        val toe = Joint(540f, 1012f, 0f, conf)
        val low = Joint(0f, 0f, 0f, 0.1f)
        t += 33
        return Skeleton(
            mapOf(
                Lm.LEFT_ANKLE to ankle, Lm.LEFT_KNEE to knee, Lm.LEFT_HIP to hip, Lm.LEFT_SHOULDER to shoulder,
                Lm.LEFT_HEEL to heel, Lm.LEFT_FOOT to toe, Lm.LEFT_ELBOW to shoulder, Lm.LEFT_WRIST to shoulder,
                Lm.RIGHT_ANKLE to low, Lm.RIGHT_KNEE to low, Lm.RIGHT_HIP to low, Lm.RIGHT_SHOULDER to low,
            ), t,
        )
    }

    private fun ExerciseTracker.feed(angles: List<Float>, lean: Float = 20f) = angles.forEach { update(squatFrame(it, lean)) }

    private fun rep(bottom: Float, frames: Int = 30): List<Float> {
        val down = (0 until frames).map { 170f - (170f - bottom) * it / (frames - 1) }
        return down + down.reversed()
    }

    private fun squatTracker() = ExerciseTracker(TrackerSpecs.forPattern(MotionPattern.SQUAT))

    @Test fun countsFullDepthSquats() {
        val tr = squatTracker()
        tr.feed(List(10) { 170f })
        repeat(3) { tr.feed(rep(88f)) }
        assertEquals(3, tr.current.reps)
        assertEquals(3, tr.current.goodReps)
        assertEquals(100, tr.current.formScore)
    }

    @Test fun doesNotCountBeforeArmed() {
        val tr = squatTracker()
        // User appears already crouched (e.g. walking in / setting up) and stands up: not a rep.
        tr.feed((0 until 30).map { 88f + (170f - 88f) * it / 29 })
        assertEquals(0, tr.current.reps)
        // ...but the next full rep from the armed standing position is counted.
        tr.feed(rep(88f))
        assertEquals(1, tr.current.reps)
    }

    @Test fun ignoresTooShallowMovement() {
        val tr = squatTracker()
        tr.feed(List(10) { 170f })
        tr.feed(rep(135f))
        assertEquals(0, tr.current.reps)
    }

    @Test fun shallowRepCountsButIsNotClean() {
        val tr = squatTracker()
        tr.feed(List(10) { 170f })
        tr.feed(rep(115f))
        assertEquals(1, tr.current.reps)
        assertEquals(0, tr.current.goodReps)
        assertFalse(tr.current.lastRep!!.fullRange)
    }

    @Test fun flagsExcessiveForwardLean() {
        val tr = squatTracker()
        tr.feed(List(10) { 170f })
        tr.feed(rep(88f), lean = 65f)
        assertEquals(1, tr.current.reps)
        assertTrue("lean" in tr.current.lastRep!!.faults)
        assertEquals(CueLevel.BAD, tr.current.lastRep!!.feedback.level)
    }

    @Test fun reportsOutOfFrame() {
        val tr = squatTracker()
        repeat(10) { tr.update(squatFrame(170f, conf = 0.2f)) }
        assertFalse(tr.current.inFrame)
        assertEquals(0, tr.current.reps)
    }

    @Test fun pushUpRequiresPlankPosition() {
        // An upright standing person bending their elbows must not count as push-ups.
        val tr = ExerciseTracker(TrackerSpecs.forPattern(MotionPattern.PUSH_UP))
        val c = 0.95f
        fun frame(elbow: Float): Skeleton {
            val sh = Joint(500f, 400f, 0f, c)
            val el = Joint(500f, 550f, 0f, c)
            val bend = Math.toRadians((180f - elbow).toDouble())
            val wr = Joint((500 + 150 * sin(bend)).toFloat(), (550 + 150 * cos(bend)).toFloat(), 0f, c)
            t += 33
            return Skeleton(mapOf(Lm.LEFT_SHOULDER to sh, Lm.LEFT_ELBOW to el, Lm.LEFT_WRIST to wr,
                Lm.LEFT_HIP to Joint(500f, 700f, 0f, c), Lm.LEFT_ANKLE to Joint(500f, 1100f, 0f, c)), t)
        }
        repeat(10) { tr.update(frame(170f)) }
        repeat(3) { (rep(80f)).forEach { tr.update(frame(it)) } }
        assertEquals(0, tr.current.reps)
    }

    @Test fun angleMath() {
        val a = Joint(0f, 0f); val b = Joint(0f, 1f); val c = Joint(1f, 1f)
        assertEquals(90f, Skeleton.angleOf(a, b, c), 0.01f)
    }

    @Test fun oneEuroSmoothsJitterButTracksMovement() {
        val f = OneEuroFilter()
        var out = 0.0
        for (i in 0 until 60) out = f.filter(100.0 + if (i % 2 == 0) 3.0 else -3.0, i * 33L)
        assertTrue("jitter should be damped", kotlin.math.abs(out - 100.0) < 2.0)
        for (i in 60 until 120) out = f.filter(300.0, i * 33L)
        assertEquals(300.0, out, 1.0)
    }
}
