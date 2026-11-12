package com.trangnx.ttsbrowser.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.trangnx.ttsbrowser.R

/** Reading comfort: whether toolbars slide away while scrolling down. */
@Composable
fun ReadingSettingsSection(autoHideBars: Boolean, enabled: Boolean, onAutoHideBars: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.settings_reading_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(value = autoHideBars, enabled = enabled, role = Role.Switch, onValueChange = onAutoHideBars),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_auto_hide_bars), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = autoHideBars, onCheckedChange = null, enabled = enabled)
        }
        Text(
            stringResource(R.string.settings_auto_hide_bars_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
