package org.mz.mzfit.view

import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mz.mzfit.ml.pose.PoseAnalyzer
import org.mz.mzfit.ml.pose.PoseLandmarkModel
import org.mz.mzfit.ml.pose.Smoother

class PoseViewModel : ViewModel() {

    // Camera permission state
    private val _hasCameraPermission = androidx.compose.runtime.mutableStateOf(false)
    val hasCameraPermission = _hasCameraPermission

    fun setCameraPermission(granted: Boolean) {
        _hasCameraPermission.value = granted
    }

    // Landmark state
    private val _landmarks =
        androidx.compose.runtime.mutableStateOf<List<PoseLandmarkModel>>(emptyList())
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

                if (raw.isNotEmpty()) {
                    val smoothed = smoother.smooth(raw)
                    _landmarks.value = smoothed
                } else {
                    _landmarks.value = emptyList()
                }
            }
        }
    }

    fun getAnalyzer(): ImageAnalysis.Analyzer = analyzer
}
