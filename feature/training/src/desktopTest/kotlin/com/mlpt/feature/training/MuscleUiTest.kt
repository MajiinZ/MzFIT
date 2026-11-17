package com.mlpt.feature.training

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mlpt.shared.*
import java.io.File
import kotlin.test.*

class MuscleUiTest {
    private fun tap(scene: ImageComposeScene, x: Float, y: Float) {
        scene.sendPointerEvent(PointerEventType.Press, Offset(x, y), type = PointerType.Touch)
        scene.sendPointerEvent(PointerEventType.Release, Offset(x, y), type = PointerType.Touch)
    }
    private fun ImageComposeScene.png(time: Long = 0): ByteArray = render(time).use { image -> image.encodeToData()!!.use { it.bytes } }

    @Test fun tappingTheDrawnQuadricepsChangesTheSelectedMuscle() {
        var selected by mutableStateOf(MuscleGroup.Chest)
        val scene = ImageComposeScene(300, 360) { TrainingTheme { MuscleDiagram(false, selected) { selected = it } } }
        try {
            scene.render().close()
            val ratio = 360f / 460f
            tap(scene, (300f - 300f * ratio) / 2 + 128f * ratio, 260f * ratio)
            scene.render(100_000_000).close()
            assertEquals(MuscleGroup.Quads, selected)
        } finally { scene.close() }
    }

    @Test fun playAdvancesTheAnimationAndPauseStopsIt() {
        val scene = ImageComposeScene(420, 520) { TrainingTheme { LiftAnimation(LiftMotion.Squat) } }
        try {
            scene.render().close()
            tap(scene, 42f, 296f)
            repeat(8) { scene.render((it + 1) * 100_000_000L).close() }
            val moving = scene.png(900_000_000)
            repeat(8) { scene.render(1_000_000_000L + it * 100_000_000L).close() }
            assertFalse(moving.contentEquals(scene.png(1_800_000_000)))
            tap(scene, 42f, 296f)
            repeat(8) { scene.render(1_900_000_000L + it * 100_000_000L).close() }
            val paused = scene.png(2_800_000_000)
            assertContentEquals(paused, scene.png(3_800_000_000))
        } finally { scene.close() }
    }

    @Test fun renderMuscleMapAndEveryMainLiftForVisualReview() {
        val directory = File("build/reports/visuals").apply { mkdirs() }
        val explorer = ImageComposeScene(420, 1600) {
            TrainingTheme { Column(Modifier.fillMaxSize().background(AppBackground).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) { MuscleExplorer {} } }
        }
        try { File(directory, "muscle-explorer.png").writeBytes(explorer.png()) } finally { explorer.close() }
        LiftMotion.entries.chunked(4).forEachIndexed { page, motions ->
            val scene = ImageComposeScene(680, 1320) {
                TrainingTheme {
                    Column(Modifier.fillMaxSize().background(AppBackground).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        motions.forEach { motion ->
                            Text(exerciseLibrary.first { it.motion == motion }.name)
                            Row {
                                listOf(0f, 1f).forEach { position -> Canvas(Modifier.size(320.dp, 260.dp)) { drawLiftScene(motion, liftPoseAt(motion, position)) } }
                            }
                        }
                    }
                }
            }
            try { File(directory, "lift-previews-${page + 1}.png").writeBytes(scene.png()) } finally { scene.close() }
        }
        assertTrue(File(directory, "muscle-explorer.png").length() > 0)
    }
}
