package com.trangnx.ttsbrowser.playback

import android.app.PendingIntent
import android.content.Intent
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.trangnx.ttsbrowser.MainActivity
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.TtsBrowserApp
import com.trangnx.ttsbrowser.di.AppContainer
import com.trangnx.ttsbrowser.extraction.ExtractionResult
import com.trangnx.ttsbrowser.ui.browser.message

/**
 * Foreground media service: exposes the app-wide [TtsPlayer] through a MediaSession, which drives
 * the notification, lock screen and headset buttons. Media3 promotes it to the foreground while playing.
 * Previous/next there open the adjacent chapter (loaded headless, see [AdjacentChapterLoader]).
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val container: AppContainer get() = (application as TtsBrowserApp).container

    override fun onCreate() {
        super.onCreate()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val chapters = container.adjacentChapterLoader
        val player = ChapterSkippingPlayer(
            container.ttsPlayer,
            onPreviousChapter = { chapters.openPrevious { showChapterFailure(it, R.string.previous_chapter_none) } },
            onNextChapter = { chapters.openNext { showChapterFailure(it, R.string.next_chapter_none) } },
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()
    }

    private fun showChapterFailure(reason: ExtractionResult.Reason?, noChapter: Int) {
        Toast.makeText(this, reason?.message ?: noChapter, Toast.LENGTH_LONG).show()
    }

    /** The saved rate is the only source of speed (the app sets it on the player directly): controllers cannot change it. */
    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(
                    TTS_PLAYER_COMMANDS.buildUpon().remove(Player.COMMAND_SET_SPEED_AND_PITCH).build(),
                )
                .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        container.progressTracker.flush()
        // The app's own controller keeps the service bound; without the UI it is not needed.
        container.playbackConnection.release()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        container.progressTracker.flush()
        session?.run {
            // The player outlives the service (app-wide); stopping frees the TTS engine.
            player.stop()
            release()
        }
        session = null
        super.onDestroy()
    }
}
