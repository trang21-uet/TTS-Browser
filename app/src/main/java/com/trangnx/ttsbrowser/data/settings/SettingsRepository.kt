package com.trangnx.ttsbrowser.data.settings

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
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
    // A corrupt file only holds display preferences: start over rather than fail.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** User preferences in DataStore. Values are clamped on read and write. */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    /** Never fails: read errors show defaults and reading is retried, so later writes still arrive. */
    val readerSettings: Flow<ReaderSettings> = store.data
        .retryWhen { cause, attempt ->
            if (cause !is IOException) return@retryWhen false
            emit(emptyPreferences())
            delay(RETRY_DELAY_MS * (attempt + 1).coerceAtMost(MAX_RETRY_STEPS))
            true
        }
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            ReaderSettings(
                fontSizeSp = clampFontSize(prefs[FONT_SIZE] ?: ReaderSettings.DEFAULT_FONT_SIZE_SP),
                theme = ReaderTheme.entries.firstOrNull { it.name == prefs[READER_THEME] } ?: ReaderTheme.System,
                rate = clampRate(prefs[RATE] ?: 1f),
            )
        }
        .distinctUntilChanged()

    suspend fun setFontSize(sp: Int) = edit { it[FONT_SIZE] = clampFontSize(sp) }

    suspend fun setReaderTheme(theme: ReaderTheme) = edit { it[READER_THEME] = theme.name }

    suspend fun setRate(rate: Float) = edit { it[RATE] = clampRate(rate) }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        store.edit(block)
    }

    private fun clampFontSize(sp: Int) = sp.coerceIn(ReaderSettings.MIN_FONT_SIZE_SP, ReaderSettings.MAX_FONT_SIZE_SP)

    private fun clampRate(rate: Float) = rate.coerceIn(TtsEngine.MIN_RATE, TtsEngine.MAX_RATE)

    private companion object {
        const val RETRY_DELAY_MS = 1_000L
        const val MAX_RETRY_STEPS = 30L

        val FONT_SIZE = intPreferencesKey("fontSize")
        val READER_THEME = stringPreferencesKey("readerTheme")
        val RATE = floatPreferencesKey("rate")
    }
}
