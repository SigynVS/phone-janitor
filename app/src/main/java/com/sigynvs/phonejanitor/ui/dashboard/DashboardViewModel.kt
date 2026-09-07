package com.sigynvs.phonejanitor.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sigynvs.phonejanitor.di.AppContainer
import com.sigynvs.phonejanitor.permission.StorageAccess
import com.sigynvs.phonejanitor.quarantine.QuarantineStore
import com.sigynvs.phonejanitor.quarantine.QuarantineSummary
import com.sigynvs.phonejanitor.scan.DuplicateScanner
import com.sigynvs.phonejanitor.scan.FileScanner
import com.sigynvs.phonejanitor.scan.ScanResult
import com.sigynvs.phonejanitor.scan.ScanSession
import com.sigynvs.phonejanitor.scan.ScanType
import com.sigynvs.phonejanitor.settings.SettingsRepository
import com.sigynvs.phonejanitor.update.UpdateRepository
import com.sigynvs.phonejanitor.update.UpdateState
import com.sigynvs.phonejanitor.util.StorageInfo
import com.sigynvs.phonejanitor.util.readStorageInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val hasStorageAccess: Boolean = false,
    val storage: StorageInfo = StorageInfo.EMPTY,
    val quarantine: QuarantineSummary = QuarantineSummary.EMPTY,
    val scans: Map<ScanType, ScanResult> = emptyMap(),
    val dryRun: Boolean = false,
)

class DashboardViewModel(
    private val fileScanner: FileScanner,
    private val duplicateScanner: DuplicateScanner,
    private val scanSession: ScanSession,
    private val settings: SettingsRepository,
    private val quarantineStore: QuarantineStore,
    private val updateRepository: UpdateRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val access = MutableStateFlow(StorageAccess.isGranted())
    private val storage = MutableStateFlow(readStorageInfo())

    val updateState: StateFlow<UpdateState> = updateRepository.state

    init {
        updateRepository.maybeAutoCheck()
    }

    fun onUpdateAction(): Boolean = when (updateRepository.state.value) {
        is UpdateState.Available -> {
            updateRepository.download()
            true
        }
        is UpdateState.ReadyToInstall -> updateRepository.install()
        else -> true
    }

    fun dismissUpdate() = updateRepository.reset()

    fun installPermissionIntent() = updateRepository.installPermissionIntent()

    val uiState: StateFlow<DashboardUiState> = combine(
        access,
        storage,
        scanSession.results,
        quarantineStore.observeSummary(),
        settings.filters,
    ) { hasAccess, storageInfo, scans, quarantine, filters ->
        DashboardUiState(
            hasStorageAccess = hasAccess,
            storage = storageInfo,
            quarantine = quarantine,
            scans = scans,
            dryRun = filters.dryRun,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    /** Re-checked whenever the dashboard resumes (e.g. returning from the Settings permission screen). */
    fun refresh() {
        access.value = StorageAccess.isGranted()
        storage.value = readStorageInfo()
    }

    fun startScan(type: ScanType) {
        if (!type.enabled || scanSession.result(type)?.isScanning == true) return
        scanSession.markScanning(type)
        appScope.launch {
            val filters = settings.filters.first()
            val items = when (type) {
                ScanType.DUPLICATES -> duplicateScanner.scan(filters)
                else -> fileScanner.scan(type, filters)
            }
            scanSession.publish(type, items)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DashboardViewModel(
                    fileScanner = container.fileScanner,
                    duplicateScanner = container.duplicateScanner,
                    scanSession = container.scanSession,
                    settings = container.settings,
                    quarantineStore = container.quarantineStore,
                    updateRepository = container.updateRepository,
                    appScope = container.appScope,
                )
            }
        }
    }
}
