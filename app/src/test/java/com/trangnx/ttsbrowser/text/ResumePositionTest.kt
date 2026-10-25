package com.trangnx.ttsbrowser.text

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumePositionTest {

    private val paragraphs = listOf("Câu một. Câu hai.", "Câu ba. Câu bốn.")
    private val sentences = SentenceSegmenter().segment(paragraphs)

    @Test
    fun keepsSavedIndexWhenSentenceStillStartsAtOffset() {
        val saved = sentences[2]
        assertEquals(2, sentences.resumeIndex(saved.index, saved.start))
    }

    @Test
    fun usesTheSentenceCoveringTheOffsetWhenSplittingChanged() {
        // Saved with a different segmentation: index 0 and an offset inside the second paragraph.
        val saved = sentences[3]
        assertEquals(3, sentences.resumeIndex(sentenceIndex = 0, charOffset = saved.start + 2))
    }

    @Test
    fun offsetPastTheEndResumesAtTheLastSentence() {
        assertEquals(sentences.lastIndex, sentences.resumeIndex(sentenceIndex = 99, charOffset = 10_000))
    }

    @Test
    fun offsetBeforeTheFirstSentenceResumesAtTheStart() {
        assertEquals(0, sentences.resumeIndex(sentenceIndex = 5, charOffset = -1))
    }

    @Test
    fun emptyChapterResumesAtZero() {
        assertEquals(0, emptyList<Sentence>().resumeIndex(3, 40))
    }
}
