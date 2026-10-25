package com.trangnx.ttsbrowser.ui.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The [PageRequest] the reader left for the browser page. A page change drops it, except a chapter
 * extraction on its way: from the page it was asked on to the chapter URL and one redirect from there.
 */
class PendingPageRequest {
    private val _value = MutableStateFlow<PageRequest?>(null)
    val value: StateFlow<PageRequest?> = _value.asStateFlow()

    fun set(request: PageRequest?) {
        _value.value = request
    }

    /** [previous] and [page] are [pageOf] forms of the old and new browser URL. */
    fun onPageChanged(previous: String?, page: String) {
        val request = _value.value as? PageRequest.ExtractWhenLoaded
        val onTheWay = request != null && (
            page == pageOf(request.url) ||
                previous == request.fromUrl?.let(::pageOf) ||
                previous == pageOf(request.url)
            )
        if (!onTheWay) _value.value = null
    }
}
