package com.trangnx.ttsbrowser.extraction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCleanerTest {

    @Test
    fun splitsLines_collapsesWhitespace_dropsBlankLines() {
        val raw = "\n\n  Dòng   một có\tkhoảng trắng.  \n\n\n Dòng​ hai. \n"
        assertEquals(listOf("Dòng một có khoảng trắng.", "Dòng hai."), TextCleaner.paragraphs(raw))
    }

    @Test
    fun dropsNavigationAndSymbolLines() {
        val raw = listOf(
            "Chương trước | Mục lục | Chương sau",
            "« Chương trước",
            "***",
            "Hắn bước ra sau núi, trước mặt là vực sâu.",
            "Next »",
        ).joinToString("\n")
        assertEquals(listOf("Hắn bước ra sau núi, trước mặt là vực sâu."), TextCleaner.paragraphs(raw))
    }

    @Test
    fun dropsLeadingTitleRepeat() {
        val raw = "Chương 5: Gặp lại\nChương 5:  Gặp lại\nNội dung chương."
        assertEquals(listOf("Nội dung chương."), TextCleaner.paragraphs(raw, title = "Chương 5: Gặp lại"))
    }

    @Test
    fun dropsHashtagOnlyLines() {
        val raw = "Chương 104: LÊN HẾT ĐI!\n#CTCL #akay\n“Chào đón ta sao?”\nTa thích #hashtag giữa câu."
        assertEquals(
            listOf("“Chào đón ta sao?”", "Ta thích #hashtag giữa câu."),
            TextCleaner.paragraphs(raw, title = "Chương 104: LÊN HẾT ĐI!"),
        )
        assertTrue(TextCleaner.isJunk("#CTCL"))
        assertFalse(TextCleaner.isJunk("#1 là con số may mắn của hắn."))
    }

    @Test
    fun watermarkAndAdLines() {
        assertTrue(TextCleaner.isJunk("Bạn đang đọc truyện tại akaytruyen.com"))
        assertTrue(TextCleaner.isJunk("Nguồn: truyenfull.vn"))
        assertTrue(TextCleaner.isJunk("Quảng cáo"))
        assertFalse(TextCleaner.isJunk("Sau đó hắn đọc lại bức thư một lần nữa."))
        assertFalse(TextCleaner.isJunk("Trước khi đi, nàng dặn dò rất kỹ."))
        // A story sentence that mentions a domain and common words is kept.
        assertFalse(TextCleaner.isJunk("Hắn đứng tại quầy, nhìn tấm biển abc.vn rồi bỏ đi."))
        assertFalse(TextCleaner.isJunk("Nàng đọc lại tờ giấy ghi địa chỉ shop.com."))
    }
}
