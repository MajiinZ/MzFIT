package com.mlpt.shared

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// MzFit palette. Legacy token names stay stable for the existing shared components.
val Ink = Color(0xFF101C18)
val BrandNavy = Color(0xFF02021D)
val BrandLime = Color(0xFFBCFF34)
val PerformanceBlue = Color(0xFF0E713C)
val AppBackground = Color(0xFFF2F6F3)
val CardSurface = Color(0xFFFFFFFF)
val SoftBlue = Color(0xFFE3F4E8)
val SecondaryText = Color(0xFF52645A)
val Divider = Color(0xFFCCDCD1)
val HeroAccent = Color(0xFF173D2A)

@Composable
fun TrainingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PerformanceBlue, onPrimary = CardSurface, primaryContainer = SoftBlue,
            onPrimaryContainer = Ink, secondary = PerformanceBlue, onSecondary = CardSurface,
            secondaryContainer = SoftBlue, onSecondaryContainer = Ink,
            tertiary = Ink, onTertiary = CardSurface,
            background = AppBackground, onBackground = Ink,
            surface = CardSurface, onSurface = Ink,
            surfaceVariant = SoftBlue, onSurfaceVariant = SecondaryText,
            outline = SecondaryText, outlineVariant = Divider, surfaceTint = PerformanceBlue,
        ),
        typography = Typography(
            displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 50.sp),
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 31.sp),
        ), content = content,
    )
}
