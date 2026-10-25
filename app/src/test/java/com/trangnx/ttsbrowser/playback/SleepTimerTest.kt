package com.trangnx.ttsbrowser.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** The clock is fake and the loop re-checks it every few ms, so a test "waits" by moving the clock. */
class SleepTimerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = AtomicLong(1_000)
    private val expired = AtomicInteger()
    private val expiredLatch = CountDownLatch(1)

    private val timer = SleepTimer(scope, clock::get, checkIntervalMs = 5) {
        expired.incrementAndGet()
        expiredLatch.countDown()
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun countdownEndsAtTheChosenTime() {
        timer.startMinutes(30)
        assertEquals(SleepTimerState.Countdown(endsAt = 1_000 + 30 * SleepTimer.MS_PER_MINUTE), timer.state.value)

        clock.addAndGet(29 * SleepTimer.MS_PER_MINUTE)
        assertFalse(expiredLatch.await(100, TimeUnit.MILLISECONDS))
        assertEquals(0, expired.get())

        clock.addAndGet(SleepTimer.MS_PER_MINUTE)
        assertTrue(expiredLatch.await(2, TimeUnit.SECONDS))
        assertEquals(SleepTimerState.Off, timer.state.value)
        assertEquals(1, expired.get())
    }

    @Test
    fun cancelledTimerNeverExpires() {
        timer.startMinutes(15)
        timer.cancel()
        clock.addAndGet(20 * SleepTimer.MS_PER_MINUTE)

        assertFalse(expiredLatch.await(100, TimeUnit.MILLISECONDS))
        assertEquals(SleepTimerState.Off, timer.state.value)
    }

    @Test
    fun startingAgainReplacesTheRunningTimer() {
        timer.startMinutes(15)
        timer.startMinutes(60)
        clock.addAndGet(30 * SleepTimer.MS_PER_MINUTE)

        assertFalse(expiredLatch.await(100, TimeUnit.MILLISECONDS))
        assertEquals(SleepTimerState.Countdown(endsAt = 1_000 + 60 * SleepTimer.MS_PER_MINUTE), timer.state.value)
    }

    @Test
    fun endOfChapterIsUsedUpWhenTheChapterEnds() {
        timer.startEndOfChapter()
        assertEquals(SleepTimerState.EndOfChapter, timer.state.value)

        timer.onChapterEnded()
        assertEquals(SleepTimerState.Off, timer.state.value)
        assertEquals(0, expired.get())
    }

    @Test
    fun chapterEndLeavesACountdownRunning() {
        timer.startMinutes(15)
        timer.onChapterEnded()
        assertTrue(timer.state.value is SleepTimerState.Countdown)
    }
}
