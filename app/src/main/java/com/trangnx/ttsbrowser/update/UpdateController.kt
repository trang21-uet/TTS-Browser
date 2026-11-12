package com.trangnx.ttsbrowser.update

import com.trangnx.ttsbrowser.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val release: GithubRelease) : UpdateState
    data class Downloading(val release: GithubRelease) : UpdateState
    data class Ready(val release: GithubRelease, val apk: File, val bannerDismissed: Boolean = false) : UpdateState
}

/** One-off outcomes the UI reports in a snackbar. */
enum class UpdateEvent { UpToDate, CheckFailed, DownloadFailed, InstallNeedsPermission, InstallFailed }

/**
 * App-wide update flow: check, download, install. Lives in the app container so a download
 * keeps running while the user moves between screens. Call from the main thread.
 */
class UpdateController(
    private val scope: CoroutineScope,
    private val repository: UpdateRepository,
    private val installer: ApkInstaller,
    private val settings: SettingsRepository,
    val installedVersion: AppVersion,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** 0..1 while downloading; null until the first chunk reports a total size. */
    private val _progress = MutableStateFlow<Float?>(null)
    val progress: StateFlow<Float?> = _progress.asStateFlow()

    /** Whether the "new version" dialog is open. "Để sau" closes it; the state stays [UpdateState.Available]. */
    private val _dialogVisible = MutableStateFlow(false)
    val dialogVisible: StateFlow<Boolean> = _dialogVisible.asStateFlow()

    private val eventChannel = Channel<UpdateEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var downloadJob: Job? = null
    private var launchChecked = false

    /** Once per process, and only when the user left automatic checks on. Silent unless an update exists. */
    fun checkOnLaunch() {
        if (launchChecked) return
        launchChecked = true
        scope.launch {
            if (settings.autoUpdateCheck.first()) check(manual = false)
        }
    }

    /** What the Settings button does in the current state. */
    fun primaryAction() {
        when (_state.value) {
            UpdateState.Idle -> check(manual = true)
            is UpdateState.Available -> _dialogVisible.value = true
            is UpdateState.Ready -> install()
            UpdateState.Checking, is UpdateState.Downloading -> Unit
        }
    }

    fun dismissDialog() {
        _dialogVisible.value = false
    }

    fun download() {
        val release = (_state.value as? UpdateState.Available)?.release ?: return
        _dialogVisible.value = false
        _progress.value = null
        _state.value = UpdateState.Downloading(release)
        downloadJob = scope.launch {
            try {
                val apk = repository.download(release) { _progress.value = it }
                _state.value = UpdateState.Ready(release, apk)
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                if (isActive) {
                    _state.value = UpdateState.Available(release)
                    eventChannel.trySend(UpdateEvent.DownloadFailed)
                }
            }
        }
    }

    fun cancelDownload() {
        val release = (_state.value as? UpdateState.Downloading)?.release ?: return
        downloadJob?.cancel()
        downloadJob = null
        _progress.value = null
        _state.value = UpdateState.Available(release)
    }

    fun install() {
        val ready = _state.value as? UpdateState.Ready ?: return
        when (installer.install(ready.apk)) {
            ApkInstaller.Result.Started -> Unit
            ApkInstaller.Result.NeedsPermission -> eventChannel.trySend(UpdateEvent.InstallNeedsPermission)
            ApkInstaller.Result.Failed -> eventChannel.trySend(UpdateEvent.InstallFailed)
        }
    }

    fun dismissBanner() {
        (_state.value as? UpdateState.Ready)?.let { _state.value = it.copy(bannerDismissed = true) }
    }

    private fun check(manual: Boolean) {
        if (_state.value != UpdateState.Idle) return
        _state.value = UpdateState.Checking
        scope.launch {
            try {
                val latest = repository.fetchLatest()
                if (latest != null && latest.version > installedVersion) {
                    _state.value = UpdateState.Available(latest)
                    _dialogVisible.value = true
                } else {
                    _state.value = UpdateState.Idle
                    if (manual) eventChannel.trySend(UpdateEvent.UpToDate)
                }
            } catch (e: CancellationException) {
                _state.value = UpdateState.Idle
                throw e
            } catch (_: IOException) {
                _state.value = UpdateState.Idle
                if (manual) eventChannel.trySend(UpdateEvent.CheckFailed)
            }
        }
    }
}
