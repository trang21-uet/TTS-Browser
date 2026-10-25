package com.trangnx.ttsbrowser.tts.edge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AudioMemoryCacheTest {
    @Test fun `evicts least recently read entry by total bytes`() {
        val cache = AudioMemoryCache(8)
        cache.put(key("a"), ByteArray(3))
        cache.put(key("b"), ByteArray(3))
        assertNotNull(cache[key("a")])
        cache.put(key("c"), ByteArray(4))
        assertNull(cache[key("b")])
        assertNotNull(cache[key("a")])
        assertEquals(7, cache.sizeBytes)
    }

    @Test fun `oversized audio and replacements cannot exceed limit`() {
        val cache = AudioMemoryCache(8)
        cache.put(key("a"), ByteArray(4))
        cache.put(key("a"), ByteArray(6))
        assertEquals(6, cache.sizeBytes)
        cache.put(key("big"), ByteArray(9))
        assertEquals(6, cache.sizeBytes)
        assertNull(cache[key("big")])
        cache.clear()
        assertEquals(0, cache.sizeBytes)
        assertNull(cache[key("a")])
    }

    @Test fun `cache keys isolate voice rate and pitch`() {
        val cache = AudioMemoryCache()
        cache.put(key("a"), ByteArray(1))
        assertNull(cache[key("a").copy(voice = EdgeProtocol.NAM_MINH)])
        assertNull(cache[key("a").copy(rate = 1.5f)])
        assertNull(cache[key("a").copy(pitch = 1.5f)])
    }

    private fun key(text: String) = AudioKey(text, EdgeProtocol.HOAI_MY, 1f, 1f)
}
