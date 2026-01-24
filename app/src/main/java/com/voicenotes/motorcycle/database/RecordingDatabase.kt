package com.voicenotes.motorcycle.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Recording::class], version = 1, exportSchema = false)
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
    }
}
