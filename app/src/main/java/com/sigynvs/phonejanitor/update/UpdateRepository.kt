package com.sigynvs.phonejanitor.update

import com.sigynvs.phonejanitor.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Owns the update lifecycle: check → available → download → ready → (system installer). */
class UpdateRepository(
    private val checker: UpdateChecker,
    private val installer: UpdateInstaller,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Runs at most once per [AUTO_CHECK_INTERVAL]; failures stay silent. */
    fun maybeAutoCheck() {
        scope.launch {
            val last = settings.lastUpdateCheck.first()
            if (System.currentTimeMillis() - last < AUTO_CHECK_INTERVAL) return@launch
            runCheck(silentOnError = true)
        }
    }

    fun checkNow() {
        scope.launch { runCheck(silentOnError = false) }
    }

    private suspend fun runCheck(silentOnError: Boolean) {
        val current = _state.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        settings.setLastUpdateCheck(System.currentTimeMillis())
        _state.value = runCatching { checker.check() }.fold(
            onSuccess = { info -> if (info == null) UpdateState.UpToDate else UpdateState.Available(info) },
            onFailure = {
                if (silentOnError) UpdateState.Idle
                else UpdateState.Failed(it.message ?: "Couldn't reach GitHub to check for updates.")
            },
        )
    }

    fun download() {
        val available = _state.value as? UpdateState.Available ?: return
        scope.launch {
            val info = available.info
            _state.value = UpdateState.Downloading(info, 0f)
            _state.value = runCatching {
                installer.download(info.apkUrl, info.sizeBytes) { progress ->
                    _state.value = UpdateState.Downloading(info, progress)
                }
            }.fold(
                onSuccess = { apk -> UpdateState.ReadyToInstall(info, apk) },
                onFailure = { UpdateState.Failed(it.message ?: "Download failed.") },
            )
        }
    }

    /** Launches the system installer if allowed; returns false if the user must grant install permission first. */
    fun install(): Boolean {
        val ready = _state.value as? UpdateState.ReadyToInstall ?: return true
        if (!installer.canInstallPackages()) return false
        installer.launchInstall(ready.apk)
        return true
    }

    fun canInstallPackages(): Boolean = installer.canInstallPackages()
    fun installPermissionIntent() = installer.installPermissionIntent()

    fun reset() {
        if (_state.value !is UpdateState.Downloading) _state.value = UpdateState.Idle
    }

    private companion object {
        val AUTO_CHECK_INTERVAL: Long = TimeUnit.DAYS.toMillis(1)
    }
}
