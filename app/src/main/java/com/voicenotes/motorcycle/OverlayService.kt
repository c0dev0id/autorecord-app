package com.voicenotes.motorcycle

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.location.Location
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class OverlayService : LifecycleService(), TextToSpeech.OnInitListener {

    companion object {
        private const val PREF_TRY_ONLINE_PROCESSING = "tryOnlineProcessingDuringRide"
        private const val PREF_ADD_OSM_NOTE = "addOsmNote"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var bubbleLine1: TextView? = null
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var textToSpeech: TextToSpeech? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentLocation: Location? = null
    private var recordingFilePath: String? = null
    private var isTtsInitialized = false
    
    private val handler = Handler(Looper.getMainLooper())
    private var recordingDuration = 10
    private var remainingSeconds = 0
    private var countdownRunnable: Runnable? = null
    private var ttsTimeoutRunnable: Runnable? = null
    private var bluetoothScoTimeoutRunnable: Runnable? = null
    
    // Flags to prevent double cleanup
    private var isOverlayRemoved = false
    private var isServiceStopping = false
    
    // Exception handler for coroutines
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("OverlayService", "Coroutine exception caught", throwable)
        DebugLogger.logError(
            service = "OverlayService",
            error = "Coroutine exception: ${throwable.message}",
            exception = throwable
        )
    }

    override fun onCreate() {
        super.onCreate()
        
        // Check if overlay permission is granted
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        textToSpeech = TextToSpeech(this, this)
        
        // Add TTS timeout
        ttsTimeoutRunnable = Runnable {
            if (!isTtsInitialized) {
                Log.w("OverlayService", "TTS initialization timeout - proceeding without TTS")
                DebugLogger.logError(
                    service = "OverlayService",
                    error = "TTS initialization timeout after 10 seconds - proceeding without TTS"
                )
                startRecordingProcess()
            }
        }
        handler.postDelayed(ttsTimeoutRunnable!!, 10000)
        
        createOverlay()
    }

    private fun createOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100 // Position from top
        }
        
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)
        bubbleLine1 = overlayView?.findViewById(R.id.bubbleLine1)
        
        windowManager?.addView(overlayView, params)
        
        updateOverlay(getString(R.string.acquiring_location))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val additionalDuration = intent?.getIntExtra("additionalDuration", -1) ?: -1
        
        if (additionalDuration > 0) {
            // This is an extension request - use the duration from the intent
            extendRecordingDuration(additionalDuration)
            return START_NOT_STICKY
        }
        
        // Normal startup flow - wait for TTS initialization
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        ttsTimeoutRunnable?.let { handler.removeCallbacks(it) }
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            isTtsInitialized = true
        } else {
            isTtsInitialized = false
            Log.w("OverlayService", "TTS initialization failed")
            DebugLogger.logError(
                service = "OverlayService",
                error = "TTS initialization failed with status: $status"
            )
        }
        
        // Start the recording process after TTS initialization (or timeout)
        handler.post {
            startRecordingProcess()
        }
    }

    private fun startRecordingProcess() {
        acquireLocation()
    }

    private fun acquireLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            updateOverlay("Location permission not granted")
            handler.postDelayed({ stopSelfAndFinish() }, 2000)
            return
        }

        val cancellationTokenSource = CancellationTokenSource()
        
        // Add 30-second timeout
        val locationTimeoutRunnable = Runnable {
            cancellationTokenSource.cancel()
            Log.w("OverlayService", "GPS location acquisition timeout after 30 seconds - trying last known location")
            DebugLogger.logError(
                service = "OverlayService",
                error = "GPS location acquisition timeout after 30 seconds - falling back to last known location"
            )
            // Try last known location as fallback
            tryLastKnownLocation()
        }
        handler.postDelayed(locationTimeoutRunnable, 30000)

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location: Location? ->
            handler.removeCallbacks(locationTimeoutRunnable)
            if (location != null) {
                currentLocation = location
                onLocationAcquired()
            } else {
                tryLastKnownLocation()
            }
        }.addOnFailureListener { exception ->
            handler.removeCallbacks(locationTimeoutRunnable)
            Log.e("OverlayService", "GPS location acquisition failed", exception)
            DebugLogger.logError(
                service = "OverlayService",
                error = "GPS location acquisition failed - trying last known location",
                exception = exception
            )
            tryLastKnownLocation()
        }
    }

    private fun tryLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            onLocationFailed()
            return
        }
        
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                currentLocation = location
                updateOverlay("Using last known location")
                DebugLogger.logInfo(
                    service = "OverlayService",
                    message = "Using last known location: ${location.latitude}, ${location.longitude}"
                )
                handler.postDelayed({
                    onLocationAcquired()
                }, 1000)
            } else {
                updateOverlay("Location unavailable - please ensure GPS is enabled")
                Log.e("OverlayService", "No location available - last known location is null")
                DebugLogger.logError(
                    service = "OverlayService",
                    error = "Location unavailable - both current and last known location failed. GPS may be disabled."
                )
                handler.postDelayed({ stopSelfAndFinish() }, 3000)
            }
        }.addOnFailureListener { exception ->
            Log.e("OverlayService", "Failed to get last known location", exception)
            DebugLogger.logError(
                service = "OverlayService",
                error = "Failed to get last known location",
                exception = exception
            )
            onLocationFailed()
        }
    }

    private fun onLocationAcquired() {
        currentLocation?.let { location ->
            val coords = "${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}"
            updateOverlay(getString(R.string.location_acquired_coords, coords))
        }
        
        // Speak both announcements BEFORE recording starts
        speakText(getString(R.string.location_acquired)) {
            speakText(getString(R.string.recording_started)) {
                startRecording()
            }
        }
    }

    private fun onLocationFailed() {
        updateOverlay(getString(R.string.location_failed))
        handler.postDelayed({ stopSelfAndFinish() }, 1000)
    }

    private fun speakText(text: String, onComplete: () -> Unit) {
        if (!isTtsInitialized || textToSpeech == null) {
            onComplete()
            return
        }
        
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                handler.post { onComplete() }
            }

            override fun onError(utteranceId: String?) {
                handler.post { onComplete() }
            }
        })

        val utteranceId = "tts_${System.currentTimeMillis()}"
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    private fun startRecording() {
        try {
            val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val saveDir = prefs.getString("saveDirectory", null)
            recordingDuration = prefs.getInt("recordingDuration", 10)
            
            // Save recording state to SharedPreferences
            prefs.edit().apply {
                putBoolean("isCurrentlyRecording", true)
                putLong("recordingStartTime", System.currentTimeMillis())
                putInt("initialRecordingDuration", recordingDuration)
                apply()
            }

            if (saveDir.isNullOrEmpty()) {
                updateOverlay("Save directory not configured")
                handler.postDelayed({ stopSelfAndFinish() }, 1000)
                return
            }

            val location = currentLocation ?: run {
                updateOverlay("Location not available")
                handler.postDelayed({ stopSelfAndFinish() }, 1000)
                return
            }

            // Create filename with coordinates and timestamp
            // Note: Using AAC encoding in MPEG-4 container, so extension is .m4a (not .mp3)
            // Android's MediaRecorder doesn't support true MP3 encoding natively
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val lat = String.format("%.6f", location.latitude)
            val lng = String.format("%.6f", location.longitude)
            val fileName = "${lat},${lng}_${timestamp}.m4a"

            val directory = File(saveDir)
            if (!directory.exists()) {
                directory.mkdirs()
            }

            recordingFilePath = File(directory, fileName).absolutePath
            
            // Set audio source
            val audioSource = getPreferredAudioSource()

            mediaRecorder = MediaRecorder(this).apply {
                setAudioSource(audioSource)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(recordingFilePath)
                prepare()
                start()
            }
            
            // Start countdown immediately
            startCountdown()

        } catch (e: IllegalStateException) {
            e.printStackTrace()
            updateOverlay("Recording failed: Invalid state")
            Log.e("OverlayService", "MediaRecorder illegal state", e)
            DebugLogger.logError(
                service = "OverlayService",
                error = "MediaRecorder illegal state - recording setup or start failed",
                exception = e
            )
            handler.postDelayed({ stopSelfAndFinish() }, 3000)
        } catch (e: RuntimeException) {
            e.printStackTrace()
            val errorMsg = when {
                e.message?.contains("start failed") == true -> "Recording failed: Microphone in use"
                e.message?.contains("audio") == true -> "Recording failed: Audio source unavailable"
                else -> "Recording failed: ${e.message ?: "Unknown error"}"
            }
            updateOverlay(errorMsg)
            Log.e("OverlayService", "MediaRecorder runtime error", e)
            DebugLogger.logError(
                service = "OverlayService",
                error = "MediaRecorder runtime error: $errorMsg",
                exception = e
            )
            handler.postDelayed({ stopSelfAndFinish() }, 3000)
        } catch (e: Exception) {
            e.printStackTrace()
            updateOverlay("Recording failed: ${e.message ?: "Unknown error"}")
            Log.e("OverlayService", "MediaRecorder error", e)
            DebugLogger.logError(
                service = "OverlayService",
                error = "MediaRecorder error: ${e.message ?: "Unknown error"}",
                exception = e
            )
            handler.postDelayed({ stopSelfAndFinish() }, 3000)
        }
    }
    
    private fun getPreferredAudioSource(): Int {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        
        // Check Bluetooth permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            Log.d("OverlayService", "Bluetooth permission not granted, using VOICE_RECOGNITION source")
            return MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
        
        return if (audioManager.isBluetoothScoAvailableOffCall) {
            Log.d("OverlayService", "Bluetooth SCO available, starting...")
            audioManager.startBluetoothSco()
            
            // Store timeout reference
            bluetoothScoTimeoutRunnable = Runnable {
                if (!audioManager.isBluetoothScoOn) {
                    Log.d("OverlayService", "Bluetooth SCO timeout")
                }
            }
            handler.postDelayed(bluetoothScoTimeoutRunnable!!, 5000)
            
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        } else {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
    }

    private fun startCountdown() {
        // Cancel any existing countdown first to prevent duplicates
        countdownRunnable?.let { handler.removeCallbacks(it) }
        
        remainingSeconds = recordingDuration
        
        countdownRunnable = object : Runnable {
            override fun run() {
                if (remainingSeconds > 0) {
                    updateOverlay(getString(R.string.recording_countdown, remainingSeconds))
                    remainingSeconds--
                    handler.postDelayed(this, 1000)
                } else {
                    stopRecording()
                }
            }
        }
        handler.post(countdownRunnable!!)
    }
    
    private fun extendRecordingDuration(additionalSeconds: Int) {
        // Stop current countdown
        countdownRunnable?.let { handler.removeCallbacks(it) }
        
        // Add additional seconds to remaining time
        remainingSeconds += additionalSeconds
        
        // Restart countdown with new duration
        startCountdown()
        
        // Update bubble to show extension
        updateOverlay("Recording extended! ${remainingSeconds}s remaining")
    }

    private fun stopRecording() {
        try {
            // Stop countdown
            countdownRunnable?.let { handler.removeCallbacks(it) }
            
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: IllegalStateException) {
                    Log.w("OverlayService", "MediaRecorder already stopped", e)
                }
                release()
            }
            mediaRecorder = null

            updateOverlay(getString(R.string.recording_stopped_msg))

            // Speak "Recording stopped"
            speakText(getString(R.string.recording_stopped)) {
                finishRecordingProcess()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            updateOverlay("Recording failed")
            handler.postDelayed({ stopSelfAndFinish() }, 1000)
        }
    }
    
    private fun finishRecordingProcess() {
        // Show file saved message
        val fileName = recordingFilePath?.let { File(it).name } ?: "unknown"
        updateOverlay("File saved: $fileName")
        
        // Check if online processing is enabled
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val tryOnlineProcessing = prefs.getBoolean(PREF_TRY_ONLINE_PROCESSING, true) // default: true
        
        if (tryOnlineProcessing) {
            // Check internet connectivity
            if (!NetworkUtils.isOnline(this)) {
                // Offline - skip post-processing
                Log.d("OverlayService", "Offline - skipping post-processing")
                handler.postDelayed({ stopSelfAndFinish() }, 2000)
                return
            }
            
            // Online - start post-processing
            startPostProcessing()
        } else {
            // Online processing disabled - quit
            Log.d("OverlayService", "Online processing disabled - quitting")
            handler.postDelayed({ stopSelfAndFinish() }, 2000)
        }
    }

    private fun startPostProcessing() {
        val filePath = recordingFilePath ?: return
        val location = currentLocation ?: return
        
        updateOverlay("Online: Transcribing:")
        
        // Launch coroutine with exception handler
        lifecycleScope.launch(coroutineExceptionHandler) {
            try {
                val transcriptionService = TranscriptionService(this@OverlayService)
                val result = transcriptionService.transcribeAudioFile(filePath)
                
                result.onSuccess { transcribedText ->
                    // Check if coroutine is still active
                    if (!isActive) return@launch
                    
                    handleTranscriptionSuccess(transcribedText, location, filePath)
                }.onFailure { error ->
                    // Check if coroutine is still active
                    if (!isActive) return@launch
                    
                    handleTranscriptionFailure(error)
                }
            } catch (e: Exception) {
                Log.e("OverlayService", "Post-processing error", e)
                DebugLogger.logError(
                    service = "OverlayService",
                    error = "Post-processing error: ${e.message}",
                    exception = e
                )
            } finally {
                // Always stop service after all work is done
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        handler.postDelayed({ stopSelfAndFinish() }, 1000)
                    }
                }
            }
        }
    }

    private suspend fun handleTranscriptionSuccess(transcribedText: String, location: Location, filePath: String) {
        withContext(Dispatchers.Main) {
            // Check if coroutine is still active
            if (!isActive) return@withContext
            
            // Use fallback text if transcription is empty
            val finalText = if (transcribedText.isBlank()) {
                val coords = extractCoordinatesFromFilename(filePath)
                "$coords (no text)"
            } else {
                transcribedText
            }
            
            updateOverlay("Online: Transcribing: $finalText")
            Log.d("OverlayService", "Transcription successful: $finalText")
            
            // Wait 1 second
            delay(1000)
            
            // Check if still active before continuing
            if (!isActive) return@withContext
            
            // Create GPX waypoint
            createGpxWaypoint(location, finalText, filePath)
            
            // Check if OSM note creation is enabled
            val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val addOsmNote = prefs.getBoolean(PREF_ADD_OSM_NOTE, false)
            
            if (addOsmNote) {
                createOsmNote(location, finalText)
            }
            // Note: Service will be stopped by finally block in startPostProcessing
        }
    }
    
    private suspend fun createOsmNote(location: Location, text: String) {
        // Check if coroutine is still active
        if (!isActive) return
        
        updateOverlay("Online: Creating OSM Note")
        
        val oauthManager = OsmOAuthManager(this)
        val accessToken = oauthManager.getAccessToken()
        
        if (accessToken == null) {
            Log.e("OverlayService", "No OSM access token found")
            return
        }
        
        val osmService = OsmNotesService()
        val result = osmService.createNote(location.latitude, location.longitude, text, accessToken)
        
        result.onSuccess {
            if (isActive) {
                withContext(Dispatchers.Main) {
                    updateOverlay("Online: OSM Note created.")
                }
            }
        }.onFailure { error ->
            if (isActive) {
                withContext(Dispatchers.Main) {
                    updateOverlay("Online: OSM Note creation failed :(")
                    Log.e("OverlayService", "OSM note creation failed", error)
                }
            }
        }
        // Note: Service will be stopped by finally block in startPostProcessing
    }

    private suspend fun handleTranscriptionFailure(error: Throwable) {
        withContext(Dispatchers.Main) {
            // Check if coroutine is still active
            if (!isActive) return@withContext
            
            updateOverlay("Online: Transcribing: failed :-(")
            Log.e("OverlayService", "Transcription failed", error)
            
            // Wait 1 second
            delay(1000)
            
            // Note: Service will be stopped by finally block in startPostProcessing
        }
    }

    private fun extractCoordinatesFromFilename(filePath: String): String {
        val fileName = File(filePath).nameWithoutExtension
        // Format: "latitude,longitude_timestamp"
        return fileName.substringBefore("_")
    }

    private fun createGpxWaypoint(location: Location, transcribedText: String, filePath: String) {
        val lat = String.format("%.6f", location.latitude)
        val lng = String.format("%.6f", location.longitude)
        val waypointName = "VoiceNote: $lat,$lng"
        val waypointDesc = transcribedText
        
        Log.d("OverlayService", "Creating GPX waypoint: name=$waypointName, desc=$waypointDesc")
        
        // Call existing createOrUpdateGpxFile method with new waypoint data
        createOrUpdateGpxFile(location, waypointName, waypointDesc)
        
        // Create/update CSV file
        createOrUpdateCsvFile(location, waypointDesc)
    }

    private fun createOrUpdateGpxFile(location: Location?, waypointName: String, waypointDesc: String) {
        try {
            if (location == null) {
                return
            }
            
            val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val saveDir = prefs.getString("saveDirectory", null) ?: return
            
            val gpxFile = File(saveDir, "voicenote_waypoint_collection.gpx")
            
            val lat = String.format("%.6f", location.latitude)
            val lng = String.format("%.6f", location.longitude)
            
            if (gpxFile.exists()) {
                // Parse existing GPX and check for duplicates
                val existingContent = gpxFile.readText()
                val updatedContent = replaceOrAddWaypoint(existingContent, lat, lng, waypointName, waypointDesc)
                gpxFile.writeText(updatedContent)
            } else {
                // Create new GPX file
                val gpxContent = createNewGpxFile(lat, lng, waypointName, waypointDesc)
                gpxFile.writeText(gpxContent)
            }
            
            Log.d("OverlayService", "GPX waypoint created/updated: $waypointName")
            
        } catch (e: Exception) {
            Log.e("OverlayService", "Failed to create/update GPX file", e)
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
            Log.d("OverlayService", "Replacing existing waypoint at $lat,$lng")
            gpxContent.replace(waypointPattern, newWaypoint)
        } else {
            // Add new waypoint before closing </gpx>
            Log.d("OverlayService", "Adding new waypoint at $lat,$lng")
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
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
    }
    
    private fun createOrUpdateCsvFile(location: Location?, text: String) {
        try {
            if (location == null) {
                return
            }
            
            val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val saveDir = prefs.getString("saveDirectory", null) ?: return
            
            val csvFile = File(saveDir, "voicenote_waypoint_collection.csv")
            
            val lat = String.format("%.6f", location.latitude)
            val lng = String.format("%.6f", location.longitude)
            val coords = "$lat,$lng"
            
            // Parse date and time from current timestamp
            val now = Date()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
            val date = dateFormat.format(now)
            val time = timeFormat.format(now)
            
            // Create map links
            val googleMapsLink = "https://www.google.com/maps?q=$lat,$lng"
            val osmLink = "https://www.openstreetmap.org/?mlat=$lat&mlon=$lng&zoom=17"
            
            if (csvFile.exists()) {
                // Read existing content and check for duplicates
                val existingLines = csvFile.readLines()
                val updatedContent = replaceOrAddCsvEntry(existingLines, date, time, coords, text, googleMapsLink, osmLink)
                csvFile.writeText(updatedContent)
            } else {
                // Create new CSV file with UTF-8 BOM and header
                val csvContent = buildNewCsvFile(date, time, coords, text, googleMapsLink, osmLink)
                csvFile.writeText(csvContent)
            }
            
            Log.d("OverlayService", "CSV entry created/updated: $coords")
            
        } catch (e: Exception) {
            Log.e("OverlayService", "Failed to create/update CSV file", e)
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
                Log.d("OverlayService", "Replacing existing CSV entry at $coords")
            } else {
                updatedLines.add(line)
            }
        }
        
        // If not found, add new entry
        if (!found) {
            updatedLines.add(newEntry)
            Log.d("OverlayService", "Adding new CSV entry at $coords")
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


    private fun updateOverlay(text: String) {
        handler.post {
            bubbleLine1?.text = text
        }
    }

    private fun stopSelfAndFinish() {
        // Prevent multiple calls
        if (isServiceStopping) {
            Log.d("OverlayService", "Service already stopping, ignoring duplicate call")
            return
        }
        isServiceStopping = true
        
        // Cancel Bluetooth SCO timeout
        bluetoothScoTimeoutRunnable?.let { handler.removeCallbacks(it) }
        
        // Cancel TTS timeout
        ttsTimeoutRunnable?.let { handler.removeCallbacks(it) }
        
        // Cancel countdown if running
        countdownRunnable?.let { handler.removeCallbacks(it) }
        
        // Clear recording state from SharedPreferences
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove("isCurrentlyRecording")
            remove("recordingStartTime")
            remove("initialRecordingDuration")
            apply()
        }
        
        // Remove overlay with protection
        if (!isOverlayRemoved) {
            overlayView?.let {
                try {
                    windowManager?.removeView(it)
                    isOverlayRemoved = true
                } catch (e: IllegalArgumentException) {
                    Log.w("OverlayService", "Overlay already removed", e)
                }
            }
        }
        
        // Stop Bluetooth SCO if it was started
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (audioManager.isBluetoothScoOn) {
            audioManager.stopBluetoothSco()
        }
        
        // Send broadcast to finish MainActivity
        val finishIntent = Intent("com.voicenotes.motorcycle.FINISH_ACTIVITY")
        sendBroadcast(finishIntent)
        
        stopSelf()
    }

    override fun onDestroy() {
        // Clean up handler first to prevent any callbacks from running
        handler.removeCallbacksAndMessages(null)
        
        // Clean up all timeouts
        ttsTimeoutRunnable?.let { handler.removeCallbacks(it) }
        bluetoothScoTimeoutRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable?.let { handler.removeCallbacks(it) }
        
        textToSpeech?.shutdown()
        mediaRecorder?.release()
        
        // Remove overlay with protection
        if (!isOverlayRemoved) {
            overlayView?.let {
                try {
                    windowManager?.removeView(it)
                    isOverlayRemoved = true
                } catch (e: IllegalArgumentException) {
                    Log.w("OverlayService", "Overlay already removed in onDestroy", e)
                }
            }
        }
        
        super.onDestroy()
    }
}
