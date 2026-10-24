package com.trangnx.ttsbrowser.extraction

/**
 * Normalizes extracted page text into paragraphs: one paragraph per non-blank line,
 * whitespace collapsed, navigation/watermark lines dropped.
 */
object TextCleaner {
    private val invisibleChars = Regex("[\\u200B-\\u200D\\u2060\\uFEFF\\u00AD]")
    private val spaces = Regex("[\\s\\u00A0\\u3000]+")
    private val navSeparators = Regex("[|•·/\\\\<>«»‹›←→\\-–—\\[\\]()]+")
    private val domain = Regex("\\b[a-z0-9-]+\\.(com|net|vn|org|info|xyz|me|io|co|top|site|tv)\\b")
    // Specific phrases only: common words like "tại"/"đọc" also appear in story sentences that mention a domain.
    private val watermarkPhrases = listOf(
        "đọc truyện", "đọc full", "đọc chương", "truy cập", "nguồn:", "website", "theo dõi", "ủng hộ",
    )

    private val navPhrases = setOf(
        "chương trước", "chương sau", "chương tiếp", "chương tiếp theo", "chương kế", "chap trước", "chap sau",
        "mục lục", "danh sách chương", "trước", "sau", "tiếp", "tiếp theo", "báo lỗi", "báo lỗi chương",
        "prev", "previous", "next", "back", "index", "previous chapter", "next chapter", "table of contents",
    )

    private const val WATERMARK_MAX_LENGTH = 150
    private const val AD_MAX_LENGTH = 80

    fun paragraphs(raw: String, title: String = ""): List<String> {
        val lines = raw.replace(invisibleChars, "")
            .split('\n')
            .map { it.replace(spaces, " ").trim() }
            .filter { it.isNotEmpty() && !isJunk(it) }
        // Content often repeats the chapter heading as its first line.
        val normalizedTitle = title.replace(spaces, " ").trim()
        return if (normalizedTitle.isEmpty()) lines else lines.dropWhile { it.equals(normalizedTitle, ignoreCase = true) }
    }

    internal fun isJunk(line: String): Boolean {
        if (line.none { it.isLetterOrDigit() }) return true
        val lower = line.lowercase()
        val parts = lower.split(navSeparators).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isNotEmpty() && parts.all { it in navPhrases }) return true
        if (lower.length <= WATERMARK_MAX_LENGTH && domain.containsMatchIn(lower) && watermarkPhrases.any { it in lower }) {
            return true
        }
        return lower.length <= AD_MAX_LENGTH && "quảng cáo" in lower
    }
}
