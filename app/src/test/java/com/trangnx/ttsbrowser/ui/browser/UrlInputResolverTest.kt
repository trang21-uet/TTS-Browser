package com.trangnx.ttsbrowser.ui.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlInputResolverTest {

    @Test
    fun blankInput_returnsNull() {
        assertNull(UrlInputResolver.resolve("   "))
    }

    @Test
    fun fullUrl_isKept() {
        assertEquals("http://example.com/a?b=1", UrlInputResolver.resolve(" http://example.com/a?b=1 "))
    }

    @Test
    fun bareDomain_getsHttps() {
        assertEquals("https://akaytruyen.com", UrlInputResolver.resolve("akaytruyen.com"))
        assertEquals(
            "https://akaytruyen.com/truyen/abc/chuong-1",
            UrlInputResolver.resolve("akaytruyen.com/truyen/abc/chuong-1"),
        )
    }

    @Test
    fun localhostAndIpWithPort_getHttps() {
        assertEquals("https://localhost:8080/x", UrlInputResolver.resolve("localhost:8080/x"))
        assertEquals("https://192.168.1.2", UrlInputResolver.resolve("192.168.1.2"))
    }

    @Test
    fun keywords_becomeSearch() {
        assertEquals(
            "https://www.google.com/search?q=truy%E1%BB%87n+ti%C3%AAn+hi%E1%BB%87p",
            UrlInputResolver.resolve("truyện tiên hiệp"),
        )
        assertEquals("https://www.google.com/search?q=kotlin", UrlInputResolver.resolve("kotlin"))
    }

    @Test
    fun domainWithSpaces_becomesSearch() {
        assertEquals("https://www.google.com/search?q=read+example.com", UrlInputResolver.resolve("read example.com"))
    }
}
