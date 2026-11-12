package com.trangnx.ttsbrowser.extraction

import android.content.res.AssetManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.IOException

/** Hosts of pure ad and tracker networks; a host is blocked when it or a parent domain is listed. */
class AdHostBlocklist(private val hosts: Set<String>) {

    fun isBlocked(host: String?): Boolean {
        var candidate = host?.trim('.')?.lowercase().orEmpty()
        while (candidate.contains('.')) {
            if (candidate in hosts) return true
            candidate = candidate.substringAfter('.')
        }
        return false
    }

    companion object {
        /** One host per line; blank lines and `#` comments are ignored. */
        fun parse(lines: Sequence<String>) = AdHostBlocklist(
            lines.map { it.substringBefore('#').trim().lowercase() }.filter { it.isNotEmpty() }.toSet(),
        )
    }
}

/**
 * Answers requests to ad hosts with an empty response so the page loads faster and shows no ads.
 * Main-frame navigations are never blocked. A missing or unreadable list just disables blocking.
 */
class AdBlocker(private val assets: AssetManager) {
    @Volatile var enabled: Boolean = true

    private val blocklist: AdHostBlocklist by lazy {
        try {
            assets.open(ASSET).bufferedReader().use { AdHostBlocklist.parse(it.lineSequence()) }
        } catch (_: IOException) {
            AdHostBlocklist(emptySet())
        }
    }

    /** Called on a WebView IO thread; null lets the request through. */
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (!enabled || request.isForMainFrame || !blocklist.isBlocked(request.url.host)) return null
        return WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    private companion object {
        const val ASSET = "ad-hosts.txt"
    }
}
