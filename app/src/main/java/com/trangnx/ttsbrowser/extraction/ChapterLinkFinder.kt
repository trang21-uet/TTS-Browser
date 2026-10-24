package com.trangnx.ttsbrowser.extraction

/**
 * Picks the previous/next chapter links from the page's anchors by scoring signals:
 * `rel` links, link text/aria, id/class tokens, icon classes and the chapter number in the URL.
 */
object ChapterLinkFinder {
    data class Result(val prevUrl: String?, val nextUrl: String?)

    private enum class Direction { Prev, Next }

    private const val REL_SCORE = 100
    private const val PHRASE_SCORE = 60
    private const val SYMBOL_SCORE = 50
    private const val ATTR_SCORE = 30
    private const val ICON_SCORE = 25
    private const val NUMBER_SCORE = 20
    private const val NUMBER_MISMATCH_PENALTY = 40
    private const val MIN_SCORE = 20
    private const val MAX_LABEL_WORDS = 3

    private val nextPhrases = listOf("chương sau", "chương tiếp", "chương kế", "chap sau", "chap tiếp", "tiếp theo", "next chapter")
    private val prevPhrases = listOf("chương trước", "chap trước", "previous chapter", "prev chapter")
    private val nextWords = setOf("sau", "tiếp", "next", "›", "»", "→", ">", ">>", "⟩", "▶", "►")
    private val prevWords = setOf("trước", "prev", "previous", "‹", "«", "←", "<", "<<", "⟨", "◀", "◄")
    private val nextEnglish = Regex("\\bnext\\b")
    private val prevEnglish = Regex("\\bprev(ious)?\\b")
    private val tokenSplit = Regex("[^a-z0-9]+")
    private val nextTokens = setOf("next", "nxt", "forward")
    private val prevTokens = setOf("prev", "previous", "back", "pre")
    private val nextIconTokens = setOf("right", "next", "forward")
    private val prevIconTokens = setOf("left", "prev", "back", "backward")
    // Path only, and the prefix must start a word: "sach24.vn" or "/watch10" are not chapter 24 / 10.
    private val chapterNumber = Regex("(?<![a-z0-9])(?:chuong|chapter|chap|ch)[-_/.]?(\\d+)", RegexOption.IGNORE_CASE)
    private val urlParts = Regex("^https?://([^/?#]+)([^?#]*)", RegexOption.IGNORE_CASE)
    private val spaces = Regex("\\s+")

    fun find(pageUrl: String, links: PageLinks): Result {
        val page = withoutFragment(pageUrl)
        val current = chapterRefOf(pageUrl)
        val usable = links.anchors.filter { isUsable(it, page) }

        fun best(direction: Direction, rel: String?, exclude: String?): Pair<String, Int>? {
            val scores = LinkedHashMap<String, Int>()
            rel?.takeIf { isNavigable(it, page) }?.let { scores[it] = REL_SCORE }
            for (anchor in usable) {
                val score = score(anchor, direction, current)
                if (score > (scores[anchor.href] ?: Int.MIN_VALUE)) scores[anchor.href] = score
            }
            // maxBy keeps the first maximum, i.e. document order breaks ties.
            return scores.entries.filter { it.key != exclude && it.value >= MIN_SCORE }
                .maxByOrNull { it.value }?.toPair()
        }

        var next = best(Direction.Next, links.relNext, exclude = null)
        var prev = best(Direction.Prev, links.relPrev, exclude = null)
        if (next != null && prev != null && next.first == prev.first) {
            // One link can't be both: keep the stronger direction and look again for the other.
            if (next.second >= prev.second) {
                prev = best(Direction.Prev, links.relPrev, exclude = next.first)
            } else {
                next = best(Direction.Next, links.relNext, exclude = prev.first)
            }
        }
        return Result(prevUrl = prev?.first, nextUrl = next?.first)
    }

    private fun score(anchor: LinkCandidate, direction: Direction, current: ChapterRef?): Int {
        var score = 0
        score += textScore(anchor, direction)
        score += tokenScore(anchor.attrs, direction, nextTokens, prevTokens, ATTR_SCORE)
        score += tokenScore(anchor.icons, direction, nextIconTokens, prevIconTokens, ICON_SCORE)
        val ref = chapterRefOf(anchor.href)
        // Numbers only compare within one story: /truyen-b/chuong-51 is not the next of /truyen-a/chuong-50.
        if (current != null && ref != null && ref.story == current.story) {
            val expected = if (direction == Direction.Next) current.number + 1 else current.number - 1
            score += if (ref.number == expected) NUMBER_SCORE else -NUMBER_MISMATCH_PENALTY
        }
        return score
    }

    private fun textScore(anchor: LinkCandidate, direction: Direction): Int {
        val text = "${anchor.text} ${anchor.label}".lowercase().replace(spaces, " ").trim()
        if (text.isEmpty()) return 0
        val (own, other) = if (direction == Direction.Next) nextPhrases to prevPhrases else prevPhrases to nextPhrases
        if (own.any { it in text }) return PHRASE_SCORE
        if (other.any { it in text }) return 0
        // Short labels only ("Sau »", "‹ Prev"): a long text starting with "Sau" is a title, not navigation.
        val parts = text.split(' ')
        if (parts.size > MAX_LABEL_WORDS) return 0
        val words = if (direction == Direction.Next) nextWords else prevWords
        if (parts.first() in words || parts.last() in words) return SYMBOL_SCORE
        val english = if (direction == Direction.Next) nextEnglish else prevEnglish
        return if (english.containsMatchIn(text)) SYMBOL_SCORE else 0
    }

    /** Scores only when the tokens point one way; `btn-back-next` (both) is ambiguous. */
    private fun tokenScore(value: String, direction: Direction, next: Set<String>, prev: Set<String>, weight: Int): Int {
        if (value.isBlank()) return 0
        val tokens = value.lowercase().split(tokenSplit).toSet()
        val isNext = tokens.any { it in next }
        val isPrev = tokens.any { it in prev }
        if (isNext == isPrev) return 0
        return if (isNext == (direction == Direction.Next)) weight else 0
    }

    private fun isUsable(anchor: LinkCandidate, page: String): Boolean = !anchor.disabled && isNavigable(anchor.href, page)

    private fun isNavigable(href: String, page: String): Boolean =
        (href.startsWith("http://") || href.startsWith("https://")) && withoutFragment(href) != page

    private fun withoutFragment(url: String): String = url.substringBefore('#').trimEnd('/')

    /** [story] is host + path before the chapter marker, e.g. `site.vn/truyen-a/`. */
    internal data class ChapterRef(val story: String, val number: Int)

    internal fun chapterRefOf(url: String): ChapterRef? {
        val parts = urlParts.find(url) ?: return null
        val path = parts.groupValues[2]
        val match = chapterNumber.findAll(path).lastOrNull() ?: return null
        val number = match.groupValues[1].toIntOrNull() ?: return null
        return ChapterRef(story = (parts.groupValues[1] + path.substring(0, match.range.first)).lowercase(), number = number)
    }
}
