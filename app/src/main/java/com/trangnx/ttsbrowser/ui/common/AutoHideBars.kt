package com.trangnx.ttsbrowser.ui.common

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val SCROLL_THRESHOLD = 32.dp
private const val SETTLE_MS = 450L

/**
 * Whether toolbars are tucked away: they hide after scrolling down a little and come back on the first
 * scroll up. Hiding resizes the content, which can nudge its scroll position, so scroll deltas arriving
 * right after a change are ignored.
 */
@Stable
class BarsVisibility internal constructor(
    private val thresholdPx: Float,
    private val now: () -> Long = SystemClock::uptimeMillis,
) {
    var hidden by mutableStateOf(false)
        private set
    private var travel = 0f
    private var changedAt = Long.MIN_VALUE / 2

    /** [delta] is positive when the content moved up (the user scrolled down). */
    fun onScroll(delta: Float) {
        if (delta == 0f || now() - changedAt < SETTLE_MS) return
        if (travel * delta < 0f) travel = 0f
        travel += delta
        when {
            travel > thresholdPx -> set(true)
            travel < -thresholdPx -> set(false)
        }
    }

    fun show() = set(false)

    private fun set(value: Boolean) {
        travel = 0f
        if (hidden == value) return
        hidden = value
        changedAt = now()
    }

    /** Observes drags and flings of scrollables below it; list scrolls done by code are not reported. */
    val nestedScroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput) onScroll(-available.y)
            return Offset.Zero
        }
    }
}

@Composable
fun rememberBarsVisibility(): BarsVisibility {
    val threshold = with(LocalDensity.current) { SCROLL_THRESHOLD.toPx() }
    return remember(threshold) { BarsVisibility(threshold) }
}
