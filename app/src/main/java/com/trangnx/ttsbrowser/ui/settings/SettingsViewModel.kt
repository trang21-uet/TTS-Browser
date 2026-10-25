package com.trangnx.ttsbrowser.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.trangnx.ttsbrowser.TtsBrowserApp
import com.trangnx.ttsbrowser.data.settings.SettingsRepository
import com.trangnx.ttsbrowser.data.settings.SpeechEngine
import com.trangnx.ttsbrowser.data.settings.SpeechSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<SpeechSettings?> = repository.speechSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val failures = Channel<Unit>(Channel.CONFLATED)
    val saveFailures = failures.receiveAsFlow()

    fun setEngine(engine: SpeechEngine) = save { repository.setEngine(engine) }
    fun setVoice(voice: String) = save { repository.setEdgeVoice(voice) }
    fun setRate(rate: Float) = save { repository.setRate(rate) }
    fun setPitch(pitch: Float) = save { repository.setPitch(pitch) }

    private fun save(action: suspend () -> Unit) {
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failures.trySend(Unit)
            } finally {
                _saving.value = false
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                SettingsViewModel((this[APPLICATION_KEY] as TtsBrowserApp).container.settingsRepository)
            }
        }
    }
}
