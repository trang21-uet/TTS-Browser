package com.trangnx.ttsbrowser.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * What the MediaSession exposes: "next" / "previous" (notification, lock screen, headset buttons)
 * move between chapters instead of sentences. Sentence seeks by index ([Player.seekTo]) still reach
 * [TtsPlayer] unchanged, which is what the in-app sentence buttons use.
 */
@OptIn(UnstableApi::class)
class ChapterSkippingPlayer(
    player: Player,
    private val onPreviousChapter: () -> Unit,
    private val onNextChapter: () -> Unit,
) : ForwardingPlayer(player) {
    override fun seekToPrevious() = onPreviousChapter()

    override fun seekToPreviousMediaItem() = onPreviousChapter()

    override fun seekToNext() = onNextChapter()

    override fun seekToNextMediaItem() = onNextChapter()
}
