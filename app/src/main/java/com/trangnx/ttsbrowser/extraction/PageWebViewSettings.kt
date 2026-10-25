package com.trangnx.ttsbrowser.extraction

import android.annotation.SuppressLint
import android.webkit.WebSettings

/** Settings every page-loading WebView (browser tab, headless loader) shares: scripts on, no local file access. */
@SuppressLint("SetJavaScriptEnabled")
fun WebSettings.applyPageDefaults() {
    javaScriptEnabled = true
    domStorageEnabled = true
    allowFileAccess = false
    allowContentAccess = false
    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
}
