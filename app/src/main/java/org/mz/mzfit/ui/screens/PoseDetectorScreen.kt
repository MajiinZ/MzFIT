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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mz.mzfit.camera.CameraPreview
import org.mz.mzfit.view.PoseViewModel

@Composable
fun PoseDetectionScreen(
    poseViewModel: PoseViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember { mutableStateOf(false) }

    // Ask for CAMERA permission
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

    // Re-check when returning from permission dialog
    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    val poseResult by poseViewModel.poseState

    Box(modifier = Modifier.fillMaxSize()) {

        if (hasPermission) {
            CameraPreview(
                analyzer = poseViewModel.getAnalyzer(),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "Camera permission required",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Text(
            text = "Landmarks: ${poseResult?.landmarks?.size ?: 0}",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            color = Color.White
        )
    }
}
