package com.pace.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object PaceColors {
    val Background = Color(0xFF0D1114)
    val Surface = Color(0xFF161B20)
    val SurfaceHigh = Color(0xFF1F262D)
    val Primary = Color(0xFF4FD1A5)
    val OnPrimary = Color(0xFF00241A)
    val Secondary = Color(0xFFFFB84D)
    val Tertiary = Color(0xFF7AB8FF)
    val Error = Color(0xFFFF6B6B)
    val Muted = Color(0xFF8B96A1)
    val Ahead = Color(0xFF4FD1A5)
    val OnPace = Color(0xFF7AB8FF)
    val Behind = Color(0xFFFFB84D)
    val Purple = Color(0xFFC792EA)
    val Pink = Color(0xFFFF8FB1)
}

private val scheme = darkColorScheme(
    primary = PaceColors.Primary,
    onPrimary = PaceColors.OnPrimary,
    primaryContainer = Color(0xFF0F3D30),
    onPrimaryContainer = Color(0xFFB7F5DD),
    secondary = PaceColors.Secondary,
    onSecondary = Color(0xFF2B1A00),
    secondaryContainer = Color(0xFF3D2B0A),
    onSecondaryContainer = Color(0xFFFFE0B0),
    tertiary = PaceColors.Tertiary,
    background = PaceColors.Background,
    onBackground = Color(0xFFE6EBEF),
    surface = PaceColors.Surface,
    onSurface = Color(0xFFE6EBEF),
    surfaceVariant = PaceColors.SurfaceHigh,
    onSurfaceVariant = Color(0xFFB4BEC7),
    surfaceContainer = PaceColors.Surface,
    surfaceContainerHigh = PaceColors.SurfaceHigh,
    surfaceContainerHighest = Color(0xFF28313A),
    surfaceContainerLow = Color(0xFF12171B),
    outline = Color(0xFF3A444E),
    outlineVariant = Color(0xFF2A333B),
    error = PaceColors.Error,
)

private val typography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun PaceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
