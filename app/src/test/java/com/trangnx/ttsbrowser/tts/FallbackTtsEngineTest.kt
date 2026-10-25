package com.trangnx.ttsbrowser.tts

import com.trangnx.ttsbrowser.text.Sentence
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.CoroutineContext

/** All engine calls and callbacks run on a controllable single thread, as they do in the app. */
class FallbackTtsEngineTest {
    private val dispatcher = QueuedDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val primary = FakeEngine()
    private val system = FakeEngine()
    private var fallbackNotifications = 0
    private val engine = FallbackTtsEngine(primary, { system }, { fallbackNotifications++ }, scope)
    private val sentences = List(4) { Sentence(it, "CÃ¢u $it.", 0, it * 7) }
    private val received = mutableListOf<TtsEvent>()

    @After
    fun tearDown() {
        engine.release()
        scope.cancel()
        dispatcher.drain()
    }

    private fun start(from: Int = 0) {
        assertTrue(runBlocking { engine.init() }.isSuccess)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { engine.events.collect { received += it } }
        engine.speak(sentences, from)
        dispatcher.drain()
    }

    private fun send(source: FakeEngine, event: TtsEvent) {
        assertTrue(source.events.tryEmit(event))
        dispatcher.drain()
    }

    @Test
    fun edgeFailureRepeatsTheCurrentSentenceAndForwardsSystemEvents() {
        start(1)
        send(primary, TtsEvent.Started(1))
        send(primary, TtsEvent.Done(1))
        send(primary, TtsEvent.Started(2))
        send(primary, TtsEvent.Failed(2, 403))

        assertEquals(listOf(2), system.starts)
        assertEquals(1, fallbackNotifications)
        assertTrue(primary.releases > 0)
        assertFalse(received.any { it is TtsEvent.Failed })
        send(system, TtsEvent.Started(2))
        send(system, TtsEvent.Done(2))
        assertEquals(TtsEvent.Done(2), received.last())
    }

    @Test
    fun individualEdgeErrorAlsoFallsBackWithoutSkippingTheFailedSentence() {
        start()
        send(primary, TtsEvent.Error(1, -1))
        assertEquals(listOf(1), system.starts)
        assertFalse(received.any { it is TtsEvent.Error })
    }

    @Test
    fun fallbackPreservesProsodyAndLiveVolume() {
        engine.setRate(1.7f)
        engine.setPitch(0.8f)
        engine.setVolume(0.6f)
        start(1)
        send(primary, TtsEvent.Failed(1, -1))

        assertEquals(1.7f, system.appliedRate, 0f)
        assertEquals(0.8f, system.appliedPitch, 0f)
        assertEquals(0.6f, system.appliedVolume, 0f)
        engine.setVolume(0.2f)
        assertEquals(0.2f, system.appliedVolume, 0f)
    }

    @Test
    fun pauseAndSeekAfterFallbackContinueWithSystemWithoutAnotherNotification() {
        start()
        send(primary, TtsEvent.Failed(0, -1))
        engine.stop()
        dispatcher.drain()
        engine.speak(sentences, 3)
        dispatcher.drain()

        assertEquals(listOf(0), primary.starts)
        assertEquals(listOf(0, 3), system.starts)
        assertEquals(1, system.initializations)
        assertEquals(1, fallbackNotifications)
    }

    @Test
    fun bufferedEventOfStoppedSpeechIsDiscardedBeforeItReachesTheListener() {
        start()
        primary.events.tryEmit(TtsEvent.Started(1))
        // Let the inner collector forward it, while the outward collector remains queued.
        dispatcher.next()
        engine.stop()
        dispatcher.drain()
        assertTrue(received.isEmpty())
    }

    @Test
    fun bufferedEventOfOldSpeechDoesNotOverwriteTheNewSeekPosition() {
        start()
        primary.events.tryEmit(TtsEvent.Started(1))
        dispatcher.next()
        engine.speak(sentences, 3)
        dispatcher.drain()
        assertTrue(received.isEmpty())
        send(primary, TtsEvent.Started(3))
        assertEquals(listOf(TtsEvent.Started(3)), received)
        assertEquals(listOf(0, 3), primary.starts)
    }

    @Test
    fun failedSystemInitializationReportsFatalErrorAtTheFailedSentence() {
        system.initResult = Result.failure(IllegalStateException("missing voice"))
        start(2)
        send(primary, TtsEvent.Failed(2, -1))
        assertEquals(listOf(TtsEvent.Failed(2, -1)), received)
        assertTrue(system.starts.isEmpty())
    }

    @Test
    fun reinitializationAfterFallbackDoesNotReconnectToEdge() {
        start()
        send(primary, TtsEvent.Failed(0, -1))
        assertTrue(runBlocking { engine.init() }.isSuccess)
        assertEquals(1, primary.initializations)
        assertEquals(1, system.initializations)
        assertEquals(1, fallbackNotifications)
    }

    @Test
    fun stoppingWhileSystemInitializesCancelsItAndNeverStartsSpeech() {
        var cancelled = false
        system.initialize = {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { cancelled = true }
            }
        }
        start()
        send(primary, TtsEvent.Failed(1, -1))
        engine.stop()
        dispatcher.drain()

        assertTrue(cancelled)
        assertTrue(system.starts.isEmpty())
        assertTrue(received.isEmpty())
    }

    @Test
    fun initialEdgeFailureUsesSystemAndReleaseClosesIt() {
        primary.initResult = Result.failure(IllegalStateException("unavailable"))
        start(2)
        assertEquals(listOf(2), system.starts)
        assertTrue(primary.starts.isEmpty())
        engine.release()
        dispatcher.drain()
        assertTrue(system.releases > 0)
        assertEquals(1, fallbackNotifications)
        send(system, TtsEvent.Started(3))
        assertTrue(received.isEmpty())
    }

    private class FakeEngine : TtsEngine {
        override val events = MutableSharedFlow<TtsEvent>(extraBufferCapacity = 16)
        val starts = mutableListOf<Int>()
        var appliedRate = 1f
        var appliedPitch = 1f
        var appliedVolume = 1f
        var releases = 0
        var initializations = 0
        var initResult = Result.success(Unit)
        var initialize: (suspend () -> Result<Unit>)? = null
        override suspend fun init(): Result<Unit> {
            initializations++
            return initialize?.invoke() ?: initResult
        }
        override fun speak(sentences: List<Sentence>, fromIndex: Int) { starts += fromIndex }
        override fun stop() = Unit
        override fun setRate(rate: Float) { this.appliedRate = rate }
        override fun setPitch(pitch: Float) { this.appliedPitch = pitch }
        override fun setVolume(volume: Float) { this.appliedVolume = volume }
        override fun release() { releases++ }
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queue.addLast(block) }
        fun next() { queue.removeFirst().run() }
        fun drain() {
            var tasks = 0
            while (queue.isNotEmpty()) {
                check(tasks++ < 1_000) { "Coroutine did not become idle" }
                next()
            }
        }
    }
}
