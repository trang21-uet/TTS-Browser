package com.trangnx.ttsbrowser.ui.reader

import com.trangnx.ttsbrowser.text.Sentence
import com.trangnx.ttsbrowser.text.SentenceSegmenter

/** Sentence [index] covers `[start, end)` of its paragraph's text. */
data class SentenceSpan(val index: Int, val start: Int, val end: Int)

/**
 * Sentences grouped by paragraph, with offsets local to the paragraph ([Sentence.start] counts in the
 * paragraphs joined by [SentenceSegmenter.PARAGRAPH_SEPARATOR]). Sentences that do not fit are skipped.
 */
fun sentenceSpansByParagraph(paragraphs: List<String>, sentences: List<Sentence>): List<List<SentenceSpan>> {
    val paragraphStarts = IntArray(paragraphs.size)
    var base = 0
    paragraphs.forEachIndexed { i, paragraph ->
        paragraphStarts[i] = base
        base += paragraph.length + SentenceSegmenter.PARAGRAPH_SEPARATOR.length
    }
    val result = List(paragraphs.size) { ArrayList<SentenceSpan>() }
    for (sentence in sentences) {
        val paragraph = sentence.paragraphIndex
        if (paragraph !in paragraphs.indices) continue
        val start = sentence.start - paragraphStarts[paragraph]
        val end = start + sentence.text.length
        if (start < 0 || end > paragraphs[paragraph].length) continue
        result[paragraph] += SentenceSpan(sentence.index, start, end)
    }
    return result
}
