package com.trangnx.ttsbrowser.playback

import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.extraction.Chapter
import com.trangnx.ttsbrowser.playback.AudioFocusHandler.FocusChange
import com.trangnx.ttsbrowser.text.Sentence
import com.trangnx.ttsbrowser.tts.TtsEngine
import com.trangnx.ttsbrowser.tts.TtsInitException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

/**
 * Media3 [Player] over a [TtsEngine]: one media item per sentence (see [buildSentencePlaylist]).
 * Lives for the app process (the UI loads chapters with [setChapter]); [PlaybackService] wraps it
 * in a MediaSession. The engine is created on first play and released on stop or error.
 */
@OptIn(UnstableApi::class)
class TtsPlayer(
    context: Context,
    engineFactory: () -> TtsEngine,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val appContext = context.applicationContext
    private val scope = MainScope()
    private val audioFocus = AudioFocusHandler(appContext, ::onAudioFocusChange)
    private val noisy = BecomingNoisyReceiver(appContext) { pause(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) }

    private var sentences: List<Sentence> = emptyList()
    private var playlist: List<MediaItemData> = emptyList()
    private var playlistMetadata = MediaMetadata.EMPTY
    private var index = 0
    private var playWhenReady = false
    private var playWhenReadyReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
    private var playbackState = Player.STATE_IDLE
    private var suppression = Player.PLAYBACK_SUPPRESSION_REASON_NONE
    private var error: PlaybackException? = null
    private var parameters = PlaybackParameters.DEFAULT

    private val speech = SpeechDriver(scope, engineFactory, SpeechListener())

    /** Engine/voice changes take effect immediately, preserving the sentence and play intent. */
    fun configureSpeech(rate: Float, pitch: Float, replaceEngine: Boolean) {
        val next = PlaybackParameters(rate, pitch)
        if (!replaceEngine && next == parameters) return
        parameters = next
        if (replaceEngine) speech.release()
        sync(restartSpeech = true)
    }

    fun setSpeechVolume(volume: Float) = speech.setVolume(volume)

    /** Replaces the queue with [chapter]; keeps the play/pause state. */
    fun setChapter(chapter: Chapter, sentences: List<Sentence>, startIndex: Int = 0) {
        speech.stopSpeaking()
        this.sentences = sentences
        playlist = buildSentencePlaylist(chapter, sentences) { number, total ->
            appContext.getString(R.string.playback_sentence_position, number, total)
        }
        playlistMetadata = MediaMetadata.Builder().setTitle(chapter.title).build()
        index = if (sentences.isEmpty()) 0 else startIndex.coerceIn(0, sentences.lastIndex)
        error = null
        playbackState = if (sentences.isEmpty()) Player.STATE_IDLE else Player.STATE_READY
        sync()
    }

    override fun getState(): State {
        val builder = State.Builder()
            .setAvailableCommands(TTS_PLAYER_COMMANDS)
            .setPlayWhenReady(playWhenReady, playWhenReadyReason)
            .setPlaybackState(playbackState)
            .setPlaybackSuppressionReason(suppression)
            .setPlayerError(error)
            .setPlaylist(playlist)
            .setPlaylistMetadata(playlistMetadata)
            // Constant: an extrapolated position would turn "previous" into "restart sentence" after 3 s.
            .setContentPositionMs { 0L }
            .setPlaybackParameters(parameters)
            .setAudioAttributes(SPEECH_AUDIO_ATTRIBUTES)
        if (playlist.isNotEmpty()) builder.setCurrentMediaItemIndex(index)
        return builder.build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        playWhenReadyReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
        sync(publish = false)
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        error = null
        if (sentences.isNotEmpty() && playbackState == Player.STATE_IDLE) playbackState = Player.STATE_READY
        sync(publish = false)
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        playbackState = Player.STATE_IDLE
        error = null
        speech.release()
        sync(publish = false)
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        playWhenReady = false
        speech.release()
        sync(publish = false)
        scope.cancel()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (sentences.isNotEmpty()) {
            // "Play" after the end seeks to the default position: start the chapter over.
            val restart = playbackState == Player.STATE_ENDED && seekCommand == Player.COMMAND_SEEK_TO_DEFAULT_POSITION
            index = if (restart) 0 else mediaItemIndex.coerceIn(0, sentences.lastIndex)
            if (playbackState == Player.STATE_ENDED) playbackState = Player.STATE_READY
            sync(restartSpeech = true, publish = false)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        parameters = PlaybackParameters(
            playbackParameters.speed.coerceIn(TtsEngine.MIN_RATE, TtsEngine.MAX_RATE),
            playbackParameters.pitch.coerceIn(TtsEngine.MIN_PITCH, TtsEngine.MAX_PITCH),
        )
        sync(restartSpeech = true, publish = false)
        return Futures.immediateVoidFuture()
    }

    /**
     * Brings audio focus, the noisy receiver and speech in line with the current state.
     * [restartSpeech] speaks again from [index] if speech is running. [publish] is false inside
     * `handle*` methods: SimpleBasePlayer reads the state afterwards and reports the right reasons.
     */
    private fun sync(restartSpeech: Boolean = false, publish: Boolean = true) {
        val active = playWhenReady && error == null &&
            (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING)
        if (active && !audioFocus.request()) {
            playWhenReady = false
            sync(publish = publish)
            return
        }
        if (!active) {
            audioFocus.abandon()
            suppression = Player.PLAYBACK_SUPPRESSION_REASON_NONE
        }
        noisy.setEnabled(active)

        when {
            !active || suppression != Player.PLAYBACK_SUPPRESSION_REASON_NONE -> speech.stopSpeaking()
            !speech.ensureEngine() -> if (playbackState == Player.STATE_READY) playbackState = Player.STATE_BUFFERING
            !speech.isSpeaking || restartSpeech -> speech.speak(sentences, index, parameters.speed, parameters.pitch)
        }
        if (publish) invalidateState()
    }

    private fun fail(message: String, cause: Throwable?, code: Int) {
        speech.release()
        error = PlaybackException(message, cause, code)
        playbackState = Player.STATE_IDLE
        playWhenReady = false
        sync()
    }

    private inner class SpeechListener : SpeechDriver.Listener {
        override fun onEngineReady() {
            if (playbackState == Player.STATE_BUFFERING) playbackState = Player.STATE_READY
            sync()
        }

        override fun onEngineFailed(cause: Throwable) {
            val message = when ((cause as? TtsInitException)?.reason) {
                TtsInitException.Reason.LanguageUnsupported -> R.string.playback_error_language
                else -> R.string.playback_error_engine
            }
            fail(appContext.getString(message), cause, PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK)
        }

        override fun onSentenceStarted(index: Int) {
            if (index == this@TtsPlayer.index || index !in sentences.indices) return
            this@TtsPlayer.index = index
            invalidateState()
        }

        override fun onFinished() {
            speech.release() // frees the TTS service until the next play
            playbackState = Player.STATE_ENDED
            sync()
        }

        override fun onSpeakFailed(code: Int) {
            fail(appContext.getString(R.string.playback_error_speak, code), null, PlaybackException.ERROR_CODE_UNSPECIFIED)
        }
    }

    private fun pause(reason: Int) {
        if (!playWhenReady) return
        playWhenReady = false
        playWhenReadyReason = reason
        sync()
    }

    private fun onAudioFocusChange(change: FocusChange) {
        when (change) {
            FocusChange.Gain -> suppression = Player.PLAYBACK_SUPPRESSION_REASON_NONE
            FocusChange.TransientLoss -> suppression = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
            FocusChange.Loss -> {
                pause(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
                return
            }
        }
        sync()
    }
}
