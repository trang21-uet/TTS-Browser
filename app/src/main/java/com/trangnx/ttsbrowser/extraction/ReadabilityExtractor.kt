package com.trangnx.ttsbrowser.extraction

import com.trangnx.ttsbrowser.data.repo.SiteRuleRepository
import com.trangnx.ttsbrowser.text.Sentence
import com.trangnx.ttsbrowser.text.SentenceSegmenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

sealed interface ExtractionResult {
    /** [selectorMissed]: the site's saved selector no longer matched, Readability was used instead. */
    data class Success(val chapter: Chapter, val sentences: List<Sentence>, val selectorMissed: Boolean) : ExtractionResult

    data class Failure(val reason: Reason, val detail: String? = null) : ExtractionResult

    /** [LoadFailed]: the page itself did not load (headless loading only). */
    enum class Reason { NoContent, TooShort, ScriptError, LoadFailed }
}

/**
 * Extracts the current page of a WebView into a [Chapter]: the site's saved selector if any,
 * otherwise Readability.js; then cleans the text, finds chapter links and splits sentences.
 */
class ReadabilityExtractor(
    private val scripts: ExtractionScripts,
    private val siteRules: SiteRuleRepository,
    private val segmenter: SentenceSegmenter = SentenceSegmenter(),
) {
    /** Readability.js + extract-page.js wrapped in an IIFE, built once; only the args differ per call. */
    @Volatile
    private var scriptPrefix: String? = null

    suspend fun extract(runner: PageScriptRunner, pageUrl: String): ExtractionResult {
        val selector = try {
            siteRules.selectorFor(pageUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // A broken rule lookup should not block extraction.
        }
        val first = run(runner, selector) ?: return TIMEOUT
        val result = toResult(first, pageUrl)
        if (first.source == ContentSource.Selector && (result as? Failure)?.reason == ExtractionResult.Reason.TooShort) {
            // The saved region matched but is (nearly) empty on this page: let Readability try.
            val fallback = run(runner, selector = null) ?: return TIMEOUT
            return toResult(fallback.copy(selectorMissed = true), pageUrl)
        }
        return result
    }

    private suspend fun run(runner: PageScriptRunner, selector: String?): RawPage? {
        val args = JSONObject().put("selector", selector ?: JSONObject.NULL)
        val script = "${scriptPrefix()}$args);"
        return try {
            JsResult.rawPage(withTimeout(TIMEOUT_MS) { runner.evaluate(script) })
        } catch (_: TimeoutCancellationException) {
            null
        }
    }

    private suspend fun scriptPrefix(): String = scriptPrefix ?: buildString {
        append("(function(args){\n")
        append(scripts.load(READABILITY_JS)).append('\n')
        append(scripts.load(EXTRACT_JS)).append('\n')
        append("return ttsExtractPage(args);\n})(")
    }.also { scriptPrefix = it }

    private fun toResult(raw: RawPage, pageUrl: String): ExtractionResult {
        if (raw.error != null) return Failure(ExtractionResult.Reason.ScriptError, raw.error)
        if (!raw.ok) return Failure(ExtractionResult.Reason.NoContent)
        val chapter = toChapter(raw, pageUrl)
        if (chapter.length < MIN_CONTENT_LENGTH) return Failure(ExtractionResult.Reason.TooShort)
        return ExtractionResult.Success(chapter, segmenter.segment(chapter.paragraphs), raw.selectorMissed)
    }

    private fun toChapter(raw: RawPage, pageUrl: String): Chapter {
        val url = raw.url.ifBlank { pageUrl }
        val links = ChapterLinkFinder.find(url, raw.links)
        return Chapter(
            url = url,
            title = raw.title,
            paragraphs = TextCleaner.paragraphs(raw.text, raw.title),
            prevUrl = links.prevUrl,
            nextUrl = links.nextUrl,
            source = raw.source,
            pageTitle = raw.pageTitle,
            coverUrl = raw.coverUrl,
        )
    }

    private companion object {
        const val READABILITY_JS = "Readability.js"
        const val EXTRACT_JS = "extract-page.js"
        const val MIN_CONTENT_LENGTH = 200
        const val TIMEOUT_MS = 10_000L
        val TIMEOUT = Failure(ExtractionResult.Reason.ScriptError, "timeout")
    }
}

private typealias Failure = ExtractionResult.Failure
