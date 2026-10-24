package com.trangnx.ttsbrowser.ui.browser

import java.net.URLEncoder

/** Turns address-bar input into a loadable URL: keeps URLs, completes domains, otherwise searches. */
object UrlInputResolver {
    private const val SEARCH_URL = "https://www.google.com/search?q="

    private val schemeRegex = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.+")
    private val hostRegex = Regex(
        """^(localhost|(\d{1,3}\.){3}\d{1,3}|([a-zA-Z0-9-]+\.)+[a-zA-Z]{2,})(:\d{1,5})?([/?#].*)?$""",
    )

    /** Returns `null` for blank input. */
    fun resolve(input: String): String? {
        val text = input.trim()
        return when {
            text.isEmpty() -> null
            schemeRegex.matches(text) -> text
            text.startsWith("about:") -> text
            !text.contains(' ') && hostRegex.matches(text) -> "https://$text"
            else -> SEARCH_URL + URLEncoder.encode(text, Charsets.UTF_8.name())
        }
    }
}
