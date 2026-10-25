package com.trangnx.ttsbrowser.playback

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import com.trangnx.ttsbrowser.extraction.Chapter
import com.trangnx.ttsbrowser.text.Sentence

/** The chapter loaded into the player, shown by the reader and the mini player. */
data class LoadedChapter(val chapter: Chapter, val sentences: List<Sentence>)

/** What the reader and the mini player show about the player. */
data class PlaybackUiState(
    val sentenceIndex: Int = 0,
    /** Same rule as the notification: play while paused, ended, idle or suppressed. */
    val showPlay: Boolean = true,
    val isBuffering: Boolean = false,
    /** The whole chapter has been read. */
    val isEnded: Boolean = false,
    val error: String? = null,
)

@OptIn(UnstableApi::class)
internal fun Player.toUiState() = PlaybackUiState(
    sentenceIndex = currentMediaItemIndex,
    showPlay = Util.shouldShowPlayButton(this),
    isBuffering = playbackState == Player.STATE_BUFFERING,
    isEnded = playbackState == Player.STATE_ENDED,
    error = playerError?.message,
)
