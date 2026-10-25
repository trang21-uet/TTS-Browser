package com.trangnx.ttsbrowser.data.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechPreferencesTest {
    @Test
    fun `old rate remains system rate while Edge starts with defaults`() {
        val prefs = mutablePreferencesOf(SpeechPreferences.SYSTEM_RATE to 1.7f)
        assertEquals(1.7f, SpeechPreferences.read(prefs).rate)
        prefs[SpeechPreferences.ENGINE] = SpeechEngine.Edge.name
        assertEquals(1f, SpeechPreferences.read(prefs).rate)
    }

    @Test
    fun `engines preserve separate speed and pitch`() {
        val prefs = mutablePreferencesOf(
            SpeechPreferences.SYSTEM_RATE to 1.2f,
            SpeechPreferences.SYSTEM_PITCH to 0.9f,
            SpeechPreferences.EDGE_RATE to 2.3f,
            SpeechPreferences.EDGE_PITCH to 1.6f,
        )
        assertEquals(SpeechSettings(rate = 1.2f, pitch = 0.9f), SpeechPreferences.read(prefs))
        prefs[SpeechPreferences.ENGINE] = SpeechEngine.Edge.name
        assertEquals(SpeechSettings(engine = SpeechEngine.Edge, rate = 2.3f, pitch = 1.6f), SpeechPreferences.read(prefs))
    }

    @Test
    fun `invalid preferences use safe defaults and clamp limits`() {
        val prefs = mutablePreferencesOf(
            SpeechPreferences.ENGINE to "unknown-engine",
            SpeechPreferences.EDGE_VOICE to "invalid-voice",
            SpeechPreferences.SYSTEM_RATE to Float.NaN,
            SpeechPreferences.SYSTEM_PITCH to Float.POSITIVE_INFINITY,
        )
        assertEquals(SpeechSettings(), SpeechPreferences.read(prefs))
        prefs[SpeechPreferences.SYSTEM_RATE] = 20f
        prefs[SpeechPreferences.SYSTEM_PITCH] = -10f
        assertEquals(3f, SpeechPreferences.read(prefs).rate)
        assertEquals(0.5f, SpeechPreferences.read(prefs).pitch)
        assertEquals(SpeechSettings(), SpeechPreferences.read(emptyPreferences()))
    }

    @Test
    fun `both supported voices round trip`() {
        SpeechSettings.EDGE_VOICES.forEach { voice ->
            val prefs = mutablePreferencesOf(SpeechPreferences.EDGE_VOICE to voice)
            assertEquals(voice, SpeechPreferences.read(prefs).edgeVoice)
        }
    }
}
