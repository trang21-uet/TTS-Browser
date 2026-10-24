package com.trangnx.ttsbrowser.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostOfTest {
    @Test
    fun normalizesHost() {
        assertEquals("akaytruyen.com", hostOf("https://www.AkayTruyen.com/truyen/x?a=1"))
        assertEquals("site.vn", hostOf("http://user@site.vn:8080/chương 1"))
        assertEquals("[::1]", hostOf("http://[::1]:8080/x"))
        assertNull(hostOf("about:blank"))
        assertNull(hostOf(""))
    }
}
