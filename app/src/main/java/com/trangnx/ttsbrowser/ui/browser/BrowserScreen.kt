package com.trangnx.ttsbrowser.ui.browser

import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.extraction.asScriptRunner
import com.trangnx.ttsbrowser.ui.common.BarsVisibility

private enum class BrowserSheet { None, History, Bookmarks }

/**
 * Browser tab: address bar, progress, WebView (or the bookmark start page) with the "Đọc" overlay,
 * and history/bookmark sheets.
 */
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    extractionViewModel: ExtractionViewModel,
    webView: WebView,
    bars: BarsVisibility,
    hideBars: Boolean,
    modifier: Modifier = Modifier,
) {
    val runner = remember(webView) { webView.asScriptRunner() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(BrowserSheet.None) }

    // The user navigating themselves drops what the reader asked of the page it was loading.
    val navigate: (WebView.() -> Unit) -> Unit = { action ->
        extractionViewModel.request(null)
        webView.action()
    }
    val open: (String) -> Unit = { url ->
        sheet = BrowserSheet.None
        navigate { loadUrl(url) }
    }
    val bookmarkLinks = bookmarks.map { PageLink(it.id, it.url, it.title) }

    // Page scrolling drives the auto-hide; at the top of the page the bars are always shown.
    val topSlopPx = with(LocalDensity.current) { 16.dp.toPx() }
    DisposableEffect(webView, bars) {
        webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY <= topSlopPx) bars.show() else bars.onScroll((scrollY - oldScrollY).toFloat())
        }
        onDispose { webView.setOnScrollChangeListener(null) }
    }

    BackHandler(enabled = state.canGoBack) { navigate { goBack() } }

    Column(modifier.fillMaxSize()) {
        AnimatedVisibility(visible = !hideBars, enter = expandVertically(), exit = shrinkVertically()) {
            BrowserAddressBar(
                state = state,
                actions = BrowserBarActions(
                    onSubmit = { input -> UrlInputResolver.resolve(input)?.let(open) },
                    onReloadOrStop = { navigate { if (state.isLoading) stopLoading() else reload() } },
                    onBack = { navigate { goBack() } },
                    onForward = { navigate { goForward() } },
                    onToggleBookmark = viewModel::toggleBookmark,
                    onShowHistory = { sheet = BrowserSheet.History },
                    onShowBookmarks = { sheet = BrowserSheet.Bookmarks },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
            Box(Modifier.fillMaxSize()) {
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
                ExtractionOverlay(extractionViewModel, runner, state.url, state.isLoading)
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
