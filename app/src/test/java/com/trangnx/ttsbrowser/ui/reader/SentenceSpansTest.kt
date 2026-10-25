package com.trangnx.ttsbrowser.ui.reader

import com.trangnx.ttsbrowser.text.Sentence
import com.trangnx.ttsbrowser.text.SentenceSegmenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSpansTest {

    @Test
    fun spansAreLocalToTheirParagraph() {
        val paragraphs = listOf("Trời đã tối. Hắn vẫn ngồi đó!", "", "Nàng hỏi: \"Huynh đi đâu?\" Hắn không đáp.")
        val sentences = SentenceSegmenter().segment(paragraphs)
        val spans = sentenceSpansByParagraph(paragraphs, sentences)

        assertEquals(listOf(listOf(0, 1), emptyList(), listOf(2, 3)), spans.map { list -> list.map { it.index } })
        spans.forEachIndexed { paragraph, list ->
            list.forEach { span ->
                assertEquals(sentences[span.index].text, paragraphs[paragraph].substring(span.start, span.end))
            }
        }
    }

    @Test
    fun longSentenceCutInPieces_coversTheParagraph() {
        val paragraph = List(15) { "hắn chạy mãi không dừng lại giữa rừng sâu" }.joinToString(", ") + "."
        val sentences = SentenceSegmenter().segment(listOf(paragraph))
        val spans = sentenceSpansByParagraph(listOf(paragraph), sentences).single()

        assertTrue(spans.size > 1)
        assertEquals(0, spans.first().start)
        assertEquals(paragraph.length, spans.last().end)
    }

    @Test
    fun sentencesOutsideTheText_areSkipped() {
        val paragraphs = listOf("Một câu.")
        val sentences = listOf(
            Sentence(index = 0, text = "Một câu.", paragraphIndex = 0, start = 0),
            Sentence(index = 1, text = "Câu cũ dài hơn đoạn.", paragraphIndex = 0, start = 4),
            Sentence(index = 2, text = "Lạc.", paragraphIndex = 3, start = 0),
        )
        assertEquals(listOf(listOf(SentenceSpan(0, 0, 8))), sentenceSpansByParagraph(paragraphs, sentences))
    }
}
