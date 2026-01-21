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

    override fun onCreate() {
        super.onCreate()
        
        // Check if overlay permission is granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        textToSpeech = TextToSpeech(this, this)
        
        createOverlay()
    }

    private fun createOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
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
        val extendedDuration = intent?.getIntExtra("extendedDuration", -1) ?: -1
        
        if (extendedDuration > 0) {
            // This is an extension request - use the duration from the intent
            extendRecordingDuration(extendedDuration)
            return START_NOT_STICKY
        }
        
        // Normal startup flow - wait for TTS initialization
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            isTtsInitialized = true
        } else {
            isTtsInitialized = false
        }
        
        // Start the recording process after TTS initialization
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

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location: Location? ->
            if (location != null) {
                currentLocation = location
                onLocationAcquired()
            } else {
                onLocationFailed()
            }
        }.addOnFailureListener {
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

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this).apply {
                    setAudioSource(audioSource)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128000)
                    setAudioSamplingRate(44100)
                    setOutputFile(recordingFilePath)
                    prepare()
                    start()
                }
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder().apply {
                    setAudioSource(audioSource)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128000)
                    setAudioSamplingRate(44100)
                    setOutputFile(recordingFilePath)
                    prepare()
                    start()
                }
            }
            
            // Start countdown immediately
            startCountdown()

        } catch (e: Exception) {
            updateOverlay("Recording failed")
            e.printStackTrace()
            handler.postDelayed({ stopSelfAndFinish() }, 1000)
        }
    }
    
    private fun getPreferredAudioSource(): Int {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        
        // Check Bluetooth permission for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                Log.d("OverlayService", "Bluetooth permission not granted, using VOICE_RECOGNITION source")
                return MediaRecorder.AudioSource.VOICE_RECOGNITION
            }
        }
        
        return if (audioManager.isBluetoothScoAvailableOffCall) {
            Log.d("OverlayService", "Bluetooth SCO available, starting Bluetooth SCO with VOICE_RECOGNITION")
            audioManager.startBluetoothSco()
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        } else {
            Log.d("OverlayService", "Bluetooth SCO not available, using VOICE_RECOGNITION source")
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
    }

    private fun startCountdown() {
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
                stop()
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
        
        // Launch coroutine for transcription
        lifecycleScope.launch {
            val transcriptionService = TranscriptionService(this@OverlayService)
            val result = transcriptionService.transcribeAudioFile(filePath)
            
            result.onSuccess { transcribedText ->
                handleTranscriptionSuccess(transcribedText, location, filePath)
            }.onFailure { error ->
                handleTranscriptionFailure(error)
            }
        }
    }

    private suspend fun handleTranscriptionSuccess(transcribedText: String, location: Location, filePath: String) {
        withContext(Dispatchers.Main) {
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
            
            // Create GPX waypoint
            createGpxWaypoint(location, finalText, filePath)
            
            // Check if OSM note creation is enabled
            val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val addOsmNote = prefs.getBoolean(PREF_ADD_OSM_NOTE, false)
            
            if (addOsmNote) {
                createOsmNote(location, finalText)
            } else {
                // Quit app
                handler.postDelayed({ stopSelfAndFinish() }, 1000)
            }
        }
    }
    
    private suspend fun createOsmNote(location: Location, text: String) {
        updateOverlay("Online: Creating OSM Note")
        
        val oauthManager = OsmOAuthManager(this)
        val accessToken = oauthManager.getAccessToken()
        
        if (accessToken == null) {
            Log.e("OverlayService", "No OSM access token found")
            handler.postDelayed({ stopSelfAndFinish() }, 1000)
            return
        }
        
        val osmService = OsmNotesService()
        val result = osmService.createNote(location.latitude, location.longitude, text, accessToken)
        
        result.onSuccess {
            withContext(Dispatchers.Main) {
                updateOverlay("Online: OSM Note created.")
                handler.postDelayed({ stopSelfAndFinish() }, 1000)
            }
        }.onFailure { error ->
            withContext(Dispatchers.Main) {
                updateOverlay("Online: OSM Note creation failed :(")
                Log.e("OverlayService", "OSM note creation failed", error)
                handler.postDelayed({ stopSelfAndFinish() }, 1000)
            }
        }
    }

    private suspend fun handleTranscriptionFailure(error: Throwable) {
        withContext(Dispatchers.Main) {
            updateOverlay("Online: Transcribing: failed :-(")
            Log.e("OverlayService", "Transcription failed", error)
            
            // Wait 1 second
            delay(1000)
            
            // Skip GPX and OSM creation
            // Quit app
            handler.postDelayed({ stopSelfAndFinish() }, 1000)
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


    private fun updateOverlay(text: String) {
        handler.post {
            bubbleLine1?.text = text
        }
    }

    private fun stopSelfAndFinish() {
        // Clear recording state from SharedPreferences
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove("isCurrentlyRecording")
            remove("recordingStartTime")
            remove("initialRecordingDuration")
            apply()
        }
        
        // Remove overlay
        overlayView?.let {
            windowManager?.removeView(it)
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
        // Clean up countdowns
        countdownRunnable?.let { handler.removeCallbacks(it) }
        
        textToSpeech?.shutdown()
        mediaRecorder?.release()
        
        overlayView?.let {
            windowManager?.removeView(it)
        }
        
        super.onDestroy()
    }
}
