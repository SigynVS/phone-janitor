package com.sigynvs.phonejanitor.cache

import android.content.Context

/**
 * Milestone 6. Reads per-app cache sizes via StorageStatsManager (requires the "Usage access"
 * toggle). There is no API for a non-root app to clear another app's cache since Android 6 —
 * so the UI will list the biggest offenders and deep-link into each app's settings screen.
 * Stubbed for now.
 */
class CacheInspector(private val context: Context) {

    data class AppCache(
        val packageName: String,
        val label: String,
        val cacheBytes: Long,
    )

    suspend fun inspect(): List<AppCache> = emptyList()
}
