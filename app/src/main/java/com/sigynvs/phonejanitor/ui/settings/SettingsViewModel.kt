package com.sigynvs.phonejanitor.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sigynvs.phonejanitor.BuildConfig
import com.sigynvs.phonejanitor.di.AppContainer
import com.sigynvs.phonejanitor.scan.ScanFilters
import com.sigynvs.phonejanitor.settings.SettingsRepository
import com.sigynvs.phonejanitor.update.UpdateRepository
import com.sigynvs.phonejanitor.update.UpdateState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val updates: UpdateRepository,
) : ViewModel() {

    val filters: StateFlow<ScanFilters> =
        settings.filters.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanFilters.DEFAULT)

    val updateState: StateFlow<UpdateState> = updates.state

    val versionLabel: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    fun setDownloadsDays(value: Int) = viewModelScope.launch { settings.setDownloadsDays(value) }
    fun setScreenshotsDays(value: Int) = viewModelScope.launch { settings.setScreenshotsDays(value) }
    fun setLargeMb(value: Int) = viewModelScope.launch { settings.setLargeMb(value) }
    fun setDryRun(value: Boolean) = viewModelScope.launch { settings.setDryRun(value) }

    fun checkForUpdates() = updates.checkNow()
    fun downloadUpdate() = updates.download()

    /** true = installer launched; false = user must grant "install unknown apps" first. */
    fun installUpdate(): Boolean = updates.install()

    fun installPermissionIntent(): Intent = updates.installPermissionIntent()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container.settings, container.updateRepository) }
        }
    }
}
