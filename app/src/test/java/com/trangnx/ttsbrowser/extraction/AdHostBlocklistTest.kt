package com.trangnx.ttsbrowser.extraction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdHostBlocklistTest {
    private val blocklist = AdHostBlocklist.parse(
        sequenceOf("# comment", "", "doubleclick.net", "  Ads.Example.COM  # trailing", "an.yandex.ru"),
    )

    @Test
    fun blocksListedHostAndItsSubdomains() {
        assertTrue(blocklist.isBlocked("doubleclick.net"))
        assertTrue(blocklist.isBlocked("stats.g.doubleclick.net"))
        assertTrue(blocklist.isBlocked("ads.example.com"))
    }

    @Test
    fun doesNotBlockSimilarOrParentHosts() {
        assertFalse(blocklist.isBlocked("notdoubleclick.net"))
        assertFalse(blocklist.isBlocked("example.com"))
        assertFalse(blocklist.isBlocked("yandex.ru"))
        assertFalse(blocklist.isBlocked("net"))
    }

    @Test
    fun ignoresCaseAndTrailingDot() {
        assertTrue(blocklist.isBlocked("DoubleClick.NET."))
    }

    @Test
    fun nullOrEmptyHostIsNotBlocked() {
        assertFalse(blocklist.isBlocked(null))
        assertFalse(blocklist.isBlocked(""))
    }

    @Test
    fun parseSkipsCommentsAndBlankLines() {
        val parsed = AdHostBlocklist.parse(sequenceOf("# only a comment", "   ", "a.com # note"))
        assertTrue(parsed.isBlocked("a.com"))
        assertEquals(false, parsed.isBlocked("b.com"))
    }
}
