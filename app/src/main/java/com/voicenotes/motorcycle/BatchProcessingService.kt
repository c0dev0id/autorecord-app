package com.voicenotes.motorcycle

import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.io.File

class BatchProcessingService : LifecycleService() {
    
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        lifecycleScope.launch {
            processAllFiles()
        }
        
        return START_NOT_STICKY
    }
    
    private suspend fun processAllFiles() {
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val saveDir = prefs.getString("saveDirectory", null)
        
        DebugLogger.logInfo(
            service = "BatchProcessingService",
            message = "Starting batch processing"
        )
        
        if (saveDir.isNullOrEmpty()) {
            Log.e("BatchProcessing", "Save directory not configured")
            DebugLogger.logError(
                service = "BatchProcessingService",
                error = "Save directory not configured"
            )
            sendBroadcast(Intent("com.voicenotes.motorcycle.BATCH_COMPLETE"))
            stopSelf()
            return
        }
        
        val directory = File(saveDir)
        // Process .ogg (Opus) and .amr (AMR_WB) audio files
        val audioFiles = directory.listFiles { file -> 
            file.extension == "ogg" || file.extension == "amr" 
        } ?: emptyArray()
        
        Log.d("BatchProcessing", "Found ${audioFiles.size} files to process")
        DebugLogger.logInfo(
            service = "BatchProcessingService",
            message = "Found ${audioFiles.size} audio files (.ogg, .m4a) to process in $saveDir"
        )
        
        val transcriptionService = TranscriptionService(this)
        val osmService = OsmNotesService()
        val oauthManager = OsmOAuthManager(this)
        val addOsmNote = prefs.getBoolean("addOsmNote", false)
        
        val totalFiles = audioFiles.size
        
        for ((index, file) in audioFiles.withIndex()) {
            val currentFile = index + 1
            Log.d("BatchProcessing", "Processing file $currentFile/$totalFiles: ${file.name}")
            DebugLogger.logInfo(
                service = "BatchProcessingService",
                message = "Processing file $currentFile/$totalFiles: ${file.name}"
            )
            
            try {
                withTimeout(120000) { // 2 minute timeout per file
                    processFile(file, currentFile, totalFiles, saveDir, transcriptionService, oauthManager, addOsmNote)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("BatchProcessingService", "Timeout processing file: ${file.name}")
                DebugLogger.logError(
                    service = "BatchProcessingService",
                    error = "File processing timeout: ${file.name}",
                    exception = e
                )
                
                // Send error status
                val errorProgressIntent = Intent("com.voicenotes.motorcycle.BATCH_PROGRESS")
                errorProgressIntent.putExtra("filename", file.name)
                errorProgressIntent.putExtra("status", "timeout")
                errorProgressIntent.putExtra("current", currentFile)
                errorProgressIntent.putExtra("total", totalFiles)
                sendBroadcast(errorProgressIntent)
                
                // Continue to next file
            } catch (e: Exception) {
                Log.e("BatchProcessingService", "Error processing file: ${file.name}", e)
                DebugLogger.logError(
                    service = "BatchProcessingService",
                    error = "Error processing file: ${file.name}",
                    exception = e
                )
                
                // Send error status
                val errorProgressIntent = Intent("com.voicenotes.motorcycle.BATCH_PROGRESS")
                errorProgressIntent.putExtra("filename", file.name)
                errorProgressIntent.putExtra("status", "error")
                errorProgressIntent.putExtra("current", currentFile)
                errorProgressIntent.putExtra("total", totalFiles)
                sendBroadcast(errorProgressIntent)
                
                // Continue to next file
            }
        }
        
        // Broadcast completion
        DebugLogger.logInfo(
            service = "BatchProcessingService",
            message = "Batch processing complete. Processed $totalFiles files."
        )
        sendBroadcast(Intent("com.voicenotes.motorcycle.BATCH_COMPLETE"))
        stopSelf()
    }
    
    private suspend fun processFile(
        file: File,
        currentFile: Int,
        totalFiles: Int,
        saveDir: String,
        transcriptionService: TranscriptionService,
        oauthManager: OsmOAuthManager,
        addOsmNote: Boolean
    ) {
            // Broadcast progress with detailed status
            val progressIntent = Intent("com.voicenotes.motorcycle.BATCH_PROGRESS")
            progressIntent.putExtra("filename", file.name)
            progressIntent.putExtra("status", "transcribing")
            progressIntent.putExtra("current", currentFile)
            progressIntent.putExtra("total", totalFiles)
            sendBroadcast(progressIntent)
            
            // Transcribe file
            val result = transcriptionService.transcribeAudioFile(file.absolutePath)
            
            result.onSuccess { transcribedText ->
                DebugLogger.logInfo(
                    service = "BatchProcessingService",
                    message = "Transcription successful for ${file.name}: $transcribedText"
                )
                
                val coords = extractCoordinatesFromFilename(file.name)
                val finalText = if (transcribedText.isBlank()) "$coords (no text)" else transcribedText
                
                // Update progress status
                val gpxProgressIntent = Intent("com.voicenotes.motorcycle.BATCH_PROGRESS")
                gpxProgressIntent.putExtra("filename", file.name)
                gpxProgressIntent.putExtra("status", "creating GPX")
                gpxProgressIntent.putExtra("current", currentFile)
                gpxProgressIntent.putExtra("total", totalFiles)
                sendBroadcast(gpxProgressIntent)
                
                // Create/update GPX waypoint
                DebugLogger.logInfo(
                    service = "BatchProcessingService",
                    message = "Creating GPX waypoint for ${file.name} at $coords"
                )
                createGpxWaypointFromFile(file, finalText, coords)
                
                // Create/update CSV file
                DebugLogger.logInfo(
                    service = "BatchProcessingService",
                    message = "Creating CSV entry for ${file.name} at $coords"
                )
                createCsvEntryFromFile(file, finalText, coords)
                
                // Create OSM note if enabled
                if (addOsmNote && oauthManager.isAuthenticated()) {
                    val osmProgressIntent = Intent("com.voicenotes.motorcycle.BATCH_PROGRESS")
                    osmProgressIntent.putExtra("filename", file.name)
                    osmProgressIntent.putExtra("status", "creating OSM note")
                    osmProgressIntent.putExtra("current", currentFile)
                    osmProgressIntent.putExtra("total", totalFiles)
                    sendBroadcast(osmProgressIntent)
                    
                    val (lat, lng) = parseCoordinates(coords)
                    val accessToken = oauthManager.getAccessToken()!!
                    DebugLogger.logInfo(
                        service = "BatchProcessingService",
                        message = "Creating OSM note for ${file.name} at $lat,$lng"
                    )
                    val osmService = OsmNotesService()
                    osmService.createNote(lat, lng, finalText, accessToken)
                }
                
                // Send completion status for this file
                val doneProgressIntent = Intent("com.voicenotes.motorcycle.BATCH_PROGRESS")
                doneProgressIntent.putExtra("filename", file.name)
                doneProgressIntent.putExtra("status", "complete")
                doneProgressIntent.putExtra("current", currentFile)
                doneProgressIntent.putExtra("total", totalFiles)
                sendBroadcast(doneProgressIntent)
                
            }.onFailure { error ->
                Log.e("BatchProcessing", "Failed to process ${file.name}", error)
                DebugLogger.logError(
                    service = "BatchProcessingService",
                    error = "Failed to process ${file.name}",
                    exception = error
                )
                
                // Send error status
                val errorProgressIntent = Intent("com.voicenotes.motorcycle.BATCH_PROGRESS")
                errorProgressIntent.putExtra("filename", file.name)
                errorProgressIntent.putExtra("status", "error")
                errorProgressIntent.putExtra("current", currentFile)
                errorProgressIntent.putExtra("total", totalFiles)
                sendBroadcast(errorProgressIntent)
            }
    }
    
    private fun extractCoordinatesFromFilename(filename: String): String {
        return filename.substringBefore("_")
    }
    
    private fun parseCoordinates(coords: String): Pair<Double, Double> {
        val parts = coords.split(",")
        return Pair(parts[0].toDouble(), parts[1].toDouble())
    }
    
    private fun createGpxWaypointFromFile(file: File, text: String, coords: String) {
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
    
    private fun createCsvEntryFromFile(file: File, text: String, coords: String) {
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
