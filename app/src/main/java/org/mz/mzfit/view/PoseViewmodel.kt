package org.mz.mzfit.view

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import org.mz.mzfit.ml.pose.PoseAnalyzer
import org.mz.mzfit.ml.pose.PoseResult
import org.mz.mzfit.ml.pose.PoseUseCase

class PoseViewModel : ViewModel() {

    private val useCase = PoseUseCase()

    private val _poseState = mutableStateOf<PoseResult?>(null)
    val poseState: State<PoseResult?> = _poseState

    fun getAnalyzer() = PoseAnalyzer { pose ->
        _poseState.value = useCase.convertToResult(pose)
    }
}
