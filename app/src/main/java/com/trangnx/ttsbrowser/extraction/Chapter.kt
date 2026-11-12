package com.trangnx.ttsbrowser.extraction

/** Where the chapter text came from. */
enum class ContentSource { Readability, Selector }

/**
 * A cleaned chapter extracted from a page. When [startsWithTitle] is set, `paragraphs[0]` is [title],
 * so the title is read aloud like the rest of the text.
 */
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
    val startsWithTitle: Boolean = false,
) {
    /** Length of the story text, without the title paragraph. */
    val length: Int get() = paragraphs.drop(if (startsWithTitle) 1 else 0).sumOf { it.length }
}
