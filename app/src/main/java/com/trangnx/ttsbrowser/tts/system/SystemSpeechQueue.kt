package com.trangnx.ttsbrowser.tts.system

import com.trangnx.ttsbrowser.text.Sentence
import com.trangnx.ttsbrowser.tts.TtsEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** Main-thread queue; binder callbacks may only call [emit]. */
internal class SystemSpeechQueue(
    private val enqueue: (String, String) -> Boolean,
    private val stopEngine: () -> Unit,
) {
    private val raw = MutableSharedFlow<Pair<UtteranceId, TtsEvent>>(extraBufferCapacity = 64)
    private var generation = 0
    private var sentences: List<Sentence> = emptyList()
    private var nextToQueue = 0
    val events = raw.filter { it.first.generation == generation }.map { it.second }

    fun start(sentences: List<Sentence>, fromIndex: Int) {
        stop()
        this.sentences = sentences
        nextToQueue = fromIndex
        queueMore(fromIndex + QUEUE_AHEAD)
    }

    fun stop() {
        generation++
        sentences = emptyList()
        stopEngine()
    }

    fun emit(id: UtteranceId, event: TtsEvent) {
        raw.tryEmit(id to event)
    }

    fun finished(id: UtteranceId) {
        if (id.generation == generation) queueMore(id.index + 1 + QUEUE_AHEAD)
    }

    private fun queueMore(upTo: Int) {
        while (nextToQueue < minOf(upTo, sentences.size)) {
            val id = UtteranceId(generation, nextToQueue)
            if (!enqueue(sentences[nextToQueue].text, id.encode())) {
                // Invalidate queued callbacks first, then publish failure under the new generation.
                // Publishing before invalidation silently filtered out the fatal error.
                stop()
                emit(UtteranceId(generation, id.index), TtsEvent.Failed(id.index, -1))
                return
            }
            nextToQueue++
        }
    }

    private companion object {
        const val QUEUE_AHEAD = 5
    }
}
