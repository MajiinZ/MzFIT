package org.mz.mzfit.ui.musclemap

import android.graphics.Matrix
import android.graphics.Region
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import org.mz.mzfit.data.Muscle
import org.mz.mzfit.ui.theme.MzColors

enum class FigureView { FRONT, BACK }

/** Parsed, cached geometry for the muscle map. Parsing happens once per process. */
private object MuscleGeometry {
    class Shape(val muscle: Muscle, val path: Path) {
        /** Hit-test region at 4x resolution for accurate taps. */
        val region: Region by lazy {
            val p = android.graphics.Path(path.asAndroidPath()).apply { transform(Matrix().apply { setScale(HIT_SCALE, HIT_SCALE) }) }
            Region().apply {
                setPath(p, Region(0, 0, (MuscleMapPaths.VIEWPORT_WIDTH * HIT_SCALE).toInt(), (MuscleMapPaths.VIEWPORT_HEIGHT * HIT_SCALE).toInt()))
            }
        }
    }

    const val HIT_SCALE = 4f

    private fun parse(d: String): Path = PathParser().parsePathString(d).toPath()

    val silhouette: Path by lazy { parse(MuscleMapPaths.SILHOUETTE) }
    val front: List<Shape> by lazy { MuscleMapPaths.front.map { Shape(it.muscle, parse(it.pathData)) } }
    val back: List<Shape> by lazy { MuscleMapPaths.back.map { Shape(it.muscle, parse(it.pathData)) } }

    fun shapes(view: FigureView) = if (view == FigureView.FRONT) front else back
}

/** Heat-map color for training load 0..1. */
fun loadColor(load: Float): Color = when {
    load <= 0f -> MzColors.MuscleIdle
    load < 0.5f -> lerp(Color(0xFF5C4A2E), MzColors.Amber, load / 0.5f)
    else -> lerp(MzColors.Amber, MzColors.Ember, ((load - 0.5f) / 0.5f).coerceIn(0f, 1f))
}

/** Color resolver highlighting an exercise's primary and secondary movers. */
fun exerciseColors(primary: Collection<Muscle>, secondary: Collection<Muscle>): (Muscle) -> Color = { m ->
    when (m) {
        in primary -> MzColors.MusclePrimary
        in secondary -> MzColors.MuscleSecondary.copy(alpha = 0.85f)
        else -> MzColors.MuscleIdle
    }
}

/**
 * Anatomical body figure with individually colored, tappable muscles.
 *
 * @param colorFor fill color for each muscle
 * @param selected muscle to outline in the brand accent
 * @param onMuscleTap enables tap-to-select when non-null
 */
@Composable
fun MuscleFigure(
    view: FigureView,
    colorFor: (Muscle) -> Color,
    modifier: Modifier = Modifier,
    selected: Muscle? = null,
    onMuscleTap: ((Muscle) -> Unit)? = null,
    animate: Boolean = true,
) {
    val shapes = remember(view) { MuscleGeometry.shapes(view) }
    val reveal = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { if (animate) reveal.animateTo(1f, tween(650)) }

    val tapModifier = if (onMuscleTap != null) Modifier.pointerInput(view) {
        detectTapGestures { pos ->
            val scale = minOf(size.width / MuscleMapPaths.VIEWPORT_WIDTH, size.height / MuscleMapPaths.VIEWPORT_HEIGHT)
            val dx = (size.width - MuscleMapPaths.VIEWPORT_WIDTH * scale) / 2f
            val dy = (size.height - MuscleMapPaths.VIEWPORT_HEIGHT * scale) / 2f
            val vx = (pos.x - dx) / scale * MuscleGeometry.HIT_SCALE
            val vy = (pos.y - dy) / scale * MuscleGeometry.HIT_SCALE
            shapes.lastOrNull { it.region.contains(vx.toInt(), vy.toInt()) }?.let { onMuscleTap(it.muscle) }
        }
    } else Modifier

    Canvas(modifier.aspectRatio(MuscleMapPaths.VIEWPORT_WIDTH / MuscleMapPaths.VIEWPORT_HEIGHT).then(tapModifier)) {
        val scale = minOf(size.width / MuscleMapPaths.VIEWPORT_WIDTH, size.height / MuscleMapPaths.VIEWPORT_HEIGHT)
        val dx = (size.width - MuscleMapPaths.VIEWPORT_WIDTH * scale) / 2f
        val dy = (size.height - MuscleMapPaths.VIEWPORT_HEIGHT * scale) / 2f
        translate(dx, dy) {
            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                drawPath(MuscleGeometry.silhouette, MzColors.Silhouette)
                val r = reveal.value
                shapes.forEach { s ->
                    val target = colorFor(s.muscle)
                    val c = if (r < 1f) lerp(MzColors.MuscleIdle, target, r) else target
                    drawPath(s.path, c)
                    // Thin separation line gives the segmented anatomical look.
                    drawPath(s.path, MzColors.Background.copy(alpha = 0.9f), style = Stroke(width = 0.9f))
                }
                if (selected != null) {
                    shapes.filter { it.muscle == selected }.forEach {
                        drawPath(it.path, MzColors.Volt, style = Stroke(width = 1.6f))
                    }
                }
            }
        }
    }
}

/** Front and back figures side by side with captions. */
@Composable
fun MuscleMapPair(
    colorFor: (Muscle) -> Color,
    modifier: Modifier = Modifier,
    selected: Muscle? = null,
    onMuscleTap: ((Muscle) -> Unit)? = null,
    showCaptions: Boolean = true,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        FigureView.entries.forEach { v ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                MuscleFigure(v, colorFor, Modifier.fillMaxWidth(), selected, onMuscleTap)
                if (showCaptions) {
                    Spacer(Modifier.height(6.dp))
                    Text(if (v == FigureView.FRONT) "FRONT" else "BACK",
                        style = MaterialTheme.typography.labelSmall, color = MzColors.TextMuted)
                }
            }
        }
    }
}
