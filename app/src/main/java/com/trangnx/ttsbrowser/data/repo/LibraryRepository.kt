package com.trangnx.ttsbrowser.data.repo

import android.net.Uri
import com.trangnx.ttsbrowser.data.cover.CoverStore
import com.trangnx.ttsbrowser.data.db.BookEntity
import com.trangnx.ttsbrowser.data.db.BookWithProgress
import com.trangnx.ttsbrowser.data.db.LibraryDao
import com.trangnx.ttsbrowser.data.db.ProgressEntity
import com.trangnx.ttsbrowser.extraction.Chapter
import com.trangnx.ttsbrowser.text.Sentence
import kotlinx.coroutines.flow.Flow
import java.io.File

/** Saved stories and where each was left off. */
class LibraryRepository(
    private val dao: LibraryDao,
    private val covers: CoverStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Most recently read first. */
    val books: Flow<List<BookWithProgress>> = dao.observeAll()

    /** Where a story's cover file is. */
    fun coverFile(book: BookEntity): File? = book.coverFile?.let(covers::file)

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
        val book = dao.saveBook(host, name, progressOf(chapter, sentence))
        // The page's image is the default cover; one already chosen stays. Best effort, after the story is saved.
        if (book.coverFile == null && chapter.coverUrl.isNotBlank()) {
            covers.download(chapter.coverUrl, chapter.url)?.let { setCover(book.id, it) }
        }
        return true
    }

    suspend fun saveProgress(bookId: Long, chapter: Chapter, sentence: Sentence?) {
        dao.saveProgress(progressOf(chapter, sentence).copy(bookId = bookId))
    }

    /** Uses the image at [uri] as the cover; `false` if it cannot be read as an image (the old cover stays). */
    suspend fun setCoverFromUri(bookId: Long, uri: Uri): Boolean {
        val file = covers.import(uri) ?: return false
        setCover(bookId, file)
        return true
    }

    suspend fun clearCover(bookId: Long) = setCover(bookId, null)

    private suspend fun setCover(bookId: Long, file: String?) {
        val old = dao.coverFileOf(bookId)
        dao.setCoverFile(bookId, file)
        covers.delete(old)
    }

    suspend fun delete(bookId: Long) {
        val cover = dao.coverFileOf(bookId)
        dao.delete(bookId)
        covers.delete(cover)
    }

    private fun progressOf(chapter: Chapter, sentence: Sentence?) = ProgressEntity(
        bookId = 0,
        chapterUrl = chapter.url,
        chapterTitle = chapter.title,
        sentenceIndex = sentence?.index ?: 0,
        charOffset = sentence?.start ?: 0,
        updatedAt = clock(),
    )
}
