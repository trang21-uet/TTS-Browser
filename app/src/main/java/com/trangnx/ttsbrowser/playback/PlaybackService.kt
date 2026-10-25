package com.trangnx.ttsbrowser.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.trangnx.ttsbrowser.MainActivity
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.TtsBrowserApp
import com.trangnx.ttsbrowser.di.AppContainer
import com.trangnx.ttsbrowser.extraction.ExtractionResult
import com.trangnx.ttsbrowser.ui.browser.message
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground media service: exposes the app-wide [TtsPlayer] through a MediaSession, which drives
 * the notification, lock screen and headset buttons. Media3 promotes it to the foreground while playing.
 * Adds a "next chapter" button (loaded headless, see [NextChapterLoader]) while the chapter has one.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = MainScope()
    private val container: AppContainer get() = (application as TtsBrowserApp).container

    override fun onCreate() {
        super.onCreate()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val session = MediaSession.Builder(this, container.ttsPlayer)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()
        this.session = session
        scope.launch {
            container.playbackConnection.loaded
                .map { it?.chapter?.nextUrl != null }
                .distinctUntilChanged()
                .collect { hasNext -> session.setMediaButtonPreferences(if (hasNext) listOf(nextChapterButton()) else emptyList()) }
        }
    }

    private fun nextChapterButton(): CommandButton =
        CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD)
            .setSessionCommand(NEXT_CHAPTER)
            .setDisplayName(getString(R.string.extract_next))
            .build()

    private fun showNextChapterFailure(reason: ExtractionResult.Reason?) {
        Toast.makeText(this, reason?.message ?: R.string.next_chapter_none, Toast.LENGTH_LONG).show()
    }

    /** The saved rate is the only source of speed (the app sets it on the player directly): controllers cannot change it. */
    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(
                    TTS_PLAYER_COMMANDS.buildUpon().remove(Player.COMMAND_SET_SPEED_AND_PITCH).build(),
                )
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(NEXT_CHAPTER).build(),
                )
                .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != NEXT_CHAPTER.customAction) {
                return super.onCustomCommand(session, controller, customCommand, args)
            }
            container.nextChapterLoader.openNext(::showNextChapterFailure)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
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
        scope.cancel()
        session?.run {
            // The player outlives the service (app-wide); stopping frees the TTS engine.
            player.stop()
            release()
        }
        session = null
        super.onDestroy()
    }

    private companion object {
        val NEXT_CHAPTER = SessionCommand("com.trangnx.ttsbrowser.NEXT_CHAPTER", Bundle.EMPTY)
    }
}
