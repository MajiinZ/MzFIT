package org.mz.mzfit.ui.screens.session

import android.app.Application
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mz.mzfit.MzFitApplication
import org.mz.mzfit.data.CameraFacing
import org.mz.mzfit.data.Exercise
import org.mz.mzfit.data.WorkoutLog
import org.mz.mzfit.ml.pose.CueLevel
import org.mz.mzfit.ml.pose.ExerciseTracker
import org.mz.mzfit.ml.pose.Joint
import org.mz.mzfit.ml.pose.Skeleton
import org.mz.mzfit.ml.pose.SkeletonSmoother
import org.mz.mzfit.ml.pose.TrackerSpecs
import org.mz.mzfit.ml.pose.TrackerState
import java.util.Locale

enum class SessionStatus { WAITING, COUNTDOWN, ACTIVE, PAUSED, FINISHED }

data class SessionSummary(val reps: Int, val goodReps: Int, val formScore: Int, val durationSec: Int, val holdSec: Int)

data class SessionUi(
    val status: SessionStatus = SessionStatus.WAITING,
    val countdown: Int = 3,
    val elapsedSec: Int = 0,
    val tracker: TrackerState = TrackerState(),
    val targetReps: Int = 10,
    val facing: CameraFacing = CameraFacing.FRONT,
    val fps: Int = 0,
    val summary: SessionSummary? = null,
    val setNumber: Int = 1,
)

class TrainingViewModel(app: Application, val exercise: Exercise) : AndroidViewModel(app), TextToSpeech.OnInitListener {

    private val container = (app as MzFitApplication).container
    val settings = container.settings.settings

    private val spec = TrackerSpecs.forPattern(requireNotNull(exercise.pattern))
    private val tracker = ExerciseTracker(spec)
    private val smoother = SkeletonSmoother()

    private val _ui = MutableStateFlow(SessionUi(targetReps = exercise.defaultReps, facing = settings.value.cameraFacing))
    val ui: StateFlow<SessionUi> = _ui.asStateFlow()

    /** High-frequency skeleton stream for the overlay, kept separate from the HUD state. */
    private val _skeleton = MutableStateFlow<Skeleton?>(null)
    val skeleton: StateFlow<Skeleton?> = _skeleton.asStateFlow()

    private var tts: TextToSpeech? = TextToSpeech(app, this)
    private var ttsReady = false
    private var lastSpokenCue = ""
    private var lastSpokenAt = 0L
    private var inFrameSince = 0L
    private var startedAt = 0L
    private var pausedAccum = 0L
    private var pausedAt = 0L
    private var timerJob: Job? = null
    private var frameCount = 0
    private var fpsWindowStart = 0L
    private var targetAnnounced = false

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        tts?.language = Locale.getDefault()
        tts?.setSpeechRate(1.05f)
    }

    fun onPose(joints: Map<Int, Joint>, timestampMs: Long) {
        val sk = smoother.smooth(joints, timestampMs)
        _skeleton.value = if (joints.isEmpty()) null else sk
        trackFps(timestampMs)

        when (_ui.value.status) {
            SessionStatus.WAITING -> {
                val inFrame = sk.allVisible(spec.required(sk.bestSide()))
                if (inFrame) {
                    if (inFrameSince == 0L) inFrameSince = timestampMs
                    if (timestampMs - inFrameSince > 700) startCountdown()
                } else inFrameSince = 0L
            }
            SessionStatus.ACTIVE -> {
                val before = tracker.current
                val after = tracker.update(sk)
                _ui.update { it.copy(tracker = after) }
                if (after.reps > before.reps) onRep(after)
                else after.cue?.let { if (it.level == CueLevel.BAD) speakCue(it.text) }
                if (exercise.isHold && after.holdMs / 1000 > before.holdMs / 1000) onHoldTick(after)
            }
            else -> Unit
        }
    }

    private fun trackFps(ts: Long) {
        if (fpsWindowStart == 0L) fpsWindowStart = ts
        frameCount++
        if (ts - fpsWindowStart >= 1000) {
            val fps = (frameCount * 1000 / (ts - fpsWindowStart)).toInt()
            _ui.update { it.copy(fps = fps) }
            frameCount = 0; fpsWindowStart = ts
        }
    }

    private fun startCountdown() {
        if (_ui.value.status != SessionStatus.WAITING) return
        val secs = settings.value.countdownSeconds
        if (secs <= 0) { beginActive(); return }
        _ui.update { it.copy(status = SessionStatus.COUNTDOWN, countdown = secs) }
        viewModelScope.launch {
            for (i in secs downTo 1) {
                _ui.update { it.copy(countdown = i) }
                speak(i.toString(), flush = true)
                buzz(25)
                delay(1000)
            }
            beginActive()
        }
    }

    private fun beginActive() {
        tracker.reset()
        startedAt = SystemClock.elapsedRealtime(); pausedAccum = 0
        targetAnnounced = false
        _ui.update { it.copy(status = SessionStatus.ACTIVE, tracker = tracker.current, elapsedSec = 0) }
        speak("Go", flush = true)
        buzz(60)
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(250)
                if (_ui.value.status == SessionStatus.ACTIVE) {
                    val e = ((SystemClock.elapsedRealtime() - startedAt - pausedAccum) / 1000).toInt()
                    _ui.update { it.copy(elapsedSec = e) }
                }
            }
        }
    }

    private fun onRep(state: TrackerState) {
        val good = state.lastRep?.score?.let { it >= 80 } ?: true
        buzz(if (good) 40 else 120)
        val rep = state.lastRep
        val speech = buildString {
            append(state.reps)
            if (rep != null && rep.feedback.level != CueLevel.GOOD) append(". ").append(rep.feedback.text)
        }
        speak(speech, flush = true)
        lastSpokenCue = rep?.feedback?.text ?: ""
        lastSpokenAt = SystemClock.elapsedRealtime()
        if (!targetAnnounced && state.reps >= _ui.value.targetReps) {
            targetAnnounced = true
            speak("Target reached. Finish strong.", flush = false)
        }
    }

    private fun onHoldTick(state: TrackerState) {
        val s = (state.holdMs / 1000).toInt()
        if (s > 0 && s % 10 == 0) speak("$s seconds", flush = true)
        if (!targetAnnounced && s >= exercise.defaultReps) {
            targetAnnounced = true
            speak("Target reached", flush = true); buzz(150)
        }
    }

    private fun speakCue(text: String) {
        val now = SystemClock.elapsedRealtime()
        if (text == lastSpokenCue && now - lastSpokenAt < 6000) return
        if (now - lastSpokenAt < 2500) return
        lastSpokenCue = text; lastSpokenAt = now
        speak(text, flush = false)
        buzz(80)
    }

    private fun speak(text: String, flush: Boolean) {
        if (!settings.value.voiceCoaching || !ttsReady) return
        tts?.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, text.hashCode().toString())
    }

    private fun buzz(ms: Long) {
        if (!settings.value.haptics) return
        val ctx = getApplication<Application>()
        val vib: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(VibratorManager::class.java))?.defaultVibrator
        } else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
        vib?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun togglePause() {
        _ui.update {
            when (it.status) {
                SessionStatus.ACTIVE -> { pausedAt = SystemClock.elapsedRealtime(); it.copy(status = SessionStatus.PAUSED) }
                SessionStatus.PAUSED -> { pausedAccum += SystemClock.elapsedRealtime() - pausedAt; it.copy(status = SessionStatus.ACTIVE) }
                else -> it
            }
        }
    }

    fun flipCamera() {
        val next = if (_ui.value.facing == CameraFacing.FRONT) CameraFacing.BACK else CameraFacing.FRONT
        smoother.reset()
        _ui.update { it.copy(facing = next) }
        container.settings.update { it.copy(cameraFacing = next) }
    }

    fun adjustTarget(delta: Int) = _ui.update { it.copy(targetReps = (it.targetReps + delta).coerceIn(1, 300)) }

    fun finishSet() {
        val s = _ui.value
        if (s.status == SessionStatus.FINISHED) return
        timerJob?.cancel()
        val t = s.tracker
        val holdSec = (t.holdMs / 1000).toInt()
        val reps = if (exercise.isHold) holdSec else t.reps
        val summary = SessionSummary(reps, t.goodReps, t.formScore, s.elapsedSec, holdSec)
        if (reps > 0) {
            container.workouts.add(
                WorkoutLog(
                    exerciseId = exercise.id, timestamp = System.currentTimeMillis(),
                    reps = reps, goodReps = if (exercise.isHold) reps else t.goodReps,
                    formScore = t.formScore, durationSec = s.elapsedSec, aiTracked = true,
                )
            )
        }
        speak(if (reps > 0) "Set complete. Form score ${t.formScore}" else "Set ended", flush = true)
        _ui.update { it.copy(status = SessionStatus.FINISHED, summary = summary) }
    }

    fun nextSet() {
        tracker.reset(); smoother.reset(); inFrameSince = 0L
        _ui.update { it.copy(status = SessionStatus.WAITING, summary = null, tracker = TrackerState(), elapsedSec = 0, setNumber = it.setNumber + 1) }
    }

    override fun onCleared() {
        tts?.stop(); tts?.shutdown(); tts = null
        super.onCleared()
    }
}
