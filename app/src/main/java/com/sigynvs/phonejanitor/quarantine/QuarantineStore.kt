package com.sigynvs.phonejanitor.quarantine

import android.content.Context
import android.os.Environment
import com.sigynvs.phonejanitor.scan.ScanItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Owns the quarantine folder and its Room ledger.
 *
 * Delete is a two-step: [quarantine] moves a file into `/PhoneJanitor/.trash/` and records how to
 * put it back; [purgeExpired] (or an explicit [emptyAll]) is the only path that actually erases bytes.
 */
class QuarantineStore(
    context: Context,
    private val dao: QuarantineDao,
) {
    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    private val trashDir: File =
        File(Environment.getExternalStorageDirectory(), "PhoneJanitor/$TRASH_DIR_NAME")

    fun observeAll(): Flow<List<QuarantineEntry>> = dao.observeAll()

    fun observeSummary(): Flow<QuarantineSummary> =
        combine(dao.observeCount(), dao.observeTotalBytes()) { count, bytes ->
            QuarantineSummary(count, bytes)
        }

    suspend fun quarantine(items: List<ScanItem>, dryRun: Boolean): QuarantineOutcome =
        withContext(Dispatchers.IO) {
            if (dryRun) {
                return@withContext QuarantineOutcome(
                    moved = 0,
                    freedBytes = items.sumOf { it.sizeBytes },
                    failed = 0,
                    dryRun = true,
                )
            }
            ensureTrashDir()
            var moved = 0
            var freed = 0L
            var failed = 0
            for (item in items) {
                val src = File(item.path)
                if (!src.isFile) {
                    failed++
                    continue
                }
                val id = UUID.randomUUID().toString()
                val dest = File(trashDir, "${id}__${src.name.take(120)}")
                val ok = runCatching { moveFile(src, dest) }.getOrDefault(false)
                if (!ok) {
                    failed++
                    continue
                }
                dao.insert(
                    QuarantineEntry(
                        id = id,
                        originalPath = item.path,
                        trashPath = dest.absolutePath,
                        name = src.name,
                        sizeBytes = item.sizeBytes,
                        mimeType = item.mimeType,
                        scanType = item.scanType.name,
                        quarantinedAt = System.currentTimeMillis(),
                    )
                )
                MediaScanned.refresh(appContext, item.path, dest.absolutePath)
                moved++
                freed += item.sizeBytes
            }
            QuarantineOutcome(moved, freed, failed, dryRun = false)
        }

    suspend fun restore(entry: QuarantineEntry): Boolean = withContext(Dispatchers.IO) {
        val src = File(entry.trashPath)
        if (!src.isFile) {
            dao.delete(entry)
            return@withContext false
        }
        var target = File(entry.originalPath)
        target.parentFile?.mkdirs()
        if (target.exists()) target = uniqueSibling(target)
        val ok = runCatching { moveFile(src, target) }.getOrDefault(false)
        if (ok) {
            dao.delete(entry)
            MediaScanned.refresh(appContext, entry.trashPath, target.absolutePath)
        }
        ok
    }

    suspend fun deleteNow(entry: QuarantineEntry) = withContext(Dispatchers.IO) {
        File(entry.trashPath).delete()
        dao.delete(entry)
        MediaScanned.refresh(appContext, entry.trashPath)
    }

    suspend fun emptyAll() = withContext(Dispatchers.IO) {
        val all = dao.getAll()
        all.forEach { File(it.trashPath).delete() }
        dao.clear()
        all.forEach { MediaScanned.refresh(appContext, it.trashPath) }
    }

    suspend fun purgeExpired(now: Long = System.currentTimeMillis()): QuarantineOutcome =
        withContext(Dispatchers.IO) {
            val cutoff = now - QUARANTINE_EXPIRY_MS
            val expired = dao.olderThan(cutoff)
            var count = 0
            var freed = 0L
            for (entry in expired) {
                File(entry.trashPath).delete()
                dao.delete(entry)
                MediaScanned.refresh(appContext, entry.trashPath)
                count++
                freed += entry.sizeBytes
            }
            // Sweep stray files with no ledger row that are themselves past the expiry window.
            if (trashDir.isDirectory) {
                val known = dao.getAll().mapTo(HashSet()) { it.trashPath }
                trashDir.listFiles()?.forEach { f ->
                    if (f.isFile && f.name != ".nomedia" &&
                        f.absolutePath !in known &&
                        now - f.lastModified() > QUARANTINE_EXPIRY_MS
                    ) {
                        f.delete()
                    }
                }
            }
            QuarantineOutcome(moved = count, freedBytes = freed, failed = 0, dryRun = false)
        }

    private fun ensureTrashDir() {
        if (!trashDir.isDirectory) trashDir.mkdirs()
        val noMedia = File(trashDir, ".nomedia")
        if (!noMedia.exists()) runCatching { noMedia.createNewFile() }
    }

    /** Same-volume rename first; fall back to copy+verify+delete. */
    private fun moveFile(src: File, dest: File): Boolean {
        if (src.renameTo(dest)) return true
        src.inputStream().use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return if (dest.length() == src.length()) {
            src.delete()
        } else {
            dest.delete()
            false
        }
    }

    private fun uniqueSibling(file: File): File {
        val base = file.nameWithoutExtension
        val ext = file.extension.let { if (it.isEmpty()) "" else ".$it" }
        var n = 1
        while (true) {
            val candidate = File(file.parentFile, "$base (restored $n)$ext")
            if (!candidate.exists()) return candidate
            n++
        }
    }

    companion object {
        const val TRASH_DIR_NAME = ".trash"
    }
}
