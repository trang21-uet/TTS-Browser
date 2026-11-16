package com.trangnx.ttsbrowser.extraction

import android.database.SQLException
import com.trangnx.ttsbrowser.data.repo.OfflineChapterRepository
import com.trangnx.ttsbrowser.text.SentenceSegmenter
import kotlinx.coroutines.CancellationException

/** Loads the chapter at a URL without the browser tab. */
fun interface ChapterLoader {
    suspend fun load(url: String): ExtractionResult
}

/**
 * A downloaded chapter if there is one (no network needed), otherwise [online]. A storage error
 * falls back to [online] too: reading must not depend on the download store. A downloaded chapter
 * without a next link is loaded again when possible, in case newer chapters were published.
 */
class OfflineFirstChapterLoader(
    private val offline: OfflineChapterRepository,
    private val online: ChapterLoader,
    private val segmenter: SentenceSegmenter = SentenceSegmenter(),
) : ChapterLoader {
    override suspend fun load(url: String): ExtractionResult {
        val chapter = try {
            offline.find(url)
        } catch (_: SQLException) {
            null
        }
        if (chapter == null) return online.load(url)
        val stored = ExtractionResult.Success(chapter, segmenter.segment(chapter.paragraphs), selectorMissed = false)
        if (chapter.nextUrl != null) return stored
        // It may have been the newest chapter when downloaded: the page may link to a newer one by now.
        val fresh = try {
            online.load(url)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (fresh !is ExtractionResult.Success) return stored
        // Keep the newer copy so the next open finds its next link without loading the page.
        if (fresh.chapter.nextUrl != null) {
            try {
                offline.refresh(url, fresh.chapter)
            } catch (_: SQLException) {
                // The stored copy stays; the page is checked again next time.
            }
        }
        return fresh
    }
}
