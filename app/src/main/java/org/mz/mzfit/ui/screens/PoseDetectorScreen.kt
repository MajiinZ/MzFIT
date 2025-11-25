package org.mz.mzfit.ui.screens

import android.app.Activity
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.mz.mzfit.camera.CameraPreview
import org.mz.mzfit.ui.componenets.PoseOverlay
import org.mz.mzfit.view.PoseViewModel

@Composable
fun PoseDetectionScreen(
    poseViewModel: PoseViewModel
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }

    val landmarks = poseViewModel.landmarks.value   // ✔️ MATCHES YOUR VIEWMODEL

    // Request permission once
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(android.Manifest.permission.CAMERA),
                0
            )
        } else {
            hasPermission = true
        }
    }


    Box(Modifier.fillMaxSize()) {

        if (hasPermission) {

            // Start camera ONLY after permission confirmed
            CameraPreview(
                analyzer = poseViewModel.getAnalyzer(),
                modifier = Modifier.fillMaxSize()
            )

            PoseOverlay(
                modifier = Modifier.fillMaxSize(),
                landmarks = landmarks
            )

        } else {
            Text(
                text = "Camera permission required",
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }


        // Debug: landmark count
        Text(
            text = "Landmarks: ${landmarks.size}",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            color = Color.White
        )
    }
}
