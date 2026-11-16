package com.trangnx.ttsbrowser.download

import android.database.SQLException
import com.trangnx.ttsbrowser.data.repo.OfflineChapterRepository
import com.trangnx.ttsbrowser.extraction.Chapter
import com.trangnx.ttsbrowser.extraction.ChapterLoader
import com.trangnx.ttsbrowser.extraction.ExtractionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DownloadState {
    data object Idle : DownloadState

    /** [done] of [total] chapters are downloaded (or were already). */
    data class Running(val bookId: Long, val bookTitle: String, val done: Int, val total: Int) : DownloadState

    /**
     * Ended with [done] chapters. [failure] null: all asked for, or the story had no more chapters
     * ([reachedEnd]); [cancelled]: stopped by the user.
     */
    data class Finished(
        val bookId: Long,
        val bookTitle: String,
        val done: Int,
        val reachedEnd: Boolean,
        val cancelled: Boolean = false,
        val failure: ExtractionResult.Reason? = null,
    ) : DownloadState
}

/**
 * Downloads chapters of a saved story ahead: from a chapter, follows the `nextUrl` links one page at a
 * time and stores each chapter for offline reading. Chapters already downloaded are not loaded again,
 * but their links are followed. One download at a time; it runs as long as the app process does.
 */
class ChapterDownloader(
    private val scope: CoroutineScope,
    private val online: ChapterLoader,
    private val store: OfflineChapterRepository,
    private val pauseMs: Long = PAUSE_BETWEEN_PAGES_MS,
) {
    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private var job: Job? = null

    /** Downloads [count] chapters starting with [fromUrl]; `false` if a download is already running. */
    fun start(bookId: Long, bookTitle: String, fromUrl: String, count: Int): Boolean {
        if (job?.isActive == true || count < 1) return false
        _state.value = DownloadState.Running(bookId, bookTitle, done = 0, total = count)
        job = scope.launch { _state.value = download(bookId, bookTitle, fromUrl, count) }
        return true
    }

    fun cancel() {
        val running = _state.value as? DownloadState.Running ?: return
        job?.cancel()
        _state.value = DownloadState.Finished(running.bookId, running.bookTitle, running.done, reachedEnd = false, cancelled = true)
    }

    /** Forgets a finished download's result once it has been shown. */
    fun clearFinished() {
        if (_state.value is DownloadState.Finished) _state.value = DownloadState.Idle
    }

    private suspend fun download(bookId: Long, title: String, fromUrl: String, count: Int): DownloadState.Finished {
        var url: String? = fromUrl
        var done = 0
        val visited = HashSet<String>() // a site linking back to an earlier chapter must not loop
        while (url != null && done < count && visited.add(url)) {
            // A stored chapter without a next link may have been the newest one then: check the page again.
            val stored = findStored(url)?.takeIf { it.nextUrl != null }
            val chapter = stored ?: when (val result = loadOnline(url)) {
                is ExtractionResult.Success -> result.chapter
                is ExtractionResult.Failure -> return DownloadState.Finished(bookId, title, done, reachedEnd = false, failure = result.reason)
            }
            if (stored == null) {
                try {
                    store.save(bookId, url, chapter)
                } catch (_: SQLException) {
                    return DownloadState.Finished(bookId, title, done, reachedEnd = false, failure = ExtractionResult.Reason.LoadFailed)
                }
            }
            done++
            _state.value = DownloadState.Running(bookId, title, done, count)
            url = chapter.nextUrl
            // Be gentle with the site: one page at a time, with a short pause.
            if (stored == null && url != null && done < count) delay(pauseMs)
        }
        return DownloadState.Finished(bookId, title, done, reachedEnd = done < count)
    }

    private suspend fun findStored(url: String): Chapter? = try {
        store.find(url)
    } catch (_: SQLException) {
        null
    }

    private suspend fun loadOnline(url: String): ExtractionResult = try {
        online.load(url)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        ExtractionResult.Failure(ExtractionResult.Reason.LoadFailed)
    }

    companion object {
        const val PAUSE_BETWEEN_PAGES_MS = 800L
        const val MAX_CHAPTERS = 500
    }
}
