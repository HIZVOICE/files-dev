package com.hyperfiles.manager.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF3482FF)

private val DarkColors = darkColorScheme(
    primary = Accent,
    background = Color(0xFF0F1013),
    surface = Color(0xFF1B1D22),
    surfaceContainerHigh = Color(0xFF23262E),
    onBackground = Color(0xFFECEDEF),
    onSurface = Color(0xFFECEDEF),
    onSurfaceVariant = Color(0xFF9AA0AA),
)

private val LightColors = lightColorScheme(
    primary = Accent,
    background = Color(0xFFF4F5F7),
    surface = Color(0xFFFFFFFF),
)

/** Material 3 theme for the Compose screens as we migrate off XML Views. */
@Composable
fun FilesDevTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
