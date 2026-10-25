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

/** Progress can read 100 before the page is done, or a redirect can follow: wait for loading to stay finished. */
private const val LOAD_SETTLE_MS = 700L

/**
 * "Đọc" FAB over the page plus what it opens: picker bar, failure dialog (a successful extraction
 * opens the reader, see [ExtractionViewModel.openReader]). Also runs requests the reader left for this page.
 */
@Composable
fun BoxScope.ExtractionOverlay(
    viewModel: ExtractionViewModel,
    runner: PageScriptRunner,
    pageUrl: String,
    isLoading: Boolean,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val request by viewModel.request.collectAsStateWithLifecycle()

    LaunchedEffect(pageUrl) { viewModel.onPageUrlChanged(runner, pageUrl) }
    LaunchedEffect(request, pageUrl, isLoading) {
        // Restarted (so cancelled) whenever loading resumes or the URL changes during the wait.
        if (request is PageRequest.ExtractWhenLoaded && !isLoading) delay(LOAD_SETTLE_MS)
        viewModel.handleRequest(runner, pageUrl, isLoading)
    }

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

    (state as? ExtractionUiState.Failed)?.let { failed ->
        ExtractionFailedDialog(
            reason = failed.reason,
            onPick = { viewModel.startPicking(runner) },
            onDismiss = viewModel::dismiss,
        )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.extract_failed_title)) },
        text = { Text(stringResource(reason.message) + "\n\n" + stringResource(R.string.extract_failed_hint)) },
        confirmButton = { TextButton(onClick = onPick) { Text(stringResource(R.string.extract_pick_manual)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
