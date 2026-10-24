package com.trangnx.ttsbrowser.extraction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterLinkFinderTest {

    private val base = "https://akaytruyen.com"

    /** Synthetic page shaped like akaytruyen.com: icon-only nav buttons sharing `btn-back-next`. */
    private fun akayAnchors(prev: String?, next: String?, chapterList: List<Int>) = buildList {
        add(LinkCandidate("$base/", text = "Trang chủ"))
        add(LinkCandidate("$base/the-loai/tien-hiep", text = "Tiên Hiệp"))
        add(LinkCandidate("$base/truyen/ten-truyen", text = "Tên Truyện"))
        add(LinkCandidate("javascript:void(0)", text = "Báo lỗi"))
        repeat(2) { // nav is rendered above and below the content
            prev?.let { add(LinkCandidate(it, attrs = " btn btn-back-next", icons = "fa fa-chevron-left")) }
            next?.let { add(LinkCandidate(it, attrs = " btn btn-back-next", icons = "fa fa-chevron-right")) }
        }
        chapterList.forEach { add(LinkCandidate("$base/ten-truyen/chuong-$it-tieu-de", text = "Chương $it: Tiêu đề")) }
        add(LinkCandidate("$base/truyen/sau-khi-xuyen-khong", text = "Sau khi xuyên không ta thành đại lão"))
    }

    @Test
    fun akaytruyen_iconOnlyNav_findsBothLinks() {
        val page = "$base/ten-truyen/chuong-5-hello"
        val prev = "$base/ten-truyen/chuong-4-abc"
        val next = "$base/ten-truyen/chuong-6-xyz"
        val result = ChapterLinkFinder.find(page, PageLinks(anchors = akayAnchors(prev, next, listOf(1, 2, 3, 7))))
        assertEquals(prev, result.prevUrl)
        assertEquals(next, result.nextUrl)
    }

    @Test
    fun akaytruyen_lastChapter_hasNoNext() {
        // The "next" control is a disabled <button>, so it is not collected at all.
        val page = "$base/ten-truyen/chuong-10-ket"
        val prev = "$base/ten-truyen/chuong-9-abc"
        val result = ChapterLinkFinder.find(page, PageLinks(anchors = akayAnchors(prev, next = null, listOf(1, 2, 3))))
        assertEquals(prev, result.prevUrl)
        assertNull(result.nextUrl)
    }

    @Test
    fun textLinks_vietnamese() {
        val anchors = listOf(
            LinkCandidate("https://site.vn/a/2", text = "« Chương trước"),
            LinkCandidate("https://site.vn/a/muc-luc", text = "Mục lục"),
            LinkCandidate("https://site.vn/a/4", text = "Chương sau »"),
        )
        val result = ChapterLinkFinder.find("https://site.vn/a/3", PageLinks(anchors = anchors))
        assertEquals("https://site.vn/a/2", result.prevUrl)
        assertEquals("https://site.vn/a/4", result.nextUrl)
    }

    @Test
    fun relLinks_win() {
        val links = PageLinks(
            relNext = "https://site.vn/c/next-by-rel",
            relPrev = "https://site.vn/c/prev-by-rel",
            anchors = listOf(LinkCandidate("https://site.vn/c/other", text = "Next")),
        )
        val result = ChapterLinkFinder.find("https://site.vn/c/now", links)
        assertEquals("https://site.vn/c/next-by-rel", result.nextUrl)
        assertEquals("https://site.vn/c/prev-by-rel", result.prevUrl)
    }

    @Test
    fun classTokens_andDisabled() {
        val anchors = listOf(
            LinkCandidate("https://site.vn/x/prev", attrs = "chap-prev"),
            LinkCandidate("https://site.vn/x/next-disabled", attrs = "chap-next", disabled = true),
        )
        val result = ChapterLinkFinder.find("https://site.vn/x/now", PageLinks(anchors = anchors))
        assertEquals("https://site.vn/x/prev", result.prevUrl)
        assertNull(result.nextUrl)
    }

    @Test
    fun ignoresSamePageAndTitlesStartingWithSau() {
        val anchors = listOf(
            LinkCandidate("https://site.vn/x/now#comments", text = "Chương sau"),
            LinkCandidate("https://site.vn/truyen/sau-con-mua", text = "Sau cơn mưa trời lại sáng"),
        )
        val result = ChapterLinkFinder.find("https://site.vn/x/now", PageLinks(anchors = anchors))
        assertNull(result.nextUrl)
        assertNull(result.prevUrl)
    }

    @Test
    fun urlChapterNumber_aloneIsEnough() {
        val anchors = listOf(
            LinkCandidate("https://site.vn/t/chuong-11", text = "11"),
            LinkCandidate("https://site.vn/t/chuong-13", text = "13"),
            LinkCandidate("https://site.vn/t/chuong-20", text = "20"),
        )
        val result = ChapterLinkFinder.find("https://site.vn/t/chuong-12", PageLinks(anchors = anchors))
        assertEquals("https://site.vn/t/chuong-11", result.prevUrl)
        assertEquals("https://site.vn/t/chuong-13", result.nextUrl)
    }

    @Test
    fun chapterRefOf_parsesCommonPatterns() {
        assertEquals(
            ChapterLinkFinder.ChapterRef("a.com/truyen-x/", 12),
            ChapterLinkFinder.chapterRefOf("https://A.com/truyen-x/chuong-12-ten"),
        )
        assertEquals(ChapterLinkFinder.ChapterRef("a.com/book/", 7), ChapterLinkFinder.chapterRefOf("https://a.com/book/chapter_7.html"))
        assertNull(ChapterLinkFinder.chapterRefOf("https://a.com/truyen/ten-truyen"))
        // "ch" inside a word, or in the host, is not a chapter marker.
        assertNull(ChapterLinkFinder.chapterRefOf("https://sach24.vn/truyen/abc/"))
        assertNull(ChapterLinkFinder.chapterRefOf("https://a.com/watch10/abc"))
    }

    @Test
    fun otherStoryWithAdjacentNumber_isNotNext() {
        val anchors = listOf(
            LinkCandidate("https://site.vn/truyen-a/chuong-49", attrs = "btn", icons = "fa fa-chevron-left"),
            LinkCandidate("https://site.vn/truyen-b/chuong-51", text = "Chương 51: Truyện khác"),
        )
        val result = ChapterLinkFinder.find("https://site.vn/truyen-a/chuong-50", PageLinks(anchors = anchors))
        assertEquals("https://site.vn/truyen-a/chuong-49", result.prevUrl)
        assertNull(result.nextUrl)
    }

    @Test
    fun hostLookingLikeChapter_doesNotPenalizeNavLinks() {
        val anchors = listOf(LinkCandidate("https://sach24.vn/truyen/abc/trang-2", text = "Sau »"))
        val result = ChapterLinkFinder.find("https://sach24.vn/truyen/abc/trang-1", PageLinks(anchors = anchors))
        assertEquals("https://sach24.vn/truyen/abc/trang-2", result.nextUrl)
    }
}
