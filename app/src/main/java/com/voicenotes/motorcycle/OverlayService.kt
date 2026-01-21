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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class OverlayService : Service(), TextToSpeech.OnInitListener {

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
        // TODO: GPX waypoint creation will happen in post-processing after transcription
        // Create or update GPX file
        // recordingFilePath?.let { filePath ->
        //     val fileName = File(filePath).name
        //     currentLocation?.let { location ->
        //         val lat = String.format("%.6f", location.latitude)
        //         val lng = String.format("%.6f", location.longitude)
        //         val waypointName = "VoiceNote: ${lat},${lng}"
        //         
        //         // Use transcribed text if available, otherwise fall back to filename
        //         val waypointDesc = fileName
        //         
        //         createOrUpdateGpxFile(location, waypointName, waypointDesc)
        //     }
        // }

        updateOverlay(getString(R.string.file_saved))

        // Wait 2 seconds and quit
        handler.postDelayed({
            stopSelfAndFinish()
        }, 2000)
    }

    private fun createOrUpdateGpxFile(location: Location?, waypointName: String, waypointDesc: String) {
        try {
            if (location == null) {
                return
            }
            
            val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val saveDir = prefs.getString("saveDirectory", null) ?: return
            
            val gpxFile = File(saveDir, "voicenote_waypoint_collection.gpx")
            
            if (gpxFile.exists()) {
                val content = gpxFile.readText()
                val gpxEndTag = "</gpx>"
                
                if (content.contains(gpxEndTag)) {
                    val waypoint = createWaypointXml(location, waypointName, waypointDesc)
                    val updatedContent = content.replace(gpxEndTag, "$waypoint\n$gpxEndTag")
                    gpxFile.writeText(updatedContent)
                } else {
                    val gpxContent = createGpxFile(location, waypointName, waypointDesc)
                    gpxFile.writeText(gpxContent)
                }
            } else {
                val gpxContent = createGpxFile(location, waypointName, waypointDesc)
                gpxFile.writeText(gpxContent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun createGpxFile(location: Location, name: String, desc: String): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        
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
${createWaypointXml(location, name, desc)}
</gpx>"""
    }
    
    private fun createWaypointXml(location: Location, name: String, desc: String): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        
        return """  <wpt lat="${String.format("%.6f", location.latitude)}" lon="${String.format("%.6f", location.longitude)}">
    <time>$timestamp</time>
    <name>$name</name>
    <desc>$desc</desc>
  </wpt>"""
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

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
