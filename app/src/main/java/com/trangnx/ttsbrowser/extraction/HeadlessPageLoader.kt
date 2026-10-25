package com.trangnx.ttsbrowser.extraction

import android.content.Context
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Loads a page in a WebView that is not attached to any UI and extracts its chapter, for opening a
 * saved story or the next chapter while the browser tab is not showing it (e.g. from the notification).
 * Shares cookies with the browser tab, so a site that needs a login or passed a challenge there works here too.
 */
class HeadlessPageLoader(context: Context, private val extractor: ReadabilityExtractor) {
    private val appContext = context.applicationContext

    suspend fun load(url: String): ExtractionResult = withContext(Dispatchers.Main) { loadAndExtract(url) }

    private suspend fun loadAndExtract(url: String): ExtractionResult = coroutineScope {
        val webView = WebView(appContext)
        val settled = CompletableDeferred<String>() // LOADED, otherwise why not
        var settle: Job? = null
        try {
            webView.settings.applyPageDefaults()
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.scheme?.lowercase().let { it != "http" && it != "https" }

                // Redirects and challenge pages finish more than once: wait until no other load follows.
                override fun onPageFinished(view: WebView, url: String?) {
                    settle?.cancel()
                    settle = launch {
                        delay(SETTLE_MS)
                        settled.complete(LOADED)
                    }
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) settled.complete(error.description.toString())
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    settled.complete("renderer gone")
                    return true
                }
            }
            webView.loadUrl(url)
            val error = withTimeoutOrNull(LOAD_TIMEOUT_MS) { settled.await() } ?: "timeout"
            if (error == LOADED) {
                extractor.extract(webView.asScriptRunner(), webView.url ?: url)
            } else {
                ExtractionResult.Failure(ExtractionResult.Reason.LoadFailed, error)
            }
        } finally {
            settle?.cancel()
            webView.stopLoading()
            webView.destroy()
        }
    }

    private companion object {
        const val LOADED = ""
        const val LOAD_TIMEOUT_MS = 15_000L
        const val SETTLE_MS = 500L
    }
}
