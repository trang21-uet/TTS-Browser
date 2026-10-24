package com.trangnx.ttsbrowser.text

import java.text.BreakIterator
import java.util.Locale

/**
 * One TTS utterance. [start] is the char offset in the chapter text, i.e. the paragraphs
 * joined with [SentenceSegmenter.PARAGRAPH_SEPARATOR]; used to restore progress if segmentation changes.
 */
data class Sentence(val index: Int, val text: String, val paragraphIndex: Int, val start: Int)

/** Splits paragraphs into sentences; sentences longer than [maxLength] are cut at `,;:` or spaces. */
class SentenceSegmenter(
    private val locale: Locale = Locale.forLanguageTag("vi"),
    private val maxLength: Int = DEFAULT_MAX_LENGTH,
) {
    fun segment(paragraphs: List<String>): List<Sentence> {
        val result = ArrayList<Sentence>()
        var base = 0
        // BreakIterator is stateful and not thread-safe: one instance per call.
        val iterator = BreakIterator.getSentenceInstance(locale)
        paragraphs.forEachIndexed { paragraphIndex, paragraph ->
            iterator.setText(paragraph)
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                for ((from, to) in splitLong(paragraph, start, end)) {
                    val text = paragraph.substring(from, to)
                    if (text.any { it.isLetterOrDigit() }) {
                        result += Sentence(result.size, text, paragraphIndex, base + from)
                    }
                }
                start = end
                end = iterator.next()
            }
            base += paragraph.length + PARAGRAPH_SEPARATOR.length
        }
        return result
    }

    /** Trimmed `[from, to)` ranges of `text[start, end)`, each at most [maxLength] long. */
    private fun splitLong(text: String, start: Int, end: Int): List<Pair<Int, Int>> {
        val ranges = ArrayList<Pair<Int, Int>>()
        var from = skipSpaces(text, start, end)
        while (from < end) {
            var to = trimEnd(text, from, end)
            if (to - from > maxLength) to = cutPoint(text, from)
            val trimmedTo = trimEnd(text, from, to)
            if (trimmedTo > from) ranges += from to trimmedTo
            from = skipSpaces(text, to, end)
        }
        return ranges
    }

    /** End of the first piece of a too-long range starting at [from]. */
    private fun cutPoint(text: String, from: Int): Int {
        val limit = from + maxLength
        val minCut = from + maxLength / MIN_PIECE_DIVISOR
        for (i in limit downTo minCut + 1) {
            if (text[i - 1] in CLAUSE_PUNCTUATION) return i
        }
        for (i in limit downTo minCut + 1) {
            if (text[i].isWhitespace()) return i
        }
        return limit
    }

    private fun skipSpaces(text: String, from: Int, end: Int): Int {
        var i = from
        while (i < end && text[i].isWhitespace()) i++
        return i
    }

    private fun trimEnd(text: String, from: Int, to: Int): Int {
        var i = to
        while (i > from && text[i - 1].isWhitespace()) i--
        return i
    }

    companion object {
        const val PARAGRAPH_SEPARATOR = "\n\n"
        const val DEFAULT_MAX_LENGTH = 300
        private const val MIN_PIECE_DIVISOR = 3
        private val CLAUSE_PUNCTUATION = setOf(',', ';', ':', '，', '、')
    }
}
