package com.voicenotes.motorcycle

import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
        
        if (saveDir.isNullOrEmpty()) {
            Log.e("BatchProcessing", "Save directory not configured")
            sendBroadcast(Intent("com.voicenotes.motorcycle.BATCH_COMPLETE"))
            stopSelf()
            return
        }
        
        val directory = File(saveDir)
        val m4aFiles = directory.listFiles { file -> file.extension == "m4a" } ?: emptyArray()
        
        Log.d("BatchProcessing", "Found ${m4aFiles.size} files to process")
        
        val transcriptionService = TranscriptionService(this)
        val osmService = OsmNotesService()
        val oauthManager = OsmOAuthManager(this)
        val addOsmNote = prefs.getBoolean("addOsmNote", false)
        
        for ((index, file) in m4aFiles.withIndex()) {
            Log.d("BatchProcessing", "Processing file ${index + 1}/${m4aFiles.size}: ${file.name}")
            
            // Broadcast progress
            val progressIntent = Intent("com.voicenotes.motorcycle.BATCH_PROGRESS")
            progressIntent.putExtra("filename", file.name)
            sendBroadcast(progressIntent)
            
            // Transcribe file
            val result = transcriptionService.transcribeAudioFile(file.absolutePath)
            
            result.onSuccess { transcribedText ->
                val coords = extractCoordinatesFromFilename(file.name)
                val finalText = if (transcribedText.isBlank()) "$coords (no text)" else transcribedText
                
                // Create/update GPX waypoint
                createGpxWaypointFromFile(file, finalText, coords)
                
                // Create OSM note if enabled
                if (addOsmNote && oauthManager.isAuthenticated()) {
                    val (lat, lng) = parseCoordinates(coords)
                    val accessToken = oauthManager.getAccessToken()!!
                    osmService.createNote(lat, lng, finalText, accessToken)
                }
            }.onFailure { error ->
                Log.e("BatchProcessing", "Failed to process ${file.name}", error)
            }
        }
        
        // Broadcast completion
        sendBroadcast(Intent("com.voicenotes.motorcycle.BATCH_COMPLETE"))
        stopSelf()
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
}
