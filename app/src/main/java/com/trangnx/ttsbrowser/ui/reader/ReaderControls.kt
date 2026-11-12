package com.trangnx.ttsbrowser.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.playback.PlaybackUiState
import com.trangnx.ttsbrowser.ui.player.PlayPauseButton

/**
 * Bottom bar of the reader: player error, then previous sentence (outer), previous chapter,
 * play/pause, next chapter, next sentence (outer), and the sentence position.
 */
@Composable
fun ReaderControls(
    state: PlaybackUiState,
    sentenceCount: Int,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    prevChapterUrl: String?,
    nextChapterUrl: String?,
    openingChapter: Boolean,
    chapterError: String?,
    onOpenChapter: (String) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
        ) {
            if (openingChapter) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (chapterError != null) {
                Text(
                    chapterError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (state.error != null) {
                Text(
                    state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                OutlinedIconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.NavigateBefore, stringResource(R.string.player_previous))
                }
                IconButton(
                    onClick = { prevChapterUrl?.let(onOpenChapter) },
                    enabled = !openingChapter && prevChapterUrl != null,
                ) {
                    Icon(Icons.Filled.SkipPrevious, stringResource(R.string.extract_prev))
                }
                PlayPauseButton(state, onTogglePlay, filled = true)
                IconButton(
                    onClick = { nextChapterUrl?.let(onOpenChapter) },
                    enabled = !openingChapter && nextChapterUrl != null,
                ) {
                    Icon(Icons.Filled.SkipNext, stringResource(R.string.extract_next))
                }
                OutlinedIconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.NavigateNext, stringResource(R.string.player_next))
                }
            }
            Text(
                stringResource(R.string.playback_sentence_position, state.sentenceIndex + 1, sentenceCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
