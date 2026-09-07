package com.sigynvs.phonejanitor.scan

/**
 * User-tunable scan thresholds (persisted via SettingsRepository).
 * A hard 7-day "recently modified" floor is enforced in [FileScanner] and is NOT configurable.
 */
data class ScanFilters(
    val downloadsOlderThanDays: Int = DEFAULT_DOWNLOADS_DAYS,
    val screenshotsOlderThanDays: Int = DEFAULT_SCREENSHOTS_DAYS,
    val largeFileMinMb: Int = DEFAULT_LARGE_MB,
    val dryRun: Boolean = false,
) {
    val largeFileMinBytes: Long get() = largeFileMinMb.toLong() * 1024L * 1024L

    companion object {
        const val DEFAULT_DOWNLOADS_DAYS = 90
        const val DEFAULT_SCREENSHOTS_DAYS = 60
        const val DEFAULT_LARGE_MB = 100

        /** Files touched within this window are never listed, regardless of settings. */
        const val SAFETY_FLOOR_DAYS = 7

        val DEFAULT = ScanFilters()
    }
}
