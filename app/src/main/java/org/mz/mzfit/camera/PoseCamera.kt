package org.mz.mzfit.camera

import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import org.mz.mzfit.data.CameraFacing
import org.mz.mzfit.ml.pose.Joint
import java.util.concurrent.Executors

/**
 * Live camera preview + ML Kit pose detection.
 *
 * Uses [MlKitAnalyzer] with [ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED], so every landmark
 * arrives already transformed into PreviewView pixel space — rotation, FILL_CENTER cropping and
 * front-camera mirroring are all handled by CameraX. The overlay can draw the points as-is.
 */
@Composable
fun PoseCameraView(
    facing: CameraFacing,
    highAccuracy: Boolean,
    onPose: (joints: Map<Int, Joint>, timestampMs: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnPose = rememberUpdatedState(onPose)

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        }
    }

    // Camera direction.
    DisposableEffect(facing) {
        val selector = if (facing == CameraFacing.FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        runCatching { controller.cameraSelector = selector }
            .onFailure { Log.w("PoseCamera", "Camera $facing unavailable", it) }
        onDispose { }
    }

    // Detector + analyzer. Recreated only when the model choice changes.
    DisposableEffect(highAccuracy) {
        val detector: PoseDetector = PoseDetection.getClient(
            if (highAccuracy) AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE).build()
            else PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE).build()
        )
        val executor = Executors.newSingleThreadExecutor()
        controller.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(listOf(detector), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { result ->
                val pose = result.getValue(detector) ?: return@MlKitAnalyzer
                val joints = HashMap<Int, Joint>(33)
                pose.allPoseLandmarks.forEach { lm ->
                    joints[lm.landmarkType] = Joint(
                        x = lm.position.x, y = lm.position.y, z = lm.position3D.z,
                        confidence = lm.inFrameLikelihood,
                    )
                }
                latestOnPose.value(joints, SystemClock.elapsedRealtime())
            }
        )
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            detector.close()
            executor.shutdown()
        }
    }

    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                this.controller = controller
                setBackgroundColor(ContextCompat.getColor(ctx, android.R.color.black))
            }
        },
    )
}
