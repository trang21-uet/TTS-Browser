package com.trangnx.ttsbrowser.ui.reader

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.data.settings.ReaderTextColor
import com.trangnx.ttsbrowser.data.settings.ReaderTheme
import com.trangnx.ttsbrowser.ui.theme.DarkColors
import com.trangnx.ttsbrowser.ui.theme.LightColors

@get:StringRes
private val ReaderTheme.label: Int
    get() = when (this) {
        ReaderTheme.System -> R.string.reader_theme_system
        ReaderTheme.Light -> R.string.reader_theme_light
        ReaderTheme.Dark -> R.string.reader_theme_dark
        ReaderTheme.Sepia -> R.string.reader_theme_sepia
        ReaderTheme.Mint -> R.string.reader_theme_mint
        ReaderTheme.Black -> R.string.reader_theme_black
    }

@get:StringRes
private val ReaderTextColor.label: Int
    get() = when (this) {
        ReaderTextColor.Auto -> R.string.reader_text_auto
        ReaderTextColor.Black -> R.string.reader_text_black
        ReaderTextColor.Charcoal -> R.string.reader_text_charcoal
        ReaderTextColor.Brown -> R.string.reader_text_brown
        ReaderTextColor.Gray -> R.string.reader_text_gray
        ReaderTextColor.Cream -> R.string.reader_text_cream
        ReaderTextColor.White -> R.string.reader_text_white
    }

private val SwatchSize = 44.dp

/** Round page-color pickers; [ReaderTheme.System] shows the light and dark page side by side. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeSwatches(selected: ReaderTheme, onSelect: (ReaderTheme) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (theme in ReaderTheme.entries) {
            val color = theme.swatch
            val light = LightColors.background
            val dark = DarkColors.background
            val fill: Brush = if (color != null) {
                SolidColor(color)
            } else {
                Brush.horizontalGradient(0f to light, 0.5f to light, 0.5f to dark, 1f to dark)
            }
            Swatch(fill, color ?: light, stringResource(theme.label), theme == selected, onClick = { onSelect(theme) })
        }
    }
}

/** Ink pickers; "Tự động" follows the page color and is drawn as an "A". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextColorSwatches(selected: ReaderTextColor, onSelect: (ReaderTextColor) -> Unit) {
    val neutral = MaterialTheme.colorScheme.surfaceVariant
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (option in ReaderTextColor.entries) {
            val ink = option.ink
            Swatch(SolidColor(ink ?: neutral), ink ?: neutral, stringResource(option.label), option == selected, onClick = { onSelect(option) }) {
                if (ink == null) {
                    Text("A", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Swatch(
    fill: Brush,
    checkBackdrop: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit = {},
) {
    val outline = if (selected) {
        BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }
    Box(
        Modifier
            .size(SwatchSize)
            .clip(CircleShape)
            .background(fill)
            .border(outline, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = label,
                tint = if (checkBackdrop.luminance() > 0.5f) Color.Black else Color.White,
            )
        }
    }
}
