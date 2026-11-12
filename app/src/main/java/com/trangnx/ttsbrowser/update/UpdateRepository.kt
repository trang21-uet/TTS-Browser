package com.trangnx.ttsbrowser.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Reads the newest release of this app from the public GitHub repository and downloads its APK.
 * Requests carry no credentials and nothing about the user or the device.
 */
class UpdateRepository(
    private val updatesDir: File,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    /**
     * Null when there is no release yet or its tag does not parse.
     * Unauthenticated, so it shares GitHub's 60 requests/hour per IP: plenty for one check per launch.
     */
    @Throws(IOException::class)
    suspend fun fetchLatest(): GithubRelease? {
        val request = Request.Builder().url(LATEST_RELEASE_URL).header("Accept", "application/vnd.github+json").build()
        val body = http.newCall(request).useResponse { response ->
            when {
                response.code == 404 -> null
                !response.isSuccessful -> throw IOException("GitHub HTTP ${response.code}")
                else -> response.body.string()
            }
        }
        return body?.let(GithubRelease::parse)
    }

    /** Downloads [release]'s APK into [updatesDir], replacing older downloads. [onProgress] gets 0..1. */
    @Throws(IOException::class)
    suspend fun download(release: GithubRelease, onProgress: (Float) -> Unit): File {
        val url = release.apkUrl ?: throw IOException("Release has no APK")
        updatesDir.mkdirs()
        updatesDir.listFiles()?.forEach { it.delete() }
        val partial = File(updatesDir, "TTS-Browser-${release.version}.apk.part")
        val target = File(updatesDir, "TTS-Browser-${release.version}.apk")
        try {
            http.newCall(Request.Builder().url(url).build()).useResponse { response ->
                if (!response.isSuccessful) throw IOException("Download HTTP ${response.code}")
                val total = response.body.contentLength().takeIf { it > 0 } ?: release.apkSize
                var received = 0L
                var lastPercent = -1
                response.body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            received += read
                            val percent = total?.let { (received * 100 / it).toInt().coerceAtMost(100) }
                            if (percent != null && percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
                // A truncated file would only fail later, inside the system installer.
                if (release.apkSize != null && received != release.apkSize) throw IOException("Incomplete download")
            }
            if (!partial.renameTo(target)) throw IOException("Cannot store the download")
            return target
        } finally {
            partial.delete()
        }
    }

    /** Runs the blocking call off the main thread and aborts it, even mid-read, when the coroutine is cancelled. */
    private suspend fun <T> Call.useResponse(block: (Response) -> T): T = withContext(Dispatchers.IO) {
        val call = this@useResponse
        val watcher = launch {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            call.execute().use(block)
        } finally {
            watcher.cancel()
            coroutineContext.ensureActive()
        }
    }

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/trang21-uet/TTS-Browser/releases/latest"
        const val BUFFER_BYTES = 16 * 1024
    }
}
