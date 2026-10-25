package com.trangnx.ttsbrowser.playback

import com.trangnx.ttsbrowser.extraction.Chapter
import com.trangnx.ttsbrowser.extraction.ContentSource
import com.trangnx.ttsbrowser.text.SentenceSegmenter
import org.junit.Assert.assertEquals
import org.junit.Test

class SentencePlaylistTest {

    private val chapter = Chapter(
        url = "https://example.com/truyen/chuong-1",
        title = "Chương 1: Mở đầu",
        paragraphs = listOf("Câu một. Câu hai.", "Câu ba."),
        prevUrl = null,
        nextUrl = null,
        source = ContentSource.Readability,
    )

    @Test
    fun oneItemPerSentenceWithUniqueIdsAndPosition() {
        val sentences = SentenceSegmenter().segment(chapter.paragraphs)
        val playlist = buildSentencePlaylist(chapter, sentences) { n, total -> "$n/$total" }

        assertEquals(3, playlist.size)
        assertEquals(3, playlist.map { it.uid }.toSet().size)
        assertEquals("Chương 1: Mở đầu", playlist[0].mediaItem.mediaMetadata.title)
        assertEquals(listOf("1/3", "2/3", "3/3"), playlist.map { it.mediaItem.mediaMetadata.artist.toString() })
    }
}
