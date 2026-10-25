package com.trangnx.ttsbrowser.ui.browser

import com.trangnx.ttsbrowser.extraction.ExtractionResult
import com.trangnx.ttsbrowser.extraction.PickedElement

/** A successful extraction loads the chapter and opens the reader, so there is no "ready" state here. */
sealed interface ExtractionUiState {
    data object Idle : ExtractionUiState
    data object Extracting : ExtractionUiState
    data class Failed(val reason: ExtractionResult.Reason) : ExtractionUiState

    data class Picking(val picked: PickedElement? = null, val error: PickError? = null) : ExtractionUiState
}

enum class PickError {
    /** No unique selector could be built for the picked element. */
    NoSelector,

    /** The page has no http(s) host to remember the selector for. */
    CannotSave,
}

/** Work the reader asks of the browser page, run once the browser is shown again. */
sealed interface PageRequest {
    /** Pick the content region by hand. */
    data object Pick : PageRequest

    /** Forget the site's selector and extract with Readability. */
    data object ClearRule : PageRequest

    /**
     * Extract the page once [url], asked while the browser showed [fromUrl], has loaded (previous/next
     * chapter from the reader). Reached by [url] itself or by any other page than [fromUrl] (a redirect).
     */
    data class ExtractWhenLoaded(val url: String, val fromUrl: String?) : PageRequest {
        fun isReachedBy(pageUrl: String): Boolean =
            pageOf(pageUrl) == pageOf(url) || fromUrl == null || pageOf(pageUrl) != pageOf(fromUrl)
    }
}

/** A chapter to show in the reader; [continuing]: previous/next chapter asked from the reader. */
data class ReaderRequest(val result: ExtractionResult.Success, val continuing: Boolean)

/** The page part of [url]: fragments do not make another page. */
fun pageOf(url: String): String = url.substringBefore('#')
