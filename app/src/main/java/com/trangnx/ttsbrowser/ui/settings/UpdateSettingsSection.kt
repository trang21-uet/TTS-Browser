package com.trangnx.ttsbrowser.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.trangnx.ttsbrowser.update.AppVersion
import com.trangnx.ttsbrowser.update.UpdateState

/** Installed version, the launch-check switch and the manual check / download / install button. */
@Composable
fun UpdateSettingsSection(
    installed: AppVersion,
    state: UpdateState,
    autoCheck: Boolean,
    enabled: Boolean,
    onAutoCheck: (Boolean) -> Unit,
    onAction: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.update_section_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.update_current_version, installed.toString()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(value = autoCheck, enabled = enabled, role = Role.Switch, onValueChange = onAutoCheck),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.update_auto_check), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = autoCheck, onCheckedChange = null, enabled = enabled)
        }
        OutlinedButton(
            onClick = onAction,
            enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when (state) {
                    UpdateState.Idle -> stringResource(R.string.update_action_check)
                    UpdateState.Checking -> stringResource(R.string.update_action_checking)
                    is UpdateState.Available -> stringResource(R.string.update_action_available, state.release.version.toString())
                    is UpdateState.Downloading -> stringResource(R.string.update_action_downloading, state.release.version.toString())
                    is UpdateState.Ready -> stringResource(R.string.update_action_install, state.release.version.toString())
                },
            )
        }
    }
}
