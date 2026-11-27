package com.mlpt.feature.training

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mlpt.shared.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MuscleExplorer(onBrowseWorkouts: (String) -> Unit) {
    var back by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(MuscleGroup.Chest) }
    var allLifts by remember { mutableStateOf(false) }
    var openedGuide by remember { mutableStateOf<ExerciseGuide?>(null) }
    val regions = remember(back) { muscleRegions(back) }
    fun select(group: MuscleGroup) {
        selected = group
        allLifts = false
        if (regions.none { it.muscle == group }) back = !back
    }
    Text("Find your focus.", style = MaterialTheme.typography.headlineLarge, color = Ink)
    Text("Tap a muscle to discover exercises that train it. Open any main lift for an animated movement guide.", color = SecondaryText)
    Surface(color = CardSurface, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false to "Front", true to "Back").forEach { (isBack, label) ->
                    FilterChip(selected = back == isBack, onClick = {
                        back = isBack
                        if (muscleRegions(back).none { it.muscle == selected }) selected = if (back) MuscleGroup.UpperBack else MuscleGroup.Chest
                        allLifts = false
                    }, label = { Text(label) })
                }
            }
            MuscleDiagram(back, selected, ::select)
            Text("Selected: ${selected.label}", color = PerformanceBlue, style = MaterialTheme.typography.titleMedium)
            Text("Or choose a muscle below", style = MaterialTheme.typography.labelMedium, color = SecondaryText)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MuscleGroup.entries.forEach { muscle ->
                    FilterChip(selected = selected == muscle, onClick = { select(muscle) }, label = { Text(muscle.label) })
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !allLifts, onClick = { allLifts = false }, label = { Text("Muscle matches") })
        FilterChip(selected = allLifts, onClick = { allLifts = true }, label = { Text("All main lifts · 8") })
    }
    if (allLifts) {
        Text("The main lifts", style = MaterialTheme.typography.headlineMedium)
        Text("Eight animated guides. Tap a lift, then press Play or drag the position slider.", color = SecondaryText)
        exerciseLibrary.filter { it.motion != null }.forEach { guide -> ExerciseGuideCard(guide, null) { openedGuide = guide } }
    } else {
        Text("Train your ${selected.label.lowercase()}", style = MaterialTheme.typography.headlineMedium)
        Text("Primary-target exercises come first. Choose for your experience, equipment and goals; there is no single best lift for everyone.", color = SecondaryText)
        val matches = exercisesFor(selected)
        Text("Primary target", style = MaterialTheme.typography.titleMedium, color = PerformanceBlue)
        matches.filter { selected in it.primary }.forEach { guide -> ExerciseGuideCard(guide, selected) { openedGuide = guide } }
        if (matches.any { selected in it.secondary }) {
            Text("Also works as a supporting muscle", style = MaterialTheme.typography.titleMedium)
            matches.filter { selected in it.secondary }.forEach { guide -> ExerciseGuideCard(guide, selected) { openedGuide = guide } }
        }
        OutlinedButton(onClick = { onBrowseWorkouts(selected.bodyPart) }) { Text("Browse ${selected.bodyPart.lowercase()} workouts →") }
    }
    Text("The map is a simplified muscle-group guide. Exercise targeting is curated from ACE and NASM exercise libraries.", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
    openedGuide?.let { guide ->
        ModalBottomSheet(onDismissRequest = { openedGuide = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = CardSurface) {
            key(guide.id) {
                Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(guide.name, style = MaterialTheme.typography.headlineMedium)
                    Text(guide.equipment, color = SecondaryText)
                    Text("Primary: ${guide.primary.joinToString { it.label }}", color = PerformanceBlue)
                    if (guide.secondary.isNotEmpty()) Text("Supporting: ${guide.secondary.joinToString { it.label }}", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                    Text(guide.focus)
                    guide.motion?.let { LiftAnimation(it) }
                    Text("Movement cues", style = MaterialTheme.typography.titleLarge)
                    guide.steps.forEachIndexed { index, step -> Text("${index + 1}. $step") }
                    val uri = LocalUriHandler.current
                    TextButton(onClick = { uri.openUri("https://www.nasm.org/workout-exercise-guidance") }) { Text("Reference exercise library ↗") }
                    OutlinedButton(onClick = { openedGuide = null }) { Text("Close guide") }
                }
            }
        }
    }
}

@Composable
private fun ExerciseGuideCard(guide: ExerciseGuide, selected: MuscleGroup?, onClick: () -> Unit) {
    Surface(color = CardSurface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open ${guide.name} guide", onClick = onClick)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(guide.name, style = MaterialTheme.typography.titleLarge)
            Text(if (selected == null) guide.primary.joinToString { it.label } else if (selected in guide.primary) "Primary target · ${selected.label}" else "Supporting muscle · ${selected.label}", color = PerformanceBlue, style = MaterialTheme.typography.labelLarge)
            Text(guide.focus, color = SecondaryText)
            Text(guide.equipment, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
            Text(if (guide.motion == null) "View exercise →" else "Watch lift animation →", color = PerformanceBlue, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun MuscleDiagram(back: Boolean, selected: MuscleGroup, onSelect: (MuscleGroup) -> Unit) {
    val regions = remember(back) { muscleRegions(back) }
    Canvas(Modifier.fillMaxWidth().height(360.dp)
        .semantics { contentDescription = "${if (back) "Back" else "Front"} muscle map. ${selected.label} selected. Equivalent muscle selection buttons are below the map." }
        .pointerInput(back) {
            detectTapGestures { tap ->
                val ratio = minOf(size.width / 300f, size.height / 460f)
                val x = (tap.x - (size.width - 300f * ratio) / 2) / ratio
                val y = (tap.y - (size.height - 460f * ratio) / 2) / ratio
                muscleAt(back, MapPoint(x, y))?.let(onSelect)
            }
        }) {
        val ratio = minOf(size.width / 300f, size.height / 460f)
        translate((size.width - 300f * ratio) / 2, (size.height - 460f * ratio) / 2) {
            scale(ratio, ratio, Offset.Zero) {
                fun polygon(vararg coords: Float): Path = Path().apply {
                    moveTo(coords[0], coords[1]); coords.toList().chunked(2).drop(1).forEach { lineTo(it[0], it[1]) }; close()
                }
                drawOval(Divider, Offset(132f, 21f), Size(36f, 45f))
                drawRoundRect(Divider, Offset(139f, 59f), Size(22f, 22f), CornerRadius(5f))
                drawPath(polygon(126f, 77f, 150f, 70f, 174f, 77f, 183f, 112f, 181f, 157f, 173f, 186f, 185f, 221f, 150f, 239f, 115f, 221f, 127f, 186f, 119f, 157f, 117f, 112f), Divider)
                val leftArm = polygon(109f, 79f, 124f, 89f, 116f, 134f, 107f, 160f, 93f, 188f, 83f, 215f, 72f, 221f, 66f, 212f, 76f, 181f, 88f, 150f, 91f, 111f, 96f, 91f)
                drawPath(leftArm, Divider)
                scale(-1f, 1f, Offset(150f, 230f)) { drawPath(leftArm, Divider) }
                val leftLeg = polygon(118f, 213f, 149f, 225f, 146f, 288f, 144f, 324f, 146f, 359f, 138f, 405f, 140f, 429f, 125f, 438f, 109f, 435f, 116f, 421f, 118f, 401f, 111f, 365f, 117f, 324f, 110f, 286f, 110f, 251f)
                drawPath(leftLeg, Divider)
                scale(-1f, 1f, Offset(150f, 230f)) { drawPath(leftLeg, Divider) }
                regions.forEach { region ->
                    val path = Path().apply { moveTo(region.points.first().x, region.points.first().y); region.points.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                    drawPath(path, if (region.muscle == selected) PerformanceBlue else HeroAccent.copy(alpha = 0.55f))
                    drawPath(path, CardSurface, style = Stroke(1.8f))
                }
                if (!back) {
                    listOf(150f, 164f, 178f).forEach { y -> drawLine(CardSurface, Offset(139f, y), Offset(161f, y), 1.8f) }
                    drawLine(CardSurface, Offset(150f, 135f), Offset(150f, 189f), 1.8f)
                }
            }
        }
    }
}
