package com.trangnx.ttsbrowser.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.ui.common.PlaceholderContent

/** A page entry shown in history/bookmark lists. */
data class PageLink(val key: Long, val url: String, val title: String)

/** List of pages with open + delete actions; shared by history, bookmarks and the start page. */
@Composable
fun PageLinkList(
    links: List<PageLink>,
    emptyMessage: String,
    onOpen: (PageLink) -> Unit,
    onDelete: (PageLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (links.isEmpty()) {
        PlaceholderContent(emptyMessage, modifier.padding(32.dp))
        return
    }
    LazyColumn(modifier) {
        items(links, key = { it.key }) { link ->
            ListItem(
                headlineContent = { Text(link.title.ifBlank { link.url }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(link.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingContent = {
                    IconButton(onClick = { onDelete(link) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                },
                modifier = Modifier.clickable { onOpen(link) },
            )
        }
    }
}

/** Bottom sheet wrapping [PageLinkList] with a title and an optional "clear all" action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageLinkSheet(
    title: String,
    links: List<PageLink>,
    emptyMessage: String,
    onDismiss: () -> Unit,
    onOpen: (PageLink) -> Unit,
    onDelete: (PageLink) -> Unit,
    onClearAll: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (onClearAll != null && links.isNotEmpty()) {
                TextButton(onClick = onClearAll) { Text(stringResource(R.string.action_clear_all)) }
            }
        }
        PageLinkList(links, emptyMessage, onOpen, onDelete, Modifier.fillMaxWidth())
    }
}
