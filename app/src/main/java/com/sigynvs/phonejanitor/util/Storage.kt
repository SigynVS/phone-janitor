package com.sigynvs.phonejanitor.util

import android.os.Environment
import android.os.StatFs

data class StorageInfo(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
    val usedFraction: Float get() = if (totalBytes <= 0) 0f else usedBytes.toFloat() / totalBytes

    companion object {
        val EMPTY = StorageInfo(0, 0)
    }
}

/** Internal-storage totals. No permission required. */
fun readStorageInfo(): StorageInfo = runCatching {
    val stat = StatFs(Environment.getDataDirectory().path)
    StorageInfo(totalBytes = stat.totalBytes, freeBytes = stat.availableBytes)
}.getOrDefault(StorageInfo.EMPTY)
