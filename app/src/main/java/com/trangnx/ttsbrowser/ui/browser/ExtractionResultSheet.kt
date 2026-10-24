package com.trangnx.ttsbrowser.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.extraction.ContentSource
import com.trangnx.ttsbrowser.extraction.ExtractionResult

/**
 * Preview of the extracted chapter, to check extraction on real sites.
 * Replaced by the reader screen in Phase 5.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractionResultSheet(
    result: ExtractionResult.Success,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onPickOther: () -> Unit,
    onClearRule: () -> Unit,
) {
    val chapter = result.chapter
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Text(chapter.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
                Text(
                    stringResource(R.string.extract_meta, chapter.paragraphs.size, result.sentences.size, chapter.length),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                val note = when {
                    result.selectorMissed -> R.string.extract_selector_missed
                    chapter.source == ContentSource.Selector -> R.string.extract_source_selector
                    else -> null
                }
                if (note != null) {
                    Text(
                        stringResource(note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                ChapterNavRow(chapter.prevUrl, chapter.nextUrl, onOpenUrl)
                Row(Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = onPickOther) { Text(stringResource(R.string.extract_pick_other)) }
                    if (chapter.source == ContentSource.Selector) {
                        TextButton(onClick = onClearRule) { Text(stringResource(R.string.extract_clear_rule)) }
                    }
                }
            }
            items(chapter.paragraphs) { paragraph ->
                Text(
                    paragraph,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ChapterNavRow(prevUrl: String?, nextUrl: String?, onOpenUrl: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = { prevUrl?.let(onOpenUrl) }, enabled = prevUrl != null, modifier = Modifier.weight(1f)) {
            Icon(Icons.AutoMirrored.Outlined.NavigateBefore, contentDescription = null)
            Text(stringResource(R.string.extract_prev))
        }
        OutlinedButton(onClick = { nextUrl?.let(onOpenUrl) }, enabled = nextUrl != null, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.extract_next))
            Icon(Icons.AutoMirrored.Outlined.NavigateNext, contentDescription = null)
        }
    }
}
