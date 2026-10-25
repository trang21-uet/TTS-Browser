package com.trangnx.ttsbrowser.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SleepTimerState {
    data object Off : SleepTimerState

    /** Stops at [endsAt] (on the [SleepTimer] clock). */
    data class Countdown(val endsAt: Long) : SleepTimerState

    /** Stops when the chapter has been read to the end. */
    data object EndOfChapter : SleepTimerState
}

/**
 * Sleep timer: calls [onExpire] (pause playback) when the time is up. [now] is a clock that keeps
 * running in deep sleep (`SystemClock.elapsedRealtime`); the loop only re-checks it every
 * [checkIntervalMs], so a delayed wake-up still ends on time. Main thread only.
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val checkIntervalMs: Long = CHECK_INTERVAL_MS,
    private val onVolume: (Float) -> Unit = {},
    private val onExpire: () -> Unit,
) {
    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var job: Job? = null

    fun startMinutes(minutes: Int) {
        cancel()
        _state.value = SleepTimerState.Countdown(endsAt = now() + minutes * MS_PER_MINUTE)
        job = scope.launch {
            while (true) {
                val remaining = (_state.value as? SleepTimerState.Countdown)?.let { it.endsAt - now() } ?: return@launch
                if (remaining <= 0) break
                onVolume((remaining.toFloat() / FADE_DURATION_MS).coerceIn(0f, 1f))
                delay(minOf(remaining, checkIntervalMs, if (remaining <= FADE_DURATION_MS) 1_000L else remaining - FADE_DURATION_MS))
            }
            expire()
        }
    }

    fun startEndOfChapter() {
        cancel()
        _state.value = SleepTimerState.EndOfChapter
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = SleepTimerState.Off
        onVolume(1f)
    }

    /**
     * The chapter was read to the end: playback has stopped by itself, so an end-of-chapter timer is
     * used up. (When chapters follow each other automatically, this is where it will stop them.)
     */
    fun onChapterEnded() {
        if (_state.value == SleepTimerState.EndOfChapter) cancel()
    }

    private fun expire() {
        job = null
        _state.value = SleepTimerState.Off
        onExpire()
        onVolume(1f)
    }

    companion object {
        const val MS_PER_MINUTE = 60_000L
        const val CHECK_INTERVAL_MS = 15_000L
        const val FADE_DURATION_MS = 15_000L
    }
}
