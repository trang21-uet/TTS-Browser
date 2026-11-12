package com.trangnx.ttsbrowser.ui.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.trangnx.ttsbrowser.extraction.AdBlocker
import com.trangnx.ttsbrowser.extraction.applyCookieDefaults
import com.trangnx.ttsbrowser.extraction.applyPageDefaults
import com.trangnx.ttsbrowser.extraction.flushPageCookies

/** Page events forwarded from the WebView clients. */
interface BrowserPageListener {
    fun onPageStarted(url: String)
    fun onPageFinished(url: String)
    fun onProgressChanged(progress: Int)
    fun onTitleReceived(title: String)
    fun onHistoryChanged(url: String, isReload: Boolean, canGoBack: Boolean, canGoForward: Boolean)

    /** The renderer crashed or was killed; the WebView is replaced with a fresh one. */
    fun onRenderProcessGone()
}

/**
 * Creates the single browser WebView, kept for the whole Activity so it survives tab switches.
 * Paused/resumed with the lifecycle and destroyed when leaving composition or when its renderer dies.
 */
@Composable
fun rememberBrowserWebView(listener: BrowserPageListener, adBlocker: AdBlocker): WebView {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var generation by remember { mutableIntStateOf(0) }
    val webView = remember(context, generation) {
        createBrowserWebView(context, listener, adBlocker) {
            listener.onRenderProcessGone()
            generation++
        }
    }
    DisposableEffect(webView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                Lifecycle.Event.ON_PAUSE -> {
                    webView.onPause()
                    flushPageCookies()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }
    }
    return webView
}

private fun createBrowserWebView(
    context: Context,
    listener: BrowserPageListener,
    adBlocker: AdBlocker,
    onRenderGone: () -> Unit,
): WebView =
    WebView(context).apply {
        settings.applyPageDefaults()
        applyCookieDefaults()
        settings.apply {
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
        }
        webViewClient = BrowserWebViewClient(listener, adBlocker, onRenderGone)
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) = listener.onProgressChanged(newProgress)
            override fun onReceivedTitle(view: WebView, title: String?) = listener.onTitleReceived(title.orEmpty())
        }
    }

private class BrowserWebViewClient(
    private val listener: BrowserPageListener,
    private val adBlocker: AdBlocker,
    private val onRenderGone: () -> Unit,
) : WebViewClient() {

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) = adBlocker.intercept(request)

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val scheme = request.url.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") return false
        // Other schemes (tel:, mailto:, market:, ...) go to an external app if one exists.
        val intent = Intent(Intent.ACTION_VIEW, request.url).addCategory(Intent.CATEGORY_BROWSABLE)
        try {
            view.context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // No handler: ignore the navigation.
        } catch (_: SecurityException) {
            // Target not exported: ignore.
        }
        return true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        if (url != null) listener.onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        flushPageCookies()
        if (url != null) listener.onPageFinished(url)
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        // Returning true keeps the app alive; this WebView is unusable and gets recreated.
        onRenderGone()
        return true
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        if (url != null) listener.onHistoryChanged(url, isReload, view.canGoBack(), view.canGoForward())
    }
}
