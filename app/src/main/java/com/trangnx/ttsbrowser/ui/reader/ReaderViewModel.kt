package com.trangnx.ttsbrowser.ui.reader

import android.database.SQLException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.trangnx.ttsbrowser.TtsBrowserApp
import com.trangnx.ttsbrowser.data.db.BookEntity
import com.trangnx.ttsbrowser.data.repo.LibraryRepository
import com.trangnx.ttsbrowser.data.settings.ReaderSettings
import com.trangnx.ttsbrowser.data.settings.ReaderTheme
import com.trangnx.ttsbrowser.data.settings.SettingsRepository
import com.trangnx.ttsbrowser.extraction.ExtractionResult
import com.trangnx.ttsbrowser.extraction.HeadlessPageLoader
import com.trangnx.ttsbrowser.playback.LoadedChapter
import com.trangnx.ttsbrowser.playback.PlaybackConnection
import com.trangnx.ttsbrowser.playback.PlaybackUiState
import com.trangnx.ttsbrowser.playback.SleepTimer
import com.trangnx.ttsbrowser.playback.SleepTimerState
import com.trangnx.ttsbrowser.text.BookTitleSuggester
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    private val library: LibraryRepository,
    private val sleepTimer: SleepTimer,
    private val chapterLoader: HeadlessPageLoader,
) : ViewModel() {

    private val _shown = MutableStateFlow<LoadedChapter?>(null)
    val shown: StateFlow<LoadedChapter?> = _shown.asStateFlow()

    private val _openingChapter = MutableStateFlow(false)
    val openingChapter: StateFlow<Boolean> = _openingChapter.asStateFlow()
    private val _chapterError = MutableStateFlow<ExtractionResult.Reason?>(null)
    val chapterError: StateFlow<ExtractionResult.Reason?> = _chapterError.asStateFlow()
    private var chapterJob: Job? = null
    private var chapterRequest = 0L

    /** Loads an adjacent chapter without leaving the reader or replacing unrelated playback. */
    fun openChapter(url: String) {
        val from = _shown.value ?: return
        if (_openingChapter.value || url == from.chapter.url) return
        if (url != from.chapter.prevUrl && url != from.chapter.nextUrl) return
        val request = ++chapterRequest
        _openingChapter.value = true
        _chapterError.value = null
        chapterJob = viewModelScope.launch {
            try {
                val result = chapterLoader.load(url)
                // The reader or player may have moved on while the page was loading.
                if (request != chapterRequest || _shown.value !== from) return@launch
                when (result) {
                    is ExtractionResult.Success -> show(LoadedChapter(result.chapter, result.sentences), continuing = true)
                    is ExtractionResult.Failure -> _chapterError.value = result.reason
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (request == chapterRequest && _shown.value === from) {
                    _chapterError.value = ExtractionResult.Reason.LoadFailed
                }
            } finally {
                if (request == chapterRequest) _openingChapter.value = false
            }
        }
    }

    /** Leaving the reader cancels its pending navigation and destroys the temporary WebView. */
    fun cancelChapterNavigation() {
        chapterRequest++
        chapterJob?.cancel()
        chapterJob = null
        _openingChapter.value = false
        _chapterError.value = null
    }

    val loaded: StateFlow<LoadedChapter?> = playback.loaded
    val playbackState: StateFlow<PlaybackUiState> = playback.state

    /** Whether the player holds [shown]: only then the reader highlights and shows player state. */
    val isShownLoaded: StateFlow<Boolean> = combine(_shown, playback.loaded) { shown, _ ->
        shown != null && playback.isLoaded(shown)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The saved story the shown chapter belongs to, if any. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val savedBook: StateFlow<BookEntity?> = _shown
        .flatMapLatest { shown -> shown?.let { library.bookOf(it.chapter) } ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimer.state

    init {
        viewModelScope.launch {
            var previousUrl = playback.loaded.value?.chapter?.url
            playback.loaded.collect { now ->
                val url = now?.chapter?.url
                // The player went on to another chapter by itself (the notification's next chapter): follow it.
                if (now != null && previousUrl != null && url != previousUrl && _shown.value?.chapter?.url == previousUrl) {
                    _shown.value = now
                }
                previousUrl = url
            }
        }
    }

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

    /**
     * Opens a saved story's chapter (Library) at [startIndex], paused. A chapter that is already in the
     * player keeps its live position: it is more recent than the saved one.
     */
    fun resume(chapter: LoadedChapter, startIndex: Int) {
        _shown.value = chapter
        if (playback.isLoaded(chapter)) {
            playback.load(chapter)
        } else {
            playback.load(chapter, startIndex)
            playback.pause()
        }
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

    fun suggestedBookName(): String =
        _shown.value?.chapter?.let { BookTitleSuggester.suggest(it.pageTitle, it.title) }.orEmpty()

    /** Saves the shown chapter (at the playing sentence if the player has it) as story [name]. */
    fun saveBook(name: String) {
        val shown = _shown.value ?: return
        val sentence = if (isShownLoaded.value) shown.sentences.getOrNull(playback.state.value.sentenceIndex) else null
        viewModelScope.launch {
            try {
                library.saveBook(name, shown.chapter, sentence)
            } catch (_: SQLException) {
                // Storage failure: nothing saved, the menu still offers "Lưu truyện".
            }
        }
    }

    fun startSleepTimer(minutes: Int) = sleepTimer.startMinutes(minutes)

    fun startSleepTimerAtChapterEnd() = sleepTimer.startEndOfChapter()

    fun cancelSleepTimer() = sleepTimer.cancel()

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
                ReaderViewModel(
                    container.playbackConnection,
                    container.settingsRepository,
                    container.libraryRepository,
                    container.sleepTimer,
                    container.headlessPageLoader,
                )
            }
        }
    }
}
