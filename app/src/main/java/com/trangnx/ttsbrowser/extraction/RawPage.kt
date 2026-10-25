package com.trangnx.ttsbrowser.extraction

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** An `<a href>` on the page, as collected by `extract-page.js`. */
data class LinkCandidate(
    val href: String,
    val text: String = "",
    /** aria-label + title. */
    val label: String = "",
    /** id + class + rel. */
    val attrs: String = "",
    /** Classes (and img alt) of icon-like descendants, e.g. `fa fa-chevron-right`. */
    val icons: String = "",
    val disabled: Boolean = false,
)

data class PageLinks(
    val relNext: String? = null,
    val relPrev: String? = null,
    val anchors: List<LinkCandidate> = emptyList(),
)

/** Uncleaned result of `extract-page.js`. */
data class RawPage(
    val ok: Boolean,
    val url: String = "",
    val title: String = "",
    val pageTitle: String = "",
    val text: String = "",
    val source: ContentSource = ContentSource.Readability,
    val selectorMissed: Boolean = false,
    val links: PageLinks = PageLinks(),
    val error: String? = null,
)

/** Decodes values returned by `WebView.evaluateJavascript`, which are JSON literals. */
object JsResult {
    /** The script returned a string (e.g. a JSON.stringify result); `null` for anything else. */
    fun string(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
    }

    fun rawPage(raw: String?): RawPage {
        val json = string(raw) ?: return RawPage(ok = false, error = "no result")
        return runCatching { parseRawPage(JSONObject(json)) }
            .getOrElse { RawPage(ok = false, error = it.message ?: "invalid result") }
    }

    private fun parseRawPage(obj: JSONObject): RawPage = RawPage(
        ok = obj.optBoolean("ok"),
        url = obj.optString("url"),
        title = obj.optString("title"),
        pageTitle = obj.optString("pageTitle"),
        text = obj.optString("text"),
        source = if (obj.optString("source") == "selector") ContentSource.Selector else ContentSource.Readability,
        selectorMissed = obj.optBoolean("selectorMissed"),
        links = obj.optJSONObject("links")?.let(::parseLinks) ?: PageLinks(),
        error = obj.optNullableString("error"),
    )

    private fun parseLinks(obj: JSONObject): PageLinks = PageLinks(
        relNext = obj.optNullableString("relNext"),
        relPrev = obj.optNullableString("relPrev"),
        anchors = obj.optJSONArray("anchors")?.let(::parseAnchors).orEmpty(),
    )

    private fun parseAnchors(array: JSONArray): List<LinkCandidate> = (0 until array.length()).mapNotNull { i ->
        val a = array.optJSONObject(i) ?: return@mapNotNull null
        val href = a.optString("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        LinkCandidate(
            href = href,
            text = a.optString("text"),
            label = a.optString("label"),
            attrs = a.optString("attrs"),
            icons = a.optString("icons"),
            disabled = a.optBoolean("disabled"),
        )
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
}
