package com.trangnx.ttsbrowser.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.trangnx.ttsbrowser.TtsBrowserApp
import com.trangnx.ttsbrowser.data.repo.SiteRuleRepository
import com.trangnx.ttsbrowser.extraction.ElementPicker
import com.trangnx.ttsbrowser.extraction.ExtractionResult
import com.trangnx.ttsbrowser.extraction.PageScriptRunner
import com.trangnx.ttsbrowser.extraction.PickerStatus
import com.trangnx.ttsbrowser.extraction.ReadabilityExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "Đọc" flow on the browser page: extraction, manual region picking and per-site selectors.
 * A successful extraction asks for the reader ([openReader]); it does not touch the player.
 * Calls take a [PageScriptRunner] bound to the WebView, which stays in the UI layer.
 * Activity-scoped: the reader sends [PageRequest]s that run when the browser is shown again.
 */
class ExtractionViewModel(
    private val extractor: ReadabilityExtractor,
    private val picker: ElementPicker,
    private val siteRules: SiteRuleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ExtractionUiState>(ExtractionUiState.Idle)
    val state: StateFlow<ExtractionUiState> = _state.asStateFlow()

    private val _pageResult = MutableStateFlow<ExtractionResult.Success?>(null)
    /** Last successful extraction of the page the browser shows; cleared when the page changes. */
    val pageResult: StateFlow<ExtractionResult.Success?> = _pageResult.asStateFlow()

    private val pending = PendingPageRequest()
    val request: StateFlow<PageRequest?> = pending.value

    private val readerRequests = Channel<ReaderRequest>(Channel.CONFLATED)
    /** Emits each successful extraction; collected for the whole Activity, so never stale. */
    val openReader: Flow<ReaderRequest> = readerRequests.receiveAsFlow()

    private var job: Job? = null
    private var pageUrl: String? = null

    fun extract(runner: PageScriptRunner, url: String) {
        pending.set(null)
        launch { runExtraction(runner, url) }
    }

    /** Sets (or with null, drops: the user navigates themselves) the reader's request for the page. */
    fun request(request: PageRequest?) = pending.set(request)

    /** Runs the pending [request] on the page at [url]; extraction waits until the page has loaded. */
    fun handleRequest(runner: PageScriptRunner, url: String, isLoading: Boolean) {
        when (val request = pending.value.value) {
            null -> return
            PageRequest.Pick -> startPicking(runner)
            PageRequest.ClearRule -> clearSiteRule(runner, url)
            is PageRequest.ExtractWhenLoaded -> {
                if (isLoading || !request.isReachedBy(url)) return
                launch { runExtraction(runner, url, continuing = true) }
            }
        }
        pending.set(null)
    }

    fun dismiss() {
        job?.cancel()
        _state.value = ExtractionUiState.Idle
    }

    fun startPicking(runner: PageScriptRunner) = launch {
        picker.start(runner)
        _state.value = ExtractionUiState.Picking()
    }

    /**
     * One poll of the page picker; the UI calls it in a sequential loop while picking (the page reports
     * taps only when asked). A picker that vanished (reload, new document) ends picking.
     */
    suspend fun pollPick(runner: PageScriptRunner) {
        val current = _state.value as? ExtractionUiState.Picking ?: return
        if (job?.isActive == true) return // expand/confirm in flight owns the state
        val status = try {
            withTimeoutOrNull(PICK_POLL_TIMEOUT_MS) { picker.status(runner) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return
        if (_state.value != current || job?.isActive == true) return
        _state.value = when (status) {
            PickerStatus.Gone -> ExtractionUiState.Idle
            is PickerStatus.Active ->
                if (status.picked == current.picked) current else ExtractionUiState.Picking(status.picked)
        }
    }

    fun expandPick(runner: PageScriptRunner) = launch {
        _state.value = when (val status = picker.expand(runner)) {
            PickerStatus.Gone -> ExtractionUiState.Idle
            is PickerStatus.Active -> ExtractionUiState.Picking(status.picked)
        }
    }

    fun confirmPick(runner: PageScriptRunner, url: String) = launch {
        val picked = (_state.value as? ExtractionUiState.Picking)?.picked
        val selector = picker.selector(runner)
        if (selector == null) {
            _state.value = ExtractionUiState.Picking(picked, PickError.NoSelector)
            return@launch
        }
        if (!siteRules.save(url, selector)) {
            _state.value = ExtractionUiState.Picking(picked, PickError.CannotSave)
            return@launch
        }
        picker.stop(runner)
        runExtraction(runner, url)
    }

    fun cancelPick(runner: PageScriptRunner) = launch {
        picker.stop(runner)
        _state.value = ExtractionUiState.Idle
    }

    /** Forgets the site's selector and extracts again with Readability. */
    fun clearSiteRule(runner: PageScriptRunner, url: String) = launch {
        siteRules.clear(url)
        runExtraction(runner, url)
    }

    /**
     * Navigating away drops whatever belongs to the old page: a running extraction, its result
     * or error, requests for it, and the picker (stopped too, in case the page changed URL without
     * a new document). A chapter extraction on its way is kept (see [PendingPageRequest]).
     */
    fun onPageUrlChanged(runner: PageScriptRunner, url: String) {
        val page = pageOf(url)
        if (page == pageUrl) return
        val previous = pageUrl
        pageUrl = page
        _pageResult.value = null
        pending.onPageChanged(previous, page)
        if (previous == null || _state.value == ExtractionUiState.Idle) return
        val wasPicking = _state.value is ExtractionUiState.Picking
        job?.cancel()
        _state.value = ExtractionUiState.Idle
        if (wasPicking) viewModelScope.launch { picker.stopQuietly(runner) }
    }

    private suspend fun runExtraction(runner: PageScriptRunner, url: String, continuing: Boolean = false) {
        _state.value = ExtractionUiState.Extracting
        _state.value = when (val result = extractor.extract(runner, url)) {
            is ExtractionResult.Success -> {
                _pageResult.value = result
                readerRequests.trySend(ReaderRequest(result, continuing))
                ExtractionUiState.Idle
            }
            is ExtractionResult.Failure -> ExtractionUiState.Failed(result.reason)
        }
    }

    /** Runs one action at a time; a new action cancels the running one. Errors end in [ExtractionUiState.Failed]. */
    private fun launch(block: suspend () -> Unit) {
        job?.cancel()
        job = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = ExtractionUiState.Failed(ExtractionResult.Reason.ScriptError)
            }
        }
    }

    companion object {
        private const val PICK_POLL_TIMEOUT_MS = 2_000L

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TtsBrowserApp).container
                ExtractionViewModel(container.readabilityExtractor, container.elementPicker, container.siteRuleRepository)
            }
        }
    }
}
