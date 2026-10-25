package com.trangnx.ttsbrowser.playback

import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.extraction.Chapter
import com.trangnx.ttsbrowser.text.Sentence

/**
 * App-side entry to playback: loads a chapter into [TtsPlayer] and plays it through a
 * [MediaController], which binds [PlaybackService] so the session and notification exist.
 * [release] unbinds (task removed), so the service can stop once playback is not ongoing.
 * Main thread only.
 */
class PlaybackConnection(context: Context, private val player: TtsPlayer) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private var controllerFuture: ListenableFuture<MediaController>? = null

    fun play(chapter: Chapter, sentences: List<Sentence>, startIndex: Int = 0) {
        player.setChapter(chapter, sentences, startIndex)
        withController(retry = true) { it.play() }
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
                when {
                    controller?.isConnected == true -> action(controller)
                    controllerFuture !== future -> Unit // released meanwhile
                    else -> {
                        drop(future)
                        if (retry) {
                            withController(retry = false, action)
                        } else {
                            Toast.makeText(appContext, R.string.playback_error_connect, Toast.LENGTH_LONG).show()
                        }
                    }
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
        future.addListener({ runCatching { future.get() }.getOrNull()?.addListener(errorToast) }, mainExecutor)
        controllerFuture = future
        return future
    }

    /** Forgets [future] if it is still the current one, releasing its controller. */
    private fun drop(future: ListenableFuture<MediaController>) {
        if (controllerFuture !== future) return
        controllerFuture = null
        MediaController.releaseFuture(future)
    }

    // Until the reader screen (Phase 5) shows player errors.
    private val errorToast = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Toast.makeText(appContext, error.message, Toast.LENGTH_LONG).show()
        }
    }
}
