package com.trangnx.ttsbrowser.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trangnx.ttsbrowser.R

/** Previous/next chapter buttons at the end of the text; disabled when the link was not found. */
@Composable
fun ChapterNavRow(prevUrl: String?, nextUrl: String?, onOpenUrl: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
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
