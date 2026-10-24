package com.trangnx.ttsbrowser.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trangnx.ttsbrowser.R

/** Bottom bar shown while picking the content region on the page. */
@Composable
fun ElementPickerBar(
    state: ExtractionUiState.Picking,
    onExpand: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val picked = state.picked
    Surface(modifier.fillMaxWidth(), tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = when {
                    state.error == PickError.NoSelector -> stringResource(R.string.picker_selector_failed)
                    state.error == PickError.CannotSave -> stringResource(R.string.picker_cannot_save)
                    picked != null -> stringResource(R.string.picker_picked, picked.label, picked.textLength)
                    else -> stringResource(R.string.picker_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.picker_cancel)) }
                OutlinedButton(onClick = onExpand, enabled = picked != null) { Text(stringResource(R.string.picker_expand)) }
                Button(onClick = onConfirm, enabled = picked != null) { Text(stringResource(R.string.picker_confirm)) }
            }
        }
    }
}
