package org.mz.mzfit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mz.mzfit.data.AppContainer
import org.mz.mzfit.data.BodySide
import org.mz.mzfit.data.Exercise
import org.mz.mzfit.ui.musclemap.FigureView
import org.mz.mzfit.ui.musclemap.MuscleFigure
import org.mz.mzfit.ui.musclemap.exerciseColors
import org.mz.mzfit.ui.theme.MzColors

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = MzColors.Volt,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp))
        }
    }
}

@Composable
fun Pill(text: String, color: Color = MzColors.TextMuted, background: Color = MzColors.SurfaceHighest) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = color,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun AiBadge() {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MzColors.Volt.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.AutoAwesome, null, tint = MzColors.Volt, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text("AI COACH", style = MaterialTheme.typography.labelSmall, color = MzColors.Volt)
    }
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.clip(shape).background(MzColors.Surface).border(1.dp, MzColors.Outline.copy(alpha = 0.6f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

/** Exercise row with a mini muscle map thumbnail. */
@Composable
fun ExerciseRow(ex: Exercise, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SurfaceCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val back = ex.primary.count { it.side == BodySide.BACK } > ex.primary.count { it.side == BodySide.FRONT }
            Box(Modifier.size(width = 52.dp, height = 84.dp).clip(RoundedCornerShape(12.dp)).background(MzColors.Background),
                contentAlignment = Alignment.Center) {
                MuscleFigure(if (back) FigureView.BACK else FigureView.FRONT, exerciseColors(ex.primary, ex.secondary),
                    Modifier.padding(4.dp).fillMaxHeight(), animate = false)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(ex.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(ex.primary.joinToString { it.displayName }, style = MaterialTheme.typography.bodySmall, color = MzColors.Ember,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (ex.isAiTracked) AiBadge()
                    Pill(ex.equipment.displayName.uppercase())
                    Pill(ex.level.displayName.uppercase())
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MzColors.TextMuted)
        }
    }
}

@Composable
fun Legend(items: List<Pair<Color, String>>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (c, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = MzColors.TextMuted)
            }
        }
    }
}
