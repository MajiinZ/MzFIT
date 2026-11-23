package org.mz.mzfit.ml.pose

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Phase { SETUP, READY, ECCENTRIC, CONCENTRIC, HOLDING }

enum class CueLevel { GOOD, INFO, WARN, BAD }

data class Cue(val text: String, val level: CueLevel)

/** A joint angle to draw on the overlay. */
data class AngleReadout(val vertex: Int, val from: Int, val to: Int, val degrees: Float)

data class RepResult(
    val number: Int,
    val score: Int,
    val fullRange: Boolean,
    val faults: List<String>,
    val durationMs: Long,
    val feedback: Cue,
)

data class TrackerState(
    val reps: Int = 0,
    val goodReps: Int = 0,
    val phase: Phase = Phase.SETUP,
    /** Range-of-motion progress for the current rep, 0..1. */
    val progress: Float = 0f,
    /** Threshold progress needed to count the rep. */
    val activeThreshold: Float = 0.6f,
    val cue: Cue? = null,
    val readouts: List<AngleReadout> = emptyList(),
    val faultJoints: Set<Int> = emptySet(),
    val inFrame: Boolean = false,
    val holdMs: Long = 0,
    val goodHoldMs: Long = 0,
    val formScore: Int = 100,
    val lastRep: RepResult? = null,
    val side: Side = Side.LEFT,
)

/**
 * Real-time rep counter + form coach. Feed it one smoothed [Skeleton] per camera frame.
 *
 * Design:
 *  • Angle-based state machine with hysteresis (rest → eccentric → concentric → rest).
 *  • Only "arms" after the user has held the start position for a few frames, so walking into
 *    frame or setting up never counts as a rep.
 *  • Form checks are debounced (must fail several consecutive frames) to avoid false alarms
 *    from single noisy frames, and checks that can't be evaluated are skipped, not failed.
 *  • Each rep is scored on range of motion, form faults and tempo.
 */
class ExerciseTracker(private val spec: TrackerSpec) {

    private var state = TrackerState(activeThreshold = (spec as? RepSpec)?.activeProgress ?: 0f)
    private var armedFrames = 0
    private var armed = false
    private var repStartMs = 0L
    private var repMaxProgress = 0f
    private val repFaults = LinkedHashMap<String, Int>()
    private val failStreak = HashMap<String, Int>()
    private val repScores = mutableListOf<Int>()
    private var lastFrameMs = 0L
    private var lastSide: Side? = null

    val current: TrackerState get() = state

    fun reset() {
        state = TrackerState(activeThreshold = state.activeThreshold)
        armed = false; armedFrames = 0; repFaults.clear(); failStreak.clear(); repScores.clear()
        lastFrameMs = 0L
    }

    fun update(sk: Skeleton): TrackerState {
        val side = chooseSide(sk)
        val required = spec.required(side)
        val inFrame = sk.allVisible(required)
        val dt = if (lastFrameMs == 0L) 0L else (sk.timestampMs - lastFrameMs).coerceIn(0, 250)
        lastFrameMs = sk.timestampMs

        if (!inFrame) {
            armedFrames = 0
            state = state.copy(
                inFrame = false, side = side, phase = if (armed) state.phase else Phase.SETUP,
                readouts = emptyList(), faultJoints = emptySet(),
                cue = Cue("Step back — I need to see your full ${describe(required, side)}", CueLevel.WARN),
            )
            return state
        }

        // ---- form checks (debounced) ----
        val active = state.phase == Phase.ECCENTRIC || state.phase == Phase.CONCENTRIC || state.phase == Phase.HOLDING
        val failing = mutableListOf<FormCheck>()
        spec.checks.forEach { check ->
            if (check.activeOnly && !active) { failStreak[check.id] = 0; return@forEach }
            val ok = check.passes(sk, side)
            val streak = if (ok == false) (failStreak[check.id] ?: 0) + 1 else 0
            failStreak[check.id] = streak
            if (streak >= FAULT_DEBOUNCE_FRAMES) failing += check
        }
        val faultJoints = failing.flatMap { it.highlight(side) }.toSet()

        state = when (spec) {
            is RepSpec -> updateRep(spec, sk, side, failing)
            is HoldSpec -> updateHold(spec, sk, side, failing, dt)
        }.copy(inFrame = true, side = side, faultJoints = faultJoints)
        return state
    }

    private fun updateRep(spec: RepSpec, sk: Skeleton, side: Side, failing: List<FormCheck>): TrackerState {
        val (angle, readouts) = measure(spec, sk, side)
        if (angle == null) return state.copy(readouts = emptyList())
        val p = spec.progress(angle)
        val now = sk.timestampMs
        var s = state.copy(progress = min(p, 1f), readouts = readouts)

        if (!armed) {
            val atRest = p < REST_PROGRESS && spec.gate(sk, side)
            armedFrames = if (atRest) armedFrames + 1 else 0
            if (armedFrames >= ARM_FRAMES) {
                armed = true
                return s.copy(phase = Phase.READY, cue = Cue("Ready — ${spec.goingCue.lowercase()}", CueLevel.GOOD))
            }
            val msg = if (!spec.gate(sk, side) && spec.gateMessage.isNotEmpty()) spec.gateMessage else "Get into the start position"
            return s.copy(phase = Phase.SETUP, cue = Cue(msg, CueLevel.INFO))
        }

        failing.forEach { repFaults[it.id] = (repFaults[it.id] ?: 0) + 1 }
        val faultCue = failing.firstOrNull()?.let { Cue(it.message, CueLevel.BAD) }

        when (s.phase) {
            Phase.SETUP, Phase.READY, Phase.HOLDING -> {
                if (p > START_PROGRESS) {
                    if (!spec.gate(sk, side)) {
                        return s.copy(cue = Cue(spec.gateMessage, CueLevel.INFO))
                    }
                    repStartMs = now; repMaxProgress = p; repFaults.clear()
                    s = s.copy(phase = Phase.ECCENTRIC, cue = faultCue ?: Cue(spec.goingCue, CueLevel.INFO))
                } else {
                    s = s.copy(cue = faultCue ?: s.cue)
                }
            }
            Phase.ECCENTRIC -> {
                repMaxProgress = max(repMaxProgress, p)
                s = if (p >= 0.92f) {
                    s.copy(phase = Phase.CONCENTRIC, cue = faultCue ?: Cue("Good depth — ${spec.returnCue.lowercase()}", CueLevel.GOOD))
                } else if (p < repMaxProgress - TURN_BACK && repMaxProgress >= spec.activeProgress) {
                    s.copy(phase = Phase.CONCENTRIC, cue = faultCue ?: Cue(spec.returnCue, CueLevel.INFO))
                } else if (p < REST_PROGRESS) {
                    // Aborted before reaching the counting threshold — no rep.
                    s.copy(phase = Phase.READY, cue = Cue(spec.partialMessage, CueLevel.WARN))
                } else s.copy(cue = faultCue ?: s.cue)
            }
            Phase.CONCENTRIC -> {
                repMaxProgress = max(repMaxProgress, p)
                if (p < REST_PROGRESS) {
                    s = completeRep(spec, s, now)
                } else {
                    s = s.copy(cue = faultCue ?: s.cue)
                }
            }
        }
        return s
    }

    private fun completeRep(spec: RepSpec, s: TrackerState, now: Long): TrackerState {
        val duration = now - repStartMs
        val full = repMaxProgress >= 0.92f
        val faults = repFaults.filterValues { it >= FAULT_DEBOUNCE_FRAMES }.keys.toList()
        val tooFast = spec.minRepMs > 0 && duration < spec.minRepMs
        var score = 100
        if (!full) score -= 25
        score -= 20 * faults.size
        if (tooFast) score -= 10
        score = score.coerceIn(0, 100)
        repScores += score
        val good = score >= 80
        val feedback = when {
            faults.isNotEmpty() -> Cue(spec.checks.first { it.id == faults.first() }.message, CueLevel.BAD)
            !full -> Cue(spec.partialMessage, CueLevel.WARN)
            tooFast -> Cue("Slow it down — control every inch", CueLevel.WARN)
            else -> Cue(PRAISE[(s.reps) % PRAISE.size], CueLevel.GOOD)
        }
        val result = RepResult(s.reps + 1, score, full, faults, duration, feedback)
        repFaults.clear()
        return s.copy(
            reps = s.reps + 1,
            goodReps = s.goodReps + if (good) 1 else 0,
            phase = Phase.READY,
            formScore = repScores.average().roundToInt(),
            lastRep = result,
            cue = feedback,
        )
    }

    private fun updateHold(spec: HoldSpec, sk: Skeleton, side: Side, failing: List<FormCheck>, dt: Long): TrackerState {
        val inPosition = spec.gate(sk, side)
        val readouts = listOfNotNull(sk.angle(side.shoulder, side.hip, side.ankle)?.let {
            AngleReadout(side.hip, side.shoulder, side.ankle, it)
        })
        if (!inPosition) {
            return state.copy(phase = if (state.holdMs > 0) Phase.READY else Phase.SETUP, readouts = readouts,
                cue = Cue(spec.gateMessage, CueLevel.INFO))
        }
        val hold = state.holdMs + dt
        val good = state.goodHoldMs + if (failing.isEmpty()) dt else 0
        return state.copy(
            phase = Phase.HOLDING, holdMs = hold, goodHoldMs = good, readouts = readouts,
            formScore = if (hold > 0) ((good * 100) / hold).toInt() else 100,
            cue = failing.firstOrNull()?.let { Cue(it.message, CueLevel.BAD) } ?: Cue(spec.holdCue, CueLevel.GOOD),
        )
    }

    private fun measure(spec: RepSpec, sk: Skeleton, side: Side): Pair<Float?, List<AngleReadout>> {
        fun read(s: Side): AngleReadout? {
            val t = spec.joint(s)
            return sk.angle(t.a, t.b, t.c)?.let { AngleReadout(t.b, t.a, t.c, it) }
        }
        return when (spec.sideMode) {
            SideMode.BEST -> read(side).let { it?.degrees to listOfNotNull(it) }
            SideMode.AVERAGE, SideMode.MIN -> {
                val l = read(Side.LEFT); val r = read(Side.RIGHT)
                val both = listOfNotNull(l, r)
                val v = when {
                    both.isEmpty() -> null
                    spec.sideMode == SideMode.MIN -> both.minOf { it.degrees }
                    else -> both.map { it.degrees }.average().toFloat()
                }
                v to both
            }
        }
    }

    /** Sticky side selection with hysteresis so the skeleton doesn't flip sides every frame. */
    private fun chooseSide(sk: Skeleton): Side {
        val best = sk.bestSide()
        val prev = lastSide
        val chosen = if (prev != null && best != prev &&
            sk.sideConfidence(best) - sk.sideConfidence(prev) < 0.1f) prev else best
        lastSide = chosen
        return chosen
    }

    private fun describe(required: List<Int>, side: Side): String {
        val lower = required.any { it == side.ankle || it == side.knee }
        val upper = required.any { it == side.wrist || it == side.elbow }
        return when {
            lower && upper -> "body"
            lower -> "body, head to feet"
            else -> "upper body and hips"
        }
    }

    companion object {
        const val ARM_FRAMES = 6
        const val FAULT_DEBOUNCE_FRAMES = 4
        const val REST_PROGRESS = 0.25f
        const val START_PROGRESS = 0.3f
        const val TURN_BACK = 0.12f
        private val PRAISE = listOf("Great rep", "Perfect form", "Strong", "Clean rep", "Excellent control", "That's it")
    }
}
