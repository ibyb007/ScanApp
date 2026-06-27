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
    }
}
