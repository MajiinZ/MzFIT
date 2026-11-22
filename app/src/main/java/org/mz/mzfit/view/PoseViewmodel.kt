package org.mz.mzfit.view

import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mz.mzfit.ml.pose.PoseAnalyzer
import org.mz.mzfit.ml.pose.Smoother
import org.mz.mzfit.ui.componenets.LandmarkModel

class PoseViewModel : ViewModel() {

    // Camera permission state
    private val _hasCameraPermission = androidx.compose.runtime.mutableStateOf(false)
    val hasCameraPermission = _hasCameraPermission

    fun setCameraPermission(granted: Boolean) {
        _hasCameraPermission.value = granted
    }

    // Landmark state shared with PoseOverlay
    private val _landmarks =
        androidx.compose.runtime.mutableStateOf<List<LandmarkModel>>(emptyList())
    val landmarks = _landmarks

    private val smoother = Smoother(alpha = 0.35f)

    private lateinit var analyzer: ImageAnalysis.Analyzer

    init {
        createAnalyzer()
    }

    private fun createAnalyzer() {
        analyzer = PoseAnalyzer { pose ->
            viewModelScope.launch(Dispatchers.Default) {
                val raw = pose.allPoseLandmarks
                _landmarks.value = if (raw.isNotEmpty()) {
                    smoother.smooth(raw)
                } else {
                    emptyList()
                }
            }
        }
    }

    fun getAnalyzer(): ImageAnalysis.Analyzer = analyzer
}
