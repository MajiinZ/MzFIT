package com.mlpt.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.mlpt.shared.Ink
import com.mlpt.shared.SecondaryText
import com.mlpt.shared.*
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class, androidx.camera.view.TransformExperimental::class)
internal fun LivePoseCamera(front: Boolean, onStatus: (String) -> Unit, onFrame: (CoachingFrame) -> Unit, onTrackingReset: () -> Unit, quality: PoseQuality) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val preview = remember { PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
        contentDescription = "Live camera preview with detected pose landmarks"
    } }
    val overlay = remember { PoseOverlay(context) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val statusCallback by rememberUpdatedState(onStatus)
    val frameCallback by rememberUpdatedState(onFrame)
    val resetCallback by rememberUpdatedState(onTrackingReset)
    SideEffect { overlay.setQuality(quality) }

    AndroidView(factory = {
        FrameLayout(context).apply {
            addView(preview, FrameLayout.LayoutParams(-1, -1))
            addView(overlay, FrameLayout.LayoutParams(-1, -1))
        }
    }, modifier = Modifier.fillMaxWidth().height(280.dp).onSizeChanged { viewSize = it })

    DisposableEffect(owner, front, viewSize) {
        resetCallback()
        val closed = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val detector = PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        val cameraPreview = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val transformFactory = ImageProxyTransformFactory().apply { isUsingRotationDegrees = true }
        statusCallback("Starting camera…")
        analysis.setAnalyzer(executor) { proxy ->
            if (closed.get()) { proxy.close(); return@setAnalyzer }
            val image = proxy.image
            if (image == null) { proxy.close(); return@setAnalyzer }
            val rotation = proxy.imageInfo.rotationDegrees
            val frameWidth = if (rotation == 90 || rotation == 270) proxy.height else proxy.width
            val frameHeight = if (rotation == 90 || rotation == 270) proxy.width else proxy.height
            val frameTime = SystemClock.elapsedRealtime()
            try {
                val sourceTransform = transformFactory.getOutputTransform(proxy)
                detector.process(InputImage.fromMediaImage(image, rotation))
                    .addOnSuccessListener(main) { pose ->
                        if (!closed.get()) {
                            val jointIds = listOf(PoseJoint.LeftShoulder to 11, PoseJoint.RightShoulder to 12, PoseJoint.LeftElbow to 13, PoseJoint.RightElbow to 14,
                                PoseJoint.LeftWrist to 15, PoseJoint.RightWrist to 16, PoseJoint.LeftHip to 23, PoseJoint.RightHip to 24,
                                PoseJoint.LeftKnee to 25, PoseJoint.RightKnee to 26, PoseJoint.LeftAnkle to 27, PoseJoint.RightAnkle to 28)
                            frameCallback(CoachingFrame(frameTime, frameWidth.toFloat(), frameHeight.toFloat(), jointIds.mapNotNull { (joint, id) ->
                                pose.getPoseLandmark(id)?.let { joint to PosePoint(it.position.x, it.position.y, it.inFrameLikelihood) }
                            }.toMap()))
                            preview.outputTransform?.let { target -> overlay.update(pose, CoordinateTransform(sourceTransform, target)) }
                            val visible = pose.allPoseLandmarks.count { it.inFrameLikelihood >= 0.5f }
                            statusCallback(if (visible == 0) "No pose detected. Step back into view." else "$visible / 33 landmarks visible")
                        }
                    }
                    .addOnFailureListener(main) {
                        if (!closed.get()) { overlay.clear(); resetCallback(); statusCallback("Detection unavailable. Stop and restart the camera to retry.") }
                    }
                    .addOnCompleteListener { proxy.close() }
            } catch (_: Exception) {
                proxy.close()
                main.execute { if (!closed.get()) { resetCallback(); statusCallback("Unable to analyze camera frames. Stop and retry.") } }
            }
        }
        providerFuture.addListener({
            if (!closed.get()) {
                try {
                    val ready = providerFuture.get()
                    provider = ready
                    val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    preview.doOnLayout {
                        if (!closed.get()) {
                            try {
                                val viewport = preview.viewPort ?: error("Preview is not ready")
                                val group = UseCaseGroup.Builder().setViewPort(viewport).addUseCase(cameraPreview).addUseCase(analysis).build()
                                ready.bindToLifecycle(owner, selector, group)
                                statusCallback("Camera ready. Step back into view.")
                            } catch (_: Exception) {
                                statusCallback("Camera unavailable. Try switching cameras, or stop and restart.")
                            }
                        }
                    }
                } catch (_: Exception) {
                    statusCallback("Camera unavailable. Try switching cameras, or stop and restart.")
                }
            }
        }, main)
        onDispose {
            closed.set(true)
            analysis.clearAnalyzer()
            provider?.unbind(cameraPreview, analysis)
            detector.close()
            executor.shutdown()
            overlay.clear()
        }
    }
}

/** CameraX maps analysis coordinates into the preview's crop, rotation and mirror. */
@androidx.annotation.OptIn(androidx.camera.view.TransformExperimental::class)
private class PoseOverlay(context: Context) : View(context) {
    private var points = emptyMap<Int, android.graphics.PointF>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(115, 219, 255); strokeWidth = 5f }
    private val bones = listOf(11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16,
        11 to 23, 12 to 24, 23 to 24, 23 to 25, 25 to 27, 24 to 26, 26 to 28,
        27 to 29, 29 to 31, 27 to 31, 28 to 30, 30 to 32, 28 to 32)
    fun update(value: Pose, transform: CoordinateTransform) {
        points = value.allPoseLandmarks.filter { it.inFrameLikelihood >= 0.5f }.associate { landmark ->
            landmark.landmarkType to android.graphics.PointF(landmark.position.x, landmark.position.y).also { transform.mapPoint(it) }
        }
        invalidate()
    }
    fun clear() { points = emptyMap(); invalidate() }
    fun setQuality(quality: PoseQuality) {
        paint.color = when (quality) {
            PoseQuality.WithinChecks -> Color.rgb(50, 215, 170)
            PoseQuality.Adjust -> Color.rgb(255, 183, 70)
            PoseQuality.NotVisible, PoseQuality.CameraAngle -> Color.rgb(160, 177, 196)
            else -> Color.rgb(115, 219, 255)
        }
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bones.forEach { (a, b) ->
            val start = points[a]
            val end = points[b]
            if (start != null && end != null) canvas.drawLine(start.x, start.y, end.x, end.y, paint)
        }
        points.values.forEach { canvas.drawCircle(it.x, it.y, 7f, paint) }
    }
}
