package com.trangnx.ttsbrowser.download

import com.trangnx.ttsbrowser.data.db.OfflineChapterCount
import com.trangnx.ttsbrowser.data.db.OfflineChapterDao
import com.trangnx.ttsbrowser.data.db.OfflineChapterEntity
import com.trangnx.ttsbrowser.data.repo.OfflineChapterRepository
import com.trangnx.ttsbrowser.extraction.Chapter
import com.trangnx.ttsbrowser.extraction.ChapterLoader
import com.trangnx.ttsbrowser.extraction.ContentSource
import com.trangnx.ttsbrowser.extraction.ExtractionResult
import com.trangnx.ttsbrowser.extraction.OfflineFirstChapterLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory DAO; Unconfined scope + no pause make each download run to its end inside `start`. */
class ChapterDownloaderTest {
    private class FakeDao : OfflineChapterDao {
        val rows = MutableStateFlow<Map<String, OfflineChapterEntity>>(emptyMap())
        override suspend fun find(url: String) = rows.value.values.firstOrNull { it.url == url || it.requestedUrl == url }
        override suspend fun upsert(chapter: OfflineChapterEntity) {
            rows.value = rows.value + (chapter.url to chapter)
        }
        override fun observeCounts(): Flow<List<OfflineChapterCount>> =
            rows.map { all -> all.values.groupBy { it.bookId }.map { (id, list) -> OfflineChapterCount(id, list.size) } }
        override suspend fun deleteForBook(bookId: Long) {
            rows.value = rows.value.filterValues { it.bookId != bookId }
        }
    }

    /** Site with chapters 1..[last]; chapter n links to n+1. [failAt]: that chapter fails to load. */
    private class FakeSite(private val last: Int, private val failAt: Int? = null) : ChapterLoader {
        val loaded = mutableListOf<String>()
        override suspend fun load(url: String): ExtractionResult {
            loaded += url
            val n = url.substringAfterLast('-').toInt()
            if (n == failAt) return ExtractionResult.Failure(ExtractionResult.Reason.LoadFailed)
            return ExtractionResult.Success(chapter(n, if (n < last) url(n + 1) else null), emptyList(), selectorMissed = false)
        }
    }

    private val dao = FakeDao()
    private val store = OfflineChapterRepository(dao) { 42L }

    private fun downloader(site: ChapterLoader) =
        ChapterDownloader(CoroutineScope(Dispatchers.Unconfined), site, store, pauseMs = 0)

    @Test
    fun downloadsTheChosenNumberOfChaptersFollowingNextLinks() {
        val site = FakeSite(last = 10)
        val downloader = downloader(site)
        assertTrue(downloader.start(1, "Truyện", url(3), count = 4))
        assertEquals(listOf(url(3), url(4), url(5), url(6)), site.loaded)
        assertEquals(setOf(url(3), url(4), url(5), url(6)), dao.rows.value.keys)
        assertEquals(DownloadState.Finished(1, "Truyện", done = 4, reachedEnd = false), downloader.state.value)
    }

    @Test
    fun stopsAtTheLastChapter() {
        val downloader = downloader(FakeSite(last = 3))
        downloader.start(1, "T", url(2), count = 10)
        assertEquals(DownloadState.Finished(1, "T", done = 2, reachedEnd = true), downloader.state.value)
    }

    @Test
    fun reportsFailureWithChaptersDoneSoFar() {
        val downloader = downloader(FakeSite(last = 10, failAt = 3))
        downloader.start(1, "T", url(1), count = 5)
        assertEquals(
            DownloadState.Finished(1, "T", done = 2, reachedEnd = false, failure = ExtractionResult.Reason.LoadFailed),
            downloader.state.value,
        )
        assertEquals(2, dao.rows.value.size)
    }

    @Test
    fun alreadyDownloadedChaptersAreNotLoadedAgainButFollowed() {
        val first = FakeSite(last = 10)
        downloader(first).start(1, "T", url(1), count = 3)
        val second = FakeSite(last = 10)
        downloader(second).start(1, "T", url(1), count = 5)
        assertEquals(listOf(url(4), url(5)), second.loaded)
        assertEquals(5, dao.rows.value.size)
    }

    @Test
    fun storedLastChapterIsCheckedAgainForNewerChapters() {
        downloader(FakeSite(last = 2)).start(1, "T", url(1), count = 5)
        val updated = FakeSite(last = 4)
        val downloader = downloader(updated)
        downloader.start(1, "T", url(1), count = 5)
        assertEquals(listOf(url(2), url(3), url(4)), updated.loaded)
        assertEquals(DownloadState.Finished(1, "T", done = 4, reachedEnd = true), downloader.state.value)
    }

    @Test
    fun rejectsInvalidCount() {
        assertFalse(downloader(FakeSite(last = 3)).start(1, "T", url(1), count = 0))
    }

    @Test
    fun offlineFirstLoaderReadsDownloadedChapterWithoutTheSite() = runBlocking {
        downloader(FakeSite(last = 5)).start(1, "T", url(1), count = 2)
        val site = FakeSite(last = 5)
        val loader = OfflineFirstChapterLoader(store, site)

        val offline = loader.load(url(2)) as ExtractionResult.Success
        assertEquals(chapter(2, url(3)), offline.chapter)
        assertTrue(offline.sentences.isNotEmpty())
        assertTrue(site.loaded.isEmpty())

        loader.load(url(3))
        assertEquals(listOf(url(3)), site.loaded)
    }

    private companion object {
        fun url(n: Int) = "https://site.vn/truyen-a/chuong-$n"

        fun chapter(n: Int, next: String?) = Chapter(
            url = url(n),
            title = "Chương $n",
            paragraphs = listOf("Chương $n", "Đoạn một của chương $n.", "Đoạn hai."),
            prevUrl = if (n > 1) url(n - 1) else null,
            nextUrl = next,
            source = ContentSource.Readability,
            pageTitle = "Truyện A",
            startsWithTitle = true,
        )
    }
}
