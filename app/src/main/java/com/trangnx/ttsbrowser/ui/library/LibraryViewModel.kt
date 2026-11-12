package com.trangnx.ttsbrowser.ui.library

import android.database.SQLException
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.trangnx.ttsbrowser.TtsBrowserApp
import com.trangnx.ttsbrowser.data.db.BookEntity
import com.trangnx.ttsbrowser.data.db.BookWithProgress
import com.trangnx.ttsbrowser.data.repo.LibraryRepository
import com.trangnx.ttsbrowser.data.settings.LibraryLayout
import com.trangnx.ttsbrowser.data.settings.SettingsRepository
import com.trangnx.ttsbrowser.extraction.ExtractionResult
import com.trangnx.ttsbrowser.extraction.HeadlessPageLoader
import com.trangnx.ttsbrowser.playback.LoadedChapter
import com.trangnx.ttsbrowser.playback.PlaybackConnection
import com.trangnx.ttsbrowser.text.resumeIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/** A saved story's chapter to show in the reader at [startIndex] (unless it is playing already). */
data class ResumeRequest(val chapter: LoadedChapter, val startIndex: Int)

/**
 * Saved stories. Opening one loads its chapter in a headless WebView (the browser tab stays as it is)
 * and asks for the reader at the saved sentence. Activity-scoped: the reader opens above whichever tab is showing.
 */
class LibraryViewModel(
    private val library: LibraryRepository,
    private val loader: HeadlessPageLoader,
    private val playback: PlaybackConnection,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Null until read. */
    val books: StateFlow<List<BookWithProgress>?> = library.books
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _opening = MutableStateFlow<Long?>(null)

    /** Id of the story being loaded. */
    val opening: StateFlow<Long?> = _opening.asStateFlow()

    private val resumeRequests = Channel<ResumeRequest>(Channel.BUFFERED)
    val resume: Flow<ResumeRequest> = resumeRequests.receiveAsFlow()

    private val failureReasons = Channel<ExtractionResult.Reason>(Channel.BUFFERED)
    val failures: Flow<ExtractionResult.Reason> = failureReasons.receiveAsFlow()

    private val coverFailures = Channel<Unit>(Channel.BUFFERED)
    val coverFailed: Flow<Unit> = coverFailures.receiveAsFlow()

    /** Null until read, so the screen does not flash the default layout. */
    val layout: StateFlow<LibraryLayout?> = settings.libraryLayout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun open(item: BookWithProgress) {
        val progress = item.progress ?: return
        if (_opening.value != null) return
        // Listening to this chapter right now: show it as it is.
        playback.loaded.value?.takeIf { it.chapter.url == progress.chapterUrl }?.let {
            resumeRequests.trySend(ResumeRequest(it, startIndex = 0))
            return
        }
        _opening.value = item.book.id
        viewModelScope.launch {
            try {
                when (val result = loader.load(progress.chapterUrl)) {
                    is ExtractionResult.Success -> resumeRequests.trySend(
                        ResumeRequest(
                            LoadedChapter(result.chapter, result.sentences),
                            result.sentences.resumeIndex(progress.sentenceIndex, progress.charOffset),
                        ),
                    )
                    is ExtractionResult.Failure -> failureReasons.trySend(result.reason)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failureReasons.trySend(ExtractionResult.Reason.LoadFailed)
            } finally {
                _opening.value = null
            }
        }
    }

    /** The cover file of [book], if it has one. */
    fun coverOf(book: BookEntity): File? = library.coverFile(book)

    /** Uses the image the user picked as [item]'s cover; [coverFailed] fires if it is not a readable image. */
    fun setCover(item: BookWithProgress, uri: Uri) {
        viewModelScope.launch {
            try {
                if (!library.setCoverFromUri(item.book.id, uri)) coverFailures.trySend(Unit)
            } catch (_: SQLException) {
                coverFailures.trySend(Unit)
            }
        }
    }

    fun clearCover(item: BookWithProgress) {
        viewModelScope.launch {
            try {
                library.clearCover(item.book.id)
            } catch (_: SQLException) {
                // Storage failure: the cover stays.
            }
        }
    }

    fun setLayout(layout: LibraryLayout) {
        viewModelScope.launch {
            try {
                settings.setLibraryLayout(layout)
            } catch (_: IOException) {
                // Storage failure: the old layout stays.
            }
        }
    }

    fun delete(item: BookWithProgress) {
        viewModelScope.launch {
            try {
                library.delete(item.book.id)
            } catch (_: SQLException) {
                // Storage failure: the story stays in the list.
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TtsBrowserApp).container
                LibraryViewModel(
                    container.libraryRepository,
                    container.headlessPageLoader,
                    container.playbackConnection,
                    container.settingsRepository,
                )
            }
        }
    }
}
