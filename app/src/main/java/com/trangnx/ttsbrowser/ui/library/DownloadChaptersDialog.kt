package com.trangnx.ttsbrowser.ui.library

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.data.db.BookWithProgress
import com.trangnx.ttsbrowser.download.ChapterDownloader
import com.trangnx.ttsbrowser.download.DownloadState
import com.trangnx.ttsbrowser.ui.browser.message

private val COUNT_PRESETS = listOf(10, 20, 50, 100)
private const val DEFAULT_COUNT = 20

/**
 * Asks how many chapters of [item] to download, starting with the chapter being read;
 * [downloaded] chapters of the story are stored already and are not loaded again.
 */
@Composable
fun DownloadChaptersDialog(item: BookWithProgress, downloaded: Int, onDismiss: () -> Unit, onDownload: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(DEFAULT_COUNT.toString()) }
    val count = text.toIntOrNull()?.takeIf { it in 1..ChapterDownloader.MAX_CHAPTERS }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.download_from, item.progress?.chapterTitle.orEmpty()))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    COUNT_PRESETS.forEach { preset ->
                        FilterChip(
                            selected = count == preset,
                            onClick = { text = preset.toString() },
                            label = { Text(preset.toString()) },
                        )
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { value -> text = value.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.download_count)) },
                    supportingText = { Text(stringResource(R.string.download_count_hint, ChapterDownloader.MAX_CHAPTERS)) },
                    isError = count == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (downloaded > 0) Text(stringResource(R.string.download_existing, downloaded))
            }
        },
        confirmButton = {
            TextButton(enabled = count != null, onClick = { count?.let(onDownload) }) {
                Text(stringResource(R.string.download_start))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.picker_cancel)) } },
    )
}

/** Progress of the running download, with a button to stop it. */
@Composable
fun DownloadProgressBar(state: DownloadState.Running, onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.download_progress, state.bookTitle, state.done, state.total),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancel) { Text(stringResource(R.string.download_stop)) }
            }
            LinearProgressIndicator(
                progress = { state.done.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
            )
        }
    }
}

/** What a finished download did, for the snackbar. */
internal fun downloadResultText(resources: Resources, state: DownloadState.Finished): String = when {
    state.cancelled -> resources.getString(R.string.download_cancelled, state.done)
    state.failure != null ->
        resources.getString(R.string.download_failed, state.done, resources.getString(state.failure.message))
    state.reachedEnd -> resources.getString(R.string.download_done_end, state.done, state.bookTitle)
    else -> resources.getString(R.string.download_done, state.done, state.bookTitle)
}
