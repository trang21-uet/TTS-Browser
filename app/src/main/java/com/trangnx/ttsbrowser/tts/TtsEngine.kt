package com.trangnx.ttsbrowser.tts

import com.trangnx.ttsbrowser.text.Sentence
import kotlinx.coroutines.flow.Flow

/** Progress of the current [TtsEngine.speak] call, by sentence index. */
sealed interface TtsEvent {
    val index: Int

    data class Started(override val index: Int) : TtsEvent
    data class Done(override val index: Int) : TtsEvent
    /** One sentence failed; the engine goes on with the next. */
    data class Error(override val index: Int, val code: Int) : TtsEvent

    /** The engine cannot go on (e.g. its service died); speech has stopped. */
    data class Failed(override val index: Int, val code: Int) : TtsEvent
}

/** Why [TtsEngine.init] failed. */
class TtsInitException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { EngineUnavailable, LanguageUnsupported }
}

/**
 * Speaks sentences one utterance each. Called on the main thread; [events] must be collected
 * there too, so events of a stopped or replaced [speak] call are never delivered.
 */
interface TtsEngine {
    val events: Flow<TtsEvent>

    /** Prepares the engine; calling it again after success is a no-op. */
    suspend fun init(): Result<Unit>

    /** Drops any speech in progress and speaks `sentences[fromIndex..]` in order. */
    fun speak(sentences: List<Sentence>, fromIndex: Int)

    fun stop()

    /** Applies from the next [speak] call. */
    fun setRate(rate: Float)

    /** Applies from the next [speak] call. */
    fun setPitch(pitch: Float)

    /** Live gain when supported (Edge); system TTS keeps its own utterance volume. */
    fun setVolume(volume: Float) = Unit

    fun release()

    companion object {
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 3.0f
        const val MIN_PITCH = 0.5f
        const val MAX_PITCH = 2.0f
    }
}
