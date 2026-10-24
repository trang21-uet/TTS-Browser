package com.trangnx.ttsbrowser.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.trangnx.ttsbrowser.TtsBrowserApp
import com.trangnx.ttsbrowser.data.db.BookmarkEntity
import com.trangnx.ttsbrowser.data.db.HistoryEntity
import com.trangnx.ttsbrowser.data.repo.BrowsingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowserUiState(
    val url: String = "",
    val title: String = "",
    val progress: Int = 100,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isBookmarked: Boolean = false,
) {
    val isLoading: Boolean get() = progress < 100
}

/** Holds browser page state and persists history/bookmarks. The WebView itself lives in the UI layer. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModel(private val repository: BrowsingRepository) : ViewModel(), BrowserPageListener {

    private val page = MutableStateFlow(BrowserUiState())
    private var recordJob: Job? = null
    private var currentHistoryId: Long? = null

    private val isBookmarked = page.map { it.url }.distinctUntilChanged().flatMapLatest { url ->
        if (url.isBlank()) flowOf(false) else repository.isBookmarked(url)
    }

    val uiState: StateFlow<BrowserUiState> = combine(page, isBookmarked) { state, bookmarked ->
        state.copy(isBookmarked = bookmarked)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowserUiState())

    val history: StateFlow<List<HistoryEntity>> = repository.recentHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.bookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    override fun onPageStarted(url: String) {
        page.update { it.copy(url = url, progress = 0) }
    }

    override fun onPageFinished(url: String) {
        page.update { it.copy(url = url, progress = 100) }
    }

    override fun onProgressChanged(progress: Int) {
        page.update { it.copy(progress = progress) }
    }

    override fun onTitleReceived(title: String) {
        page.update { it.copy(title = title) }
        if (title.isBlank()) return
        val job = recordJob
        viewModelScope.launch {
            job?.join()
            currentHistoryId?.let { repository.updateHistoryTitle(it, title) }
        }
    }

    override fun onHistoryChanged(url: String, isReload: Boolean, canGoBack: Boolean, canGoForward: Boolean) {
        page.update { it.copy(url = url, canGoBack = canGoBack, canGoForward = canGoForward) }
        if (isReload || !url.startsWith("http")) return
        val previous = recordJob
        recordJob = viewModelScope.launch {
            previous?.join()
            currentHistoryId = runCatching { repository.recordVisit(url, title = "") }.getOrNull()
        }
    }

    override fun onRenderProcessGone() {
        page.value = BrowserUiState()
        currentHistoryId = null
    }

    fun toggleBookmark() {
        val state = uiState.value
        if (state.url.isBlank()) return
        viewModelScope.launch {
            if (state.isBookmarked) {
                repository.removeBookmark(state.url)
            } else {
                repository.addBookmark(state.url, state.title.ifBlank { state.url })
            }
        }
    }

    fun deleteHistory(id: Long) {
        viewModelScope.launch { repository.deleteHistory(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    fun removeBookmark(url: String) {
        viewModelScope.launch { repository.removeBookmark(url) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TtsBrowserApp
                BrowserViewModel(app.container.browsingRepository)
            }
        }
    }
}
