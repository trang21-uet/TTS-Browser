package com.trangnx.ttsbrowser.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.trangnx.ttsbrowser.R

/** Actions triggered from the address bar and its overflow menu. */
class BrowserBarActions(
    val onSubmit: (String) -> Unit,
    val onReloadOrStop: () -> Unit,
    val onBack: () -> Unit,
    val onForward: () -> Unit,
    val onToggleBookmark: () -> Unit,
    val onShowHistory: () -> Unit,
    val onShowBookmarks: () -> Unit,
)

@Composable
fun BrowserAddressBar(state: BrowserUiState, actions: BrowserBarActions, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf(TextFieldValue(state.url)) }
    var focused by remember { mutableStateOf(false) }
    // Follow page navigation, but never overwrite what the user is typing.
    LaunchedEffect(state.url, focused) {
        if (!focused) text = TextFieldValue(state.url)
    }

    Row(modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focus ->
                    // Select the whole URL on focus so typing replaces it.
                    if (focus.isFocused && !focused) text = text.copy(selection = TextRange(0, text.text.length))
                    focused = focus.isFocused
                },
            placeholder = { Text(stringResource(R.string.browser_address_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                actions.onSubmit(text.text)
                focusManager.clearFocus()
            }),
        )
        IconButton(onClick = actions.onReloadOrStop, enabled = state.url.isNotBlank()) {
            Icon(
                if (state.isLoading) Icons.Outlined.Close else Icons.Outlined.Refresh,
                contentDescription = stringResource(if (state.isLoading) R.string.browser_stop else R.string.browser_reload),
            )
        }
        BrowserOverflowMenu(state, actions)
    }
}

@Composable
private fun BrowserOverflowMenu(state: BrowserUiState, actions: BrowserBarActions) {
    var open by remember { mutableStateOf(false) }

    @Composable
    fun MenuItem(label: Int, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
        DropdownMenuItem(
            text = { Text(stringResource(label)) },
            leadingIcon = { Icon(icon, contentDescription = null) },
            enabled = enabled,
            onClick = {
                open = false
                onClick()
            },
        )
    }

    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.browser_menu))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem(R.string.browser_back, Icons.AutoMirrored.Outlined.ArrowBack, state.canGoBack, actions.onBack)
            MenuItem(R.string.browser_forward, Icons.AutoMirrored.Outlined.ArrowForward, state.canGoForward, actions.onForward)
            MenuItem(
                if (state.isBookmarked) R.string.browser_bookmark_remove else R.string.browser_bookmark_add,
                if (state.isBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                state.url.isNotBlank(),
                actions.onToggleBookmark,
            )
            MenuItem(R.string.browser_history, Icons.Outlined.History, onClick = actions.onShowHistory)
            MenuItem(R.string.browser_bookmarks, Icons.Outlined.Bookmarks, onClick = actions.onShowBookmarks)
        }
    }
}
