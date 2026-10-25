package com.trangnx.ttsbrowser.tts.edge

/** Main-thread LRU: audio stays in RAM and cannot grow past [maxBytes]. */
internal class AudioMemoryCache(private val maxBytes: Int = 4 * 1024 * 1024) {
    private val entries = LinkedHashMap<AudioKey, ByteArray>(16, 0.75f, true)
    var sizeBytes: Int = 0
        private set

    init { require(maxBytes > 0) }

    operator fun get(key: AudioKey): ByteArray? = entries[key]

    fun put(key: AudioKey, audio: ByteArray) {
        entries.remove(key)?.let { sizeBytes -= it.size }
        if (audio.size > maxBytes) return
        entries[key] = audio
        sizeBytes += audio.size
        val iterator = entries.entries.iterator()
        while (sizeBytes > maxBytes && iterator.hasNext()) {
            sizeBytes -= iterator.next().value.size
            iterator.remove()
        }
    }

    fun clear() {
        entries.clear()
        sizeBytes = 0
    }
}

internal data class AudioKey(val text: String, val voice: String, val rate: Float, val pitch: Float)
