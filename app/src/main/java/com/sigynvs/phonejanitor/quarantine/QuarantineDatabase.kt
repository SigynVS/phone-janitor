package com.sigynvs.phonejanitor.quarantine

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [QuarantineEntry::class], version = 1, exportSchema = false)
abstract class QuarantineDatabase : RoomDatabase() {

    abstract fun quarantineDao(): QuarantineDao

    companion object {
        fun build(context: Context): QuarantineDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                QuarantineDatabase::class.java,
                "phone_janitor.db",
            ).build()
    }
}
