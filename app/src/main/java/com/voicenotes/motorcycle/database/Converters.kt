package com.voicenotes.motorcycle.database

import androidx.room.TypeConverter

/**
 * Type converters for Room database to handle enum types
 */
class Converters {
    
    @TypeConverter
    fun fromV2SStatus(value: V2SStatus): String {
        return value.name
    }
    
    @TypeConverter
    fun toV2SStatus(value: String): V2SStatus {
        return try {
            V2SStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            V2SStatus.NOT_STARTED
        }
    }
}
