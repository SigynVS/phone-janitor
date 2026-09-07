package com.sigynvs.phonejanitor.scan

import android.os.Environment
import com.sigynvs.phonejanitor.quarantine.QuarantineStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Finds byte-identical files under shared storage.
 *
 * Pipeline (each stage discards groups that fall to one member):
 *   1. group by exact byte length
 *   2. partial hash — MD5 of the first + last 64 KB
 *   3. full hash — SHA-256 of the whole file
 *
 * A whole group is skipped if *any* member was modified within [ScanFilters.SAFETY_FLOOR_DAYS]
 * days (the set is in active use). Returned items carry [ScanItem.groupId] = the full hash and are
 * ordered so group members are contiguous, oldest first (the natural "keeper").
 */
class DuplicateScanner {

    suspend fun scan(@Suppress("UNUSED_PARAMETER") filters: ScanFilters): List<ScanItem> =
        withContext(Dispatchers.IO) {
            val scanContext = coroutineContext
            val now = System.currentTimeMillis()
            val safetyFloor = now - TimeUnit.DAYS.toMillis(ScanFilters.SAFETY_FLOOR_DAYS.toLong())

            @Suppress("DEPRECATION")
            val root = Environment.getExternalStorageDirectory()?.takeIf { it.isDirectory }
                ?: return@withContext emptyList()

            // 1. bucket every eligible file by exact size
            val bySize = HashMap<Long, MutableList<File>>()
            root.walkTopDown()
                .onEnter { !isExcludedDir(it) }
                .onFail { _, _ -> }
                .forEach { file ->
                    scanContext.ensureActive()
                    if (!file.isFile || isExcludedFile(file)) return@forEach
                    val len = file.length()
                    if (len < MIN_BYTES) return@forEach
                    bySize.getOrPut(len) { ArrayList() }.add(file)
                }

            // 2. partial hash within each non-trivial size bucket
            val byPartial = HashMap<String, MutableList<File>>()
            for (bucket in bySize.values) {
                if (bucket.size < 2) continue
                scanContext.ensureActive()
                for (file in bucket) {
                    val ph = runCatching { partialHash(file) }.getOrNull() ?: continue
                    byPartial.getOrPut("${file.length()}:$ph") { ArrayList() }.add(file)
                }
            }

            // 3. full hash within each surviving partial bucket
            val byFull = HashMap<String, MutableList<File>>()
            for (bucket in byPartial.values) {
                if (bucket.size < 2) continue
                scanContext.ensureActive()
                for (file in bucket) {
                    val fh = runCatching { fullHash(file) }.getOrNull() ?: continue
                    byFull.getOrPut(fh) { ArrayList() }.add(file)
                }
            }

            // 4. emit confirmed groups
            val out = ArrayList<ScanItem>()
            for ((hash, group) in byFull) {
                if (group.size < 2) continue
                if (group.any { it.lastModified() > safetyFloor }) continue
                group.sortBy { it.lastModified() }
                for (file in group) {
                    out += ScanItem.of(file, ScanType.DUPLICATES).copy(groupId = hash)
                }
            }

            // biggest wasted space first; members of a group stay contiguous & oldest-first
            out.sortedWith(
                compareByDescending<ScanItem> { it.sizeBytes }
                    .thenBy { it.groupId }
                    .thenBy { it.lastModified },
            )
        }

    private fun partialHash(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        val buf = ByteArray(CHUNK)
        RandomAccessFile(file, "r").use { raf ->
            val head = raf.read(buf)
            if (head > 0) md.update(buf, 0, head)
            val len = raf.length()
            if (len > CHUNK) {
                raf.seek(len - CHUNK)
                val tail = raf.read(buf)
                if (tail > 0) md.update(buf, 0, tail)
            }
        }
        return md.digest().toHex()
    }

    private fun fullHash(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(128 * 1024)
        file.inputStream().use { input ->
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xFF) }

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

    private companion object {
        const val CHUNK = 64 * 1024
        const val MIN_BYTES = 1L * 1024 * 1024 // ignore duplicates below 1 MB
    }
}
