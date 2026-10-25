package com.trangnx.ttsbrowser.tts.edge

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One WebSocket per sentence. Share this client: it owns a bounded OkHttp dispatcher/pool. */
class EdgeTtsClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Volatile private var clockOffsetMillis = 0L

    suspend fun synthesize(text: String, voice: String, rate: Float, pitch: Float): ByteArray = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
        val ssml = EdgeProtocol.ssml(text, voice, rate, pitch)
        require(ssml.toByteArray(Charsets.UTF_8).size <= MAX_SSML_BYTES) { "Edge sentence too long" }
        try {
            request(ssml)
        } catch (e: HandshakeException) {
            // A skewed device clock makes an otherwise valid token fail. Retry once using server time.
            if (e.status != 403 || e.serverTime == null) throw e
            clockOffsetMillis = e.serverTime - now()
            request(ssml)
        }
    } ?: throw IOException("Edge synthesis timed out")

    private suspend fun request(ssml: String): ByteArray = suspendCancellableCoroutine { continuation ->
        val time = now() + clockOffsetMillis
        val request = Request.Builder()
            .url(
                "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1".toHttpUrl().newBuilder()
                    .addQueryParameter("TrustedClientToken", EdgeProtocol.TRUSTED_CLIENT_TOKEN)
                    .addQueryParameter("ConnectionId", id())
                    .addQueryParameter("Sec-MS-GEC", EdgeTokenGenerator.generate(time))
                    .addQueryParameter("Sec-MS-GEC-Version", "2-${EdgeProtocol.VERSION}")
                    .build(),
            )
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .header("Cookie", "muid=${id().uppercase()};")
            .build()
        val finished = AtomicBoolean(false)
        val audio = ByteArrayOutputStream()
        var socket: WebSocket? = null
        val listener = object : WebSocketListener() {
            private fun fail(webSocket: WebSocket, error: Throwable) {
                if (finished.compareAndSet(false, true)) {
                    webSocket.cancel()
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }

            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!continuation.isActive) {
                    webSocket.cancel()
                    return
                }
                val timestamp = EdgeProtocol.timestamp(time)
                if (!webSocket.send(EdgeProtocol.config(timestamp)) ||
                    !webSocket.send(EdgeProtocol.speech(id(), timestamp, ssml))) {
                    fail(webSocket, IOException("Edge request rejected"))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (finished.get()) return
                if (text.length > MAX_HEADER_BYTES) {
                    fail(webSocket, IOException("Edge message too large"))
                    return
                }
                when (EdgeProtocol.path(text.substringBefore("\r\n\r\n"))) {
                    "turn.end" -> {
                        if (audio.size() == 0) {
                            fail(webSocket, IOException("Edge returned no audio"))
                        } else if (finished.compareAndSet(false, true)) {
                            webSocket.close(1000, null)
                            if (continuation.isActive) continuation.resume(audio.toByteArray())
                        }
                    }
                    "error" -> fail(webSocket, IOException("Edge synthesis failed"))
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (finished.get()) return
                try {
                    if (bytes.size > MAX_AUDIO_BYTES + MAX_HEADER_BYTES) throw IOException("Edge frame too large")
                    val payload = EdgeProtocol.audioPayload(bytes.toByteArray()) ?: return
                    if (payload.size > MAX_AUDIO_BYTES - audio.size()) throw IOException("Edge audio too large")
                    audio.write(payload)
                } catch (e: IOException) {
                    fail(webSocket, e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                fail(webSocket, IOException("Edge closed before turn.end"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                fail(webSocket, IOException("Edge closed before turn.end"))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val error = if (response != null) HandshakeException(response.code, parseDate(response.header("Date")))
                else IOException("Edge connection failed", t)
                response?.close()
                fail(webSocket, error)
            }
        }
        continuation.invokeOnCancellation {
            finished.set(true)
            socket?.cancel()
        }
        socket = http.newWebSocket(request, listener)
        if (!continuation.isActive) socket.cancel()
    }

    private class HandshakeException(val status: Int, val serverTime: Long?) : IOException("Edge handshake failed ($status)")

    private companion object {
        const val REQUEST_TIMEOUT_MS = 25_000L
        const val MAX_SSML_BYTES = 8 * 1024
        const val MAX_HEADER_BYTES = 16 * 1024
        const val MAX_AUDIO_BYTES = 2 * 1024 * 1024

        fun id(): String = UUID.randomUUID().toString().replace("-", "")

        fun parseDate(value: String?): Long? = try {
            value?.let { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        } catch (_: java.time.DateTimeException) { null }
    }
}
