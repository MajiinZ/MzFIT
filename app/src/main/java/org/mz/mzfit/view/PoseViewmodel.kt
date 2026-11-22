package org.mz.mzfit.view
import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.mz.mzfit.ml.pose.PoseAnalyzer
import org.mz.mzfit.ml.pose.Smoother
import org.mz.mzfit.ui.componenets.LandmarkModel
class PoseViewModel:ViewModel(){
 private val _hasCameraPermission=androidx.compose.runtime.mutableStateOf(false)
 val hasCameraPermission=_hasCameraPermission
 fun setCameraPermission(granted:Boolean){_hasCameraPermission.value=granted}
 private val _landmarks=androidx.compose.runtime.mutableStateOf<List<LandmarkModel>>(emptyList())
 val landmarks=_landmarks
 private val smoother=Smoother(alpha=0.35f)
 private val analyzer=PoseAnalyzer{pose->viewModelScope.launch{_landmarks.value=if(pose.allPoseLandmarks.isEmpty())emptyList() else smoother.smooth(pose.allPoseLandmarks)}}
 fun getAnalyzer():ImageAnalysis.Analyzer=analyzer
}