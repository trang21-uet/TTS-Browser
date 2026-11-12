package com.trangnx.ttsbrowser.extraction

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Cookie setup every page-loading WebView shares, so a login made in the browser tab also works in the
 * headless loader. Third-party cookies are off by default but many sites keep the login on a second domain.
 */
fun WebView.applyCookieDefaults() {
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(this@applyCookieDefaults, true)
    }
}

/** Writes cookies to disk now: WebView only does it lazily, and a login is lost if the process dies first. */
fun flushPageCookies() = CookieManager.getInstance().flush()

/** Settings every page-loading WebView (browser tab, headless loader) shares: scripts on, no local file access. */
@SuppressLint("SetJavaScriptEnabled")
fun WebSettings.applyPageDefaults() {
    javaScriptEnabled = true
    domStorageEnabled = true
    allowFileAccess = false
    allowContentAccess = false
    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
}
