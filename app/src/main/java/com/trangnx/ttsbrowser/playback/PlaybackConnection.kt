package com.trangnx.ttsbrowser.playback

import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.trangnx.ttsbrowser.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-side entry to playback: loads chapters into [TtsPlayer], publishes its state for the UI and
 * sends commands through a [MediaController], which binds [PlaybackService] so the session and
 * notification exist. [release] unbinds (task removed), so the service can stop once playback is
 * not ongoing. Main thread only.
 */
@OptIn(UnstableApi::class)
class PlaybackConnection(context: Context, private val player: TtsPlayer) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val _loaded = MutableStateFlow<LoadedChapter?>(null)
    val loaded: StateFlow<LoadedChapter?> = _loaded.asStateFlow()

    // Read from the player itself (same process): accurate even while no controller is connected.
    private val _state = MutableStateFlow(player.toUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                _state.value = player.toUiState()
            }
        })
    }

    /** Whether [chapter] is the loaded chapter (same page, same sentences). */
    fun isLoaded(chapter: LoadedChapter): Boolean {
        val current = _loaded.value ?: return false
        return current.chapter.url == chapter.chapter.url && current.sentences == chapter.sentences
    }

    /**
     * Makes [chapter] the player's queue (at [startIndex]), keeping the play/pause state. Already
     * loaded: only its metadata (title, links, source) is refreshed and the position is kept.
     */
    fun load(chapter: LoadedChapter, startIndex: Int = 0) {
        val changed = !isLoaded(chapter)
        // First, so a listener never sees the new chapter's sentence index next to the old chapter.
        _loaded.value = chapter
        if (changed) player.setChapter(chapter.chapter, chapter.sentences, startIndex)
    }

    fun togglePlayPause() = withController(retry = true) { Util.handlePlayPauseButtonAction(it) }

    fun pause() = withController(retry = true) { it.pause() }

    /** Reads from sentence [index]. */
    fun playFrom(index: Int) = withController(retry = true) {
        if (it.playbackState == Player.STATE_IDLE) it.prepare()
        it.seekTo(index, 0L)
        it.play()
    }

    // By index, not seekToPrevious/NextMediaItem: the session maps those to chapter skipping.
    fun previousSentence() = withController(retry = true) { seekSentence(it, -1) }

    fun nextSentence() = withController(retry = true) { seekSentence(it, 1) }

    private fun seekSentence(controller: MediaController, delta: Int) {
        val target = controller.currentMediaItemIndex + delta
        if (target in 0 until controller.mediaItemCount) controller.seekTo(target, 0L)
    }

    fun release() {
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
    }

    private fun withController(retry: Boolean, action: (MediaController) -> Unit) {
        val future = controllerFuture ?: connect()
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull()
                if (controller?.isConnected == true) {
                    action(controller)
                    return@addListener
                }
                // Failed, rejected or disconnected (onDisconnected may have dropped it already).
                drop(future)
                if (retry) {
                    withController(retry = false, action)
                } else {
                    Toast.makeText(appContext, R.string.playback_error_connect, Toast.LENGTH_LONG).show()
                }
            },
            mainExecutor,
        )
    }

    private fun connect(): ListenableFuture<MediaController> {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        lateinit var future: ListenableFuture<MediaController>
        future = MediaController.Builder(appContext, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) = drop(future)
            })
            .buildAsync()
        controllerFuture = future
        return future
    }

    /** Forgets [future] if it is still the current one, releasing its controller. */
    private fun drop(future: ListenableFuture<MediaController>) {
        if (controllerFuture !== future) return
        controllerFuture = null
        MediaController.releaseFuture(future)
    }
}
