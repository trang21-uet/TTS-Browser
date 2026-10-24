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
) {
    val length: Int get() = paragraphs.sumOf { it.length }
}
