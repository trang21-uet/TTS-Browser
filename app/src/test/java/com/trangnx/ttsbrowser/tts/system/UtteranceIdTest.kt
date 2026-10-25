package com.trangnx.ttsbrowser.tts.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UtteranceIdTest {

    @Test
    fun roundTrips() {
        val id = UtteranceId(generation = 7, index = 123)
        assertEquals(id, UtteranceId.decode(id.encode()))
    }

    @Test
    fun rejectsForeignOrBrokenIds() {
        listOf(null, "", "5", "a:1", "1:b", "1:2:3", "1:-1").forEach { assertNull(it, UtteranceId.decode(it)) }
    }
}
