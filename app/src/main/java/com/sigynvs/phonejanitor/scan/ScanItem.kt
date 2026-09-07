package com.sigynvs.phonejanitor.scan

import java.io.File

/** One candidate file found by a scan. Immutable; selection state lives in the review layer. */
data class ScanItem(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val scanType: ScanType,
    val mimeType: String?,
    /** Duplicate scans only: files sharing a non-null [groupId] are byte-identical. */
    val groupId: String? = null,
) {
    val isImage: Boolean get() = mimeType?.startsWith("image/") == true
    val isVideo: Boolean get() = mimeType?.startsWith("video/") == true

    companion object {
        fun of(file: File, scanType: ScanType): ScanItem = ScanItem(
            path = file.absolutePath,
            name = file.name,
            sizeBytes = file.length(),
            lastModified = file.lastModified(),
            scanType = scanType,
            mimeType = MimeTypes.guess(file.name),
        )
    }
}

/** Minimal extension-to-MIME map — enough to decide "show a thumbnail or an icon". */
object MimeTypes {
    fun guess(fileName: String): String? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif" -> "image/$ext"
            "mp4", "mkv", "webm", "3gp", "mov" -> "video/$ext"
            "mp3", "aac", "ogg", "wav", "flac", "m4a" -> "audio/$ext"
            "pdf" -> "application/pdf"
            "zip", "rar", "7z", "tar", "gz" -> "application/archive"
            "apk" -> "application/vnd.android.package-archive"
            "txt", "log", "csv", "json", "xml" -> "text/plain"
            else -> null
        }
    }
}
