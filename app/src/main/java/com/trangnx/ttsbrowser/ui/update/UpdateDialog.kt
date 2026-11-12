package com.trangnx.ttsbrowser.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.update.GithubRelease

/** "A newer version exists". Downloading continues in [UpdateBanner] after the dialog closes. */
@Composable
fun UpdateDialog(release: GithubRelease, onDownload: () -> Unit, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val notes = GithubRelease.displayNotes(release.notes)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_dialog_title)) },
        text = {
            Column {
                Text(
                    release.apkSize?.let { stringResource(R.string.update_dialog_version_size, release.version.toString(), it / BYTES_PER_MB) }
                        ?: stringResource(R.string.update_dialog_version, release.version.toString()),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (notes.isNotEmpty()) {
                    // Only the notes scroll, so the buttons stay on screen whatever the changelog length.
                    Text(
                        notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp).heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            if (release.apkUrl != null) {
                TextButton(onClick = onDownload) { Text(stringResource(R.string.update_dialog_download)) }
            } else {
                TextButton(onClick = {
                    uriHandler.openUri(release.pageUrl)
                    onDismiss()
                }) { Text(stringResource(R.string.update_dialog_open_page)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_dialog_later)) } },
    )
}

private const val BYTES_PER_MB = 1024f * 1024f
