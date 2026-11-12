package com.trangnx.ttsbrowser.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.update.UpdateController
import com.trangnx.ttsbrowser.update.UpdateState

/** Slim strip for the download that outlives the dialog: progress while it runs, "Cài đặt" once done. */
@Composable
fun UpdateBanner(controller: UpdateController, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsStateWithLifecycle()
    when (val current = state) {
        is UpdateState.Downloading -> {
            val progress by controller.progress.collectAsStateWithLifecycle()
            val version = current.release.version.toString()
            BannerStrip(
                message = progress?.let { stringResource(R.string.update_banner_downloading, version, (it * 100).toInt()) }
                    ?: stringResource(R.string.update_banner_downloading_unknown, version),
                progress = { progress },
                showProgress = true,
                actionLabel = stringResource(R.string.update_banner_cancel),
                onAction = controller::cancelDownload,
                onDismiss = null,
                modifier = modifier,
            )
        }
        is UpdateState.Ready -> if (!current.bannerDismissed) {
            BannerStrip(
                message = stringResource(R.string.update_banner_ready, current.release.version.toString()),
                progress = { null },
                showProgress = false,
                actionLabel = stringResource(R.string.update_banner_install),
                onAction = controller::install,
                onDismiss = controller::dismissBanner,
                modifier = modifier,
            )
        }
        else -> Unit
    }
}

@Composable
private fun BannerStrip(
    message: String,
    progress: () -> Float?,
    showProgress: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onAction) { Text(actionLabel) }
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.update_banner_hide))
                    }
                }
            }
            if (showProgress) {
                val value = progress()
                if (value == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { value }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
