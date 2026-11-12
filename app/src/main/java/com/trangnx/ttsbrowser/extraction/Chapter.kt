package com.trangnx.ttsbrowser.extraction

/** Where the chapter text came from. */
enum class ContentSource { Readability, Selector }

/** A cleaned chapter extracted from a page. */
data class Chapter(
    val url: String,
    val title: String,
    val paragraphs: List<String>,
    val prevUrl: String?,
    val nextUrl: String?,
    val source: ContentSource,
    /** The page's `og:title` / `<title>`: usually also names the story and the site, see `BookTitleSuggester`. */
    val pageTitle: String = "",
    /** The page's `og:image` (absolute http(s) URL, or empty): the default cover of a saved story. */
    val coverUrl: String = "",
) {
    val length: Int get() = paragraphs.sumOf { it.length }
}
