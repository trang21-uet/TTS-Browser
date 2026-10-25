package com.trangnx.ttsbrowser.tts.edge

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class EdgeProtocolTest {
    @Test fun `SSML escapes markup and removes invalid XML characters`() {
        val xml = EdgeProtocol.ssml("A & <B> \"x\" 'y'\u0000\uD800 😀", EdgeProtocol.HOAI_MY, 1.5f, 1.2f)
        assertTrue(xml.contains("A &amp; &lt;B&gt; &quot;x&quot; &apos;y&apos;   😀"))
        assertTrue(xml.contains("rate='+50%'"))
        assertTrue(xml.contains("pitch='+20Hz'"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `voice cannot inject XML`() {
        EdgeProtocol.ssml("text", "x'><audio src='x", 1f, 1f)
    }

    @Test fun `binary payload begins after two byte length and headers`() {
        val payload = byteArrayOf(0, 1, -1, 9)
        assertArrayEquals(payload, EdgeProtocol.audioPayload(frame("Path:audio\r\nContent-Type:audio/mpeg\r\n", payload)))
        assertNull(EdgeProtocol.audioPayload(frame("Path:other\r\n", payload)))
    }

    @Test fun `header length is unsigned big endian`() {
        val headers = "Path:audio\r\nX-Padding:" + "x".repeat(260) + "\r\n"
        assertArrayEquals(byteArrayOf(4), EdgeProtocol.audioPayload(frame(headers, byteArrayOf(4))))
    }

    @Test(expected = IOException::class)
    fun `truncated header fails`() { EdgeProtocol.audioPayload(byteArrayOf(0, 10, 1)) }

    @Test(expected = IOException::class)
    fun `missing length fails`() { EdgeProtocol.audioPayload(byteArrayOf(0)) }

    @Test fun `path uses first colon only and accepts case insensitive header name`() {
        assertEquals("turn.end", EdgeProtocol.path("X-Time:12:30\r\npath:turn.end\r\n"))
        assertEquals("Thu Jan 01 1970 00:00:00 GMT+0000 (Coordinated Universal Time)", EdgeProtocol.timestamp(0))
    }

    private fun frame(headers: String, payload: ByteArray): ByteArray {
        val bytes = headers.toByteArray()
        return byteArrayOf((bytes.size shr 8).toByte(), bytes.size.toByte()) + bytes + payload
    }
}
