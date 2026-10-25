package com.trangnx.ttsbrowser.playback

import com.trangnx.ttsbrowser.data.repo.LibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Saves where reading is whenever the player's chapter belongs to a saved story: at most every
 * [SAVE_INTERVAL_MS] while sentences go by, at once when playback pauses or ends, and on [flush]
 * (the service is going away). Main thread only.
 */
class ProgressTracker(
    private val scope: CoroutineScope,
    private val library: LibraryRepository,
    private val playback: PlaybackConnection,
) {
    private data class Position(val bookId: Long, val chapter: LoadedChapter, val sentenceIndex: Int)

    private var lastSaved: Position? = null

    /**
     * Null unless the saved story, the loaded chapter and the sentence index all describe the same
     * chapter: the story is looked up asynchronously after a chapter is loaded, and until then the
     * sentence index must not be filed under the previous chapter's story.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val position: StateFlow<Position?> = combine(
        playback.loaded.flatMapLatest { loaded ->
            if (loaded == null) flowOf(null) else library.bookOf(loaded.chapter).map { book -> book?.let { loaded to it.id } }
        },
        playback.loaded,
        playback.state.map { it.sentenceIndex }.distinctUntilChanged(),
    ) { stored, loaded, index ->
        if (stored != null && loaded != null && stored.first.chapter.url == loaded.chapter.url) {
            Position(stored.second, loaded, index)
        } else {
            null
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    fun start() {
        scope.launch {
            position.filterNotNull().conflate().collect {
                save(it)
                delay(SAVE_INTERVAL_MS)
            }
        }
        scope.launch {
            playback.state.map { it.showPlay }.distinctUntilChanged().filter { it }.collect { flush() }
        }
    }

    /** Saves the current position now, if it changed since the last save. */
    fun flush() {
        position.value?.let { scope.launch { save(it) } }
    }

    private suspend fun save(position: Position) {
        if (position == lastSaved) return
        lastSaved = position
        try {
            library.saveProgress(position.bookId, position.chapter.chapter, position.chapter.sentences.getOrNull(position.sentenceIndex))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            lastSaved = null // Storage failed: try again with the next position.
        }
    }

    private companion object {
        const val SAVE_INTERVAL_MS = 2_000L
    }
}
