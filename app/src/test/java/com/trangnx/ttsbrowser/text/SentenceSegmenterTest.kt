package com.trangnx.ttsbrowser.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSegmenterTest {

    private val segmenter = SentenceSegmenter()

    @Test
    fun splitsSentences_withParagraphIndexAndOffsets() {
        val paragraphs = listOf("Trời đã tối. Hắn vẫn ngồi đó!", "Nàng hỏi: \"Huynh đi đâu?\" Hắn không đáp.")
        val sentences = segmenter.segment(paragraphs)
        assertEquals(
            listOf("Trời đã tối.", "Hắn vẫn ngồi đó!", "Nàng hỏi: \"Huynh đi đâu?\"", "Hắn không đáp."),
            sentences.map { it.text },
        )
        assertEquals(listOf(0, 0, 1, 1), sentences.map { it.paragraphIndex })
        assertEquals(listOf(0, 1, 2, 3), sentences.map { it.index })
        val joined = paragraphs.joinToString(SentenceSegmenter.PARAGRAPH_SEPARATOR)
        sentences.forEach { assertEquals(it.text, joined.substring(it.start, it.start + it.text.length)) }
    }

    @Test
    fun longSentence_isCutAtComma() {
        val clause = "hắn chạy mãi không dừng lại giữa rừng sâu"
        val long = List(12) { clause }.joinToString(", ") + "."
        val sentences = segmenter.segment(listOf(long))
        assertTrue(sentences.size > 1)
        sentences.forEach { assertTrue(it.text, it.text.length <= SentenceSegmenter.DEFAULT_MAX_LENGTH) }
        assertTrue(sentences.dropLast(1).all { it.text.endsWith(",") })
        assertEquals(long, sentences.joinToString(" ") { it.text })
    }

    @Test
    fun longSentence_withoutPunctuation_isCutAtSpaces() {
        val text = List(80) { "chữ" }.joinToString(" ")
        val pieces = SentenceSegmenter(maxLength = 50).segment(listOf(text))
        pieces.forEach { assertTrue(it.text, it.text.length <= 50 && it.text == it.text.trim()) }
        assertEquals(text, pieces.joinToString(" ") { it.text })
    }

    @Test
    fun dropsPiecesWithoutLettersOrDigits() {
        val sentences = segmenter.segment(listOf("... Xong."))
        assertEquals(listOf("Xong."), sentences.map { it.text })
    }
}
