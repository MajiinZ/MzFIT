package org.mz.mzfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import org.mz.mzfit.ui.components.LocalAppContainer
import org.mz.mzfit.ui.navigation.MzFitApp
import org.mz.mzfit.ui.theme.MzFITTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val container = (application as MzFitApplication).container
        setContent {
            MzFITTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    MzFitApp()
                }
            }
        }
    }
}
