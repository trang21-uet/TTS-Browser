package com.trangnx.ttsbrowser.data.cover

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverSampleSizeTest {
    @Test
    fun smallImageIsNotSubsampled() = assertEquals(1, coverSampleSize(500, 300, 600))

    @Test
    fun keepsAtLeastTargetSizeOnLongerSide() {
        assertEquals(2, coverSampleSize(1200, 800, 600))
        assertEquals(4, coverSampleSize(2400, 3000, 600))
    }

    @Test
    fun usesLongerSide() = assertEquals(1, coverSampleSize(599, 1000, 1000))
}
