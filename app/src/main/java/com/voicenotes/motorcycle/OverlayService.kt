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
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
    private var bubbleLine2: TextView? = null
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var textToSpeech: TextToSpeech? = null
    private var mediaRecorder: MediaRecorder? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var currentLocation: Location? = null
    private var recordingFilePath: String? = null
    private var transcribedText: String? = null
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
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        
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
        bubbleLine2 = overlayView?.findViewById(R.id.bubbleLine2)
        
        windowManager?.addView(overlayView, params)
        
        updateBubbleLine1(getString(R.string.acquiring_location))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Wait for TTS initialization before starting
        // onInit will be called when TTS is ready
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
            updateBubbleLine1("Location permission not granted")
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
            updateBubbleLine1(getString(R.string.location_acquired_coords, coords))
        }
        
        // Speak "Location acquired" and then "Recording started"
        speakText(getString(R.string.location_acquired)) {
            speakText(getString(R.string.recording_started)) {
                startSpeechRecognitionBeforeRecording()
            }
        }
    }

    private fun onLocationFailed() {
        updateBubbleLine1(getString(R.string.location_failed))
        handler.postDelayed({ stopSelfAndFinish() }, 2000)
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

            if (saveDir.isNullOrEmpty()) {
                updateBubbleLine1("Save directory not configured")
                handler.postDelayed({ stopSelfAndFinish() }, 2000)
                return
            }

            val location = currentLocation ?: run {
                updateBubbleLine1("Location not available")
                handler.postDelayed({ stopSelfAndFinish() }, 2000)
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
            
            // Keep showing the transcribed text on line 2 during recording
            if (!transcribedText.isNullOrEmpty()) {
                updateBubbleLine2("\"${transcribedText}\"")
            } else {
                updateBubbleLine2("🎤 Recording...")
            }
            
            // Start countdown
            startCountdown()

        } catch (e: Exception) {
            updateBubbleLine1("Recording failed")
            e.printStackTrace()
            handler.postDelayed({ stopSelfAndFinish() }, 2000)
        }
    }
    
    private fun getPreferredAudioSource(): Int {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        
        // Check Bluetooth permission for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                Log.d("OverlayService", "Bluetooth permission not granted, using device microphone")
                return MediaRecorder.AudioSource.MIC
            }
        }
        
        return if (audioManager.isBluetoothScoAvailableOffCall) {
            Log.d("OverlayService", "Bluetooth SCO available, starting Bluetooth SCO")
            audioManager.startBluetoothSco()
            // MIC will route to Bluetooth if SCO is active
            MediaRecorder.AudioSource.MIC
        } else {
            Log.d("OverlayService", "Bluetooth SCO not available, using device microphone")
            MediaRecorder.AudioSource.MIC
        }
    }

    private fun startCountdown() {
        remainingSeconds = recordingDuration
        
        countdownRunnable = object : Runnable {
            override fun run() {
                if (remainingSeconds > 0) {
                    updateBubbleLine1(getString(R.string.recording_countdown, remainingSeconds))
                    remainingSeconds--
                    handler.postDelayed(this, 1000)
                } else {
                    stopRecording()
                }
            }
        }
        handler.post(countdownRunnable!!)
    }

    private fun startSpeechRecognitionBeforeRecording() {
        try {
            // Load recording duration from preferences
            val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            recordingDuration = prefs.getInt("recordingDuration", 10)
            
            transcribedText = null
            updateBubbleLine1("Listening...")
            updateBubbleLine2("🎤 Speak now...")
            
            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, recordingDuration * 1000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            }
            
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    updateBubbleLine2("🎤 Listening...")
                }
                override fun onBeginningOfSpeech() {
                    updateBubbleLine2("🎤 Recognizing...")
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    // Speech ended, now start recording
                }
                override fun onError(error: Int) {
                    // If transcription fails, continue with recording anyway
                    updateBubbleLine2("Starting recording...")
                    handler.postDelayed({
                        speakText(getString(R.string.recording_started)) {
                            startRecording()
                        }
                    }, 500)
                }
                
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        transcribedText = matches[0]
                        updateBubbleLine2("\"${transcribedText}\"")
                    } else {
                        updateBubbleLine2("")
                    }
                    // Now start the actual recording
                    handler.postDelayed({
                        speakText(getString(R.string.recording_started)) {
                            startRecording()
                        }
                    }, 500)
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val partialText = matches[0]
                        updateBubbleLine2("\"${partialText}...\"")
                    }
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            
            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            updateBubbleLine2("Speech recognition failed")
            // Continue with recording even if speech recognition fails
            handler.postDelayed({
                speakText(getString(R.string.recording_started)) {
                    startRecording()
                }
            }, 1000)
        }
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

            updateBubbleLine1(getString(R.string.recording_stopped_msg))
            // Keep the transcribed text visible on line 2
            if (!transcribedText.isNullOrEmpty()) {
                updateBubbleLine2("\"${transcribedText}\"")
            } else {
                updateBubbleLine2("")
            }

            // Speak "Recording stopped"
            speakText(getString(R.string.recording_stopped)) {
                finishRecordingProcess()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            finishRecordingProcess()
        }
    }
    
    private fun finishRecordingProcess() {
        // Create or update GPX file
        recordingFilePath?.let { filePath ->
            val fileName = File(filePath).name
            currentLocation?.let { location ->
                val lat = String.format("%.6f", location.latitude)
                val lng = String.format("%.6f", location.longitude)
                val waypointName = "VoiceNote: ${lat},${lng}"
                
                // Use transcribed text if available, otherwise fall back to filename
                val waypointDesc = if (!transcribedText.isNullOrEmpty()) {
                    Log.d("OverlayService", "Using transcribed text for waypoint: $transcribedText")
                    transcribedText!!
                } else {
                    Log.d("OverlayService", "No transcribed text available, using filename: $fileName")
                    fileName
                }
                
                createOrUpdateGpxFile(location, waypointName, waypointDesc)
            }
        }

        updateBubbleLine1(getString(R.string.file_saved))
        updateBubbleLine2("")

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
            
            val gpxFile = File(saveDir, "acquired_locations.gpx")
            
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

    private fun updateBubbleLine1(text: String) {
        handler.post {
            bubbleLine1?.text = text
        }
    }

    private fun updateBubbleLine2(text: String) {
        handler.post {
            bubbleLine2?.text = text
        }
    }

    private fun stopSelfAndFinish() {
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
        textToSpeech?.shutdown()
        mediaRecorder?.release()
        speechRecognizer?.destroy()
        
        overlayView?.let {
            windowManager?.removeView(it)
        }
        
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
