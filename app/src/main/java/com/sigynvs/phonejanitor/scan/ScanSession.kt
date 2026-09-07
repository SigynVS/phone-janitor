package com.sigynvs.phonejanitor.scan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Result of the most recent scan of one [ScanType]. */
data class ScanResult(
    val items: List<ScanItem> = emptyList(),
    val scannedAt: Long = 0L,
    val isScanning: Boolean = false,
) {
    val totalBytes: Long get() = items.sumOf { it.sizeBytes }
    val hasRun: Boolean get() = scannedAt > 0L
}

/**
 * In-memory hand-off between the dashboard (runs the scan) and the review screen (reads it).
 * Results are intentionally not persisted — a stale file list must never drive a delete.
 */
class ScanSession {

    private val _results = MutableStateFlow<Map<ScanType, ScanResult>>(emptyMap())
    val results: StateFlow<Map<ScanType, ScanResult>> = _results.asStateFlow()

    fun result(type: ScanType): ScanResult? = _results.value[type]

    fun markScanning(type: ScanType) = _results.update {
        it + (type to (it[type] ?: ScanResult()).copy(isScanning = true))
    }

    fun publish(type: ScanType, items: List<ScanItem>) = _results.update {
        it + (type to ScanResult(items = items, scannedAt = System.currentTimeMillis(), isScanning = false))
    }

    fun clear(type: ScanType) = _results.update { it - type }
}
