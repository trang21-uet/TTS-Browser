package com.trangnx.ttsbrowser.data.repo

import com.trangnx.ttsbrowser.data.db.BookEntity
import com.trangnx.ttsbrowser.data.db.BookWithProgress
import com.trangnx.ttsbrowser.data.db.LibraryDao
import com.trangnx.ttsbrowser.data.db.ProgressEntity
import com.trangnx.ttsbrowser.extraction.Chapter
import com.trangnx.ttsbrowser.text.Sentence
import kotlinx.coroutines.flow.Flow

/** Saved stories and where each was left off. */
class LibraryRepository(
    private val dao: LibraryDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Most recently read first. */
    val books: Flow<List<BookWithProgress>> = dao.observeAll()

    /**
     * The saved story [chapter] belongs to: the one whose reading position is on this chapter or the
     * one before/after it, so moving on chapter by chapter stays inside the story.
     */
    fun bookOf(chapter: Chapter): Flow<BookEntity?> =
        dao.observeByChapterUrls(listOfNotNull(chapter.url, chapter.prevUrl, chapter.nextUrl))

    /**
     * Saves [chapter] (at [sentence], or its start) as story [title]; a story with the same title on the
     * same site is updated instead of duplicated. Returns `false` if the page has no http(s) host or [title] is blank.
     */
    suspend fun saveBook(title: String, chapter: Chapter, sentence: Sentence?): Boolean {
        val host = hostOf(chapter.url) ?: return false
        val name = title.trim().ifEmpty { return false }
        dao.saveBook(host, name, progressOf(chapter, sentence))
        return true
    }

    suspend fun saveProgress(bookId: Long, chapter: Chapter, sentence: Sentence?) {
        dao.saveProgress(progressOf(chapter, sentence).copy(bookId = bookId))
    }

    suspend fun delete(bookId: Long) = dao.delete(bookId)

    private fun progressOf(chapter: Chapter, sentence: Sentence?) = ProgressEntity(
        bookId = 0,
        chapterUrl = chapter.url,
        chapterTitle = chapter.title,
        sentenceIndex = sentence?.index ?: 0,
        charOffset = sentence?.start ?: 0,
        updatedAt = clock(),
    )
}
