@file:OptIn(UnstableApi::class)

package com.trangnx.ttsbrowser.di

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.trangnx.ttsbrowser.data.db.AppDatabase
import com.trangnx.ttsbrowser.data.repo.BrowsingRepository
import com.trangnx.ttsbrowser.data.repo.LibraryRepository
import com.trangnx.ttsbrowser.data.repo.SiteRuleRepository
import com.trangnx.ttsbrowser.data.settings.SettingsRepository
import com.trangnx.ttsbrowser.extraction.ElementPicker
import com.trangnx.ttsbrowser.extraction.ExtractionScripts
import com.trangnx.ttsbrowser.extraction.HeadlessPageLoader
import com.trangnx.ttsbrowser.extraction.ReadabilityExtractor
import com.trangnx.ttsbrowser.playback.NextChapterLoader
import com.trangnx.ttsbrowser.playback.PlaybackConnection
import com.trangnx.ttsbrowser.playback.ProgressTracker
import com.trangnx.ttsbrowser.playback.SleepTimer
import com.trangnx.ttsbrowser.playback.TtsPlayer
import com.trangnx.ttsbrowser.tts.system.SystemTtsEngine
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
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

    val libraryRepository: LibraryRepository by lazy { LibraryRepository(database.libraryDao()) }

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

    val headlessPageLoader: HeadlessPageLoader by lazy { HeadlessPageLoader(appContext, readabilityExtractor) }

    val nextChapterLoader: NextChapterLoader by lazy { NextChapterLoader(appScope, headlessPageLoader, playbackConnection) }

    val progressTracker: ProgressTracker by lazy { ProgressTracker(appScope, libraryRepository, playbackConnection) }

    val sleepTimer: SleepTimer by lazy { SleepTimer(appScope, SystemClock::elapsedRealtime) { ttsPlayer.pause() } }

    init {
        // Running whenever the process is, with or without UI (the service can start alone).
        progressTracker.start()
        appScope.launch {
            playbackConnection.state.map { it.isEnded }.distinctUntilChanged().filter { it }.collect { sleepTimer.onChapterEnded() }
        }
    }
}
