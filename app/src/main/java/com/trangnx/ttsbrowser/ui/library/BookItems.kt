package com.trangnx.ttsbrowser.ui.library

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.data.db.BookWithProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What a story card or row offers besides opening the story. */
class BookActions(val onChangeCover: () -> Unit, val onClearCover: () -> Unit, val onDelete: () -> Unit)

/** One story as a row: small cover, title, chapter being read. */
@Composable
fun BookRow(item: BookWithProgress, cover: File?, loading: Boolean, actions: BookActions, onClick: () -> Unit) {
    ListItem(
        leadingContent = { CoverImage(cover, Modifier.width(56.dp).aspectRatio(COVER_RATIO).clip(RoundedCornerShape(4.dp))) },
        headlineContent = { Text(item.book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(readingText(item), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = { LoadingOrMenu(item, loading, actions) },
        modifier = Modifier.clickable(enabled = !loading, onClick = onClick),
    )
}

/** One story as a card of a two-column grid: big cover, title, chapter being read. */
@Composable
fun BookCard(item: BookWithProgress, cover: File?, loading: Boolean, actions: BookActions, onClick: () -> Unit) {
    Column(Modifier.clickable(enabled = !loading, onClick = onClick).padding(bottom = 8.dp)) {
        Box {
            CoverImage(cover, Modifier.fillMaxWidth().aspectRatio(COVER_RATIO).clip(RoundedCornerShape(8.dp)))
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) { LoadingOrMenu(item, loading, actions) }
        }
        Text(
            item.book.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            readingText(item),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun readingText(item: BookWithProgress): String =
    item.progress?.let { stringResource(R.string.library_reading, it.chapterTitle) } ?: item.book.host

@Composable
private fun LoadingOrMenu(item: BookWithProgress, loading: Boolean, actions: BookActions) {
    if (loading) {
        CircularProgressIndicator(Modifier.padding(8.dp).size(24.dp), strokeWidth = 2.dp)
    } else {
        BookMenu(hasCover = item.book.coverFile != null, actions)
    }
}

@Composable
private fun BookMenu(hasCover: Boolean, actions: BookActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, stringResource(R.string.library_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_cover_change)) },
                leadingIcon = { Icon(Icons.Outlined.Image, null) },
                onClick = { open = false; actions.onChangeCover() },
            )
            if (hasCover) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_cover_clear)) },
                    onClick = { open = false; actions.onClearCover() },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                onClick = { open = false; actions.onDelete() },
            )
        }
    }
}

/** The cover image file, decoded off the main thread; a placeholder while loading or without a cover. */
@Composable
private fun CoverImage(file: File?, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = file?.let { f ->
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }
        }
    }
    val image = bitmap
    if (image != null) {
        Image(image, null, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(
                Icons.AutoMirrored.Outlined.MenuBook,
                null,
                Modifier.fillMaxSize(PLACEHOLDER_ICON_FRACTION),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Width : height of a book cover. */
private const val COVER_RATIO = 3f / 4f
private const val PLACEHOLDER_ICON_FRACTION = 0.4f
