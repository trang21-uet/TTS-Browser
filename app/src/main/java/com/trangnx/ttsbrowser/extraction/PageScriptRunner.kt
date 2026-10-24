package com.trangnx.ttsbrowser.extraction

import android.content.res.AssetManager
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/** Runs JavaScript in a loaded page and returns the result as a JSON literal (`evaluateJavascript` semantics). */
fun interface PageScriptRunner {
    suspend fun evaluate(script: String): String?
}

/** Must be called and evaluated on the main thread, like every WebView API. */
fun WebView.asScriptRunner(): PageScriptRunner = PageScriptRunner { script ->
    suspendCancellableCoroutine { continuation ->
        evaluateJavascript(script) { result -> if (continuation.isActive) continuation.resume(result) }
    }
}

/** Loads and caches the scripts bundled in `assets/js/`. */
class ExtractionScripts(private val assets: AssetManager) {
    private val cache = ConcurrentHashMap<String, String>()

    suspend fun load(name: String): String = cache[name] ?: withContext(Dispatchers.IO) {
        assets.open("js/$name").bufferedReader().use { it.readText() }
    }.also { cache[name] = it }
}
