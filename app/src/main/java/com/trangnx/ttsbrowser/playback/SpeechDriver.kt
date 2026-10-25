package com.trangnx.ttsbrowser.playback

import android.speech.tts.TextToSpeech
import com.trangnx.ttsbrowser.text.Sentence
import com.trangnx.ttsbrowser.tts.TtsInitException
import com.trangnx.ttsbrowser.tts.TtsEngine
import com.trangnx.ttsbrowser.tts.TtsEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs the [TtsEngine] for [TtsPlayer]: creates and initializes it on demand, starts and stops
 * speech, and turns engine events into [Listener] calls. Main thread only.
 */
class SpeechDriver(
    private val scope: CoroutineScope,
    private val engineFactory: () -> TtsEngine,
    private val listener: Listener,
) {
    interface Listener {
        fun onEngineReady()
        fun onEngineFailed(cause: Throwable)
        fun onSentenceStarted(index: Int)
        fun onFinished()
        fun onSpeakFailed(code: Int)
    }

    private var engine: TtsEngine? = null
    private var job: Job? = null
    private var ready = false
    private var lastIndex = -1
    private var consecutiveErrors = 0
    private var watchdog: Job? = null

    var isSpeaking = false
        private set

    /** True if the engine can speak now; otherwise starts initializing it (once) and reports back via [Listener]. */
    fun ensureEngine(): Boolean {
        if (ready) return true
        if (job != null) return false
        val created = engineFactory()
        engine = created
        job = scope.launch {
            val result = withTimeoutOrNull(INIT_TIMEOUT_MS) { created.init() }
                ?: Result.failure(TtsInitException(TtsInitException.Reason.EngineUnavailable))
            result.exceptionOrNull()?.let {
                release()
                listener.onEngineFailed(it)
                return@launch
            }
            ready = true
            // Subscribed before the first speak, so no early event is missed.
            launch(start = CoroutineStart.UNDISPATCHED) { created.events.collect(::onEvent) }
            listener.onEngineReady()
        }
        return false
    }

    /** Speaks `sentences[fromIndex..]`, replacing any speech in progress. Needs [ensureEngine] to be true. */
    fun speak(sentences: List<Sentence>, fromIndex: Int, rate: Float, pitch: Float) {
        val current = engine?.takeIf { ready } ?: return
        current.setRate(rate)
        current.setPitch(pitch)
        lastIndex = sentences.lastIndex
        consecutiveErrors = 0
        current.speak(sentences, fromIndex)
        isSpeaking = true
        armWatchdog()
    }

    fun stopSpeaking() {
        if (!isSpeaking) return
        engine?.stop()
        isSpeaking = false
        watchdog?.cancel()
    }

    /** Frees the engine (e.g. the TTS service binding); the next [ensureEngine] creates a new one. */
    fun release() {
        job?.cancel()
        job = null
        engine?.release()
        engine = null
        ready = false
        isSpeaking = false
        watchdog?.cancel()
    }

    /** A dead TTS service sends no more callbacks: no event for [SILENCE_TIMEOUT_MS] while speaking is a failure. */
    private fun armWatchdog() {
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(SILENCE_TIMEOUT_MS)
            if (isSpeaking) fatal(TextToSpeech.ERROR_SERVICE)
        }
    }

    private fun fatal(code: Int) {
        release()
        listener.onSpeakFailed(code)
    }

    private fun onEvent(event: TtsEvent) {
        if (isSpeaking) armWatchdog()
        when (event) {
            is TtsEvent.Started -> listener.onSentenceStarted(event.index)
            is TtsEvent.Done -> {
                consecutiveErrors = 0
                if (event.index == lastIndex) finish()
            }
            // The engine goes on with the next sentence; only a run of failures stops playback.
            is TtsEvent.Error -> when {
                ++consecutiveErrors >= MAX_CONSECUTIVE_ERRORS -> fatal(event.code)
                event.index == lastIndex -> finish()
            }
            is TtsEvent.Failed -> fatal(event.code)
        }
    }

    private fun finish() {
        isSpeaking = false
        watchdog?.cancel()
        listener.onFinished()
    }

    private companion object {
        const val MAX_CONSECUTIVE_ERRORS = 3
        const val INIT_TIMEOUT_MS = 15_000L

        // Longer than one sentence (≤ 300 chars) at the slowest rate.
        const val SILENCE_TIMEOUT_MS = 90_000L
    }
}
