package com.trangnx.ttsbrowser.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.extraction.ContentSource
import com.trangnx.ttsbrowser.extraction.ExtractionResult
import com.trangnx.ttsbrowser.playback.PlaybackUiState
import com.trangnx.ttsbrowser.playback.SleepTimerState
import com.trangnx.ttsbrowser.ui.common.rememberBarsVisibility
import com.trangnx.ttsbrowser.ui.player.SleepTimerSheet
import com.trangnx.ttsbrowser.ui.browser.message

/** Browser-side actions the reader offers; the host navigates back to the browser for them. */
class ReaderActions(
    val onBack: () -> Unit,
    /** Show the chapter's page in the browser. */
    val onOpenPage: (String) -> Unit,
    val onPickOther: () -> Unit,
    val onClearRule: () -> Unit,
)

/**
 * Reader for [ReaderViewModel.shown]; player state (highlight, position, errors) only when the player
 * holds that chapter. [pageResult] is the browser page's extraction: when it is this chapter, picking
 * another region / clearing the site's selector are offered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: ReaderViewModel, pageResult: ExtractionResult.Success?, actions: ReaderActions) {
    val shown by viewModel.shown.collectAsStateWithLifecycle()
    val isLoaded by viewModel.isShownLoaded.collectAsStateWithLifecycle()
    val playerState by viewModel.playbackState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val savedBook by viewModel.savedBook.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val openingChapter by viewModel.openingChapter.collectAsStateWithLifecycle()
    val chapterError by viewModel.chapterError.collectAsStateWithLifecycle()
    val autoHide by viewModel.autoHideBars.collectAsStateWithLifecycle()
    val bars = rememberBarsVisibility()
    val barsHidden = autoHide && bars.hidden
    DisposableEffect(viewModel) {
        onDispose { viewModel.cancelChapterNavigation() }
    }
    var showDisplay by rememberSaveable { mutableStateOf(false) }
    var showSleepTimer by rememberSaveable { mutableStateOf(false) }
    var showSave by rememberSaveable { mutableStateOf(false) }

    val current = shown
    if (current == null) {
        // Nothing to show (e.g. the process was recreated).
        LaunchedEffect(Unit) { actions.onBack() }
        return
    }
    val readerSettings = settings ?: return
    val playback = if (isLoaded) playerState else PlaybackUiState()
    val chapter = current.chapter
    val samePage = pageResult?.takeIf { it.chapter.url == chapter.url }
    val note = when {
        samePage?.selectorMissed == true -> R.string.extract_selector_missed
        chapter.source == ContentSource.Selector -> R.string.extract_source_selector
        else -> null
    }

    Scaffold(
        modifier = Modifier.nestedScroll(bars.nestedScroll),
        topBar = {
            // The status bar strip stays so content never slides under it while the bar is away.
            Column {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(MaterialTheme.colorScheme.surface),
                )
                AnimatedVisibility(
                    visible = !barsHidden,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Horizontal),
                title = { Text(chapter.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.reader_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showSleepTimer = true }) {
                        val active = sleepTimer != SleepTimerState.Off
                        Icon(
                            if (active) Icons.Filled.Bedtime else Icons.Outlined.Bedtime,
                            stringResource(R.string.sleep_timer),
                        )
                    }
                    IconButton(onClick = { showDisplay = true }) {
                        Icon(Icons.Outlined.FormatSize, stringResource(R.string.reader_display))
                    }
                    ReaderMenu(
                        onOpenPage = { actions.onOpenPage(chapter.url) },
                        saved = savedBook != null,
                        onSave = { showSave = true },
                        onPickOther = actions.onPickOther.takeIf { samePage != null },
                        onClearRule = actions.onClearRule.takeIf { samePage?.chapter?.source == ContentSource.Selector },
                    )
                },
            )
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = !barsHidden,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
            ReaderControls(
                state = playback,
                sentenceCount = current.sentences.size,
                onPrevious = viewModel::previousSentence,
                onTogglePlay = viewModel::togglePlayPause,
                onNext = viewModel::nextSentence,
                prevChapterUrl = chapter.prevUrl,
                nextChapterUrl = chapter.nextUrl,
                openingChapter = openingChapter,
                chapterError = chapterError?.let { stringResource(it.message) },
                onOpenChapter = viewModel::openChapter,
            )
            }
        },
    ) { padding ->
        // Only the story takes the reader's background / text color; bars and sheets keep the app theme.
        ReaderColorTheme(readerSettings.theme, readerSettings.textColor) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                ReaderText(
                    loaded = current,
                    currentIndex = if (isLoaded) playback.sentenceIndex else -1,
                    fontSizeSp = readerSettings.fontSizeSp,
                    fontFamily = readerSettings.font.family,
                    note = note?.let { stringResource(it) },
                    onSentenceClick = viewModel::playFrom,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
    if (showSave) {
        SaveBookDialog(
            suggestedName = remember { viewModel.suggestedBookName() },
            onSave = {
                viewModel.saveBook(it)
                showSave = false
            },
            onDismiss = { showSave = false },
        )
    }
    if (showSleepTimer) {
        SleepTimerSheet(
            state = sleepTimer,
            onMinutes = viewModel::startSleepTimer,
            onEndOfChapter = viewModel::startSleepTimerAtChapterEnd,
            onOff = viewModel::cancelSleepTimer,
            onDismiss = { showSleepTimer = false },
        )
    }
    if (showDisplay) {
        ReaderDisplaySheet(
            settings = readerSettings,
            onFontSize = viewModel::setFontSize,
            onTheme = viewModel::setTheme,
            onTextColor = viewModel::setTextColor,
            onFont = viewModel::setFont,
            onRate = viewModel::setRate,
            onDismiss = { showDisplay = false },
        )
    }
}

/** Overflow menu; null actions are hidden. */
@Composable
private fun ReaderMenu(
    onOpenPage: () -> Unit,
    saved: Boolean,
    onSave: () -> Unit,
    onPickOther: (() -> Unit)?,
    onClearRule: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, stringResource(R.string.browser_menu))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val item: @Composable (Int, () -> Unit) -> Unit = { label, onClick ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        expanded = false
                        onClick()
                    },
                )
            }
            if (saved) {
                DropdownMenuItem(text = { Text(stringResource(R.string.reader_saved_book)) }, onClick = {}, enabled = false)
            } else {
                item(R.string.reader_save_book, onSave)
            }
            item(R.string.reader_open_page, onOpenPage)
            onPickOther?.let { item(R.string.extract_pick_other, it) }
            onClearRule?.let { item(R.string.extract_clear_rule, it) }
        }
    }
}
