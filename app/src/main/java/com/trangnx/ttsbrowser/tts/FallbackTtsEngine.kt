package com.trangnx.ttsbrowser.tts

import com.trangnx.ttsbrowser.text.Sentence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One Edge attempt per engine lifetime, then system speech from the failed/current sentence. */
class FallbackTtsEngine(
    private val primary: TtsEngine,
    private val fallbackFactory: () -> TtsEngine,
    private val onFallback: () -> Unit,
    private val scope: CoroutineScope = MainScope(),
) : TtsEngine {
    private var active = primary
    private var usingFallback = false
    private var fallbackReady = false
    private var generation = 0
    private var job: Job? = null
    private var sentences: List<Sentence> = emptyList()
    private var index = 0
    private var rate = 1f
    private var pitch = 1f
    private var volume = 1f
    private val raw = MutableSharedFlow<Pair<Int, TtsEvent>>(extraBufferCapacity = 64)
    override val events = raw.filter { it.first == generation }.map { it.second }

    override suspend fun init(): Result<Unit> {
        if (usingFallback) return prepareFallback()
        val result = primary.init()
        if (result.isSuccess) return result
        return prepareFallback()
    }

    private suspend fun prepareFallback(): Result<Unit> {
        if (!usingFallback) {
            primary.release()
            active = fallbackFactory()
            usingFallback = true
            onFallback()
        }
        if (fallbackReady) return Result.success(Unit)
        val result = withTimeoutOrNull(15_000) { active.init() }
            ?: Result.failure(TtsInitException(TtsInitException.Reason.EngineUnavailable))
        fallbackReady = result.isSuccess
        return result
    }

    override fun speak(sentences: List<Sentence>, fromIndex: Int) {
        stop()
        if (fromIndex !in sentences.indices) return
        this.sentences = sentences
        index = fromIndex
        val run = generation
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (usingFallback && !fallbackReady && prepareFallback().isFailure) {
                raw.emit(run to TtsEvent.Failed(index, -1))
                return@launch
            }
            collectAndSpeak(run)
        }
    }

    private suspend fun collectAndSpeak(run: Int) {
        active.setRate(rate)
        active.setPitch(pitch)
        active.setVolume(volume)
        // Subscribe before speak: synchronous rejection must also reach the fallback.
        var failedIndex: Int? = null
        val collector = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            active.events.takeWhile { event ->
                if (generation != run) return@takeWhile false
                if (!usingFallback && (event is TtsEvent.Error || event is TtsEvent.Failed)) {
                    failedIndex = event.index.coerceIn(sentences.indices)
                    false
                } else true
            }.collect { event ->
                when (event) {
                    is TtsEvent.Started -> index = event.index
                    is TtsEvent.Done -> index = (event.index + 1).coerceAtMost(sentences.lastIndex)
                    else -> Unit
                }
                raw.emit(run to event)
            }
        }
        try {
            active.speak(sentences, index)
            collector.join()
        } finally {
            collector.cancel()
        }
        if (generation == run && failedIndex != null) {
            index = failedIndex
            if (prepareFallback().isSuccess) collectAndSpeak(run)
            else raw.emit(run to TtsEvent.Failed(index, -1))
        }
    }

    override fun stop() {
        generation++
        job?.cancel()
        job = null
        active.stop()
        sentences = emptyList()
    }

    override fun setRate(rate: Float) { this.rate = rate }
    override fun setPitch(pitch: Float) { this.pitch = pitch }
    override fun setVolume(volume: Float) {
        this.volume = volume
        active.setVolume(volume)
    }

    override fun release() {
        stop()
        active.release()
        if (active !== primary) primary.release()
    }
}
