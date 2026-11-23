package org.mz.mzfit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.mz.mzfit.data.BodyPart
import org.mz.mzfit.data.ExerciseLibrary
import org.mz.mzfit.ui.components.ExerciseRow
import org.mz.mzfit.ui.components.SurfaceCard
import org.mz.mzfit.ui.theme.MzColors

@Composable
fun CoachScreen(contentPadding: PaddingValues, onStart: (String) -> Unit) {
    val grouped = BodyPart.entries.associateWith { p -> ExerciseLibrary.aiTracked.filter { it.bodyPart == p } }.filterValues { it.isNotEmpty() }

    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Pick a move and prop up your phone. MzFIT tracks 33 body landmarks in real time to count reps, " +
                    "measure joint angles and correct your form as you go.",
                    style = MaterialTheme.typography.bodyMedium, color = MzColors.TextMuted)
            }
        }
        item {
            SurfaceCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Feature(Icons.Default.Accessibility, "Full-body skeleton", "Smoothed 33-point tracking with live joint angles")
                    Feature(Icons.Default.Straighten, "Range-of-motion meter", "See exactly how deep each rep goes")
                    Feature(Icons.Default.RecordVoiceOver, "Voice & haptic cues", "Rep counts and corrections, hands-free")
                    Feature(Icons.Default.Lock, "Private by design", "Analysis runs on-device. Nothing is recorded.")
                }
            }
        }
        grouped.forEach { (part, list) ->
            item {
                Text(part.displayName.uppercase(), style = MaterialTheme.typography.labelSmall, color = MzColors.TextMuted,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp))
            }
            items(list, key = { it.id }) { ex -> ExerciseRow(ex, { onStart(ex.id) }, Modifier.padding(horizontal = 16.dp)) }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(MzColors.Volt.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MzColors.Volt, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MzColors.TextMuted)
        }
    }
}
