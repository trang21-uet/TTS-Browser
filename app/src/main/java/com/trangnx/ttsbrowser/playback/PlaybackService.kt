package com.trangnx.ttsbrowser.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.trangnx.ttsbrowser.MainActivity
import com.trangnx.ttsbrowser.TtsBrowserApp

/**
 * Foreground media service: exposes the app-wide [TtsPlayer] through a MediaSession, which drives
 * the notification, lock screen and headset buttons. Media3 promotes it to the foreground while playing.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = (application as TtsBrowserApp).container.ttsPlayer
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(openApp).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The app's own controller keeps the service bound; without the UI it is not needed.
        (application as TtsBrowserApp).container.playbackConnection.release()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        session?.run {
            // The player outlives the service (app-wide); stopping frees the TTS engine.
            player.stop()
            release()
        }
        session = null
        super.onDestroy()
    }
}
