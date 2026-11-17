@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.mlpt.app

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.compose.ui.window.ComposeUIViewController
import com.mlpt.data.*
import com.mlpt.di.AppContainer
import com.mlpt.shared.*
import kotlinx.coroutines.*
import platform.Foundation.*
import platform.UIKit.UIViewController

interface IosDocumentStore {
    fun read(key: String): IosDocumentResult
    fun write(key: String, value: String): String?
}
class IosDocumentResult(val value: String?, val error: String?)
class IosLandmark(val name: String, val x: Float, val y: Float, val confidence: Float)
class IosHistoryItem(val id: String, val title: String)
class IosCoachingStatus(
    val active: Boolean, val canStart: Boolean, val message: String, val quality: String,
    val fullReps: Int, val matchedReps: Int, val partialAttempts: Int,
    val saving: Boolean, val error: String?, val history: String,
)

/** Swift actions run on main; SQLite reads and commits run on Default. */
class IosAppBridge(store: IosDocumentStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val documents = object : LocalDocuments {
        override suspend fun read(key: String): String? = withContext(Dispatchers.Default) {
            val result = store.read(key); check(result.error == null) { result.error ?: "Read failed" }; result.value
        }
        override suspend fun write(key: String, value: String) = withContext(Dispatchers.Default) {
            val error = store.write(key, value); check(error == null) { error ?: "Write failed" }
        }
    }
    private val repository = PersistentTrainingRepository(DocumentTrainingStorage(documents), scope, ::wallTime)
    private val recorder = PoseSessionRecorder(DocumentPoseStorage(documents), scope, ::wallTime, ::monotonicTime) { NSUUID().UUIDString }
    private var historyDetails = ""

    fun makeViewController(poseController: () -> UIViewController): UIViewController = ComposeUIViewController {
        val container = remember { AppContainer(repository) }
        App(container, storageNotice = "Saved privately on this iPhone · No cloud sync",
            formatCompletedAt = { formatDate(it) }, poseContent = {
                Text("ML · Pose detection", style = MaterialTheme.typography.headlineLarge)
                Text("Experimental movement feedback · Angle checks do not assess overall exercise safety.")
                UIKitViewController(factory = poseController, modifier = Modifier.fillMaxWidth().height(1040.dp))
            })
    }
    fun instructions(exercise: Int): String = PoseExercise.entries.getOrElse(exercise) { PoseExercise.Squat }.instructions
    fun start(exercise: Int): Boolean = recorder.start(PoseExercise.entries.getOrElse(exercise) { PoseExercise.Squat })
    fun stop(interrupted: Boolean) { recorder.stop(interrupted) }
    fun retry() { recorder.retryStorage() }
    fun moreHistory() { recorder.loadMoreHistory() }
    fun resetTracking() { recorder.resetTracking() }
    fun frame(timeMs: Long, width: Float, height: Float, landmarks: List<IosLandmark>) {
        recorder.onFrame(CoachingFrame(timeMs, width, height, landmarks.mapNotNull { p ->
            PoseJoint.entries.find { it.name == p.name }?.let { it to PosePoint(p.x, p.y, p.confidence) }
        }.toMap()))
    }
    fun status(): IosCoachingStatus {
        recorder.checkTrackingFreshness()
        val s = recorder.state.value; val f = s.feedback
        val phase = when (f.phase) {
            PosePhase.FindStart -> "Find the start position"
            PosePhase.Ready -> "Ready"
            PosePhase.Moving -> "Movement in progress"
            PosePhase.TargetReached -> "Range target reached"
        }
        val history = s.history.joinToString("\n\n") {
            "${formatDate(it.startedAt)} · ${it.exerciseName}\n${it.status} · ${it.fullReps} full / ${it.matchedReps} matched / ${it.partialAttempts} partial\nSamples: ${it.usableSamples}/${it.analyzedSamples}"
        }
        return IosCoachingStatus(s.current?.status == "active", s.ready && !s.unsaved && !s.saving && s.current?.status != "active",
            "$phase · ${f.angle?.toInt()?.let { "$it°" } ?: "Waiting for tracking"}\n" + f.cues.joinToString("\n") { it.message },
            f.quality.name, f.fullReps, f.matchedReps, f.partialAttempts, s.saving, s.error,
            history.ifEmpty { "No saved pose sessions yet." } + historyDetails)
    }
    fun historyItems(): List<IosHistoryItem> = recorder.state.value.history.map {
        IosHistoryItem(it.id, "${formatDate(it.startedAt)} · ${it.exerciseName}")
    }
    fun showDetails(id: String) {
        scope.launch {
            try {
                val log = recorder.details(id) ?: return@launch
                historyDetails = "\n\n${formatDate(log.startedAt)} · ${log.exerciseName}\nATTEMPT DETAILS\n" + log.reps.joinToString("\n") {
                    "Attempt ${it.number}: ${it.durationMs} ms · ${it.minAngle.toInt()}–${it.maxAngle.toInt()}° · " +
                        (if (it.matchedChecks) "matched checks" else it.cues.joinToString())
                } + "\nRecent observations:\n" + log.observations.joinToString("\n") {
                    "${it.elapsedMs} ms · ${it.quality} · ${it.phase} · ${it.angle?.toInt() ?: "?"}° · ${it.cues.joinToString()}"
                }
            } catch (_: Exception) { historyDetails = "\nCouldn’t load session details. Try again." }
        }
    }
}
private fun wallTime() = (NSDate().timeIntervalSince1970 * 1000).toLong()
private fun monotonicTime() = (NSProcessInfo.processInfo.systemUptime * 1000).toLong()
private fun formatDate(value: Long): String = NSDateFormatter().run {
    dateStyle = NSDateFormatterShortStyle; timeStyle = NSDateFormatterShortStyle
    stringFromDate(NSDate(timeIntervalSince1970 = value / 1000.0))
}
