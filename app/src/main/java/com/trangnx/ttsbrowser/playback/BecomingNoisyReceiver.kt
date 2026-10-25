package com.trangnx.ttsbrowser.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat

/** Calls [onNoisy] when audio is about to go to the speaker (headphones unplugged, Bluetooth lost). */
class BecomingNoisyReceiver(context: Context, private val onNoisy: () -> Unit) {
    private val appContext = context.applicationContext
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onNoisy()
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled == registered) return
        if (enabled) {
            // System broadcasts still reach non-exported receivers.
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } else {
            appContext.unregisterReceiver(receiver)
        }
        registered = enabled
    }
}
