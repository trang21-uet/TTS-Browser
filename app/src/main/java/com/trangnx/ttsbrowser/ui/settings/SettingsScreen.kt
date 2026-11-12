package com.trangnx.ttsbrowser.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.data.settings.SpeechEngine
import com.trangnx.ttsbrowser.data.settings.SpeechSettings
import com.trangnx.ttsbrowser.tts.TtsEngine
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onBackgroundGuide: () -> Unit,
    onSystemVoiceSettingsClosed: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val autoUpdateCheck by viewModel.autoUpdateCheck.collectAsStateWithLifecycle()
    val autoHideBars by viewModel.autoHideBars.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val saveError = stringResource(R.string.settings_save_error)
    LaunchedEffect(viewModel, saveError) {
        viewModel.saveFailures.collect { snackbar.showSnackbar(saveError) }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        val current = settings
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            SpeechSettingsContent(
                current, enabled = !saving, onEngine = viewModel::setEngine, onVoice = viewModel::setVoice,
                onRate = viewModel::setRate, onPitch = viewModel::setPitch,
                onBackgroundGuide = onBackgroundGuide,
                onSystemVoiceSettingsClosed = onSystemVoiceSettingsClosed,
                reading = { ReadingSettingsSection(autoHideBars, !saving, viewModel::setAutoHideBars) },
                update = {
                    UpdateSettingsSection(
                        viewModel.installedVersion, updateState, autoUpdateCheck, !saving,
                        viewModel::setAutoUpdateCheck, viewModel::onUpdateAction,
                    )
                },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun SpeechSettingsContent(
    settings: SpeechSettings,
    enabled: Boolean,
    onEngine: (SpeechEngine) -> Unit,
    onVoice: (String) -> Unit,
    onRate: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onBackgroundGuide: () -> Unit,
    onSystemVoiceSettingsClosed: () -> Unit,
    reading: @Composable () -> Unit,
    update: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Column(Modifier.selectableGroup()) {
            SectionTitle(stringResource(R.string.settings_speech_engine))
            ChoiceRow(
                stringResource(R.string.settings_engine_system), stringResource(R.string.settings_system_hint),
                settings.engine == SpeechEngine.System, enabled,
            ) { onEngine(SpeechEngine.System) }
            ChoiceRow(
                stringResource(R.string.settings_engine_edge), stringResource(R.string.settings_edge_hint),
                settings.engine == SpeechEngine.Edge, enabled,
            ) { onEngine(SpeechEngine.Edge) }
        }
        if (settings.engine == SpeechEngine.Edge) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.settings_edge_privacy),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Column(Modifier.selectableGroup()) {
                SectionTitle(stringResource(R.string.settings_edge_voice))
                ChoiceRow(
                    stringResource(R.string.settings_voice_hoaimy), stringResource(R.string.settings_voice_female),
                    settings.edgeVoice == SpeechSettings.EDGE_VOICES[0], enabled,
                ) { onVoice(SpeechSettings.EDGE_VOICES[0]) }
                ChoiceRow(
                    stringResource(R.string.settings_voice_namminh), stringResource(R.string.settings_voice_male),
                    settings.edgeVoice == SpeechSettings.EDGE_VOICES[1], enabled,
                ) { onVoice(SpeechSettings.EDGE_VOICES[1]) }
            }
        }
        if (settings.engine == SpeechEngine.System) SystemVoiceSettingsButton(onSystemVoiceSettingsClosed)
        HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(stringResource(R.string.settings_speech_adjustments))
            Text(stringResource(R.string.settings_per_engine_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SpeechSlider(settings.rate, settings.engine, enabled, TtsEngine.MIN_RATE..TtsEngine.MAX_RATE, R.string.reader_rate, onRate)
            SpeechSlider(settings.pitch, settings.engine, enabled, TtsEngine.MIN_PITCH..TtsEngine.MAX_PITCH, R.string.settings_pitch, onPitch)
        }
        HorizontalDivider()
        TextButton(onClick = onBackgroundGuide) { Text(stringResource(R.string.background_guide_title)) }
        HorizontalDivider()
        reading()
        HorizontalDivider()
        update()
    }
}

@Composable
private fun SectionTitle(label: String) {
    Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
}

@Composable
private fun ChoiceRow(label: String, hint: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SpeechSlider(
    value: Float,
    engine: SpeechEngine,
    enabled: Boolean,
    range: ClosedFloatingPointRange<Float>,
    labelRes: Int,
    onSave: (Float) -> Unit,
) {
    var sliderValue by remember(value, engine, enabled) { mutableFloatStateOf(value) }
    val label = stringResource(labelRes, sliderValue)
    Column {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = (it * 10).roundToInt() / 10f },
            onValueChangeFinished = { onSave(sliderValue) },
            valueRange = range,
            steps = ((range.endInclusive - range.start) * 10).roundToInt() - 1,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}
