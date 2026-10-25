package com.trangnx.ttsbrowser.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.extraction.ContentSource
import com.trangnx.ttsbrowser.extraction.ExtractionResult
import com.trangnx.ttsbrowser.playback.PlaybackUiState

/** Browser-side actions the reader offers; the host navigates back to the browser for them. */
class ReaderActions(
    val onBack: () -> Unit,
    /** Previous/next chapter: load it in the browser and extract it. */
    val onOpenChapter: (String) -> Unit,
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
    var showDisplay by rememberSaveable { mutableStateOf(false) }

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

    ReaderColorTheme(readerSettings.theme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(chapter.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = actions.onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.reader_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDisplay = true }) {
                            Icon(Icons.Outlined.FormatSize, stringResource(R.string.reader_display))
                        }
                        ReaderMenu(
                            onOpenPage = { actions.onOpenPage(chapter.url) },
                            onPickOther = actions.onPickOther.takeIf { samePage != null },
                            onClearRule = actions.onClearRule.takeIf { samePage?.chapter?.source == ContentSource.Selector },
                        )
                    },
                )
            },
            bottomBar = {
                ReaderControls(
                    state = playback,
                    sentenceCount = current.sentences.size,
                    onPrevious = viewModel::previousSentence,
                    onTogglePlay = viewModel::togglePlayPause,
                    onNext = viewModel::nextSentence,
                )
            },
        ) { padding ->
            ReaderText(
                loaded = current,
                currentIndex = if (isLoaded) playback.sentenceIndex else -1,
                fontSizeSp = readerSettings.fontSizeSp,
                note = note?.let { stringResource(it) },
                onSentenceClick = viewModel::playFrom,
                onOpenChapter = actions.onOpenChapter,
                modifier = Modifier.padding(padding),
            )
        }
        if (showDisplay) {
            ReaderDisplaySheet(
                settings = readerSettings,
                onFontSize = viewModel::setFontSize,
                onTheme = viewModel::setTheme,
                onRate = viewModel::setRate,
                onDismiss = { showDisplay = false },
            )
        }
    }
}

/** Overflow menu; null actions are hidden. */
@Composable
private fun ReaderMenu(onOpenPage: () -> Unit, onPickOther: (() -> Unit)?, onClearRule: (() -> Unit)?) {
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
            item(R.string.reader_open_page, onOpenPage)
            onPickOther?.let { item(R.string.extract_pick_other, it) }
            onClearRule?.let { item(R.string.extract_clear_rule, it) }
        }
    }
}
