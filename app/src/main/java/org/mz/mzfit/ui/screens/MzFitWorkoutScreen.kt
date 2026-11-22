package org.mz.mzfit.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.mz.mzfit.ui.componenets.LandmarkModel
import org.mz.mzfit.view.PoseViewModel

private data class Exercise(val name:String,val muscle:String,val cue:String)
private val exercises=listOf(
 Exercise("Squats","Quads • glutes","Keep chest lifted; track knees over toes."),
 Exercise("Push-ups","Chest • triceps","Keep your body straight and hips level."),
 Exercise("Reverse lunges","Glutes • legs","Step back under control; stabilize the front knee."),
 Exercise("Bicep curls","Biceps","Keep elbows by your sides; avoid swinging."),
 Exercise("Shoulder presses","Shoulders • triceps","Brace your core and avoid arching your back."),
 Exercise("Jumping jacks","Full body • cardio","Land softly and use a steady rhythm.")
)

@Composable
fun MzFitApp(poseViewModel:PoseViewModel) {
 var camera by remember { mutableStateOf(false) }
 if(camera) Column(Modifier.fillMaxSize()) {
  TextButton(onClick={camera=false}) { Text("← Workouts") }
  PoseDetectionScreen(poseViewModel)
 } else WorkoutHome(poseViewModel.landmarks.value) { camera=true }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutHome(landmarks:List<LandmarkModel>,openCamera:()->Unit) {
 val context=LocalContext.current
 val prefs= remember { context.getSharedPreferences("mzfit_workouts",Context.MODE_PRIVATE) }
 var exercise by remember { mutableStateOf(exercises.first()) }
 var goalSets by remember { mutableIntStateOf(3) }
 var goalReps by remember { mutableIntStateOf(12) }
 var sets by remember { mutableIntStateOf(0) }
 var reps by remember { mutableIntStateOf(0) }
 var rest by remember { mutableIntStateOf(0) }
 var history by remember { mutableStateOf(prefs.getString("history","").orEmpty().split("\n").filter{it.isNotBlank()}) }
 LaunchedEffect(rest) { if(rest>0){delay(1000);rest--} }
 val coach=remember(landmarks) {
  if(landmarks.isEmpty()) "Open the camera to start pose tracking."
  else {
   val good=landmarks.count{it.likelihood>=0.55f}
   val ls=landmarks.firstOrNull{it.id==11}; val rs=landmarks.firstOrNull{it.id==12}
   val lh=landmarks.firstOrNull{it.id==23}; val rh=landmarks.firstOrNull{it.id==24}
   when {
    good<10 -> "Move fully into frame so your joints can be tracked."
    ls!=null && rs!=null && lh!=null && rh!=null && kotlin.math.abs((ls.x+rs.x-lh.x-rh.x)/2f)>0.16f -> "Your torso appears tilted; try stacking shoulders over hips."
    else -> "Landmarks visible. Move smoothly and stay controlled. This is a basic visual cue, not certified form analysis."
   }
  }
 }
 Scaffold(topBar={TopAppBar(title={Text("MzFIT")},actions={TextButton(onClick=openCamera){Text("Live form")}})}) { pad ->
  LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=28.dp)) {
   item { Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Your training space",style=MaterialTheme.typography.titleLarge);Text("Pick an exercise, set your targets and track your workout.");Button(onClick=openCamera){Text("Open live form coach")}}} }
   item { Text("Exercise library",style=MaterialTheme.typography.titleLarge) }
   items(exercises){ e -> Card(onClick={exercise=e;sets=0;reps=0},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=if(e==exercise) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(e.name,style=MaterialTheme.typography.titleMedium);Text(e.muscle,style=MaterialTheme.typography.bodySmall)};if(e==exercise)Text("Selected ✓")}} }
   item { Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    Text("Workout: ${exercise.name}",style=MaterialTheme.typography.titleLarge);Text(exercise.cue);Text("Goal: $goalSets sets × $goalReps reps")
    Row(verticalAlignment=Alignment.CenterVertically){Text("Sets");IconButton(onClick={goalSets=(goalSets-1).coerceAtLeast(1)}){Text("−")};Text("$goalSets");IconButton(onClick={goalSets=(goalSets+1).coerceAtMost(10)}){Text("+")};Text("Reps");IconButton(onClick={goalReps=(goalReps-1).coerceAtLeast(1)}){Text("−")};Text("$goalReps");IconButton(onClick={goalReps=(goalReps+1).coerceAtMost(100)}){Text("+")}}
    Text("Completed: $sets/$goalSets sets • $reps/$goalReps reps")
    LinearProgressIndicator(progress={((sets*goalReps+reps).toFloat()/(goalSets*goalReps)).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilledTonalButton(onClick={reps++},enabled=sets<goalSets && reps<goalReps){Text("+ Rep")};Button(onClick={if(reps>=goalReps){sets++;reps=0;rest=60}},enabled=sets<goalSets && reps>=goalReps){Text("Complete set")};TextButton(onClick={sets=0;reps=0;rest=0}){Text("Reset")}}
    if(sets==goalSets){Text("Workout complete!",color=MaterialTheme.colorScheme.primary);Button(onClick={val row="${exercise.name} • $sets × $goalReps";history=(listOf(row)+history).take(20);prefs.edit().putString("history",history.joinToString("\n")).apply();sets=0;reps=0}){Text("Save workout")}}
   }} }
   if(rest>0)item{Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.tertiaryContainer)){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text("Rest timer",modifier=Modifier.weight(1f));Text(String.format("%02d:%02d",rest/60,rest%60),style=MaterialTheme.typography.headlineMedium);TextButton(onClick={rest=0}){Text("Skip")}}}}
   item { Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text("Form coach",style=MaterialTheme.typography.titleMedium);Text(coach);Text("Camera angle and landmark confidence affect this heuristic feedback.",style=MaterialTheme.typography.bodySmall);TextButton(onClick=openCamera){Text("Open camera")}}} }
   item { Text("Workout history",style=MaterialTheme.typography.titleLarge) }
   if(history.isEmpty())item{Text("Saved sessions will appear here.")} else items(history){row->ListItem(headlineContent={Text(row)})}
  }
 }
}
