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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Offline TTS through [TextToSpeech] (whatever engine the user installed, e.g. Google TTS).
 * Keeps [QUEUE_AHEAD] sentences queued and tops up as each one finishes (the callback wakes our
 * process, which the foreground service keeps alive with the screen off). Platform TTS cannot
 * pause: the player stops and speaks again.
 */
class SystemTtsEngine(context: Context) : TtsEngine {
    private val appContext = context.applicationContext
    private val raw = MutableSharedFlow<Pair<UtteranceId, TtsEvent>>(extraBufferCapacity = EVENT_BUFFER)

    private var tts: TextToSpeech? = null
    private var generation = 0
    private var sentences: List<Sentence> = emptyList()
    private var nextToQueue = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var rate = 1f
    private var pitch = 1f

    // Filtered in the collector (main thread), where speak/stop change the generation.
    override val events: Flow<TtsEvent> = raw.filter { it.first.generation == generation }.map { it.second }

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
        ++generation
        engine.stop()
        engine.setSpeechRate(rate)
        engine.setPitch(pitch)
        this.sentences = sentences
        nextToQueue = fromIndex
        queueMore(upTo = fromIndex + QUEUE_AHEAD)
    }

    /** Queues sentences up to (excluding) [upTo]; a refused speak call means the engine is unusable. */
    private fun queueMore(upTo: Int) {
        val engine = tts ?: return
        while (nextToQueue < minOf(upTo, sentences.size)) {
            val id = UtteranceId(generation, nextToQueue)
            if (engine.speak(sentences[nextToQueue].text, TextToSpeech.QUEUE_ADD, null, id.encode()) == TextToSpeech.ERROR) {
                raw.tryEmit(id to TtsEvent.Failed(nextToQueue, TextToSpeech.ERROR))
                generation++ // nothing more from this run
                return
            }
            nextToQueue++
        }
    }

    /** Called on the main thread after sentence [index] of run [generation] finished. */
    private fun onUtteranceFinished(id: UtteranceId) {
        if (id.generation == generation) queueMore(upTo = id.index + 1 + QUEUE_AHEAD)
    }

    override fun stop() {
        generation++
        sentences = emptyList()
        tts?.stop()
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
        raw.tryEmit(id to event(id.index))
        return id
    }

    private inline fun finished(utteranceId: String?, event: (Int) -> TtsEvent) {
        val id = emit(utteranceId, event) ?: return
        mainHandler.post { onUtteranceFinished(id) }
    }

    private companion object {
        val VIETNAMESE: Locale = Locale.forLanguageTag("vi-VN")
        const val EVENT_BUFFER = 64
        const val QUEUE_AHEAD = 5
    }
}
