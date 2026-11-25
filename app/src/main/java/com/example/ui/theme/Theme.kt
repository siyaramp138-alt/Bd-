package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class BdEntSemanticColors(
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val whatsApp: Color,
    val whatsAppContainer: Color,
    val heroStart: Color,
    val heroEnd: Color
)

private val LightSemanticColors = BdEntSemanticColors(
    success = BdSuccess,
    successContainer = BdSuccessContainer,
    warning = BdWarning,
    warningContainer = BdWarningContainer,
    danger = BdDanger,
    dangerContainer = BdDangerContainer,
    whatsApp = BdWhatsAppGreen,
    whatsAppContainer = BdWhatsAppContainer,
    heroStart = BdDeepNavy,
    heroEnd = BdCobalt
)

private val DarkSemanticColors = BdEntSemanticColors(
    success = BdSuccessDark,
    successContainer = BdSuccessContainerDark,
    warning = BdWarningDark,
    warningContainer = BdWarningContainerDark,
    danger = BdDangerDark,
    dangerContainer = BdDangerContainerDark,
    whatsApp = BdWhatsAppDark,
    whatsAppContainer = BdWhatsAppContainerDark,
    heroStart = Color(0xFF0A1B3D),
    heroEnd = Color(0xFF173B87)
)

val LocalBdEntColors = staticCompositionLocalOf { LightSemanticColors }

private val BdLightColorScheme = lightColorScheme(
    primary = BdRoyalBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = BdDeepNavy,
    secondary = BdSkyAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = BdCobalt,
    onTertiary = Color.White,
    background = BdCanvasLight,
    onBackground = BdInkPrimaryLight,
    surface = BdSurfaceLight,
    onSurface = BdInkPrimaryLight,
    surfaceVariant = BdSurfaceVariantLight,
    onSurfaceVariant = BdInkSecondaryLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE7EEF8),
    outline = BdOutlineLight,
    outlineVariant = BdOutlineSubtleLight,
    error = BdDanger,
    onError = Color.White,
    errorContainer = BdDangerContainer,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val BdDarkColorScheme = darkColorScheme(
    primary = BdBlueLightDark,
    onPrimary = Color(0xFF0A1D44),
    primaryContainer = BdBlueContainerDark,
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = BdSkyLightDark,
    onSecondary = Color(0xFF082F49),
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = Color(0xFF60A5FA),
    onTertiary = Color(0xFF0A1D44),
    background = BdCanvasDark,
    onBackground = BdInkPrimaryDark,
    surface = BdSurfaceDark,
    onSurface = BdInkPrimaryDark,
    surfaceVariant = BdSurfaceVariantDark,
    onSurfaceVariant = BdInkSecondaryDark,
    surfaceContainerLowest = Color(0xFF0D1628),
    surfaceContainerLow = Color(0xFF111C31),
    surfaceContainer = Color(0xFF16243E),
    surfaceContainerHigh = Color(0xFF1D2E4F),
    outline = BdOutlineDark,
    outlineVariant = Color(0xFF24344D),
    error = BdDangerDark,
    onError = Color(0xFF450A0A),
    errorContainer = BdDangerContainerDark,
    onErrorContainer = Color(0xFFFECACA)
)

private val BdShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BdEntTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) BdDarkColorScheme else BdLightColorScheme
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalBdEntColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BdEntTypography,
            shapes = BdShapes,
            content = content
        )
    }
}
