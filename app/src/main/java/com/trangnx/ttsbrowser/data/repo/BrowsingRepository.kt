package com.trangnx.ttsbrowser.data.repo

import com.trangnx.ttsbrowser.data.db.BookmarkDao
import com.trangnx.ttsbrowser.data.db.BookmarkEntity
import com.trangnx.ttsbrowser.data.db.HistoryDao
import com.trangnx.ttsbrowser.data.db.HistoryEntity
import kotlinx.coroutines.flow.Flow

/** Browser history and bookmarks. */
class BrowsingRepository(
    private val historyDao: HistoryDao,
    private val bookmarkDao: BookmarkDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun recentHistory(limit: Int = HISTORY_LIMIT): Flow<List<HistoryEntity>> = historyDao.observeRecent(limit)

    fun bookmarks(): Flow<List<BookmarkEntity>> = bookmarkDao.observeAll()

    fun isBookmarked(url: String): Flow<Boolean> = bookmarkDao.observeIsBookmarked(url)

    /**
     * Records a visit and returns the history entry id. Re-visiting the most recent
     * URL only refreshes its timestamp so redirects/back-forward don't spam history.
     */
    suspend fun recordVisit(url: String, title: String): Long {
        val now = clock()
        val latest = historyDao.latest()
        if (latest != null && latest.url == url) {
            historyDao.touch(latest.id, now)
            return latest.id
        }
        return historyDao.insert(HistoryEntity(url = url, title = title, visitedAt = now))
    }

    suspend fun updateHistoryTitle(id: Long, title: String) = historyDao.updateTitle(id, title)

    suspend fun deleteHistory(id: Long) = historyDao.delete(id)

    suspend fun clearHistory() = historyDao.clear()

    suspend fun addBookmark(url: String, title: String) =
        bookmarkDao.insert(BookmarkEntity(url = url, title = title, createdAt = clock()))

    suspend fun removeBookmark(url: String) = bookmarkDao.deleteByUrl(url)

    private companion object {
        const val HISTORY_LIMIT = 500
    }
}
