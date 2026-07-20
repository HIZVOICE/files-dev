package com.hyperfiles.manager.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.hyperfiles.manager.Prefs

private val Accent = Color(0xFF3482FF)

private val DarkColors = darkColorScheme(
    primary = Accent,
    background = Color(0xFF0F1013),
    surface = Color(0xFF1B1D22),
    surfaceContainer = Color(0xFF191C21),
    surfaceContainerHigh = Color(0xFF23262E),
    onBackground = Color(0xFFECEDEF),
    onSurface = Color(0xFFECEDEF),
    onSurfaceVariant = Color(0xFF9AA0AA),
    outlineVariant = Color(0xFF2A2C31),
)

private val AmoledColors = DarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF0B0B0B),
    surfaceContainerHigh = Color(0xFF141414),
)

private val LightColors = lightColorScheme(
    primary = Accent,
    background = Color(0xFFF4F5F7),
    surface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF6B7280),
)

/**
 * Material 3 theme for the Compose screens. Honors the app's theme setting
 * (system / light / dark / amoled) so Compose matches the rest of the app.
 */
@Composable
fun FilesDevTheme(content: @Composable () -> Unit) {
    val mode = Prefs.themeMode(LocalContext.current)
    val dark = when (mode) {
        "light" -> false
        "dark", "amoled" -> true
        else -> isSystemInDarkTheme()
    }
    val colors = when {
        !dark -> LightColors
        mode == "amoled" -> AmoledColors
        else -> DarkColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
