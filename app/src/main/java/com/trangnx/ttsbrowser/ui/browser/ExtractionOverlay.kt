package com.trangnx.ttsbrowser.ui.browser

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.extraction.ExtractionResult
import com.trangnx.ttsbrowser.extraction.PageScriptRunner
import kotlinx.coroutines.delay

private const val PICK_POLL_MS = 500L

/** "Đọc" FAB over the page plus everything it opens: picker bar, failure dialog, result sheet. */
@Composable
fun BoxScope.ExtractionOverlay(
    viewModel: ExtractionViewModel,
    runner: PageScriptRunner,
    pageUrl: String,
    onOpenUrl: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(pageUrl) { viewModel.onPageUrlChanged(runner, pageUrl) }

    when (val current = state) {
        is ExtractionUiState.Picking -> {
            LaunchedEffect(Unit) {
                // Sequential: the next poll starts only after the previous one returned.
                while (true) {
                    delay(PICK_POLL_MS)
                    viewModel.pollPick(runner)
                }
            }
            ElementPickerBar(
                state = current,
                onExpand = { viewModel.expandPick(runner) },
                onConfirm = { viewModel.confirmPick(runner, pageUrl) },
                onCancel = { viewModel.cancelPick(runner) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        else -> ReadFab(
            extracting = current == ExtractionUiState.Extracting,
            onClick = { viewModel.extract(runner, pageUrl) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    when (val current = state) {
        is ExtractionUiState.Ready -> ExtractionResultSheet(
            result = current.result,
            onDismiss = viewModel::dismiss,
            onListen = viewModel::listen,
            onOpenUrl = { url ->
                viewModel.dismiss()
                onOpenUrl(url)
            },
            onPickOther = { viewModel.startPicking(runner) },
            onClearRule = { viewModel.clearSiteRule(runner, pageUrl) },
        )
        is ExtractionUiState.Failed -> ExtractionFailedDialog(
            reason = current.reason,
            onPick = { viewModel.startPicking(runner) },
            onDismiss = viewModel::dismiss,
        )
        else -> Unit
    }
}

@Composable
private fun ReadFab(extracting: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = { if (!extracting) onClick() },
        icon = {
            if (extracting) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.AutoStories, contentDescription = null)
            }
        },
        text = { Text(stringResource(R.string.extract_read)) },
        modifier = modifier,
    )
}

@Composable
private fun ExtractionFailedDialog(reason: ExtractionResult.Reason, onPick: () -> Unit, onDismiss: () -> Unit) {
    val message = when (reason) {
        ExtractionResult.Reason.NoContent -> R.string.extract_failed_no_content
        ExtractionResult.Reason.TooShort -> R.string.extract_failed_too_short
        ExtractionResult.Reason.ScriptError -> R.string.extract_failed_script
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.extract_failed_title)) },
        text = { Text(stringResource(message) + "\n\n" + stringResource(R.string.extract_failed_hint)) },
        confirmButton = { TextButton(onClick = onPick) { Text(stringResource(R.string.extract_pick_manual)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
