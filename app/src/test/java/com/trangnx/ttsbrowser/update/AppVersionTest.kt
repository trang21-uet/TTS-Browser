package com.trangnx.ttsbrowser.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun parsesTagsAndVersionNames() {
        assertEquals(AppVersion(0, 2, 0), AppVersion.parse("v0.2.0"))
        assertEquals(AppVersion(1, 10, 3), AppVersion.parse(" 1.10.3 "))
    }

    @Test
    fun malformedTagsAreNull() {
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("v1.2"))
        assertNull(AppVersion.parse("v99999999999.0.0"))
    }

    @Test
    fun comparesNumerically() {
        assertTrue(AppVersion(0, 10, 0) > AppVersion(0, 9, 9))
        assertTrue(AppVersion(1, 0, 0) > AppVersion(0, 99, 99))
        assertTrue(AppVersion(0, 2, 1) > AppVersion(0, 2, 0))
        assertEquals(0, AppVersion(0, 2, 0).compareTo(AppVersion(0, 2, 0)))
    }

    @Test
    fun printsAsVersionName() {
        assertEquals("0.2.0", AppVersion(0, 2, 0).toString())
    }
}
