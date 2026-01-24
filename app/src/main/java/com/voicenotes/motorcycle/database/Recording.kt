package com.voicenotes.motorcycle.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Status for voice-to-speech processing
 */
enum class V2SStatus {
    NOT_STARTED,    // Not yet processed
    PROCESSING,     // Currently being transcribed
    COMPLETED,      // Successfully transcribed
    FALLBACK,       // Used fallback/partial result
    ERROR,          // Transcription failed
    DISABLED        // Processing disabled
}

/**
 * Status for OSM note creation
 */
enum class OsmStatus {
    NOT_STARTED,    // Not yet created
    PROCESSING,     // Currently creating note
    COMPLETED,      // Successfully created
    ERROR,          // Creation failed
    DISABLED        // OSM integration disabled
}

@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val filename: String,           // Original filename (for display)
    val filepath: String,           // Full path to audio file in internal storage
    val timestamp: Long,            // Recording timestamp (milliseconds since epoch)
    val latitude: Double,           // GPS latitude
    val longitude: Double,          // GPS longitude
    
    val v2sStatus: V2SStatus = V2SStatus.NOT_STARTED,
    val v2sResult: String? = null,  // Transcribed text or null if not transcribed
    val v2sFallback: Boolean = false, // True if result is a fallback/partial
    
    val osmStatus: OsmStatus = OsmStatus.NOT_STARTED,
    val osmResult: String? = null,  // OSM note URL or ID if created
    val osmNoteId: Long? = null,    // OSM note ID if available
    
    val errorMsg: String? = null,   // Last error message if any
    
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
