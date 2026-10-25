package com.trangnx.ttsbrowser.ui.player

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.playback.PlaybackUiState

/** Play/pause toggle with a spinner while the engine starts; [filled] for the reader's main control. */
@Composable
fun PlayPauseButton(state: PlaybackUiState, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false) {
    val content: @Composable () -> Unit = {
        when {
            state.isBuffering -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            state.showPlay -> Icon(Icons.Filled.PlayArrow, stringResource(R.string.player_play))
            else -> Icon(Icons.Filled.Pause, stringResource(R.string.player_pause))
        }
    }
    if (filled) {
        FilledIconButton(onClick = onClick, modifier = modifier.size(56.dp), content = content)
    } else {
        IconButton(onClick = onClick, modifier = modifier, content = content)
    }
}
