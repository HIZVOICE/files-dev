package com.hyperfiles.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
 * Glass (iOS "Materials") scheme. Surfaces are translucent so the vibrant
 * gradient backdrop bleeds through like Apple's ultra-thin material; text is
 * white with a 65%-opacity secondary tier for native iOS-style vibrancy.
 * `background` is fully transparent so the Scaffold reveals the gradient, while
 * `surface` is a frosted violet panel used by the top bar, dialogs and menus.
 * `outlineVariant` is the 15%-white specular edge stroke.
 */
private val GlassColors = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF20143A),
    primaryContainer = Color(0x33FFFFFF),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFE6D9FF),
    secondaryContainer = Color(0x33FFFFFF),
    onSecondaryContainer = Color(0xFFFFFFFF),
    background = Color(0x00000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xB3241A3D),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0x1FFFFFFF),
    onSurfaceVariant = Color(0xA6FFFFFF),
    surfaceContainerLowest = Color(0x0FFFFFFF),
    surfaceContainerLow = Color(0x14FFFFFF),
    surfaceContainer = Color(0x1FFFFFFF),
    surfaceContainerHigh = Color(0x24FFFFFF),
    surfaceContainerHighest = Color(0x2EFFFFFF),
    outline = Color(0x40FFFFFF),
    outlineVariant = Color(0x26FFFFFF),
    error = Color(0xFFFFB4AB),
)

/** Vibrant abstract backdrop — deep blue → purple → soft pink, top-left to bottom-right. */
private val GlassGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF11224F),
        Color(0xFF35276F),
        Color(0xFF6E3F9E),
        Color(0xFFB65C97),
        Color(0xFFE98AA8),
    ),
    start = Offset.Zero,
    end = Offset.Infinite,
)

/**
 * Material 3 theme for the Compose screens. Honors the app's theme setting
 * (system / light / dark / amoled / glass) so Compose matches the rest of the app.
 */
@Composable
fun FilesDevTheme(content: @Composable () -> Unit) {
    val mode = Prefs.themeMode(LocalContext.current)

    if (mode == "glass") {
        MaterialTheme(colorScheme = GlassColors) {
            Box(Modifier.fillMaxSize().background(GlassGradient)) {
                // Default content color = white so unstyled text/icons stay vibrant on the gradient.
                CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
            }
        }
        return
    }

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
