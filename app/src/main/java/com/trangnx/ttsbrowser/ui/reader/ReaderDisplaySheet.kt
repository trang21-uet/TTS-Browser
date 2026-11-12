package com.trangnx.ttsbrowser.ui.reader

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.data.settings.ReaderFont
import com.trangnx.ttsbrowser.data.settings.ReaderSettings
import com.trangnx.ttsbrowser.data.settings.ReaderTextColor
import com.trangnx.ttsbrowser.data.settings.ReaderTheme
import com.trangnx.ttsbrowser.tts.TtsEngine
import kotlin.math.roundToInt

private const val RATE_STEP = 0.1f

@get:StringRes
private val ReaderFont.label: Int
    get() = when (this) {
        ReaderFont.Default -> R.string.reader_font_default
        ReaderFont.BeVietnamPro -> R.string.reader_font_be_vietnam_pro
        ReaderFont.Lora -> R.string.reader_font_lora
        ReaderFont.Serif -> R.string.reader_font_serif
        ReaderFont.Mono -> R.string.reader_font_mono
    }

/**
 * Font, size and colors apply while sliding / on tap; the speech rate is saved when the slider
 * is released, since each change restarts the current sentence.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderDisplaySheet(
    settings: ReaderSettings,
    onFontSize: (Int) -> Unit,
    onTheme: (ReaderTheme) -> Unit,
    onTextColor: (ReaderTextColor) -> Unit,
    onFont: (ReaderFont) -> Unit,
    onRate: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Label(stringResource(R.string.reader_font))
            FontChoices(settings.font, onFont)

            // Local while dragging: saved values come back from DataStore late and must not move the thumb.
            var fontSize by remember { mutableFloatStateOf(settings.fontSizeSp.toFloat()) }
            var dragging by remember { mutableStateOf(false) }
            LaunchedEffect(settings.fontSizeSp) { if (!dragging) fontSize = settings.fontSizeSp.toFloat() }
            Label(stringResource(R.string.reader_font_size, fontSize.roundToInt()))
            Slider(
                value = fontSize,
                onValueChange = { value ->
                    dragging = true
                    if (value.roundToInt() != fontSize.roundToInt()) onFontSize(value.roundToInt())
                    fontSize = value
                },
                onValueChangeFinished = {
                    dragging = false
                    onFontSize(fontSize.roundToInt())
                },
                valueRange = ReaderSettings.MIN_FONT_SIZE_SP.toFloat()..ReaderSettings.MAX_FONT_SIZE_SP.toFloat(),
                steps = ReaderSettings.MAX_FONT_SIZE_SP - ReaderSettings.MIN_FONT_SIZE_SP - 1,
            )

            Label(stringResource(R.string.reader_background))
            ThemeSwatches(settings.theme, onTheme)

            Label(stringResource(R.string.reader_text_color))
            TextColorSwatches(settings.textColor, onTextColor)

            var rate by remember(settings.rate) { mutableFloatStateOf(settings.rate) }
            Label(stringResource(R.string.reader_rate, rate))
            Slider(
                value = rate,
                onValueChange = { rate = (it / RATE_STEP).roundToInt() * RATE_STEP },
                onValueChangeFinished = { onRate(rate) },
                valueRange = TtsEngine.MIN_RATE..TtsEngine.MAX_RATE,
                steps = ((TtsEngine.MAX_RATE - TtsEngine.MIN_RATE) / RATE_STEP).roundToInt() - 1,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontChoices(selected: ReaderFont, onSelect: (ReaderFont) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (font in ReaderFont.entries) {
            FilterChip(
                selected = font == selected,
                onClick = { onSelect(font) },
                label = { Text(stringResource(font.label), fontFamily = font.family) },
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}
