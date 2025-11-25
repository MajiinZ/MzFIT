package org.mz.mzfit.ml.pose

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.Composable
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import org.mz.mzfit.camera.CameraPreview

class PoseAnalyzer(
    private val onPoseDetected: (Pose) -> Unit
) : ImageAnalysis.Analyzer {


    private val poseDetector = PoseDetection.getClient(PoseDetectorOptions.Builder().build())

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return imageProxy.close()

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        poseDetector.process(inputImage)
            .addOnSuccessListener { pose ->
                onPoseDetected(pose) // Send pose to Composable
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}

@Composable
fun CameraActivityContent(pose: Pose) {
    CameraPreview(PoseAnalyzer { pose ->})}


