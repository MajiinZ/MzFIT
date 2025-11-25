package org.mz.mzfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.mz.mzfit.ui.screens.PoseDetectionScreen
import org.mz.mzfit.ui.theme.MzFITTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MzFITTheme {
                PoseDetectionScreen()   // ⭐ Load the pose detection screen
            }
        }
    }
}
