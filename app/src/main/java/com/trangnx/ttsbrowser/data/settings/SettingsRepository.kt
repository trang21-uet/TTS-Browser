package com.trangnx.ttsbrowser.data.settings

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trangnx.ttsbrowser.tts.TtsEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException

/** Reader background; [System] follows the app's light/dark theme. */
enum class ReaderTheme { System, Light, Dark, Sepia }

enum class SpeechEngine { System, Edge }

data class SpeechSettings(
    val engine: SpeechEngine = SpeechEngine.System,
    val edgeVoice: String = EDGE_VOICES.first(),
    val rate: Float = 1f,
    val pitch: Float = 1f,
) {
    companion object {
        val EDGE_VOICES = listOf("vi-VN-HoaiMyNeural", "vi-VN-NamMinhNeural")
    }
}

/** Keys retain the old rate preference for the system engine, so upgrades preserve its speed. */
internal object SpeechPreferences {
    val ENGINE = stringPreferencesKey("engine")
    val EDGE_VOICE = stringPreferencesKey("edgeVoice")
    val SYSTEM_RATE = floatPreferencesKey("rate")
    val EDGE_RATE = floatPreferencesKey("edgeRate")
    val SYSTEM_PITCH = floatPreferencesKey("systemPitch")
    val EDGE_PITCH = floatPreferencesKey("edgePitch")

    fun engine(prefs: Preferences) = SpeechEngine.entries.firstOrNull { it.name == prefs[ENGINE] } ?: SpeechEngine.System
    fun rateKey(engine: SpeechEngine) = if (engine == SpeechEngine.Edge) EDGE_RATE else SYSTEM_RATE
    fun pitchKey(engine: SpeechEngine) = if (engine == SpeechEngine.Edge) EDGE_PITCH else SYSTEM_PITCH
    fun clampRate(value: Float) = if (value.isFinite()) value.coerceIn(TtsEngine.MIN_RATE, TtsEngine.MAX_RATE) else 1f
    fun clampPitch(value: Float) = if (value.isFinite()) value.coerceIn(TtsEngine.MIN_PITCH, TtsEngine.MAX_PITCH) else 1f
    fun voice(value: String?) = value?.takeIf { it in SpeechSettings.EDGE_VOICES } ?: SpeechSettings.EDGE_VOICES.first()

    fun read(prefs: Preferences): SpeechSettings {
        val engine = engine(prefs)
        return SpeechSettings(
            engine = engine,
            edgeVoice = voice(prefs[EDGE_VOICE]),
            rate = clampRate(prefs[rateKey(engine)] ?: 1f),
            pitch = clampPitch(prefs[pitchKey(engine)] ?: 1f),
        )
    }
}

data class ReaderSettings(
    val fontSizeSp: Int = DEFAULT_FONT_SIZE_SP,
    val theme: ReaderTheme = ReaderTheme.System,
    /** Speech rate, 1.0 = normal. */
    val rate: Float = 1f,
) {
    companion object {
        const val MIN_FONT_SIZE_SP = 14
        const val MAX_FONT_SIZE_SP = 30
        const val DEFAULT_FONT_SIZE_SP = 19
    }
}

private val Context.settingsDataStore by preferencesDataStore(
    name = "settings",
    // A corrupt file only holds preferences: start over rather than fail.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** User preferences in DataStore. Values are clamped on read and write. */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    /** Never fails: read errors show defaults and reading is retried, so later writes still arrive. */
    private val preferences: Flow<Preferences> = store.data
        .retryWhen { cause, attempt ->
            if (cause !is IOException) return@retryWhen false
            emit(emptyPreferences())
            delay(RETRY_DELAY_MS * (attempt + 1).coerceAtMost(MAX_RETRY_STEPS))
            true
        }
        .catch { emit(emptyPreferences()) }

    val speechSettings: Flow<SpeechSettings> = preferences.map(SpeechPreferences::read).distinctUntilChanged()

    val readerSettings: Flow<ReaderSettings> = preferences
        .map { prefs ->
            ReaderSettings(
                fontSizeSp = clampFontSize(prefs[FONT_SIZE] ?: ReaderSettings.DEFAULT_FONT_SIZE_SP),
                theme = ReaderTheme.entries.firstOrNull { it.name == prefs[READER_THEME] } ?: ReaderTheme.System,
                rate = SpeechPreferences.read(prefs).rate,
            )
        }
        .distinctUntilChanged()

    suspend fun setFontSize(sp: Int) = edit { it[FONT_SIZE] = clampFontSize(sp) }

    suspend fun setReaderTheme(theme: ReaderTheme) = edit { it[READER_THEME] = theme.name }

    suspend fun setEngine(engine: SpeechEngine) = edit { it[SpeechPreferences.ENGINE] = engine.name }

    suspend fun setEdgeVoice(voice: String) = edit { it[SpeechPreferences.EDGE_VOICE] = SpeechPreferences.voice(voice) }

    suspend fun setRate(rate: Float) = edit {
        it[SpeechPreferences.rateKey(SpeechPreferences.engine(it))] = SpeechPreferences.clampRate(rate)
    }

    suspend fun setPitch(pitch: Float) = edit {
        it[SpeechPreferences.pitchKey(SpeechPreferences.engine(it))] = SpeechPreferences.clampPitch(pitch)
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        store.edit(block)
    }

    private fun clampFontSize(sp: Int) = sp.coerceIn(ReaderSettings.MIN_FONT_SIZE_SP, ReaderSettings.MAX_FONT_SIZE_SP)

    private companion object {
        const val RETRY_DELAY_MS = 1_000L
        const val MAX_RETRY_STEPS = 30L

        val FONT_SIZE = intPreferencesKey("fontSize")
        val READER_THEME = stringPreferencesKey("readerTheme")
    }
}
