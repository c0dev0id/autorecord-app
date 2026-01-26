package com.voicenotes.motorcycle.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Recording::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class RecordingDatabase : RoomDatabase() {
    
    abstract fun recordingDao(): RecordingDao
    
    companion object {
        @Volatile
        private var INSTANCE: RecordingDatabase? = null

        fun getDatabase(context: Context): RecordingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RecordingDatabase::class.java,
                    "recording_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Creates an in-memory database for testing.
         * This database will not persist data and will be cleared when the process is killed.
         * It allows main thread queries for testing convenience.
         */
        fun getTestDatabase(context: Context): RecordingDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                RecordingDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}
