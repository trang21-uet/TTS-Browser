package com.trangnx.ttsbrowser.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LibraryDao {
    @Transaction
    @Query("SELECT * FROM book ORDER BY updatedAt DESC")
    abstract fun observeAll(): Flow<List<BookWithProgress>>

    /** The book whose reading position is on one of [urls] (a chapter and its neighbours). */
    @Query(
        "SELECT book.* FROM book JOIN progress ON progress.bookId = book.id " +
            "WHERE progress.chapterUrl IN (:urls) ORDER BY progress.updatedAt DESC LIMIT 1",
    )
    abstract fun observeByChapterUrls(urls: List<String>): Flow<BookEntity?>

    @Query("SELECT * FROM book WHERE host = :host AND title = :title COLLATE NOCASE LIMIT 1")
    abstract suspend fun find(host: String, title: String): BookEntity?

    @Insert
    abstract suspend fun insert(book: BookEntity): Long

    @Upsert
    protected abstract suspend fun upsertProgress(progress: ProgressEntity)

    @Query("UPDATE book SET updatedAt = :at WHERE id = :bookId")
    protected abstract suspend fun touch(bookId: Long, at: Long)

    @Query("SELECT coverFile FROM book WHERE id = :bookId")
    abstract suspend fun coverFileOf(bookId: Long): String?

    @Query("UPDATE book SET coverFile = :file WHERE id = :bookId")
    abstract suspend fun setCoverFile(bookId: Long, file: String?)

    @Query("DELETE FROM book WHERE id = :bookId")
    abstract suspend fun delete(bookId: Long)

    /** Writes the position and moves the book to the top of the library. */
    @Transaction
    open suspend fun saveProgress(progress: ProgressEntity) {
        upsertProgress(progress)
        touch(progress.bookId, progress.updatedAt)
    }

    /** Finds or creates the book for ([host], [title]) and stores [progress] (its `bookId` is ignored) for it. */
    @Transaction
    open suspend fun saveBook(host: String, title: String, progress: ProgressEntity): BookEntity {
        val book = find(host, title)
            ?: BookEntity(title = title, host = host, createdAt = progress.updatedAt, updatedAt = progress.updatedAt)
                .let { it.copy(id = insert(it)) }
        saveProgress(progress.copy(bookId = book.id))
        return book
    }
}
