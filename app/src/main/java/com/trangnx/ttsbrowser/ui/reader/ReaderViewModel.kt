package com.trangnx.ttsbrowser.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.trangnx.ttsbrowser.TtsBrowserApp
import com.trangnx.ttsbrowser.data.settings.ReaderSettings
import com.trangnx.ttsbrowser.data.settings.ReaderTheme
import com.trangnx.ttsbrowser.data.settings.SettingsRepository
import com.trangnx.ttsbrowser.playback.LoadedChapter
import com.trangnx.ttsbrowser.playback.PlaybackConnection
import com.trangnx.ttsbrowser.playback.PlaybackUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Reader and mini player. The reader shows [shown] (just extracted, or the player's chapter when opened
 * from the mini player); it becomes the player's queue only on play or a sentence tap, so opening a page
 * never replaces what is being listened to. The mini player follows [loaded].
 */
class ReaderViewModel(
    private val playback: PlaybackConnection,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _shown = MutableStateFlow<LoadedChapter?>(null)
    val shown: StateFlow<LoadedChapter?> = _shown.asStateFlow()

    val loaded: StateFlow<LoadedChapter?> = playback.loaded
    val playbackState: StateFlow<PlaybackUiState> = playback.state

    /** Whether the player holds [shown]: only then the reader highlights and shows player state. */
    val isShownLoaded: StateFlow<Boolean> = combine(_shown, playback.loaded) { shown, _ ->
        shown != null && playback.isLoaded(shown)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Null until read, so the reader does not flash the default font size. */
    val settings: StateFlow<ReaderSettings?> = settingsRepository.readerSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Shows an extracted chapter. [continuing] (previous/next chapter from the reader) while the shown
     * chapter is playing loads it right away, so reading goes on; a re-extracted loaded chapter
     * refreshes the player's metadata.
     */
    fun show(chapter: LoadedChapter, continuing: Boolean) {
        val previous = _shown.value
        _shown.value = chapter
        val readOn = continuing && previous != null && playback.isLoaded(previous) && !playback.state.value.showPlay
        if (readOn || playback.isLoaded(chapter)) playback.load(chapter)
    }

    /** Shows the player's chapter (mini player tap). */
    fun showLoaded() {
        playback.loaded.value?.let { _shown.value = it }
    }

    /** Mini player: play/pause of the loaded chapter. */
    fun toggleLoaded() = playback.togglePlayPause()

    /** Reader play button: toggles the shown chapter if loaded, otherwise loads and reads it from the start. */
    fun togglePlayPause() {
        val chapter = _shown.value ?: return
        if (playback.isLoaded(chapter)) playback.togglePlayPause() else playFrom(0)
    }

    fun playFrom(index: Int) {
        val chapter = _shown.value ?: return
        playback.load(chapter)
        playback.playFrom(index)
    }

    fun previousSentence() {
        if (isShownLoaded.value) playback.previousSentence()
    }

    fun nextSentence() {
        if (isShownLoaded.value) playback.nextSentence()
    }

    fun setFontSize(sp: Int) = save { it.setFontSize(sp) }

    fun setTheme(theme: ReaderTheme) = save { it.setReaderTheme(theme) }

    /** The player picks the new rate up from the settings. */
    fun setRate(rate: Float) = save { it.setRate(rate) }

    private fun save(block: suspend (SettingsRepository) -> Unit) {
        viewModelScope.launch {
            try {
                block(settingsRepository)
            } catch (_: IOException) {
                // Storage failure: the old value stays.
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TtsBrowserApp).container
                ReaderViewModel(container.playbackConnection, container.settingsRepository)
            }
        }
    }
}
