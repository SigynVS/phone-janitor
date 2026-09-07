package com.sigynvs.phonejanitor.quarantine

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.concurrent.TimeUnit

/** One file currently sitting in the quarantine folder, with the trail needed to undo the move. */
@Entity(tableName = "quarantine")
data class QuarantineEntry(
    @PrimaryKey val id: String,
    val originalPath: String,
    val trashPath: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val scanType: String,
    val quarantinedAt: Long,
)

/** Quarantined files are deleted automatically this long after they were moved. */
val QUARANTINE_EXPIRY_MS: Long = TimeUnit.DAYS.toMillis(14)

val QuarantineEntry.expiresAt: Long
    get() = quarantinedAt + QUARANTINE_EXPIRY_MS

data class QuarantineSummary(val count: Int, val totalBytes: Long) {
    companion object {
        val EMPTY = QuarantineSummary(0, 0)
    }
}

/** Result of a quarantine, restore-batch, or purge operation. */
data class QuarantineOutcome(
    val moved: Int,
    val freedBytes: Long,
    val failed: Int,
    val dryRun: Boolean,
)
