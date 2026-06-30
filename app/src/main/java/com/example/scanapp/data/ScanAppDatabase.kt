package com.example.scanapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DocumentEntity::class, DocumentPageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ScanAppDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao

    companion object {
        @Volatile
        private var instance: ScanAppDatabase? = null

        fun getInstance(context: Context): ScanAppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScanAppDatabase::class.java,
                    "scanapp.db"
                ).build().also { instance = it }
            }
        }

        /**
         * Closes the live Room connection and clears the cached singleton.
         *
         * Required before/after directly overwriting the underlying "scanapp.db"
         * file on disk (e.g. during a backup restore). Without this, Room keeps
         * using its existing connection and in-memory bookkeeping — including
         * SQLite's per-table autoincrement state — against the *old* file, so a
         * restored DB's rowids can collide with whatever Room still thinks the
         * "next" id is. That collision is what caused restored documents (and
         * subsequently-created new scans) to get merged into a single document
         * group: their primary keys overwrote each other instead of staying
         * distinct. Call this immediately before replacing the file, and again
         * (implicitly, via getInstance) to get a clean connection afterward.
         */
        @Synchronized
        fun closeAndReset() {
            instance?.close()
            instance = null
        }
    }
}
