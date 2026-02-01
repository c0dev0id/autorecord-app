package com.voicenotes.main

import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.voicenotes.main.database.RecordingDatabase
import com.voicenotes.main.database.Recording
import com.voicenotes.main.database.V2SStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BatchProcessingService : LifecycleService() {
    
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val recordingId = intent?.getLongExtra("recordingId", -1L) ?: -1L
        val transcribeOnly = intent?.getBooleanExtra("transcribeOnly", false) ?: false

        if (recordingId <= 0) {
            Log.e("BatchProcessing", "Invalid recordingId: $recordingId")
            stopSelf()
            return START_NOT_STICKY
        }

        lifecycleScope.launch {
            processSingleRecording(recordingId, transcribeOnly)
        }

        return START_NOT_STICKY
    }
    
    private suspend fun processSingleRecording(recordingId: Long, transcribeOnly: Boolean = false) {
        val db = RecordingDatabase.getDatabase(this@BatchProcessingService)
        val recording = withContext(Dispatchers.IO) {
            db.recordingDao().getRecordingById(recordingId)
        }

        if (recording == null) {
            Log.e("BatchProcessing", "Recording not found: $recordingId")
            stopSelf()
            return
        }

        val operation = when {
            transcribeOnly -> "transcription"
            else -> "full processing"
        }

        DebugLogger.logInfo(
            service = "BatchProcessingService",
            message = "Processing single recording ($operation): ${recording.filename}"
        )

        processRecording(recording, 1, 1, transcribeOnly)
        stopSelf()
    }
    
    private suspend fun processRecording(
        recording: Recording,
        currentFile: Int,
        totalFiles: Int,
        transcribeOnly: Boolean = false
    ) {
            val db = RecordingDatabase.getDatabase(this@BatchProcessingService)

            // Update status to PROCESSING (for transcription)
            withContext(Dispatchers.IO) {
                val updated = recording.copy(
                    v2sStatus = V2SStatus.PROCESSING,
                    updatedAt = System.currentTimeMillis()
                )
                db.recordingDao().updateRecording(updated)
            }

            // Broadcast progress with detailed status
            broadcastProgress(recording.filename, "transcribing", currentFile, totalFiles)
            
            // Transcribe file
            val transcriptionService = TranscriptionService(this)
            val result = transcriptionService.transcribeAudioFile(recording.filepath)
            
            // Check for failure first
            if (result.isFailure) {
                val ex = result.exceptionOrNull()
                DebugLogger.logError(
                    service = "BatchProcessingService",
                    error = "Transcription failed for ${recording.filename}: ${ex?.message}",
                    exception = ex
                )

                withContext(Dispatchers.IO) {
                    val updated = recording.copy(
                        v2sStatus = V2SStatus.ERROR,
                        v2sResult = null,
                        v2sFallback = false,
                        errorMsg = ex?.message ?: "Transcription failed",
                        updatedAt = System.currentTimeMillis()
                    )
                    db.recordingDao().updateRecording(updated)
                }

                broadcastProgress(recording.filename, "error", currentFile, totalFiles)
                return
            }
            
            result.onSuccess { transcribedText ->
                DebugLogger.logInfo(
                    service = "BatchProcessingService",
                    message = "Transcription successful for ${recording.filename}: $transcribedText"
                )
                
                // Format coordinates once (6 decimal places)
                val latStr = String.format("%.6f", recording.latitude)
                val lngStr = String.format("%.6f", recording.longitude)
                val coords = "$latStr,$lngStr"
                
                // Compute final text: use fallback placeholder if transcription is blank
                val finalText = if (transcribedText.isBlank()) {
                    "$coords (no text)"
                } else {
                    transcribedText
                }
                
                // Update recording with transcription result
                withContext(Dispatchers.IO) {
                    val updated = recording.copy(
                        v2sStatus = if (transcribedText.isBlank()) V2SStatus.FALLBACK else V2SStatus.COMPLETED,
                        v2sResult = finalText,
                        v2sFallback = transcribedText.isBlank(),
                        errorMsg = null,
                        updatedAt = System.currentTimeMillis()
                    )
                    db.recordingDao().updateRecording(updated)
                }
                
                // Create/update GPX and CSV (legacy support)
                try {
                    broadcastProgress(recording.filename, "creating GPX", currentFile, totalFiles)
                    
                    DebugLogger.logInfo(
                        service = "BatchProcessingService",
                        message = "Creating GPX waypoint for ${recording.filename} at $coords"
                    )
                    createGpxWaypointFromRecording(recording, finalText, coords)
                    
                    DebugLogger.logInfo(
                        service = "BatchProcessingService",
                        message = "Creating CSV entry for ${recording.filename} at $coords"
                    )
                    createCsvEntryFromRecording(recording, finalText, coords)
                } catch (e: Exception) {
                    Log.e("BatchProcessing", "Failed to create GPX/CSV", e)
                    DebugLogger.logError(
                        service = "BatchProcessingService",
                        error = "Failed to create GPX/CSV for ${recording.filename}",
                        exception = e
                    )
                }
                
                // Send completion status for this file
                broadcastProgress(recording.filename, "complete", currentFile, totalFiles)
                
            }
    }

    /**
     * Helper function to broadcast batch processing progress
     */
    private fun broadcastProgress(filename: String, status: String, current: Int, total: Int) {
        val progressIntent = Intent("com.voicenotes.main.BATCH_PROGRESS")
        progressIntent.putExtra("filename", filename)
        progressIntent.putExtra("status", status)
        progressIntent.putExtra("current", current)
        progressIntent.putExtra("total", total)
        sendBroadcast(progressIntent)
    }
    
    private fun parseCoordinates(coords: String): Pair<Double, Double> {
        val parts = coords.split(",")
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid coordinate format: expected 'latitude,longitude', got '$coords'")
        }
        try {
            val lat = parts[0].trim().toDouble()
            val lng = parts[1].trim().toDouble()
            
            // Validate coordinate ranges
            if (lat < -90.0 || lat > 90.0) {
                throw IllegalArgumentException("Invalid latitude: $lat (must be between -90 and 90)")
            }
            if (lng < -180.0 || lng > 180.0) {
                throw IllegalArgumentException("Invalid longitude: $lng (must be between -180 and 180)")
            }
            
            return Pair(lat, lng)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid coordinate format: unable to parse '$coords' as numbers", e)
        }
    }
    
    private fun createGpxWaypointFromRecording(recording: Recording, text: String, coords: String) {
        try {
            val (lat, lng) = parseCoordinates(coords)
            
            val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val saveDir = prefs.getString("saveDirectory", null) ?: return
            
            val gpxFile = File(saveDir, "voicenote_waypoint_collection.gpx")
            
            val latStr = String.format("%.6f", lat)
            val lngStr = String.format("%.6f", lng)
            val waypointName = "VoiceNote: $latStr,$lngStr"
            val waypointDesc = text
            
            if (gpxFile.exists()) {
                // Parse existing GPX and check for duplicates
                val existingContent = gpxFile.readText()
                val updatedContent = replaceOrAddWaypoint(existingContent, latStr, lngStr, waypointName, waypointDesc)
                gpxFile.writeText(updatedContent)
            } else {
                // Create new GPX file
                val gpxContent = createNewGpxFile(latStr, lngStr, waypointName, waypointDesc)
                gpxFile.writeText(gpxContent)
            }
            
            Log.d("BatchProcessing", "GPX waypoint created/updated: $waypointName")
            
        } catch (e: Exception) {
            Log.e("BatchProcessing", "Failed to create GPX waypoint", e)
        }
    }
    
    private fun replaceOrAddWaypoint(
        gpxContent: String,
        lat: String,
        lng: String,
        name: String,
        desc: String
    ): String {
        // Find existing waypoint with same coordinates (6 decimal precision)
        val waypointPattern = """<wpt lat="$lat" lon="$lng">.*?</wpt>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        
        val newWaypoint = """  <wpt lat="$lat" lon="$lng">
    <time>${getCurrentTimestamp()}</time>
    <name>$name</name>
    <desc>$desc</desc>
  </wpt>"""
        
        return if (waypointPattern.containsMatchIn(gpxContent)) {
            // Replace existing waypoint
            Log.d("BatchProcessing", "Replacing existing waypoint at $lat,$lng")
            gpxContent.replace(waypointPattern, newWaypoint)
        } else {
            // Add new waypoint before closing </gpx>
            Log.d("BatchProcessing", "Adding new waypoint at $lat,$lng")
            gpxContent.replace("</gpx>", "$newWaypoint\n</gpx>")
        }
    }
    
    private fun createNewGpxFile(lat: String, lng: String, name: String, desc: String): String {
        val timestamp = getCurrentTimestamp()
        return """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Motorcycle Voice Notes"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xmlns="http://www.topografix.com/GPX/1/1"
  xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">
  <metadata>
    <name>Voice Notes Locations</name>
    <desc>GPS locations of voice note recordings</desc>
    <time>$timestamp</time>
  </metadata>
  <wpt lat="$lat" lon="$lng">
    <time>$timestamp</time>
    <name>$name</name>
    <desc>$desc</desc>
  </wpt>
</gpx>"""
    }
    
    private fun getCurrentTimestamp(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())
    }
    
    private fun createCsvEntryFromRecording(recording: Recording, text: String, coords: String) {
        try {
            val (lat, lng) = parseCoordinates(coords)
            
            val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val saveDir = prefs.getString("saveDirectory", null) ?: return
            
            val csvFile = File(saveDir, "voicenote_waypoint_collection.csv")
            
            val latStr = String.format("%.6f", lat)
            val lngStr = String.format("%.6f", lng)
            val coordsStr = "$latStr,$lngStr"
            
            // Parse date and time from current timestamp
            val now = java.util.Date()
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            val date = dateFormat.format(now)
            val time = timeFormat.format(now)
            
            // Create map links
            val googleMapsLink = "https://www.google.com/maps?q=$latStr,$lngStr"
            val osmLink = "https://www.openstreetmap.org/?mlat=$latStr&mlon=$lngStr&zoom=17"
            
            if (csvFile.exists()) {
                // Read existing content and check for duplicates
                val existingLines = csvFile.readLines()
                val updatedContent = replaceOrAddCsvEntry(existingLines, date, time, coordsStr, text, googleMapsLink, osmLink)
                csvFile.writeText(updatedContent)
            } else {
                // Create new CSV file with UTF-8 BOM and header
                val csvContent = buildNewCsvFile(date, time, coordsStr, text, googleMapsLink, osmLink)
                csvFile.writeText(csvContent)
            }
            
            Log.d("BatchProcessing", "CSV entry created/updated: $coordsStr")
            
        } catch (e: Exception) {
            Log.e("BatchProcessing", "Failed to create CSV entry", e)
        }
    }
    
    private fun replaceOrAddCsvEntry(
        existingLines: List<String>,
        date: String,
        time: String,
        coords: String,
        text: String,
        googleMapsLink: String,
        osmLink: String
    ): String {
        val utf8Bom = "\uFEFF"
        val newEntry = buildCsvLine(date, time, coords, text, googleMapsLink, osmLink)
        
        // Check if we have a header (first line after BOM)
        if (existingLines.isEmpty()) {
            return utf8Bom + buildCsvHeader() + "\n" + newEntry
        }
        
        val header = if (existingLines[0].startsWith(utf8Bom)) {
            existingLines[0]
        } else {
            utf8Bom + existingLines[0]
        }
        
        // Find if there's already an entry with the same coordinates
        var found = false
        val updatedLines = mutableListOf<String>()
        updatedLines.add(header)
        
        for (i in 1 until existingLines.size) {
            val line = existingLines[i]
            if (line.isBlank()) continue
            
            // Check if this line has the same coordinates by parsing CSV fields
            val lineCoords = extractCoordsFromCsvLine(line)
            if (lineCoords == coords && !found) {
                updatedLines.add(newEntry)
                found = true
                Log.d("BatchProcessing", "Replacing existing CSV entry at $coords")
            } else {
                updatedLines.add(line)
            }
        }
        
        // If not found, add new entry
        if (!found) {
            updatedLines.add(newEntry)
            Log.d("BatchProcessing", "Adding new CSV entry at $coords")
        }
        
        return updatedLines.joinToString("\n")
    }
    
    private fun buildNewCsvFile(
        date: String,
        time: String,
        coords: String,
        text: String,
        googleMapsLink: String,
        osmLink: String
    ): String {
        val utf8Bom = "\uFEFF"
        val header = buildCsvHeader()
        val entry = buildCsvLine(date, time, coords, text, googleMapsLink, osmLink)
        return utf8Bom + header + "\n" + entry
    }
    
    private fun buildCsvHeader(): String {
        return "Date,Time,Coordinates,Text,Google Maps link,OSM link"
    }
    
    private fun buildCsvLine(
        date: String,
        time: String,
        coords: String,
        text: String,
        googleMapsLink: String,
        osmLink: String
    ): String {
        return "${escapeCsv(date)},${escapeCsv(time)},${escapeCsv(coords)},${escapeCsv(text)},${escapeCsv(googleMapsLink)},${escapeCsv(osmLink)}"
    }
    
    private fun escapeCsv(value: String): String {
        // Escape CSV values: wrap in quotes if contains comma, quote, or newline
        return if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
    
    private fun extractCoordsFromCsvLine(line: String): String? {
        try {
            // Parse CSV line to extract the third field (Coordinates)
            val fields = mutableListOf<String>()
            var currentField = StringBuilder()
            var insideQuotes = false
            
            for (char in line) {
                when {
                    char == '"' -> insideQuotes = !insideQuotes
                    char == ',' && !insideQuotes -> {
                        fields.add(currentField.toString())
                        currentField = StringBuilder()
                    }
                    else -> currentField.append(char)
                }
            }
            fields.add(currentField.toString())
            
            // The third field (index 2) is the coordinates
            return if (fields.size > 2) fields[2] else null
        } catch (e: Exception) {
            return null
        }
    }
}
