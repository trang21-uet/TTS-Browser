package com.trangnx.ttsbrowser.tts.edge

import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Wire framing for the Edge read-aloud endpoint; never includes page URLs or cookies. */
internal object EdgeProtocol {
    const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    const val VERSION = "143.0.3650.75"
    const val HOAI_MY = "vi-VN-HoaiMyNeural"
    const val NAM_MINH = "vi-VN-NamMinhNeural"
    val voices = setOf(HOAI_MY, NAM_MINH)

    private val timestampFormat = DateTimeFormatter
        .ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
        .withZone(ZoneOffset.UTC)

    fun timestamp(millis: Long): String = timestampFormat.format(Instant.ofEpochMilli(millis))

    fun config(timestamp: String): String = "X-Timestamp:$timestamp\r\n" +
        "Content-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
        "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{" +
        "\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"}," +
        "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n"

    fun ssml(text: String, voice: String, rate: Float, pitch: Float): String {
        require(voice in voices) { "Unsupported Edge voice" }
        require(rate.isFinite() && pitch.isFinite()) { "Invalid speech parameters" }
        val ratePercent = ((rate.coerceIn(0.5f, 3f) - 1f) * 100).roundToInt()
        // Edge prosody accepts Hz; map the app's multiplier to a relative pitch adjustment.
        val pitchHz = ((pitch.coerceIn(0.5f, 2f) - 1f) * 100).roundToInt()
        return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='vi-VN'>" +
            "<voice name='$voice'><prosody rate='${signed(ratePercent)}%' pitch='${signed(pitchHz)}Hz' volume='+0%'>" +
            escapeXml(text) + "</prosody></voice></speak>"
    }

    fun speech(requestId: String, timestamp: String, ssml: String): String =
        "X-RequestId:$requestId\r\nContent-Type:application/ssml+xml\r\n" +
            "X-Timestamp:${timestamp}Z\r\nPath:ssml\r\n\r\n$ssml"

    private fun signed(number: Int) = if (number >= 0) "+$number" else number.toString()

    internal fun escapeXml(text: String): String = buildString {
        text.codePoints().forEach { code ->
            when (code) {
                38 -> append("&amp;")
                60 -> append("&lt;")
                62 -> append("&gt;")
                34 -> append("&quot;")
                39 -> append("&apos;")
                9, 10, 13 -> appendCodePoint(code)
                in 0x20..0xD7FF, in 0xE000..0xFFFD, in 0x10000..0x10FFFF -> appendCodePoint(code)
                else -> append(' ')
            }
        }
    }

    fun path(headers: String): String? = headers.lineSequence().firstNotNullOfOrNull { line ->
        val separator = line.indexOf(':')
        if (separator > 0 && line.substring(0, separator).equals("Path", ignoreCase = true)) {
            line.substring(separator + 1).trim()
        } else null
    }

    /** Binary frames start with the two-byte big-endian header length, excluding those two bytes. */
    fun audioPayload(frame: ByteArray): ByteArray? {
        if (frame.size < 2) throw IOException("Truncated Edge frame")
        val headerLength = ((frame[0].toInt() and 255) shl 8) or (frame[1].toInt() and 255)
        if (headerLength > frame.size - 2) throw IOException("Invalid Edge header length")
        val headers = String(frame, 2, headerLength, Charsets.UTF_8)
        if (path(headers) != "audio") return null
        return frame.copyOfRange(headerLength + 2, frame.size)
    }
}
