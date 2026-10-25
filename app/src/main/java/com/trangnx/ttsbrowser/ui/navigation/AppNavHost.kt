package com.trangnx.ttsbrowser.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.TtsBrowserApp
import com.trangnx.ttsbrowser.playback.LoadedChapter
import com.trangnx.ttsbrowser.ui.browser.BrowserScreen
import com.trangnx.ttsbrowser.ui.browser.BrowserViewModel
import com.trangnx.ttsbrowser.ui.browser.ExtractionViewModel
import com.trangnx.ttsbrowser.ui.browser.PageRequest
import com.trangnx.ttsbrowser.ui.browser.pageOf
import com.trangnx.ttsbrowser.ui.browser.rememberBrowserWebView
import com.trangnx.ttsbrowser.ui.library.LibraryScreen
import com.trangnx.ttsbrowser.ui.library.LibraryViewModel
import com.trangnx.ttsbrowser.ui.player.MiniPlayerBar
import com.trangnx.ttsbrowser.ui.reader.ReaderActions
import com.trangnx.ttsbrowser.ui.reader.ReaderScreen
import com.trangnx.ttsbrowser.ui.reader.ReaderViewModel
import com.trangnx.ttsbrowser.ui.settings.SettingsScreen
import com.trangnx.ttsbrowser.ui.settings.BackgroundPlaybackGuide
import kotlinx.coroutines.launch

/** Top-level destinations shown in the bottom navigation bar. */
private enum class TopLevelRoute(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Browser("browser", R.string.nav_browser, Icons.Outlined.Public),
    Library("library", R.string.nav_library, Icons.AutoMirrored.Outlined.LibraryBooks),
    Settings("settings", R.string.nav_settings, Icons.Outlined.Settings),
}

/** Full screen, above the tabs; its own bars replace the navigation bar and the mini player. */
private const val READER_ROUTE = "reader"

@Composable
fun AppNavHost() {
    val appContext = LocalContext.current.applicationContext
    val container = (appContext as TtsBrowserApp).container
    var showBackgroundGuide by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saveError = stringResource(R.string.settings_save_error)
    BackgroundPlaybackGuide(
        container.settingsRepository,
        showBackgroundGuide,
        onSaveError = { scope.launch { snackbar.showSnackbar(saveError) } },
        onClose = { showBackgroundGuide = false },
    )
    val fallbackMessage = stringResource(R.string.playback_edge_fallback)
    LaunchedEffect(container, lifecycle, fallbackMessage) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            container.speechFallbackNotices.collect { snackbar.showSnackbar(fallbackMessage) }
        }
    }
    // Activity-scoped (outside NavHost): the page survives switching tabs, and the reader talks to
    // the browser page through the extraction view model.
    val browserViewModel: BrowserViewModel = viewModel(factory = BrowserViewModel.Factory)
    val extractionViewModel: ExtractionViewModel = viewModel(factory = ExtractionViewModel.Factory)
    val readerViewModel: ReaderViewModel = viewModel(factory = ReaderViewModel.Factory)
    val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
    val browserWebView = rememberBrowserWebView(browserViewModel)
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val readerActions = remember(navController, browserWebView, extractionViewModel) {
        // Back to the browser tab (the start destination), loading [url] unless the page shows it already.
        // Switching tabs like the tab bar keeps a Library/Settings tab's saved state.
        val showPage: (String?) -> Unit = { url ->
            navController.popBackStack(READER_ROUTE, inclusive = true)
            navController.navigateToTab(TopLevelRoute.Browser.route)
            if (url != null && pageOf(url) != browserWebView.url?.let(::pageOf)) browserWebView.loadUrl(url)
        }
        ReaderActions(
            onBack = { navController.popBackStack(READER_ROUTE, inclusive = true) },
            onOpenChapter = { url ->
                extractionViewModel.request(PageRequest.ExtractWhenLoaded(url, fromUrl = browserWebView.url))
                showPage(url)
            },
            onOpenPage = showPage,
            onPickOther = {
                extractionViewModel.request(PageRequest.Pick)
                showPage(null)
            },
            onClearRule = {
                extractionViewModel.request(PageRequest.ClearRule)
                showPage(null)
            },
        )
    }

    // Collected for the whole Activity; a result that finishes after the user left the browser is dropped.
    LaunchedEffect(extractionViewModel) {
        extractionViewModel.openReader.collect { request ->
            if (navController.currentDestination?.route != TopLevelRoute.Browser.route) return@collect
            readerViewModel.show(LoadedChapter(request.result.chapter, request.result.sentences), request.continuing)
            navController.openReader()
        }
    }

    // A saved story finished loading: the reader opens above whichever tab the user is on by then.
    LaunchedEffect(libraryViewModel) {
        libraryViewModel.resume.collect { request ->
            readerViewModel.resume(request.chapter, request.startIndex)
            navController.openReader()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (currentDestination?.route != READER_ROUTE) {
                Column {
                    MiniPlayer(readerViewModel) {
                        readerViewModel.showLoaded()
                        navController.openReader()
                    }
                    TabBar(navController, currentDestination)
                }
            }
        },
    ) { innerPadding ->
        // Tabs take the scaffold padding; the reader handles insets itself (its colors reach the status bar).
        NavHost(navController = navController, startDestination = TopLevelRoute.Browser.route) {
            composable(TopLevelRoute.Browser.route) {
                BrowserScreen(browserViewModel, extractionViewModel, browserWebView, Modifier.padding(innerPadding))
            }
            composable(TopLevelRoute.Library.route) { Box(Modifier.padding(innerPadding)) { LibraryScreen(libraryViewModel) } }
            composable(TopLevelRoute.Settings.route) {
                Box(Modifier.padding(innerPadding)) {
                    SettingsScreen(
                        onBackgroundGuide = { showBackgroundGuide = true },
                        onSystemVoiceSettingsClosed = { container.ttsPlayer.refreshSpeechEngine() },
                    )
                }
            }
            composable(READER_ROUTE) {
                val pageResult by extractionViewModel.pageResult.collectAsStateWithLifecycle()
                ReaderScreen(readerViewModel, pageResult, readerActions)
            }
        }
    }
}

private fun NavHostController.openReader() = navigate(READER_ROUTE) { launchSingleTop = true }

/** Tab switch: the left tab's back stack is saved, the target's restored. */
private fun NavHostController.navigateToTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** Own scope: playback state changes every sentence and should only recompose the bar. */
@Composable
private fun MiniPlayer(viewModel: ReaderViewModel, onOpen: () -> Unit) {
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    loaded?.let { MiniPlayerBar(it, playback, viewModel::toggleLoaded, onOpen) }
}

@Composable
private fun TabBar(navController: NavHostController, currentDestination: NavDestination?) {
    NavigationBar {
        TopLevelRoute.entries.forEach { item ->
            NavigationBarItem(
                selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                onClick = { navController.navigateToTab(item.route) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(stringResource(item.label)) },
            )
        }
    }
}
