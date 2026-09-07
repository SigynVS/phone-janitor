package com.sigynvs.phonejanitor.quarantine

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QuarantineDao {

    @Query("SELECT * FROM quarantine ORDER BY quarantinedAt DESC")
    fun observeAll(): Flow<List<QuarantineEntry>>

    @Query("SELECT * FROM quarantine ORDER BY quarantinedAt DESC")
    suspend fun getAll(): List<QuarantineEntry>

    @Query("SELECT * FROM quarantine WHERE quarantinedAt < :cutoff")
    suspend fun olderThan(cutoff: Long): List<QuarantineEntry>

    @Query("SELECT COUNT(*) FROM quarantine")
    fun observeCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM quarantine")
    fun observeTotalBytes(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: QuarantineEntry)

    @Delete
    suspend fun delete(entry: QuarantineEntry)

    @Query("DELETE FROM quarantine")
    suspend fun clear()
}
