package com.trangnx.ttsbrowser.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderScrollbarTest {

    @Test
    fun computeThumbHeight_emptyOrSingleItem_returnsZero() {
        val height0 = computeThumbHeight(
            totalItems = 0,
            visibleItemsCount = 0,
            visibleItemsHeight = 0f,
            viewportHeight = 1000f,
            trackHeight = 1000f,
            minThumbHeight = 100f,
        )
        val height1 = computeThumbHeight(
            totalItems = 1,
            visibleItemsCount = 1,
            visibleItemsHeight = 500f,
            viewportHeight = 1000f,
            trackHeight = 1000f,
            minThumbHeight = 100f,
        )
        assertEquals(0f, height0, 0.001f)
        assertEquals(0f, height1, 0.001f)
    }

    @Test
    fun computeThumbHeight_clampsToMinAndMax() {
        // Very long content -> should clamp to minThumbHeight
        val minHeight = computeThumbHeight(
            totalItems = 1000,
            visibleItemsCount = 5,
            visibleItemsHeight = 1000f,
            viewportHeight = 1000f,
            trackHeight = 1000f,
            minThumbHeight = 100f,
        )
        assertEquals(100f, minHeight, 0.001f)

        // Very short content with multiple items -> clamps to max (80% of track)
        val maxHeight = computeThumbHeight(
            totalItems = 2,
            visibleItemsCount = 2,
            visibleItemsHeight = 1100f,
            viewportHeight = 1000f,
            trackHeight = 1000f,
            minThumbHeight = 100f,
        )
        assertEquals(800f, maxHeight, 0.001f)
    }

    @Test
    fun computeScrollProgress_atTop_returnsZero() {
        val progress = computeScrollProgress(
            totalItems = 50,
            firstIndex = 0,
            firstOffset = 0,
            firstSize = 200,
            lastIndex = 5,
            lastOffset = 800,
            lastSize = 200,
            viewportEnd = 1000,
        )
        assertEquals(0f, progress, 0.001f)
    }

    @Test
    fun computeScrollProgress_atBottom_returnsOne() {
        val progress = computeScrollProgress(
            totalItems = 50,
            firstIndex = 45,
            firstOffset = 0,
            firstSize = 200,
            lastIndex = 49,
            lastOffset = 800,
            lastSize = 200,
            viewportEnd = 1000,
        )
        assertEquals(1f, progress, 0.001f)
    }

    @Test
    fun computeScrollProgress_inMiddle_interpolatesSmoothly() {
        val progress = computeScrollProgress(
            totalItems = 50,
            firstIndex = 22,
            firstOffset = -100,
            firstSize = 200,
            lastIndex = 27,
            lastOffset = 900,
            lastSize = 200,
            viewportEnd = 1000,
        )
        assertTrue(progress > 0.4f && progress < 0.6f)
    }

    @Test
    fun computeTargetScroll_atExtremes_mapsToFirstAndLast() {
        val atTop = computeTargetScroll(
            progress = 0f,
            totalItems = 50,
            visibleCount = 5,
            avgItemHeight = 200f,
        )
        assertEquals(0 to 0, atTop)

        val atBottom = computeTargetScroll(
            progress = 1f,
            totalItems = 50,
            visibleCount = 5,
            avgItemHeight = 200f,
        )
        assertEquals(49 to 0, atBottom)
    }

    @Test
    fun computeTargetScroll_inMiddle_calculatesCorrectIndexAndOffset() {
        val (index, offset) = computeTargetScroll(
            progress = 0.5f,
            totalItems = 50,
            visibleCount = 6, // maxFirstIndex = 44
            avgItemHeight = 200f,
        )
        // 0.5 * 44 = 22.0
        assertEquals(22, index)
        assertEquals(0, offset)
    }
}
