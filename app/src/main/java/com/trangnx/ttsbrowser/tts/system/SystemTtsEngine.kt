package com.trangnx.ttsbrowser.tts.system

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.trangnx.ttsbrowser.text.Sentence
import com.trangnx.ttsbrowser.tts.TtsEngine
import com.trangnx.ttsbrowser.tts.TtsEvent
import com.trangnx.ttsbrowser.tts.TtsInitException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Offline TTS through [TextToSpeech] (whatever engine the user installed, e.g. Google TTS).
 * Keeps five sentences queued and tops up as each one finishes (the callback wakes our
 * process, which the foreground service keeps alive with the screen off). Platform TTS cannot
 * pause: the player stops and speaks again.
 */
class SystemTtsEngine(context: Context) : TtsEngine {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private val queue = SystemSpeechQueue(
        enqueue = { text, id -> tts?.speak(text, TextToSpeech.QUEUE_ADD, null, id) == TextToSpeech.SUCCESS },
        stopEngine = { tts?.stop() },
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    private var rate = 1f
    private var pitch = 1f

    // Filtered in the collector (main thread), where speak/stop change the generation.
    override val events = queue.events

    override suspend fun init(): Result<Unit> {
        if (tts != null) return Result.success(Unit)
        val holder = arrayOfNulls<TextToSpeech>(1)
        val status = try {
            suspendCancellableCoroutine<Int> { cont ->
                holder[0] = TextToSpeech(appContext) { if (cont.isActive) cont.resume(it) }
            }
        } catch (e: CancellationException) {
            holder[0]?.shutdown()
            throw e
        }
        val engine = holder[0]
        if (status != TextToSpeech.SUCCESS || engine == null) {
            engine?.shutdown()
            return Result.failure(TtsInitException(TtsInitException.Reason.EngineUnavailable))
        }
        val language = engine.setLanguage(VIETNAMESE)
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.shutdown()
            return Result.failure(TtsInitException(TtsInitException.Reason.LanguageUnsupported))
        }
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        engine.setOnUtteranceProgressListener(listener)
        tts = engine
        return Result.success(Unit)
    }

    override fun speak(sentences: List<Sentence>, fromIndex: Int) {
        val engine = tts ?: return
        engine.setSpeechRate(rate)
        engine.setPitch(pitch)
        queue.start(sentences, fromIndex)
    }

    override fun stop() {
        queue.stop()
    }

    override fun setRate(rate: Float) {
        this.rate = rate.coerceIn(TtsEngine.MIN_RATE, TtsEngine.MAX_RATE)
    }

    override fun setPitch(pitch: Float) {
        this.pitch = pitch.coerceIn(TtsEngine.MIN_PITCH, TtsEngine.MAX_PITCH)
    }

    override fun release() {
        stop()
        tts?.shutdown()
        tts = null
    }

    // Called on a binder thread.
    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            emit(utteranceId) { TtsEvent.Started(it) }
        }

        override fun onDone(utteranceId: String?) = finished(utteranceId) { TtsEvent.Done(it) }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finished(utteranceId) { TtsEvent.Error(it, TextToSpeech.ERROR) }

        override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId) { TtsEvent.Error(it, errorCode) }
    }

    private inline fun emit(utteranceId: String?, event: (Int) -> TtsEvent): UtteranceId? {
        val id = UtteranceId.decode(utteranceId) ?: return null
        queue.emit(id, event(id.index))
        return id
    }

    private inline fun finished(utteranceId: String?, event: (Int) -> TtsEvent) {
        val id = emit(utteranceId, event) ?: return
        mainHandler.post { queue.finished(id) }
    }

    private companion object {
        val VIETNAMESE: Locale = Locale.forLanguageTag("vi-VN")
    }
}
