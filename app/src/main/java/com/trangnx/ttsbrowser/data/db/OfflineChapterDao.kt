package com.trangnx.ttsbrowser.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineChapterDao {
    /** The downloaded chapter at [url], whether it is its own URL or the link it was reached by. */
    @Query("SELECT * FROM offline_chapter WHERE url = :url OR requestedUrl = :url LIMIT 1")
    suspend fun find(url: String): OfflineChapterEntity?

    @Upsert
    suspend fun upsert(chapter: OfflineChapterEntity)

    @Query("SELECT bookId, COUNT(*) AS count FROM offline_chapter GROUP BY bookId")
    fun observeCounts(): Flow<List<OfflineChapterCount>>

    @Query("DELETE FROM offline_chapter WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)
}
