package com.trangnx.ttsbrowser.extraction

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsResultTest {

    /** evaluateJavascript hands back the returned string as a JSON string literal. */
    private fun asEvaluateResult(json: String) = JSONObject.quote(json)

    @Test
    fun parsesExtractResult() {
        val json = """
            {"ok":true,"url":"https://a.com/c/1","title":"Chương 1","text":"A\nB","source":"selector",
             "selectorMissed":false,
             "links":{"relNext":null,"relPrev":"https://a.com/c/0","anchors":[
               {"href":"https://a.com/c/2","text":"Sau","label":"","attrs":"btn","icons":"fa fa-chevron-right","disabled":false},
               {"href":"","text":"empty href is skipped"}
             ]}}
        """.trimIndent()
        val page = JsResult.rawPage(asEvaluateResult(json))
        assertTrue(page.ok)
        assertEquals("https://a.com/c/1", page.url)
        assertEquals("A\nB", page.text)
        assertEquals(ContentSource.Selector, page.source)
        assertNull(page.links.relNext)
        assertEquals("https://a.com/c/0", page.links.relPrev)
        assertEquals(listOf(LinkCandidate("https://a.com/c/2", "Sau", "", "btn", "fa fa-chevron-right")), page.links.anchors)
    }

    @Test
    fun parsesCoverUrl() {
        val withCover = JsResult.rawPage(asEvaluateResult("""{"ok":true,"coverUrl":"https://a.com/cover.jpg"}"""))
        assertEquals("https://a.com/cover.jpg", withCover.coverUrl)
        assertEquals("", JsResult.rawPage(asEvaluateResult("""{"ok":true}""")).coverUrl)
    }

    @Test
    fun scriptErrorAndMissingResult() {
        val error = JsResult.rawPage(asEvaluateResult("""{"ok":false,"error":"boom"}"""))
        assertFalse(error.ok)
        assertEquals("boom", error.error)

        assertFalse(JsResult.rawPage("null").ok)
        assertFalse(JsResult.rawPage(null).ok)
        assertFalse(JsResult.rawPage(asEvaluateResult("not json")).ok)
    }

    @Test
    fun string_onlyAcceptsStrings() {
        assertEquals("div.a", JsResult.string("\"div.a\""))
        assertNull(JsResult.string("true"))
        assertNull(JsResult.string("null"))
    }
}
