package com.trangnx.ttsbrowser.ui.player

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.playback.SleepTimer
import com.trangnx.ttsbrowser.playback.SleepTimerState
import kotlinx.coroutines.delay

private val DURATIONS_MIN = listOf(15, 30, 45, 60)
private const val REFRESH_MS = 15_000L

/** Picks the sleep timer; the running one shows how long is left. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    state: SleepTimerState,
    onMinutes: (Int) -> Unit,
    onEndOfChapter: () -> Unit,
    onOff: () -> Unit,
    onDismiss: () -> Unit,
) {
    var clock by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(REFRESH_MS)
            clock = SystemClock.elapsedRealtime()
        }
    }
    val remainingMinutes = (state as? SleepTimerState.Countdown)
        ?.let { ((it.endsAt - clock + SleepTimer.MS_PER_MINUTE - 1) / SleepTimer.MS_PER_MINUTE).toInt().coerceAtLeast(1) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.sleep_timer),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (remainingMinutes != null) Status(stringResource(R.string.sleep_timer_remaining, remainingMinutes))
            if (state == SleepTimerState.EndOfChapter) Status(stringResource(R.string.sleep_timer_end_of_chapter_active))
            DURATIONS_MIN.forEach { minutes ->
                Option(stringResource(R.string.sleep_timer_minutes, minutes), selected = false) {
                    onMinutes(minutes)
                    onDismiss()
                }
            }
            Option(stringResource(R.string.sleep_timer_end_of_chapter), selected = state == SleepTimerState.EndOfChapter) {
                onEndOfChapter()
                onDismiss()
            }
            if (state != SleepTimerState.Off) {
                Option(stringResource(R.string.sleep_timer_off), selected = false) {
                    onOff()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun Status(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

@Composable
private fun Option(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = if (selected) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
