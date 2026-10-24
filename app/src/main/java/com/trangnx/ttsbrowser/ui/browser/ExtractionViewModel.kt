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
import com.trangnx.ttsbrowser.extraction.PickedElement
import com.trangnx.ttsbrowser.extraction.PickerStatus
import com.trangnx.ttsbrowser.extraction.ReadabilityExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface ExtractionUiState {
    data object Idle : ExtractionUiState
    data object Extracting : ExtractionUiState
    data class Ready(val result: ExtractionResult.Success) : ExtractionUiState
    data class Failed(val reason: ExtractionResult.Reason) : ExtractionUiState

    data class Picking(val picked: PickedElement? = null, val error: PickError? = null) : ExtractionUiState
}

enum class PickError {
    /** No unique selector could be built for the picked element. */
    NoSelector,

    /** The page has no http(s) host to remember the selector for. */
    CannotSave,
}

/**
 * "Đọc" flow on the browser page: extraction, manual region picking and per-site selectors.
 * Calls take a [PageScriptRunner] bound to the WebView, which stays in the UI layer.
 */
class ExtractionViewModel(
    private val extractor: ReadabilityExtractor,
    private val picker: ElementPicker,
    private val siteRules: SiteRuleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ExtractionUiState>(ExtractionUiState.Idle)
    val state: StateFlow<ExtractionUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var pageUrl: String? = null

    fun extract(runner: PageScriptRunner, url: String) = launch { runExtraction(runner, url) }

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
     * or error, and the picker (stopped too, in case the page changed URL without a new document).
     */
    fun onPageUrlChanged(runner: PageScriptRunner, url: String) {
        val page = url.substringBefore('#')
        if (page == pageUrl) return
        val first = pageUrl == null
        pageUrl = page
        if (first || _state.value == ExtractionUiState.Idle) return
        val wasPicking = _state.value is ExtractionUiState.Picking
        job?.cancel()
        _state.value = ExtractionUiState.Idle
        if (wasPicking) {
            viewModelScope.launch {
                try {
                    picker.stop(runner)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Best effort: the old document is usually gone already.
                }
            }
        }
    }

    private suspend fun runExtraction(runner: PageScriptRunner, url: String) {
        _state.value = ExtractionUiState.Extracting
        _state.value = when (val result = extractor.extract(runner, url)) {
            is ExtractionResult.Success -> ExtractionUiState.Ready(result)
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
