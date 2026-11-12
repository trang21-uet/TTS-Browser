package com.trangnx.ttsbrowser.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.data.db.BookWithProgress
import com.trangnx.ttsbrowser.data.settings.LibraryLayout
import com.trangnx.ttsbrowser.ui.browser.message
import com.trangnx.ttsbrowser.ui.common.PlaceholderContent

/**
 * Saved stories, most recently read first, as a two-column grid of covers or as a list; a tap continues
 * reading (the reader opens via [LibraryViewModel.resume]).
 */
@Composable
fun LibraryScreen(viewModel: LibraryViewModel) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val opening by viewModel.opening.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var toDelete by remember { mutableStateOf<BookWithProgress?>(null) }
    var coverFor by remember { mutableStateOf<BookWithProgress?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val item = coverFor
        coverFor = null
        if (item != null && uri != null) viewModel.setCover(item, uri)
    }

    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.failures.collect { snackbar.showSnackbar(resources.getString(it.message)) }
    }
    LaunchedEffect(viewModel) {
        viewModel.coverFailed.collect { snackbar.showSnackbar(resources.getString(R.string.library_cover_failed)) }
    }

    val list = books ?: return
    val currentLayout = layout ?: return
    Box(Modifier.fillMaxSize()) {
        if (list.isEmpty()) {
            PlaceholderContent(stringResource(R.string.library_empty))
        } else {
            Column(Modifier.fillMaxSize()) {
                LayoutToggle(currentLayout, viewModel::setLayout)
                val content = BookListContent(
                    list = list,
                    opening = opening,
                    coverOf = { viewModel.coverOf(it.book) },
                    actionsOf = { item ->
                        BookActions(
                            onChangeCover = {
                                coverFor = item
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onClearCover = { viewModel.clearCover(item) },
                            onDelete = { toDelete = item },
                        )
                    },
                    onOpen = viewModel::open,
                )
                if (currentLayout == LibraryLayout.Grid) GridContent(content) else ListContent(content)
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

private class BookListContent(
    val list: List<BookWithProgress>,
    val opening: Long?,
    val coverOf: (BookWithProgress) -> java.io.File?,
    val actionsOf: (BookWithProgress) -> BookActions,
    val onOpen: (BookWithProgress) -> Unit,
)

@Composable
private fun LayoutToggle(layout: LibraryLayout, onChange: (LibraryLayout) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.library_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        val toGrid = layout == LibraryLayout.List
        IconButton(onClick = { onChange(if (toGrid) LibraryLayout.Grid else LibraryLayout.List) }) {
            if (toGrid) Icon(Icons.Outlined.GridView, stringResource(R.string.library_layout_grid))
            else Icon(Icons.AutoMirrored.Outlined.ViewList, stringResource(R.string.library_layout_list))
        }
    }
}

@Composable
private fun GridContent(content: BookListContent) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(content.list, key = { it.book.id }) { item ->
            BookCard(item, content.coverOf(item), content.opening == item.book.id, content.actionsOf(item)) { content.onOpen(item) }
        }
    }
}

@Composable
private fun ListContent(content: BookListContent) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(content.list, key = { it.book.id }) { item ->
            BookRow(item, content.coverOf(item), content.opening == item.book.id, content.actionsOf(item)) { content.onOpen(item) }
            HorizontalDivider()
        }
    }
}
