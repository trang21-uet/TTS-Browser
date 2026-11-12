package com.trangnx.ttsbrowser.data.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Largest power of two that keeps the decoded image at least [maxSide] px on its longer side. */
internal fun coverSampleSize(width: Int, height: Int, maxSide: Int): Int {
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= maxSide) sample *= 2
    return sample
}

/**
 * Cover images of saved stories: downloaded from the story's web page or picked from the device, scaled
 * down and kept as JPEG files in the app's private storage. Every file has a new name, so a changed cover
 * is a changed value for the UI.
 */
class CoverStore(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build(),
) {
    private val resolver = context.applicationContext.contentResolver
    private val dir = File(context.applicationContext.filesDir, "covers")

    fun file(name: String): File = File(dir, name)

    /** Downloads [imageUrl] (some sites only serve images to the page that shows them, hence [referer]); null on any failure. */
    suspend fun download(imageUrl: String, referer: String): String? = store {
        val request = Request.Builder().url(imageUrl).header("Referer", referer).build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.byteStream()?.let(::readLimited) else null
        }
    }

    /** Copies the image the user picked at [uri]; null if it cannot be read as an image. */
    suspend fun import(uri: Uri): String? = store { resolver.openInputStream(uri)?.use(::readLimited) }

    fun delete(name: String?) {
        if (name != null) file(name).delete()
    }

    private suspend fun store(read: () -> ByteArray?): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = read()?.let(::decode) ?: return@withContext null
            val name = "cover-${UUID.randomUUID()}.jpg"
            dir.mkdirs()
            file(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            name
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // Network, storage, unsupported or malformed image: the story just has no cover.
        }
    }

    /** At most [MAX_BYTES]; a larger source is not a cover. */
    private fun readLimited(input: InputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (out.size() + read > MAX_BYTES) throw IOException("image too large")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = coverSampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE_PX)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val scale = MAX_SIDE_PX.toFloat() / maxOf(decoded.width, decoded.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
        } else decoded
        return if (scaled.hasAlpha()) onWhite(scaled) else scaled // JPEG has no transparency.
    }

    private fun onWhite(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(result).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, 0f, 0f, null)
        }
        return result
    }

    private companion object {
        const val MAX_SIDE_PX = 600
        const val JPEG_QUALITY = 85
        const val MAX_BYTES = 10 * 1024 * 1024
        const val BUFFER_BYTES = 16 * 1024
        const val DOWNLOAD_TIMEOUT_SECONDS = 20L
    }
}
