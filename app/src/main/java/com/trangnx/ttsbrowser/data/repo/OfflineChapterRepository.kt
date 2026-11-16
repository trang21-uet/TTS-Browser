package com.trangnx.ttsbrowser.data.repo

import com.trangnx.ttsbrowser.data.db.OfflineChapterDao
import com.trangnx.ttsbrowser.data.db.OfflineChapterEntity
import com.trangnx.ttsbrowser.extraction.Chapter
import com.trangnx.ttsbrowser.extraction.ContentSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Chapters of saved stories downloaded ahead for reading without loading the page. */
class OfflineChapterRepository(
    private val dao: OfflineChapterDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Book id → number of downloaded chapters (books without any are absent). */
    val counts: Flow<Map<Long, Int>> = dao.observeCounts().map { rows -> rows.associate { it.bookId to it.count } }

    /** The downloaded chapter reached by [url], or null. */
    suspend fun find(url: String): Chapter? = dao.find(url)?.let(::toChapter)

    /** Stores [chapter] of story [bookId], reached by [requestedUrl]; replaces an earlier download. */
    suspend fun save(bookId: Long, requestedUrl: String, chapter: Chapter) = dao.upsert(toEntity(bookId, requestedUrl, chapter))

    /** Replaces the downloaded chapter reached by [url] with a newer copy of it; no-op if none is stored. */
    suspend fun refresh(url: String, chapter: Chapter) {
        val old = dao.find(url) ?: return
        if (old.url == chapter.url) dao.upsert(toEntity(old.bookId, old.requestedUrl, chapter))
    }

    suspend fun deleteForBook(bookId: Long) = dao.deleteForBook(bookId)

    internal fun toEntity(bookId: Long, requestedUrl: String, chapter: Chapter) = OfflineChapterEntity(
        url = chapter.url,
        requestedUrl = requestedUrl,
        bookId = bookId,
        title = chapter.title,
        text = chapter.paragraphs.joinToString(PARAGRAPH_SEPARATOR),
        startsWithTitle = chapter.startsWithTitle,
        prevUrl = chapter.prevUrl,
        nextUrl = chapter.nextUrl,
        pageTitle = chapter.pageTitle,
        source = chapter.source.name,
        savedAt = clock(),
    )

    internal fun toChapter(entity: OfflineChapterEntity) = Chapter(
        url = entity.url,
        title = entity.title,
        paragraphs = entity.text.split(PARAGRAPH_SEPARATOR).filter { it.isNotEmpty() },
        prevUrl = entity.prevUrl,
        nextUrl = entity.nextUrl,
        source = ContentSource.entries.firstOrNull { it.name == entity.source } ?: ContentSource.Readability,
        pageTitle = entity.pageTitle,
        startsWithTitle = entity.startsWithTitle,
    )

    private companion object {
        const val PARAGRAPH_SEPARATOR = "\n"
    }
}
