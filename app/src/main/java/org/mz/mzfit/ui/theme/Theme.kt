package org.mz.mzfit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object MzColors {
    val Background = Color(0xFF0A0D12)
    val Surface = Color(0xFF12161D)
    val SurfaceHigh = Color(0xFF1A1F28)
    val SurfaceHighest = Color(0xFF232A35)
    val Outline = Color(0xFF2A313C)
    val Text = Color(0xFFF2F5F8)
    val TextMuted = Color(0xFF8A94A6)

    /** Brand accent — "volt". */
    val Volt = Color(0xFFC8FF2E)
    val Ember = Color(0xFFFF5A36)
    val Amber = Color(0xFFFFB547)
    val Cyan = Color(0xFF2EE6D6)
    val Danger = Color(0xFFFF4D5E)

    // Muscle map
    val Silhouette = Color(0xFF1A2029)
    val MuscleIdle = Color(0xFF2D3440)
    val MusclePrimary = Ember
    val MuscleSecondary = Amber

    // Skeleton overlay
    val BoneGood = Cyan
    val BoneBad = Danger
    val Joint = Color.White
}

private val scheme = darkColorScheme(
    primary = MzColors.Volt,
    onPrimary = MzColors.Background,
    primaryContainer = Color(0xFF2B3A0A),
    onPrimaryContainer = MzColors.Volt,
    secondary = MzColors.Ember,
    onSecondary = Color.White,
    tertiary = MzColors.Cyan,
    background = MzColors.Background,
    onBackground = MzColors.Text,
    surface = MzColors.Surface,
    onSurface = MzColors.Text,
    surfaceVariant = MzColors.SurfaceHigh,
    onSurfaceVariant = MzColors.TextMuted,
    surfaceContainer = MzColors.Surface,
    surfaceContainerLow = MzColors.Surface,
    surfaceContainerHigh = MzColors.SurfaceHigh,
    surfaceContainerHighest = MzColors.SurfaceHighest,
    outline = MzColors.Outline,
    outlineVariant = MzColors.Outline,
    error = MzColors.Danger,
)

private val base = Typography()
private val typography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp),
)

@Composable
fun MzFITTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
