package com.trangnx.ttsbrowser.ui.reader

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.trangnx.ttsbrowser.data.settings.ReaderSettings
import com.trangnx.ttsbrowser.data.settings.ReaderTheme
import com.trangnx.ttsbrowser.tts.TtsEngine
import kotlin.math.roundToInt

private const val RATE_STEP = 0.1f

@get:StringRes
private val ReaderTheme.label: Int
    get() = when (this) {
        ReaderTheme.System -> R.string.reader_theme_system
        ReaderTheme.Light -> R.string.reader_theme_light
        ReaderTheme.Dark -> R.string.reader_theme_dark
        ReaderTheme.Sepia -> R.string.reader_theme_sepia
    }

/**
 * Font size and background apply while sliding / on tap; the speech rate is saved when the slider
 * is released, since each change restarts the current sentence.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderDisplaySheet(
    settings: ReaderSettings,
    onFontSize: (Int) -> Unit,
    onTheme: (ReaderTheme) -> Unit,
    onRate: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
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
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                ReaderTheme.entries.forEachIndexed { index, theme ->
                    SegmentedButton(
                        selected = theme == settings.theme,
                        onClick = { onTheme(theme) },
                        shape = SegmentedButtonDefaults.itemShape(index, ReaderTheme.entries.size),
                    ) { Text(stringResource(theme.label)) }
                }
            }

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

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
}
