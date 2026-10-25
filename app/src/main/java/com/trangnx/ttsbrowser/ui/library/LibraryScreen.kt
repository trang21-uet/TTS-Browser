package com.trangnx.ttsbrowser.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.data.db.BookWithProgress
import com.trangnx.ttsbrowser.ui.browser.message
import com.trangnx.ttsbrowser.ui.common.PlaceholderContent

/** Saved stories, most recently read first; a tap continues reading (the reader opens via [LibraryViewModel.resume]). */
@Composable
fun LibraryScreen(viewModel: LibraryViewModel) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val opening by viewModel.opening.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var toDelete by remember { mutableStateOf<BookWithProgress?>(null) }

    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.failures.collect { snackbar.showSnackbar(resources.getString(it.message)) }
    }

    val list = books ?: return
    Box(Modifier.fillMaxSize()) {
        if (list.isEmpty()) {
            PlaceholderContent(stringResource(R.string.library_empty))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.book.id }) { item ->
                    BookRow(
                        item = item,
                        loading = opening == item.book.id,
                        onClick = { viewModel.open(item) },
                        onDelete = { toDelete = item },
                    )
                    HorizontalDivider()
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.library_delete_title)) },
            text = { Text(item.book.title + "\n\n" + stringResource(R.string.library_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(item)
                        toDelete = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.picker_cancel)) } },
        )
    }
}

@Composable
private fun BookRow(item: BookWithProgress, loading: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val progress = item.progress
            val text = if (progress != null) stringResource(R.string.library_reading, progress.chapterTitle) else item.book.host
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            if (loading) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
            }
        },
        modifier = Modifier.clickable(enabled = !loading, onClick = onClick),
    )
}
