@file:OptIn(UnstableApi::class)

package com.trangnx.ttsbrowser.di

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.trangnx.ttsbrowser.data.settings.SpeechEngine
import com.trangnx.ttsbrowser.data.settings.SpeechSettings
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.data.cover.CoverStore
import com.trangnx.ttsbrowser.data.db.AppDatabase
import com.trangnx.ttsbrowser.data.repo.BrowsingRepository
import com.trangnx.ttsbrowser.data.repo.LibraryRepository
import com.trangnx.ttsbrowser.data.repo.SiteRuleRepository
import com.trangnx.ttsbrowser.data.settings.SettingsRepository
import com.trangnx.ttsbrowser.extraction.AdBlocker
import com.trangnx.ttsbrowser.extraction.ElementPicker
import com.trangnx.ttsbrowser.extraction.ExtractionScripts
import com.trangnx.ttsbrowser.extraction.ExtractionResult
import com.trangnx.ttsbrowser.extraction.HeadlessPageLoader
import com.trangnx.ttsbrowser.extraction.ReadabilityExtractor
import com.trangnx.ttsbrowser.playback.AdjacentChapterLoader
import com.trangnx.ttsbrowser.playback.PlaybackConnection
import com.trangnx.ttsbrowser.playback.ProgressTracker
import com.trangnx.ttsbrowser.playback.SleepTimer
import com.trangnx.ttsbrowser.playback.TtsPlayer
import com.trangnx.ttsbrowser.tts.system.SystemTtsEngine
import com.trangnx.ttsbrowser.tts.FallbackTtsEngine
import com.trangnx.ttsbrowser.tts.edge.EdgeTtsClient
import com.trangnx.ttsbrowser.tts.edge.EdgeTtsEngine
import com.trangnx.ttsbrowser.ui.browser.message
import com.trangnx.ttsbrowser.BuildConfig
import com.trangnx.ttsbrowser.update.ApkInstaller
import com.trangnx.ttsbrowser.update.AppVersion
import com.trangnx.ttsbrowser.update.UpdateController
import com.trangnx.ttsbrowser.update.UpdateRepository
import java.io.File
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    val libraryRepository: LibraryRepository by lazy { LibraryRepository(database.libraryDao(), CoverStore(appContext)) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val elementPicker: ElementPicker by lazy { ElementPicker(extractionScripts) }

    /** Process-wide main-thread scope for app singletons. */
    private val appScope = MainScope()
    private var speechSettings = SpeechSettings()
    private val edgeClient by lazy { EdgeTtsClient() }
    private val fallbackNotices = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val speechFallbackNotices = fallbackNotices.asSharedFlow()

    private fun notifySpeechFallback() {
        if (fallbackNotices.subscriptionCount.value > 0) fallbackNotices.tryEmit(Unit)
        else Toast.makeText(appContext, R.string.playback_edge_fallback, Toast.LENGTH_LONG).show()
    }

    /**
     * App-wide player; [com.trangnx.ttsbrowser.playback.PlaybackService] puts it in a MediaSession.
     * Follows the saved speech rate from creation, also when the service starts without the UI.
     */
    val ttsPlayer: TtsPlayer by lazy {
        TtsPlayer(appContext) {
            when (speechSettings.engine) {
                SpeechEngine.System -> SystemTtsEngine(appContext)
                SpeechEngine.Edge -> FallbackTtsEngine(
                    EdgeTtsEngine(appContext, edgeClient, speechSettings.edgeVoice),
                    { SystemTtsEngine(appContext) },
                    ::notifySpeechFallback,
                )
            }
        }.also { player ->
            appScope.launch {
                settingsRepository.speechSettings.collect { settings ->
                    val replace = settings.engine != speechSettings.engine || settings.edgeVoice != speechSettings.edgeVoice
                    speechSettings = settings
                    player.configureSpeech(settings.rate, settings.pitch, replaceEngine = replace)
                }
            }
        }
    }

    val updateController: UpdateController by lazy {
        UpdateController(
            appScope,
            UpdateRepository(File(appContext.cacheDir, "updates")),
            ApkInstaller(appContext),
            settingsRepository,
            requireNotNull(AppVersion.parse(BuildConfig.VERSION_NAME)) { "Invalid versionName" },
        )
    }

    val playbackConnection: PlaybackConnection by lazy { PlaybackConnection(appContext, ttsPlayer) }

    val adBlocker: AdBlocker by lazy {
        AdBlocker(appContext.assets).also { blocker ->
            appScope.launch { settingsRepository.adBlockEnabled.collect { blocker.enabled = it } }
        }
    }

    val headlessPageLoader: HeadlessPageLoader by lazy { HeadlessPageLoader(appContext, readabilityExtractor, adBlocker) }

    val adjacentChapterLoader: AdjacentChapterLoader by lazy { AdjacentChapterLoader(appScope, headlessPageLoader, playbackConnection) }

    val progressTracker: ProgressTracker by lazy { ProgressTracker(appScope, libraryRepository, playbackConnection) }

    val sleepTimer: SleepTimer by lazy {
        SleepTimer(appScope, SystemClock::elapsedRealtime, onVolume = { ttsPlayer.setSpeechVolume(it) }) { ttsPlayer.pause() }
    }

    init {
        // Running whenever the process is, with or without UI (the service can start alone).
        progressTracker.start()
        appScope.launch {
            playbackConnection.state.map { it.isEnded }.distinctUntilChanged().filter { it }.collect {
                // Read on to the next chapter, unless an end-of-chapter sleep timer says stop.
                if (!sleepTimer.onChapterEnded()) adjacentChapterLoader.openNext(::reportAutoAdvanceFailure)
            }
        }
    }

    /** [reason] null: the chapter was the last one, nothing to report. */
    private fun reportAutoAdvanceFailure(reason: ExtractionResult.Reason?) {
        reason?.let { Toast.makeText(appContext, it.message, Toast.LENGTH_LONG).show() }
    }
}
