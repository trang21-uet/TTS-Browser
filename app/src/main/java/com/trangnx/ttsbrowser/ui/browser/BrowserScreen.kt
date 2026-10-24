package com.trangnx.ttsbrowser.ui.browser

import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trangnx.ttsbrowser.R

private enum class BrowserSheet { None, History, Bookmarks }

/** Browser tab: address bar, progress, WebView (or the bookmark start page) and history/bookmark sheets. */
@Composable
fun BrowserScreen(viewModel: BrowserViewModel, webView: WebView) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(BrowserSheet.None) }

    val open: (String) -> Unit = { url ->
        sheet = BrowserSheet.None
        webView.loadUrl(url)
    }
    val bookmarkLinks = bookmarks.map { PageLink(it.id, it.url, it.title) }

    BackHandler(enabled = state.canGoBack) { webView.goBack() }

    Column(Modifier.fillMaxSize()) {
        BrowserAddressBar(
            state = state,
            actions = BrowserBarActions(
                onSubmit = { input -> UrlInputResolver.resolve(input)?.let(open) },
                onReloadOrStop = { if (state.isLoading) webView.stopLoading() else webView.reload() },
                onBack = webView::goBack,
                onForward = webView::goForward,
                onToggleBookmark = viewModel::toggleBookmark,
                onShowHistory = { sheet = BrowserSheet.History },
                onShowBookmarks = { sheet = BrowserSheet.Bookmarks },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (state.isLoading) {
                LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxSize())
            }
        }
        if (state.url.isBlank()) {
            PageLinkList(
                links = bookmarkLinks,
                emptyMessage = stringResource(R.string.browser_start_empty),
                onOpen = { open(it.url) },
                onDelete = { viewModel.removeBookmark(it.url) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            key(webView) {
                AndroidView(
                    factory = {
                        // The WebView outlives this screen; detach it from a previous holder first.
                        (webView.parent as? ViewGroup)?.removeView(webView)
                        webView
                    },
                    onRelease = { (it.parent as? ViewGroup)?.removeView(it) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    when (sheet) {
        BrowserSheet.None -> Unit
        BrowserSheet.History -> {
            val history by viewModel.history.collectAsStateWithLifecycle()
            PageLinkSheet(
                title = stringResource(R.string.browser_history),
                links = history.map { PageLink(it.id, it.url, it.title) },
                emptyMessage = stringResource(R.string.browser_history_empty),
                onDismiss = { sheet = BrowserSheet.None },
                onOpen = { open(it.url) },
                onDelete = { viewModel.deleteHistory(it.key) },
                onClearAll = viewModel::clearHistory,
            )
        }
        BrowserSheet.Bookmarks -> PageLinkSheet(
            title = stringResource(R.string.browser_bookmarks),
            links = bookmarkLinks,
            emptyMessage = stringResource(R.string.browser_bookmarks_empty),
            onDismiss = { sheet = BrowserSheet.None },
            onOpen = { open(it.url) },
            onDelete = { viewModel.removeBookmark(it.url) },
        )
    }
}
