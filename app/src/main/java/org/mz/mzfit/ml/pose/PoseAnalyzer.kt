package org.mz.mzfit.ml.pose

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

class PoseAnalyzer(
    private val onResults: (Pose) -> Unit
) : ImageAnalysis.Analyzer {

    // ML Kit Pose Detector – STREAM MODE (fastest for real-time camera)
    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(input)
            .addOnSuccessListener { pose ->
                onResults(pose)
            }
            .addOnFailureListener { e ->
                Log.e("PoseAnalyzer", "Pose detection error", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
