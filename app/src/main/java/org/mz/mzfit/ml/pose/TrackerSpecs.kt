package org.mz.mzfit.ml.pose

import org.mz.mzfit.data.MotionPattern
import kotlin.math.abs

/** How a bilateral measurement is reduced to one number. */
enum class SideMode {
    /** Use the side most visible to the camera (side-on exercises). */
    BEST,
    /** Average both sides when visible (front-facing exercises). */
    AVERAGE,
    /** Use the smaller of the two angles (e.g. the front knee in a lunge). */
    MIN,
}

/** Three landmarks forming the measured joint angle; the middle one is the vertex. */
data class JointTriple(val a: Int, val b: Int, val c: Int)

/**
 * A single biomechanical rule. [passes] returns null when it can't be evaluated this frame
 * (joints occluded, wrong camera angle), so we never penalize what we can't see.
 */
class FormCheck(
    val id: String,
    val message: String,
    /** Only evaluate while the user is mid-rep (not standing at rest). */
    val activeOnly: Boolean = true,
    /** Landmarks to paint red on the skeleton when the check fails. */
    val highlight: (Side) -> List<Int>,
    val passes: (Skeleton, Side) -> Boolean?,
)

sealed class TrackerSpec(
    val sideMode: SideMode,
    val required: (Side) -> List<Int>,
    val checks: List<FormCheck>,
    /** Must be true to *start* a rep or hold (e.g. "be in plank position"). */
    val gate: (Skeleton, Side) -> Boolean,
    val gateMessage: String,
)

class RepSpec(
    val joint: (Side) -> JointTriple,
    /** Angle at the resting/start position of the rep. */
    val restAngle: Float,
    /** Angle that must be crossed for the movement to count as a rep. */
    val activeAngle: Float,
    /** Angle representing full, ideal range of motion. */
    val targetAngle: Float,
    val partialMessage: String,
    val goingCue: String,
    val returnCue: String,
    /** Reps faster than this get a tempo warning; 0 disables. */
    val minRepMs: Long = 900,
    sideMode: SideMode,
    required: (Side) -> List<Int>,
    checks: List<FormCheck> = emptyList(),
    gate: (Skeleton, Side) -> Boolean = { _, _ -> true },
    gateMessage: String = "",
) : TrackerSpec(sideMode, required, checks, gate, gateMessage) {
    /** 0 at rest → 1 at full range of motion. Works for angles that close or open. */
    fun progress(angle: Float): Float = ((angle - restAngle) / (targetAngle - restAngle)).coerceIn(0f, 1.2f)
    val activeProgress: Float get() = (activeAngle - restAngle) / (targetAngle - restAngle)
}

class HoldSpec(
    sideMode: SideMode,
    required: (Side) -> List<Int>,
    checks: List<FormCheck>,
    gate: (Skeleton, Side) -> Boolean,
    gateMessage: String,
    val holdCue: String,
) : TrackerSpec(sideMode, required, checks, gate, gateMessage)

object TrackerSpecs {

    private fun elbow(s: Side) = JointTriple(s.shoulder, s.elbow, s.wrist)
    private fun knee(s: Side) = JointTriple(s.hip, s.knee, s.ankle)
    private fun hip(s: Side) = JointTriple(s.shoulder, s.hip, s.knee)
    private fun abduction(s: Side) = JointTriple(s.hip, s.shoulder, s.elbow)

    private fun leanUnder(max: Float, msg: String, id: String = "lean", activeOnly: Boolean = true) =
        FormCheck(id, msg, activeOnly, highlight = { listOf(it.shoulder, it.hip) }) { sk, s ->
            sk.torsoLean(s)?.let { it <= max }
        }

    private val hipsSag = FormCheck("sag", "Hips are sagging — brace your core", activeOnly = false,
        highlight = { listOf(it.hip, it.shoulder, it.ankle) }) { sk, s ->
        sk.offsetFromLine(s.shoulder, s.hip, s.ankle)?.let { it < 0.09f }
    }
    private val hipsPike = FormCheck("pike", "Hips too high — lower them into a straight line", activeOnly = false,
        highlight = { listOf(it.hip, it.shoulder, it.ankle) }) { sk, s ->
        sk.offsetFromLine(s.shoulder, s.hip, s.ankle)?.let { it > -0.12f }
    }
    private val horizontalBody: (Skeleton, Side) -> Boolean = { sk, s ->
        (sk.fromHorizontal(s.shoulder, s.ankle) ?: 90f) < 35f
    }

    /** Elbows/arms must move together on bilateral front-facing lifts. */
    private fun evenArms(triple: (Side) -> JointTriple, tolerance: Float, msg: String) =
        FormCheck("even", msg, activeOnly = true, highlight = { listOf(Lm.LEFT_ELBOW, Lm.RIGHT_ELBOW, Lm.LEFT_WRIST, Lm.RIGHT_WRIST) }) { sk, _ ->
            val l = triple(Side.LEFT).let { sk.angle(it.a, it.b, it.c) }
            val r = triple(Side.RIGHT).let { sk.angle(it.a, it.b, it.c) }
            if (l == null || r == null) null else abs(l - r) <= tolerance
        }

    fun forPattern(p: MotionPattern): TrackerSpec = when (p) {
        MotionPattern.SQUAT -> RepSpec(
            joint = ::knee, restAngle = 165f, activeAngle = 125f, targetAngle = 95f,
            partialMessage = "Go deeper — aim for thighs parallel",
            goingCue = "Sit back and down", returnCue = "Drive up through your mid-foot",
            sideMode = SideMode.BEST, required = { listOf(it.shoulder, it.hip, it.knee, it.ankle) },
            checks = listOf(
                leanUnder(50f, "Chest up — you're folding forward"),
                FormCheck("heels", "Keep your heels down", highlight = { listOf(it.heel, it.foot) }) { sk, s ->
                    val heel = sk[s.heel]; val toe = sk[s.foot]; val ankle = sk[s.ankle]
                    if (heel == null || toe == null || ankle == null) null
                    else heel.y >= toe.y - (sk.distance(s.knee, s.ankle) ?: 100f) * 0.12f
                },
            ),
        )

        MotionPattern.PUSH_UP -> RepSpec(
            joint = ::elbow, restAngle = 160f, activeAngle = 120f, targetAngle = 95f,
            partialMessage = "Lower your chest closer to the floor",
            goingCue = "Lower with control", returnCue = "Press the floor away",
            sideMode = SideMode.BEST, required = { listOf(it.shoulder, it.elbow, it.wrist, it.hip, it.ankle) },
            checks = listOf(hipsSag, hipsPike),
            gate = horizontalBody, gateMessage = "Get into a high plank to start",
        )

        MotionPattern.BICEP_CURL -> RepSpec(
            joint = ::elbow, restAngle = 155f, activeAngle = 95f, targetAngle = 60f,
            partialMessage = "Curl all the way up to your shoulder",
            goingCue = "Curl", returnCue = "Lower all the way down",
            sideMode = SideMode.BEST, required = { listOf(it.shoulder, it.elbow, it.wrist, it.hip) },
            checks = listOf(
                FormCheck("elbow_drift", "Pin your elbows to your sides", highlight = { listOf(it.shoulder, it.elbow) }) { sk, s ->
                    sk.fromVertical(s.elbow, s.shoulder)?.let { it <= 35f }
                },
                leanUnder(15f, "Don't swing — keep your torso still", id = "swing"),
            ),
            gate = { sk, s -> (sk.fromVertical(s.elbow, s.shoulder) ?: 0f) < 45f },
            gateMessage = "Stand tall with arms at your sides",
        )

        MotionPattern.SHOULDER_PRESS -> RepSpec(
            joint = ::elbow, restAngle = 85f, activeAngle = 140f, targetAngle = 162f,
            partialMessage = "Press all the way to lockout",
            goingCue = "Press overhead", returnCue = "Lower to your ears",
            sideMode = SideMode.AVERAGE, required = { listOf(it.shoulder, it.elbow, it.wrist) },
            checks = listOf(
                evenArms(::elbow, 30f, "Press both arms evenly"),
                FormCheck("arch", "Ribs down — don't lean back", highlight = { listOf(it.shoulder, it.hip) }) { sk, s ->
                    sk.torsoLean(s)?.let { it <= 15f }
                },
            ),
            gate = { sk, s ->
                val w = sk[s.wrist]; val sh = sk[s.shoulder]
                w != null && sh != null && w.y < sh.y
            },
            gateMessage = "Bring the weights up to shoulder height",
        )

        MotionPattern.LUNGE -> RepSpec(
            joint = ::knee, restAngle = 165f, activeAngle = 125f, targetAngle = 100f,
            partialMessage = "Sink lower — both knees to about 90°",
            goingCue = "Step and lower", returnCue = "Push back up",
            sideMode = SideMode.MIN, required = { listOf(it.hip, it.knee, it.ankle) },
            checks = listOf(leanUnder(25f, "Keep your torso upright")),
        )

        MotionPattern.GLUTE_BRIDGE -> RepSpec(
            joint = ::hip, restAngle = 125f, activeAngle = 150f, targetAngle = 168f,
            partialMessage = "Drive your hips higher — squeeze at the top",
            goingCue = "Drive your hips up", returnCue = "Lower with control",
            sideMode = SideMode.BEST, required = { listOf(it.shoulder, it.hip, it.knee) },
            checks = listOf(
                FormCheck("knee_angle", "Adjust your feet — knees should be near 90° at the top",
                    highlight = { listOf(it.knee, it.ankle) }) { sk, s ->
                    sk.angle(s.hip, s.knee, s.ankle)?.let { it in 60f..120f }
                },
            ),
            gate = { sk, s -> (sk.fromHorizontal(s.shoulder, s.knee) ?: 90f) < 45f },
            gateMessage = "Lie on your back with knees bent",
        )

        MotionPattern.LATERAL_RAISE -> RepSpec(
            joint = ::abduction, restAngle = 20f, activeAngle = 62f, targetAngle = 82f,
            partialMessage = "Raise a little higher — to shoulder height",
            goingCue = "Raise out to the sides", returnCue = "Lower slowly",
            sideMode = SideMode.AVERAGE, required = { listOf(it.hip, it.shoulder, it.elbow) },
            checks = listOf(
                FormCheck("too_high", "Stop at shoulder height", highlight = { listOf(it.shoulder, it.elbow) }) { sk, s ->
                    sk.angle(s.hip, s.shoulder, s.elbow)?.let { it <= 108f }
                },
                evenArms(::abduction, 22f, "Raise both arms evenly"),
                leanUnder(12f, "No swinging — keep your torso still", id = "swing"),
            ),
        )

        MotionPattern.HIP_HINGE -> RepSpec(
            joint = ::hip, restAngle = 168f, activeAngle = 135f, targetAngle = 108f,
            partialMessage = "Push your hips back further",
            goingCue = "Hips back", returnCue = "Squeeze your glutes to stand",
            sideMode = SideMode.BEST, required = { listOf(it.shoulder, it.hip, it.knee, it.ankle) },
            checks = listOf(
                FormCheck("squatting", "Hinge at the hips — don't squat it", highlight = { listOf(it.knee, it.hip) }) { sk, s ->
                    sk.angle(s.hip, s.knee, s.ankle)?.let { it >= 125f }
                },
            ),
        )

        MotionPattern.JUMPING_JACK -> RepSpec(
            joint = ::abduction, restAngle = 30f, activeAngle = 110f, targetAngle = 140f,
            partialMessage = "Bring your arms all the way overhead",
            goingCue = "Jump out", returnCue = "Jump in",
            minRepMs = 0, sideMode = SideMode.AVERAGE, required = { listOf(it.hip, it.shoulder, it.elbow, it.ankle) },
            checks = listOf(
                FormCheck("feet", "Jump your feet wider", highlight = { listOf(Lm.LEFT_ANKLE, Lm.RIGHT_ANKLE) }) { sk, _ ->
                    val feet = sk.distance(Lm.LEFT_ANKLE, Lm.RIGHT_ANKLE)
                    val hips = sk.distance(Lm.LEFT_HIP, Lm.RIGHT_HIP)
                    val abd = sk.angle(Lm.LEFT_HIP, Lm.LEFT_SHOULDER, Lm.LEFT_ELBOW)
                    if (feet == null || hips == null || abd == null) null
                    else if (abd < 120f) true else feet > hips * 1.5f
                },
            ),
        )

        MotionPattern.PLANK -> HoldSpec(
            sideMode = SideMode.BEST, required = { listOf(it.shoulder, it.hip, it.ankle) },
            checks = listOf(hipsSag, hipsPike),
            gate = horizontalBody, gateMessage = "Get into a forearm plank",
            holdCue = "Hold strong — breathe",
        )

        MotionPattern.SIT_UP -> RepSpec(
            joint = ::hip, restAngle = 125f, activeAngle = 85f, targetAngle = 65f,
            partialMessage = "Curl up higher — chest toward your thighs",
            goingCue = "Curl up", returnCue = "Lower with control",
            sideMode = SideMode.BEST, required = { listOf(it.shoulder, it.hip, it.knee) },
            gate = { sk, s -> (sk.angle(s.hip, s.knee, s.ankle) ?: 180f) < 130f },
            gateMessage = "Lie back with your knees bent",
        )

        MotionPattern.BENT_OVER_ROW -> RepSpec(
            joint = ::elbow, restAngle = 155f, activeAngle = 115f, targetAngle = 85f,
            partialMessage = "Pull higher — elbows past your torso",
            goingCue = "Row", returnCue = "Lower to a full stretch",
            sideMode = SideMode.BEST, required = { listOf(it.shoulder, it.elbow, it.wrist, it.hip) },
            checks = listOf(
                FormCheck("upright", "Stay hinged — don't stand up into the row", highlight = { listOf(it.shoulder, it.hip) }) { sk, s ->
                    sk.torsoLean(s)?.let { it >= 25f }
                },
            ),
            gate = { sk, s -> (sk.torsoLean(s) ?: 0f) >= 30f },
            gateMessage = "Hinge forward with a flat back",
        )

        MotionPattern.OVERHEAD_EXTENSION -> RepSpec(
            joint = ::elbow, restAngle = 70f, activeAngle = 125f, targetAngle = 158f,
            partialMessage = "Extend fully overhead",
            goingCue = "Extend", returnCue = "Lower behind your head",
            sideMode = SideMode.BEST, required = { listOf(it.shoulder, it.elbow, it.wrist) },
            checks = listOf(
                FormCheck("elbow_flare", "Keep your elbows pointing up", highlight = { listOf(it.shoulder, it.elbow) }) { sk, s ->
                    sk.fromVertical(s.shoulder, s.elbow)?.let { it <= 35f }
                },
            ),
            gate = { sk, s ->
                val e = sk[s.elbow]; val sh = sk[s.shoulder]
                e != null && sh != null && e.y < sh.y
            },
            gateMessage = "Raise your elbows overhead",
        )
    }
}
