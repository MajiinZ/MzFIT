package org.mz.mzfit
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import org.mz.mzfit.ui.screens.MzFitApp
import org.mz.mzfit.view.PoseViewModel
class MainActivity:ComponentActivity(){
 private val vm:PoseViewModel by viewModels()
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MzFitApp(vm)}}
}