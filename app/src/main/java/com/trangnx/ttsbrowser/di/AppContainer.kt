@file:OptIn(UnstableApi::class)

package com.trangnx.ttsbrowser.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.trangnx.ttsbrowser.data.db.AppDatabase
import com.trangnx.ttsbrowser.data.repo.BrowsingRepository
import com.trangnx.ttsbrowser.data.repo.SiteRuleRepository
import com.trangnx.ttsbrowser.data.settings.SettingsRepository
import com.trangnx.ttsbrowser.extraction.ElementPicker
import com.trangnx.ttsbrowser.extraction.ExtractionScripts
import com.trangnx.ttsbrowser.extraction.ReadabilityExtractor
import com.trangnx.ttsbrowser.playback.PlaybackConnection
import com.trangnx.ttsbrowser.playback.TtsPlayer
import com.trangnx.ttsbrowser.tts.system.SystemTtsEngine
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Manual dependency container. Repositories, TTS engines and other singletons
 * are added here as lazy properties.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    private val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    private val extractionScripts: ExtractionScripts by lazy { ExtractionScripts(appContext.assets) }

    val browsingRepository: BrowsingRepository by lazy {
        BrowsingRepository(database.historyDao(), database.bookmarkDao())
    }

    val siteRuleRepository: SiteRuleRepository by lazy { SiteRuleRepository(database.siteRuleDao()) }

    val readabilityExtractor: ReadabilityExtractor by lazy {
        ReadabilityExtractor(extractionScripts, siteRuleRepository)
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val elementPicker: ElementPicker by lazy { ElementPicker(extractionScripts) }

    /** Process-wide main-thread scope for app singletons. */
    private val appScope = MainScope()

    /**
     * App-wide player; [com.trangnx.ttsbrowser.playback.PlaybackService] puts it in a MediaSession.
     * Follows the saved speech rate from creation, also when the service starts without the UI.
     */
    val ttsPlayer: TtsPlayer by lazy {
        TtsPlayer(appContext) { SystemTtsEngine(appContext) }.also { player ->
            appScope.launch {
                settingsRepository.readerSettings.map { it.rate }.distinctUntilChanged().collect(player::setPlaybackSpeed)
            }
        }
    }

    val playbackConnection: PlaybackConnection by lazy { PlaybackConnection(appContext, ttsPlayer) }
}
