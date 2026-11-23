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

    // ---------------- squat: front view & depth standard ----------------

    /**
     * Front-on squatter. Thighs point at the camera, so only the hip's vertical drop shows depth:
     * the 2D knee angle stays ~180°. [thighDeg] is thigh-from-vertical (90 = parallel).
     */
    private fun frontSquatFrame(thighDeg: Float, kneeGap: Float = 160f): Skeleton {
        val c = 0.95f
        val dy = (200 * cos(Math.toRadians(thighDeg.toDouble()))).toFloat()
        fun leg(cx: Float, kx: Float) = Triple(Joint(cx, 800f - dy, 0f, c), Joint(kx, 800f, 0f, c), Joint(cx, 1000f, 0f, c))
        val (lh, lk, la) = leg(420f, 500f - kneeGap / 2)
        val (rh, rk, ra) = leg(580f, 500f + kneeGap / 2)
        val ls = Joint(400f, lh.y - 300f, 0f, c); val rs = Joint(600f, rh.y - 300f, 0f, c)
        t += 33
        return Skeleton(mapOf(
            Lm.LEFT_HIP to lh, Lm.LEFT_KNEE to lk, Lm.LEFT_ANKLE to la, Lm.LEFT_SHOULDER to ls,
            Lm.RIGHT_HIP to rh, Lm.RIGHT_KNEE to rk, Lm.RIGHT_ANKLE to ra, Lm.RIGHT_SHOULDER to rs,
            Lm.LEFT_HEEL to Joint(la.x, 1010f, 0f, c), Lm.LEFT_FOOT to Joint(la.x - 10, 1012f, 0f, c),
            Lm.RIGHT_HEEL to Joint(ra.x, 1010f, 0f, c), Lm.RIGHT_FOOT to Joint(ra.x + 10, 1012f, 0f, c),
        ), t)
    }

    private fun thighRep(bottom: Float, frames: Int = 30): List<Float> {
        val down = (0 until frames).map { 3f + (bottom - 3f) * it / (frames - 1) }
        return down + down.reversed()
    }

    @Test fun countsSquatsFilmedFromTheFront() {
        val tr = squatTracker()
        repeat(10) { tr.update(frontSquatFrame(3f)) }
        repeat(3) { thighRep(92f).forEach { tr.update(frontSquatFrame(it)) } }
        assertEquals(3, tr.current.reps)
        assertEquals(3, tr.current.goodReps)
    }

    @Test fun frontViewHalfSquatIsPartial() {
        val tr = squatTracker()
        repeat(10) { tr.update(frontSquatFrame(3f)) }
        thighRep(62f).forEach { tr.update(frontSquatFrame(it)) }
        assertEquals(1, tr.current.reps)
        assertFalse(tr.current.lastRep!!.fullRange)
    }

    @Test fun flagsKneesCavingIn() {
        val tr = squatTracker()
        repeat(10) { tr.update(frontSquatFrame(3f)) }
        thighRep(92f).forEach { tr.update(frontSquatFrame(it, kneeGap = if (it > 40f) 90f else 160f)) }
        assertEquals(1, tr.current.reps)
        assertTrue("valgus" in tr.current.lastRep!!.faults)
    }

    @Test fun sideViewSquatAboveParallelIsNotFullDepth() {
        // Knee 100° with a vertical shin = thigh 80° from vertical, i.e. 10° above parallel → just counts as full.
        // Knee 110° = thigh 20° above parallel → counted but flagged as partial (the old thresholds called it full).
        val tr = squatTracker()
        tr.feed(List(10) { 170f })
        tr.feed(rep(110f))
        assertEquals(1, tr.current.reps)
        assertFalse(tr.current.lastRep!!.fullRange)
        tr.feed(rep(100f))
        assertTrue(tr.current.lastRep!!.fullRange)
    }

    // ---------------- push-ups ----------------

    /** Side-on push-up. [bodyDeg] tilts the body line (0 = floor, ~45 = hands on a bench). */
    private fun pushUpFrame(elbow: Float, bodyDeg: Float = 10f, conf: Float = 0.95f): Skeleton {
        val r = Math.toRadians(bodyDeg.toDouble())
        val ankle = Joint(200f, 900f, 0f, conf)
        val shoulder = Joint((200 + 600 * cos(r)).toFloat(), (900 - 600 * sin(r)).toFloat(), 0f, conf)
        val hip = Joint((ankle.x + shoulder.x) / 2, (ankle.y + shoulder.y) / 2, 0f, conf)
        // Only the 2D elbow angle matters to the tracker: the wrist sits below the shoulder and the
        // elbow is offset toward the feet to form an isosceles triangle with the requested angle.
        val wrist = Joint(shoulder.x, shoulder.y + 260f * sin(Math.toRadians(elbow / 2.0)).toFloat() * 2f, 0f, conf)
        val half = Math.toRadians((180f - elbow) / 2.0)
        val mid = Joint((shoulder.x + wrist.x) / 2, (shoulder.y + wrist.y) / 2)
        val halfChord = kotlin.math.hypot(wrist.x - shoulder.x, wrist.y - shoulder.y) / 2
        val offset = (halfChord * kotlin.math.tan(half)).toFloat()
        val el = Joint(mid.x - offset, mid.y, 0f, conf)
        t += 33
        return Skeleton(mapOf(Lm.LEFT_SHOULDER to shoulder, Lm.LEFT_ELBOW to el, Lm.LEFT_WRIST to wrist,
            Lm.LEFT_HIP to hip, Lm.LEFT_ANKLE to ankle), t)
    }

    private fun pushRep(bottom: Float, frames: Int = 25): List<Float> {
        val down = (0 until frames).map { 170f - (170f - bottom) * it / (frames - 1) }
        return down + down.reversed()
    }

    private fun pushUps() = ExerciseTracker(TrackerSpecs.forPattern(MotionPattern.PUSH_UP))

    @Test fun pushUpFrameHasRequestedElbowAngle() {
        val sk = pushUpFrame(90f)
        assertEquals(90f, sk.angle(Lm.LEFT_SHOULDER, Lm.LEFT_ELBOW, Lm.LEFT_WRIST)!!, 0.5f)
    }

    @Test fun countsFullPushUps() {
        val tr = pushUps()
        repeat(10) { tr.update(pushUpFrame(170f)) }
        repeat(3) { pushRep(85f).forEach { tr.update(pushUpFrame(it)) } }
        assertEquals(3, tr.current.reps)
        assertEquals(3, tr.current.goodReps)
    }

    @Test fun halfPushUpCountsButIsPartial() {
        val tr = pushUps()
        repeat(10) { tr.update(pushUpFrame(170f)) }
        pushRep(112f).forEach { tr.update(pushUpFrame(it)) }
        assertEquals(1, tr.current.reps)
        assertFalse(tr.current.lastRep!!.fullRange)
    }

    @Test fun ignoresElbowWobbleAtTheTop() {
        val tr = pushUps()
        repeat(10) { tr.update(pushUpFrame(170f)) }
        repeat(3) { pushRep(135f).forEach { tr.update(pushUpFrame(it)) } }
        assertEquals(0, tr.current.reps)
    }

    @Test fun countsInclinePushUps() {
        val tr = pushUps()
        repeat(10) { tr.update(pushUpFrame(170f, bodyDeg = 45f)) }
        repeat(2) { pushRep(88f).forEach { tr.update(pushUpFrame(it, bodyDeg = 45f)) } }
        assertEquals(2, tr.current.reps)
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
