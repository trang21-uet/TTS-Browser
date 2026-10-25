package com.trangnx.ttsbrowser.text

import org.junit.Assert.assertEquals
import org.junit.Test

class BookTitleSuggesterTest {

    @Test
    fun dropsChapterAndSiteName() {
        assertEquals(
            "Tên Truyện",
            BookTitleSuggester.suggest("Chương 12: Gặp gỡ - Tên Truyện - Akay Truyện", "Chương 12: Gặp gỡ"),
        )
    }

    @Test
    fun storyBeforeChapter() {
        assertEquals("Tên Truyện", BookTitleSuggester.suggest("Tên Truyện - Chương 7", "Chương 7"))
        assertEquals("Tên Truyện", BookTitleSuggester.suggest("Tên Truyện | Chương 7 | Site", "Chương 7"))
    }

    @Test
    fun storyAfterChapterWithoutSite() {
        assertEquals("Tên Truyện", BookTitleSuggester.suggest("Chapter 3 – Tên Truyện", "Chapter 3"))
    }

    @Test
    fun matchesTheChapterByItsHeadingToo() {
        assertEquals("Tên Truyện", BookTitleSuggester.suggest("Gặp gỡ - Tên Truyện - Site", "Gặp gỡ"))
    }

    @Test
    fun hyphenInsideAWordIsNotASeparator() {
        assertEquals("Tiên-Ma Lục", BookTitleSuggester.suggest("Chương 1 - Tiên-Ma Lục", "Chương 1"))
    }

    @Test
    fun fallsBackToTheFirstSegmentThenTheChapterTitle() {
        assertEquals("Chương 5: Mở đầu", BookTitleSuggester.suggest("Chương 5: Mở đầu", "Chương 5: Mở đầu"))
        assertEquals("Chương 9", BookTitleSuggester.suggest("", "Chương 9"))
    }
}
