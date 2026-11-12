package com.trangnx.ttsbrowser.ui.reader

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.trangnx.ttsbrowser.data.settings.ReaderTextColor
import com.trangnx.ttsbrowser.data.settings.ReaderTheme
import com.trangnx.ttsbrowser.ui.theme.DarkColors
import com.trangnx.ttsbrowser.ui.theme.LightColors

/** Fixed-color reading schemes: [bg] page, [text] ink, [highlight] the sentence being read. */
private fun readerScheme(base: ColorScheme, bg: Color, text: Color, variant: Color, highlight: Color) = base.copy(
    background = bg,
    onBackground = text,
    surface = bg,
    onSurface = text,
    onSurfaceVariant = variant,
    surfaceContainerLowest = lerp(bg, text, 0.02f),
    surfaceContainerLow = lerp(bg, text, 0.04f),
    surfaceContainer = lerp(bg, text, 0.06f),
    surfaceContainerHigh = lerp(bg, text, 0.08f),
    surfaceContainerHighest = lerp(bg, text, 0.10f),
    primaryContainer = highlight,
    onPrimaryContainer = text,
)

private val SepiaColors = readerScheme(LightColors, Color(0xFFF4ECD8), Color(0xFF433422), Color(0xFF6B5A44), Color(0xFFE6CF9C))
private val MintColors = readerScheme(LightColors, Color(0xFFCCE8CF), Color(0xFF1F3325), Color(0xFF456650), Color(0xFFA9D4B0))
private val BlackColors = readerScheme(DarkColors, Color(0xFF000000), Color(0xFFD8D8D8), Color(0xFF9A9A9A), Color(0xFF33404F))

/** Page color of a fixed theme for its picker swatch; null for [ReaderTheme.System], which follows the device. */
val ReaderTheme.swatch: Color?
    get() = when (this) {
        ReaderTheme.System -> null
        ReaderTheme.Light -> LightColors.background
        ReaderTheme.Dark -> DarkColors.background
        ReaderTheme.Sepia -> SepiaColors.background
        ReaderTheme.Mint -> MintColors.background
        ReaderTheme.Black -> BlackColors.background
    }

/** Ink of a fixed text color; null for [ReaderTextColor.Auto], which follows the background. */
val ReaderTextColor.ink: Color?
    get() = when (this) {
        ReaderTextColor.Auto -> null
        ReaderTextColor.Black -> Color(0xFF1A1A1A)
        ReaderTextColor.Charcoal -> Color(0xFF3C3C3C)
        ReaderTextColor.Brown -> Color(0xFF433422)
        ReaderTextColor.Gray -> Color(0xFFB0B0B0)
        ReaderTextColor.Cream -> Color(0xFFE8DCC0)
        ReaderTextColor.White -> Color(0xFFEDEDED)
    }

/**
 * Colors of the story text only (wrap just that, not the bars or sheets): [ReaderTheme.System] keeps
 * the app scheme (dynamic color on Android 12+), as do Light/Dark when they match the system. A fixed
 * [textColor] replaces the ink and tints the sentence highlight from it.
 */
@Composable
fun ReaderColorTheme(theme: ReaderTheme, textColor: ReaderTextColor, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val base = MaterialTheme.colorScheme
    val themed: ColorScheme = when (theme) {
        ReaderTheme.System -> base
        ReaderTheme.Light -> if (systemDark) LightColors else base
        ReaderTheme.Dark -> if (systemDark) base else DarkColors
        ReaderTheme.Sepia -> SepiaColors
        ReaderTheme.Mint -> MintColors
        ReaderTheme.Black -> BlackColors
    }
    val ink = textColor.ink
    val scheme = if (ink == null) themed else themed.copy(
        onBackground = ink,
        onSurface = ink,
        onSurfaceVariant = ink.copy(alpha = 0.75f),
        primaryContainer = lerp(themed.background, ink, 0.2f),
        onPrimaryContainer = ink,
    )
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = content)
}
