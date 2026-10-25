package com.trangnx.ttsbrowser.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.data.settings.SettingsRepository
import kotlinx.coroutines.launch
import java.io.IOException

/** Shown once after preferences load, and available again from Settings. No settings change on open. */
@Composable
fun BackgroundPlaybackGuide(
    repository: SettingsRepository,
    requested: Boolean,
    onSaveError: () -> Unit,
    onClose: () -> Unit,
) {
    val seen by repository.backgroundGuideSeen.collectAsStateWithLifecycle(initialValue = null)
    var dismissed by rememberSaveable { mutableStateOf(false) }
    // Keep the save job alive after the dialog leaves composition.
    val scope = rememberCoroutineScope()
    if ((seen != false || dismissed) && !requested) return
    val context = LocalContext.current
    var exempt by remember { mutableStateOf(isBatteryExempt(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { exempt = isBatteryExempt(context) }
    val close: () -> Unit = {
        dismissed = true
        onClose()
        scope.launch {
            try {
                repository.markBackgroundGuideSeen()
            } catch (_: IOException) {
                onSaveError()
            }
        }
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.background_guide_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.background_guide_intro))
                Text(stringResource(if (exempt) R.string.background_battery_exempt else R.string.background_battery_restricted))
                TextButton(onClick = {
                    openSettings(context,
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context)),
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    )
                }, enabled = !exempt) { Text(stringResource(R.string.background_battery_action)) }
                Text(stringResource(R.string.background_hyperos_steps))
                TextButton(onClick = {
                    openSettings(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)))
                }) { Text(stringResource(R.string.background_app_settings)) }
                Text(stringResource(R.string.background_guide_hint))
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.background_guide_close)) } },
    )
}

@Composable
fun SystemVoiceSettingsButton(onSettingsClosed: () -> Unit) {
    val context = LocalContext.current
    // Settings does not return RESULT_OK when a default engine changes; refresh on any return.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onSettingsClosed()
    }
    TextButton(onClick = {
        openSettings(context, Intent("com.android.settings.TTS_SETTINGS"), launch = { launcher.launch(it) })
    }) {
        Text(stringResource(R.string.settings_system_voice_action))
    }
}

private fun packageUri(context: Context) = "package:${context.packageName}".toUri()

private fun isBatteryExempt(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

/** OEMs can omit individual Settings activities; always offer the standard Settings fallback. */
private fun openSettings(
    context: Context,
    vararg intents: Intent,
    launch: (Intent) -> Unit = { context.startActivity(it) },
) {
    for (intent in intents.toList() + Intent(Settings.ACTION_SETTINGS)) {
        try {
            launch(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next supported screen.
        } catch (_: SecurityException) {
            // Some OEM screens cannot be opened by third-party apps.
        }
    }
    Toast.makeText(context, R.string.settings_open_failed, Toast.LENGTH_LONG).show()
}
