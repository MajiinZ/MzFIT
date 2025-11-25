package org.mz.mzfit.ui.componenets

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.pose.PoseLandmark

@Composable
fun PoseOverlay(
    modifier: Modifier = Modifier,
    landmarks: List<LandmarkModel>
) {
    Canvas(modifier = modifier) {

        val radius = 6.dp.toPx()

        // Draw joints
        landmarks.forEach { lm ->
            drawCircle(
                color = Color.White,
                radius = radius,
                center = Offset(lm.x, lm.y)
            )
        }

        // Helper to draw bone connections
        fun connect(a: Int, b: Int, color: Color = Color.Cyan) {
            val p1 = landmarks.firstOrNull { it.id == a }
            val p2 = landmarks.firstOrNull { it.id == b }

            if (p1 != null && p2 != null) {
                drawLine(
                    color = color,
                    start = Offset(p1.x, p1.y),
                    end = Offset(p2.x, p2.y),
                    strokeWidth = 4.dp.toPx()
                )
            }
        }

        // ---- Full ML Kit Skeleton (upper body sample) ----
        connect(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
        connect(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW)
        connect(PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST)

        connect(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW)
        connect(PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)

        connect(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
        connect(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP)
        connect(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP)

        connect(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE)
        connect(PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE)

        connect(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE)
        connect(PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
    }
}
