package org.mz.mzfit.camera


import androidx.camera.core.ImageAnalysis
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun CameraPreview(
    analyzer: ImageAnalysis.Analyzer,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PreviewView(context).apply {
                this.scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            CameraManager(
                lifecycleOwner = lifecycleOwner,
                context = previewView.context
            ).startCamera(analyzer, previewView)
        }
    )
}
