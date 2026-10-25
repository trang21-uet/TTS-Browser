package com.trangnx.ttsbrowser.tts.system

import com.trangnx.ttsbrowser.text.Sentence
import com.trangnx.ttsbrowser.tts.TtsEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.CoroutineContext

class SystemSpeechQueueTest {
    private val dispatcher = QueuedDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val queued = mutableListOf<UtteranceId>()
    private var refuseIndex: Int? = null
    private var stops = 0
    private val queue = SystemSpeechQueue(
        enqueue = { _, encoded ->
            val id = UtteranceId.decode(encoded)!!
            queued += id
            id.index != refuseIndex
        },
        stopEngine = { stops++ },
    )
    private val received = mutableListOf<TtsEvent>()
    private val sentences = List(10) { Sentence(it, "Câu $it.", 0, it * 7) }

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { queue.events.collect { received += it } }
    }

    @After
    fun tearDown() { scope.cancel(); dispatcher.drain() }

    @Test
    fun synchronousRefusalReportsFailureImmediatelyAndStopsQueuedAudio() {
        refuseIndex = 2
        queue.start(sentences, 0)
        // Platform callbacks already queued before the refusal must never reach the player.
        queue.emit(queued[0], TtsEvent.Started(0))
        queue.emit(queued[1], TtsEvent.Done(1))
        queue.finished(queued[1])
        dispatcher.drain()
        assertEquals(listOf(TtsEvent.Failed(2, -1)), received)
        assertEquals(listOf(0, 1, 2), queued.map { it.index })
        assertEquals(2, stops)
    }

    @Test
    fun refusalWhileToppingUpAlsoReachesCollector() {
        queue.start(sentences, 0)
        refuseIndex = 5
        queue.finished(queued.first())
        dispatcher.drain()
        assertEquals(listOf(TtsEvent.Failed(5, -1)), received)
    }

    @Test
    fun explicitStopDiscardsBufferedFailure() {
        refuseIndex = 0
        queue.start(sentences, 0)
        queue.stop()
        dispatcher.drain()
        assertTrue(received.isEmpty())
    }

    @Test
    fun newSeekDiscardsBufferedFailureAndOldCallbacks() {
        refuseIndex = 0
        queue.start(sentences, 0)
        val old = queued.first()
        refuseIndex = null
        queue.start(sentences, 7)
        queue.emit(old, TtsEvent.Done(0))
        queue.finished(old)
        queue.emit(queued[1], TtsEvent.Started(7))
        dispatcher.drain()
        assertEquals(listOf(TtsEvent.Started(7)), received)
        assertEquals(listOf(0, 7, 8, 9), queued.map { it.index })
    }

    @Test
    fun normalCompletionTopsUpWindowWithoutRequeuingSentences() {
        queue.start(sentences, 0)
        assertEquals((0..4).toList(), queued.map { it.index })
        queue.emit(queued.first(), TtsEvent.Done(0))
        queue.finished(queued.first())
        dispatcher.drain()
        assertEquals((0..5).toList(), queued.map { it.index })
        assertEquals(listOf(TtsEvent.Done(0)), received)
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.addLast(block) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }
}
