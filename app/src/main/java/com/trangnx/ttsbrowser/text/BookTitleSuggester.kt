package com.trangnx.ttsbrowser.text

/** Guesses the story name from a chapter page's title ("Chương 5: X - Tên truyện - Tên site"). */
object BookTitleSuggester {
    private val separator = Regex("\\s+[-–—|·»]\\s+")
    private val chapterPrefix = Regex("^(chương|chuong|chapter|chap|tập|hồi|quyển)\\s*\\.?\\s*\\d", RegexOption.IGNORE_CASE)

    /**
     * Drops the segments that are the chapter title and, when something else is left, the trailing
     * site name; falls back to [chapterTitle] (or the whole [pageTitle]) so there is always something to edit.
     */
    fun suggest(pageTitle: String, chapterTitle: String): String {
        val segments = pageTitle.split(separator).map { it.trim() }.filter { it.isNotEmpty() }
        val rest = segments.filterNot { isChapter(it, chapterTitle) }
        val story = if (rest.size > 1) rest.first() else rest.firstOrNull()
        return story ?: segments.firstOrNull() ?: chapterTitle
    }

    private fun isChapter(segment: String, chapterTitle: String): Boolean =
        chapterPrefix.containsMatchIn(segment) ||
            (chapterTitle.isNotBlank() && (segment.contains(chapterTitle, ignoreCase = true) || chapterTitle.contains(segment, ignoreCase = true)))
}
