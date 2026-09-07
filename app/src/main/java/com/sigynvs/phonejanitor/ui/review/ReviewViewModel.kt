package com.sigynvs.phonejanitor.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sigynvs.phonejanitor.di.AppContainer
import com.sigynvs.phonejanitor.quarantine.QuarantineOutcome
import com.sigynvs.phonejanitor.quarantine.QuarantineStore
import com.sigynvs.phonejanitor.scan.DuplicateScanner
import com.sigynvs.phonejanitor.scan.FileScanner
import com.sigynvs.phonejanitor.scan.ScanItem
import com.sigynvs.phonejanitor.scan.ScanSession
import com.sigynvs.phonejanitor.scan.ScanType
import com.sigynvs.phonejanitor.settings.SettingsRepository
import com.sigynvs.phonejanitor.util.formatBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewRow(
    val item: ScanItem,
    val selected: Boolean,
    /** Non-null on the first row of a duplicate group, e.g. "3 copies · 12.4 MB each". */
    val groupHeader: String? = null,
    /** Duplicates: the copy suggested to keep (oldest in its group). */
    val isKeeper: Boolean = false,
)

data class ReviewUiState(
    val scanType: ScanType,
    val isScanning: Boolean = false,
    val hasRun: Boolean = false,
    val rows: List<ReviewRow> = emptyList(),
    val dryRun: Boolean = false,
    val committing: Boolean = false,
    val outcome: QuarantineOutcome? = null,
) {
    val total: Int get() = rows.size
    val selectedCount: Int get() = rows.count { it.selected }
    val selectedBytes: Long get() = rows.asSequence().filter { it.selected }.sumOf { it.item.sizeBytes }

    /** For the "Select all / none" toggle — keeper rows don't count toward "all". */
    val toggleableCount: Int get() = rows.count { !it.isKeeper }
    val allSelected: Boolean get() = toggleableCount > 0 && rows.count { it.selected && !it.isKeeper } == toggleableCount
}

/**
 * Drives the review list for one [ScanType].
 *
 * Default selection is per-type:
 *  - Downloads/Screenshots: everything pre-checked (one-tap cleanup of old junk).
 *  - Large Files: nothing checked (opt in per file).
 *  - Duplicates: every copy checked *except* the keeper (oldest in each group).
 *
 * [overrides] holds the user's explicit per-path choices layered on top of that default.
 */
class ReviewViewModel(
    private val scanType: ScanType,
    private val scanSession: ScanSession,
    private val settings: SettingsRepository,
    private val quarantineStore: QuarantineStore,
    private val fileScanner: FileScanner,
    private val duplicateScanner: DuplicateScanner,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val overrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val committing = MutableStateFlow(false)
    private val outcome = MutableStateFlow<QuarantineOutcome?>(null)

    val uiState: StateFlow<ReviewUiState> = combine(
        scanSession.results,
        overrides,
        settings.filters.map { it.dryRun },
        committing,
        outcome,
    ) { results, picks, dryRun, isCommitting, out ->
        val result = results[scanType]
        val items = result?.items.orEmpty()
        val keepers = keeperPaths(items)
        val groupCounts = if (scanType == ScanType.DUPLICATES) {
            items.groupingBy { it.groupId }.eachCount()
        } else {
            emptyMap()
        }

        var lastGroup: String? = null
        val rows = items.map { item ->
            val isKeeper = item.path in keepers
            val default = defaultSelected(item, keepers)
            val header = if (scanType == ScanType.DUPLICATES && item.groupId != lastGroup) {
                lastGroup = item.groupId
                val n = groupCounts[item.groupId] ?: 0
                "$n copies · ${formatBytes(item.sizeBytes)} each"
            } else {
                null
            }
            ReviewRow(
                item = item,
                selected = picks[item.path] ?: default,
                groupHeader = header,
                isKeeper = isKeeper,
            )
        }

        ReviewUiState(
            scanType = scanType,
            isScanning = result?.isScanning == true,
            hasRun = result?.hasRun == true,
            rows = rows,
            dryRun = dryRun,
            committing = isCommitting,
            outcome = out,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState(scanType))

    fun toggle(path: String) {
        overrides.update { picks ->
            val items = scanSession.result(scanType)?.items.orEmpty()
            val item = items.firstOrNull { it.path == path } ?: return@update picks
            val current = picks[path] ?: defaultSelected(item, keeperPaths(items))
            picks + (path to !current)
        }
    }

    fun setAll(selected: Boolean) {
        val items = scanSession.result(scanType)?.items.orEmpty()
        val keepers = keeperPaths(items)
        // "Select all" never checks a keeper; "Select none" clears everything.
        overrides.value = items.associate { item ->
            item.path to (selected && item.path !in keepers)
        }
    }

    fun commit() {
        if (committing.value) return
        val available = scanSession.result(scanType)?.items.orEmpty()
        if (available.isEmpty()) return
        val picks = overrides.value
        val keepers = keeperPaths(available)

        var selected = available.filter { picks[it.path] ?: defaultSelected(it, keepers) }

        if (scanType == ScanType.DUPLICATES) {
            // Never let a group lose its last surviving copy.
            val byGroup = available.groupBy { it.groupId }
            selected = selected.groupBy { it.groupId }.flatMap { (gid, chosen) ->
                val groupAll = byGroup[gid].orEmpty()
                if (groupAll.isNotEmpty() && chosen.size >= groupAll.size) {
                    chosen.sortedBy { it.lastModified }.drop(1)
                } else {
                    chosen
                }
            }
        }

        if (selected.isEmpty()) return

        committing.value = true
        viewModelScope.launch {
            val dryRun = settings.filters.first().dryRun
            val result = quarantineStore.quarantine(selected, dryRun = dryRun)
            outcome.value = result
            committing.value = false
            if (!result.dryRun && result.moved > 0) {
                overrides.value = emptyMap()
                rescan()
            }
        }
    }

    private fun defaultSelected(item: ScanItem, keepers: Set<String>): Boolean = when (scanType) {
        ScanType.DUPLICATES -> item.path !in keepers
        ScanType.LARGE_FILES -> false
        else -> true
    }

    private fun keeperPaths(items: List<ScanItem>): Set<String> {
        if (scanType != ScanType.DUPLICATES) return emptySet()
        return items.groupBy { it.groupId }
            .values
            .mapNotNullTo(HashSet()) { group -> group.minByOrNull { it.lastModified }?.path }
    }

    private fun rescan() {
        scanSession.markScanning(scanType)
        appScope.launch {
            val filters = settings.filters.first()
            val items = when (scanType) {
                ScanType.DUPLICATES -> duplicateScanner.scan(filters)
                else -> fileScanner.scan(scanType, filters)
            }
            scanSession.publish(scanType, items)
        }
    }

    companion object {
        fun factory(container: AppContainer, scanType: ScanType): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ReviewViewModel(
                        scanType = scanType,
                        scanSession = container.scanSession,
                        settings = container.settings,
                        quarantineStore = container.quarantineStore,
                        fileScanner = container.fileScanner,
                        duplicateScanner = container.duplicateScanner,
                        appScope = container.appScope,
                    )
                }
            }
    }
}
