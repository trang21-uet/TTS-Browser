package com.trangnx.ttsbrowser.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsVisibilityTest {
    private var clock = 10_000L
    private val bars = BarsVisibility(thresholdPx = 100f, now = { clock })

    @Test
    fun `hides only after scrolling down past the threshold`() {
        bars.onScroll(60f)
        assertFalse(bars.hidden)
        bars.onScroll(60f)
        assertTrue(bars.hidden)
    }

    @Test
    fun `scrolling up shows again`() {
        bars.onScroll(150f)
        clock += 1_000
        bars.onScroll(-150f)
        assertFalse(bars.hidden)
    }

    @Test
    fun `direction change restarts the accumulated distance`() {
        bars.onScroll(80f)
        bars.onScroll(-30f)
        bars.onScroll(80f)
        assertFalse(bars.hidden)
    }

    @Test
    fun `scroll right after a change is ignored while the layout settles`() {
        bars.onScroll(150f)
        clock += 100
        bars.onScroll(-150f)
        assertTrue(bars.hidden)
    }

    @Test
    fun `show works at any time`() {
        bars.onScroll(150f)
        bars.show()
        assertFalse(bars.hidden)
    }
}
