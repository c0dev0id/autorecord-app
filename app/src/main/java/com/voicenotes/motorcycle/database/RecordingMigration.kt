package com.voicenotes.motorcycle.database

import android.content.Context
import android.os.Environment
import android.util.Log
import com.voicenotes.motorcycle.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility to migrate existing recordings from external storage to internal database
 */
class RecordingMigration(private val context: Context) {
    
    companion object {
        private const val TAG = "RecordingMigration"
        private const val MIGRATION_COMPLETE_KEY = "recording_migration_complete"
    }
    
    /**
     * Check if migration has already been completed
     */
    fun isMigrationComplete(): Boolean {
        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(MIGRATION_COMPLETE_KEY, false)
    }
    
    /**
     * Mark migration as complete
     */
    private fun markMigrationComplete() {
        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(MIGRATION_COMPLETE_KEY, true).apply()
    }
    
    /**
     * Migrate existing recordings from external storage to internal database
     * Returns number of files migrated
     */
    suspend fun migrateExistingRecordings(): Int = withContext(Dispatchers.IO) {
        if (isMigrationComplete()) {
            Log.d(TAG, "Migration already complete, skipping")
            return@withContext 0
        }
        
        DebugLogger.logInfo(
            service = "RecordingMigration",
            message = "Starting migration of existing recordings"
        )
        
        // Try to find old recordings directory
        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val oldSaveDir = prefs.getString("saveDirectory", null)
        
        val possibleDirs = listOf(
            oldSaveDir?.let { File(it) },
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "VoiceNotes"),
            File(Environment.getExternalStorageDirectory(), "VoiceNotes")
        ).filterNotNull()
        
        var totalMigrated = 0
        val db = RecordingDatabase.getDatabase(context)
        val internalDir = File(context.filesDir, "recordings")
        
        if (!internalDir.exists()) {
            internalDir.mkdirs()
        }
        
        for (dir in possibleDirs) {
            if (!dir.exists() || !dir.isDirectory) {
                Log.d(TAG, "Directory does not exist: ${dir.absolutePath}")
                continue
            }
            
            Log.d(TAG, "Scanning directory: ${dir.absolutePath}")
            // Scan for both .ogg (Opus) and .m4a (AAC) audio files
            val audioFiles = dir.listFiles { file -> 
                file.extension == "ogg" || file.extension == "m4a" 
            } ?: emptyArray()
            
            Log.d(TAG, "Found ${audioFiles.size} audio files (.ogg, .m4a) in ${dir.absolutePath}")
            
            for (file in audioFiles) {
                try {
                    // Extract metadata from filename
                    val (lat, lon, timestamp) = extractMetadataFromFilename(file.name)
                    
                    if (lat == null || lon == null || timestamp == null) {
                        Log.w(TAG, "Skipping file with invalid name format: ${file.name}")
                        continue
                    }
                    
                    // Copy file to internal storage
                    val newFile = File(internalDir, file.name)
                    if (newFile.exists()) {
                        Log.d(TAG, "File already exists in internal storage: ${file.name}")
                        continue
                    }
                    
                    file.copyTo(newFile, overwrite = false)
                    
                    // Create database entry
                    val recording = Recording(
                        filename = file.name,
                        filepath = newFile.absolutePath,
                        timestamp = timestamp,
                        latitude = lat,
                        longitude = lon,
                        v2sStatus = V2SStatus.NOT_STARTED
                    )
                    
                    db.recordingDao().insertRecording(recording)
                    totalMigrated++
                    
                    Log.d(TAG, "Migrated: ${file.name}")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to migrate file: ${file.name}", e)
                    DebugLogger.logError(
                        service = "RecordingMigration",
                        error = "Failed to migrate file: ${file.name}",
                        exception = e
                    )
                }
            }
        }
        
        DebugLogger.logInfo(
            service = "RecordingMigration",
            message = "Migration complete. Migrated $totalMigrated files."
        )
        
        markMigrationComplete()
        return@withContext totalMigrated
    }
    
    /**
     * Extract coordinates and timestamp from filename
     * Expected format: {lat},{lng}_{yyyyMMdd_HHmmss}.m4a
     * Returns Triple(lat, lon, timestamp in millis) or Triple(null, null, null) if parsing fails
     */
    private fun extractMetadataFromFilename(filename: String): Triple<Double?, Double?, Long?> {
        try {
            // Remove extension
            val nameWithoutExt = filename.substringBeforeLast(".")
            
            // Split by underscore: [coords, date, time]
            val parts = nameWithoutExt.split("_")
            if (parts.size < 3) {
                return Triple(null, null, null)
            }
            
            // Parse coordinates
            val coords = parts[0].split(",")
            if (coords.size != 2) {
                return Triple(null, null, null)
            }
            
            val lat = coords[0].toDoubleOrNull()
            val lon = coords[1].toDoubleOrNull()
            
            // Parse timestamp
            val dateStr = parts[1]
            val timeStr = parts[2]
            val timestampStr = "${dateStr}_${timeStr}"
            
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val date = dateFormat.parse(timestampStr)
            val timestamp = date?.time
            
            if (lat == null || lon == null || timestamp == null) {
                return Triple(null, null, null)
            }
            
            return Triple(lat, lon, timestamp)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse filename: $filename", e)
            return Triple(null, null, null)
        }
    }
}
