package com.example.scanapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DocumentEntity::class, DocumentPageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ScanAppDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao

    companion object {
        @Volatile
        private var instance: ScanAppDatabase? = null

        /**
         * Adds the manualOrder column backing the Home list's drag-to-reorder
         * feature. Existing rows are backfilled with their own id as the
         * initial manual position, so a freshly-migrated library orders the
         * same way it was inserted rather than every row tying at 0 — the
         * person can then drag to customize from there.
         */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN manualOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE documents SET manualOrder = id")
            }
        }

        fun getInstance(context: Context): ScanAppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScanAppDatabase::class.java,
                    "scanapp.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
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
