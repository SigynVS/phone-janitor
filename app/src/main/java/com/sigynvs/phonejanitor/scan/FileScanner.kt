package com.sigynvs.phonejanitor.scan

import android.os.Environment
import com.sigynvs.phonejanitor.quarantine.QuarantineStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Walks shared storage for cleanup candidates. Requires MANAGE_EXTERNAL_STORAGE.
 *
 * Non-negotiable guards, applied to every result:
 *  - nothing modified within [ScanFilters.SAFETY_FLOOR_DAYS]
 *  - nothing under `/Android/` (OS-restricted and app-owned anyway)
 *  - nothing hidden (name starts with `.`), and never our own quarantine folder
 */
class FileScanner {

    suspend fun scan(type: ScanType, filters: ScanFilters): List<ScanItem> =
        withContext(Dispatchers.IO) {
            val scanContext = coroutineContext
            val now = System.currentTimeMillis()
            val safetyFloor = now - days(ScanFilters.SAFETY_FLOOR_DAYS)

            val roots: List<File> = when (type) {
                ScanType.DOWNLOADS_SCREENSHOTS -> downloadsRoots() + screenshotRoots()
                ScanType.LARGE_FILES -> listOfNotNull(externalRoot())
                else -> emptyList()
            }

            val out = ArrayList<ScanItem>()
            for (root in roots.distinctBy { it.absolutePath }) {
                if (!root.isDirectory) continue
                root.walkTopDown()
                    .onEnter { dir -> !isExcludedDir(dir) }
                    .onFail { _, _ -> /* unreadable dir — skip quietly */ }
                    .forEach { file ->
                        scanContext.ensureActive()
                        if (!file.isFile || isExcludedFile(file)) return@forEach
                        if (file.lastModified() > safetyFloor) return@forEach
                        when (type) {
                            ScanType.DOWNLOADS_SCREENSHOTS -> {
                                val cutoff = now - days(
                                    if (isScreenshot(file)) filters.screenshotsOlderThanDays
                                    else filters.downloadsOlderThanDays
                                )
                                if (file.lastModified() <= cutoff) out += ScanItem.of(file, type)
                            }

                            ScanType.LARGE_FILES ->
                                if (file.length() >= filters.largeFileMinBytes) out += ScanItem.of(file, type)

                            else -> Unit
                        }
                    }
            }
            out.sortByDescending { it.sizeBytes }
            out
        }

    // --- roots -------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun externalRoot(): File? =
        Environment.getExternalStorageDirectory()?.takeIf { it.isDirectory }

    @Suppress("DEPRECATION")
    private fun downloadsRoots(): List<File> = listOf(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
    )

    @Suppress("DEPRECATION")
    private fun screenshotRoots(): List<File> = listOf(
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots"),
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Screenshots"),
    )

    // --- guards ----------------------------------------------------------

    private fun isScreenshot(file: File): Boolean =
        file.absolutePath.contains("/Screenshots/", ignoreCase = true)

    private fun isExcludedDir(dir: File): Boolean {
        val name = dir.name
        return name.equals("Android", ignoreCase = true) ||
            name.startsWith(".") ||
            dir.absolutePath.contains("/${QuarantineStore.TRASH_DIR_NAME}/")
    }

    private fun isExcludedFile(file: File): Boolean =
        file.name.startsWith(".") ||
            file.length() == 0L ||
            file.absolutePath.contains("/${QuarantineStore.TRASH_DIR_NAME}/")

    private fun days(n: Int): Long = TimeUnit.DAYS.toMillis(n.toLong())
}
