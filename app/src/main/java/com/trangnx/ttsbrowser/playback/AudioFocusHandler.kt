package com.trangnx.ttsbrowser.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/** Audio focus for speech: calls and other players pause us; ducking is treated as a transient loss. */
class AudioFocusHandler(context: Context, private val onChange: (FocusChange) -> Unit) {

    enum class FocusChange { Gain, TransientLoss, Loss }

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var held = false

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        // Lowered speech is hard to follow: get LOSS_TRANSIENT instead of ducking.
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener(::onFocusChange, Handler(Looper.getMainLooper()))
        .build()

    /** True if focus is held (already or newly granted). */
    fun request(): Boolean {
        if (!held) held = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return held
    }

    fun abandon() {
        if (!held) return
        audioManager.abandonAudioFocusRequest(request)
        held = false
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> onChange(FocusChange.Gain)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onChange(FocusChange.TransientLoss)
            AudioManager.AUDIOFOCUS_LOSS -> {
                held = false
                onChange(FocusChange.Loss)
            }
        }
    }
}
