package com.trangnx.ttsbrowser.ui.reader

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.trangnx.ttsbrowser.data.settings.ReaderTheme
import com.trangnx.ttsbrowser.ui.theme.DarkColors
import com.trangnx.ttsbrowser.ui.theme.LightColors

private val SepiaText = Color(0xFF433422)
private val SepiaBackground = Color(0xFFF4ECD8)

private val SepiaColors = LightColors.copy(
    background = SepiaBackground,
    onBackground = SepiaText,
    surface = SepiaBackground,
    onSurface = SepiaText,
    onSurfaceVariant = Color(0xFF6B5A44),
    surfaceContainerLowest = Color(0xFFF8F2E3),
    surfaceContainerLow = Color(0xFFF0E7D0),
    surfaceContainer = Color(0xFFEDE3CA),
    surfaceContainerHigh = Color(0xFFE8DCC0),
    surfaceContainerHighest = Color(0xFFE2D5B7),
    primaryContainer = Color(0xFFE6CF9C),
    onPrimaryContainer = SepiaText,
)

/**
 * Reader colors: [ReaderTheme.System] keeps the app scheme (dynamic color on Android 12+), as do
 * Light/Dark when they match the system; status bar icons follow the reader background.
 */
@Composable
fun ReaderColorTheme(theme: ReaderTheme, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val base = MaterialTheme.colorScheme
    val scheme: ColorScheme = when (theme) {
        ReaderTheme.System -> base
        ReaderTheme.Light -> if (systemDark) LightColors else base
        ReaderTheme.Dark -> if (systemDark) base else DarkColors
        ReaderTheme.Sepia -> SepiaColors
    }
    StatusBarAppearance(lightBackground = scheme.background.luminance() > 0.5f, restoreLight = !systemDark)
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = content)
}

@Composable
private fun StatusBarAppearance(lightBackground: Boolean, restoreLight: Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view, lightBackground, restoreLight) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = lightBackground
        onDispose { controller.isAppearanceLightStatusBars = restoreLight }
    }
}
