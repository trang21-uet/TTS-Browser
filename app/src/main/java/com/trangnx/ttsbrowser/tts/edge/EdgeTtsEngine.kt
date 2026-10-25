package com.trangnx.ttsbrowser.tts.edge

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.trangnx.ttsbrowser.text.Sentence
import com.trangnx.ttsbrowser.tts.TtsEngine
import com.trangnx.ttsbrowser.tts.TtsEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** RAM-only sentence playlist. At most three sentences are downloaded ahead of playback. */
@OptIn(UnstableApi::class)
class EdgeTtsEngine(
    context: Context,
    private val client: EdgeTtsClient = EdgeTtsClient(),
    private val voice: String = EdgeProtocol.HOAI_MY,
) : TtsEngine {
    private val appContext = context.applicationContext
    private val scope = MainScope()
    private val cache = AudioMemoryCache()
    private val raw = MutableSharedFlow<Pair<Int, TtsEvent>>(extraBufferCapacity = 64)
    private var generation = 0
    private var job: Job? = null
    private var player: ExoPlayer? = null
    private var rate = 1f
    private var pitch = 1f
    private var volume = 1f
    private var released = false

    override val events: Flow<TtsEvent> = raw.filter { it.first == generation }.map { it.second }

    init { require(voice in EdgeProtocol.voices) }

    override suspend fun init(): Result<Unit> = if (released) {
        Result.failure(IllegalStateException("Edge engine released"))
    } else Result.success(Unit)

    override fun speak(sentences: List<Sentence>, fromIndex: Int) {
        stop()
        if (released || fromIndex !in sentences.indices) return
        val run = generation
        val speechRate = rate
        val speechPitch = pitch
        val changed = Channel<Unit>(Channel.CONFLATED)
        var current = fromIndex
        var started = -1
        var completed = fromIndex - 1
        var failed = false
        val audio = ExoPlayer.Builder(appContext)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                false, // TtsPlayer already owns audio focus and the becoming-noisy receiver.
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        audio.volume = volume
        player = audio

        fun emit(event: TtsEvent) {
            if (generation == run) raw.tryEmit(run to event)
        }

        fun finishSentence(index: Int) {
            if (index > completed && index == started) {
                completed = index
                emit(TtsEvent.Done(index))
            }
        }

        fun fail() {
            if (failed || generation != run) return
            failed = true
            job?.cancel()
            // Do not call stop(): the failure event belongs to this generation until the consumer handles it.
            audio.stop()
            emit(TtsEvent.Failed(current.coerceAtMost(sentences.lastIndex), TextToSpeech.ERROR_NETWORK))
        }

        fun startCurrent() {
            val index = audio.currentMediaItem?.mediaId?.toIntOrNull() ?: return
            if (index != started) {
                started = index
                current = index
                emit(TtsEvent.Started(index))
            }
        }

        audio.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (generation == run && !failed && isPlaying) startCurrent()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (generation != run || failed) return
                val index = mediaItem?.mediaId?.toIntOrNull() ?: return
                if (index > current) {
                    finishSentence(started)
                    current = index
                    changed.trySend(Unit)
                }
                // Also trim when a previously starved queue resumes at the next sentence.
                if (audio.currentMediaItemIndex > 0) audio.removeMediaItems(0, audio.currentMediaItemIndex)
                if (audio.isPlaying) startCurrent()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (generation != run || failed || playbackState != Player.STATE_ENDED) return
                finishSentence(started)
                current = (completed + 1).coerceAtMost(sentences.size)
                changed.trySend(Unit)
            }

            override fun onPlayerError(error: PlaybackException) {
                fail()
            }
        })

        job = scope.launch {
            try {
                coroutineScope {
                    val pending = mutableMapOf<Int, Deferred<Result<ByteArray>>>()
                    var next = fromIndex
                    while (next < sentences.size) {
                        val limit = minOf(sentences.size, current + PREFETCH_COUNT)
                        for (index in next until limit) {
                            if (index !in pending) {
                                pending[index] = async {
                                    val key = AudioKey(sentences[index].text, voice, speechRate, speechPitch)
                                    try {
                                        Result.success(cache[key] ?: client.synthesize(key.text, voice, speechRate, speechPitch).also {
                                            cache.put(key, it)
                                        })
                                    } catch (e: CancellationException) { throw e }
                                    catch (e: Exception) { Result.failure(e) }
                                }
                            }
                        }
                        while (next < limit) {
                            val bytes = pending.remove(next)!!.await().getOrThrow()
                            if (generation != run || failed) return@coroutineScope
                            val source = ProgressiveMediaSource.Factory(DataSource.Factory { ByteArrayDataSource(bytes) })
                                .createMediaSource(MediaItem.Builder().setMediaId(next.toString()).setUri("memory://edge/$next.mp3").build())
                            val wasEnded = audio.playbackState == Player.STATE_ENDED
                            audio.addMediaSource(source)
                            if (wasEnded) {
                                audio.seekTo(audio.mediaItemCount - 1, 0)
                                audio.prepare()
                            } else if (audio.playbackState == Player.STATE_IDLE) {
                                audio.prepare()
                            }
                            audio.play()
                            next++
                        }
                        if (next < sentences.size) changed.receive()
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { fail() }
            finally { changed.close() }
        }
    }

    override fun stop() {
        generation++
        job?.cancel()
        job = null
        player?.release()
        player = null
    }

    override fun setRate(rate: Float) {
        this.rate = rate.takeIf { it.isFinite() }?.coerceIn(TtsEngine.MIN_RATE, TtsEngine.MAX_RATE) ?: 1f
    }

    override fun setPitch(pitch: Float) {
        this.pitch = pitch.takeIf { it.isFinite() }?.coerceIn(TtsEngine.MIN_PITCH, TtsEngine.MAX_PITCH) ?: 1f
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
        player?.volume = this.volume
    }

    override fun release() {
        stop()
        cache.clear()
        scope.cancel()
        released = true
    }

    private companion object {
        const val PREFETCH_COUNT = 3
    }
}
