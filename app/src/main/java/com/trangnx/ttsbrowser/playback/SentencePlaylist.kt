package com.trangnx.ttsbrowser.playback

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.SimpleBasePlayer.MediaItemData
import androidx.media3.common.util.UnstableApi
import com.trangnx.ttsbrowser.extraction.Chapter
import com.trangnx.ttsbrowser.text.Sentence

/**
 * One media item per sentence, so next/previous on the notification, lock screen and headset
 * move by sentence and `seekTo(index)` jumps to a sentence. [positionLabel] gets (number, total).
 */
@OptIn(UnstableApi::class)
fun buildSentencePlaylist(
    chapter: Chapter,
    sentences: List<Sentence>,
    positionLabel: (Int, Int) -> String,
): List<MediaItemData> = sentences.map { sentence ->
    val id = "${chapter.url}#${sentence.index}"
    val metadata = MediaMetadata.Builder()
        .setTitle(chapter.title)
        .setArtist(positionLabel(sentence.index + 1, sentences.size))
        .build()
    MediaItemData.Builder(id)
        .setMediaItem(MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build())
        .build()
}
