package com.voicenotes.motorcycle

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var statusText: TextView
    private lateinit var infoText: TextView
    private lateinit var settingsButton: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var textToSpeech: TextToSpeech? = null
    private var mediaRecorder: MediaRecorder? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var currentLocation: Location? = null
    private var recordingFilePath: String? = null
    private var transcribedText: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val PERMISSIONS_REQUEST_CODE = 100

    private val requiredPermissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        infoText = findViewById(R.id.infoText)
        settingsButton = findViewById(R.id.settingsButton)
        progressBar = findViewById(R.id.progressBar)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        textToSpeech = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // Check if this is first run
        if (isFirstRun()) {
            showSetupDialog()
        } else if (isFirstActualRun()) {
            // Show explanation on first actual run after setup
            showFirstRunExplanation()
        } else {
            // Subsequent runs - always record again
            startRecordingProcess()
        }
    }

    private fun isFirstRun(): Boolean {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val saveDir = prefs.getString("saveDirectory", null)
        val triggerApp = prefs.getString("triggerApp", null)
        
        // Check if save directory exists
        if (!saveDir.isNullOrEmpty()) {
            val dir = File(saveDir)
            if (!dir.exists()) {
                // Directory doesn't exist, treat as first run
                return true
            }
        }
        
        return saveDir.isNullOrEmpty() || triggerApp.isNullOrEmpty()
    }
    
    private fun isFirstActualRun(): Boolean {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        return !prefs.getBoolean("hasRunBefore", false)
    }
    
    private fun markAsRun() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        prefs.edit().putBoolean("hasRunBefore", true).apply()
    }
    
    private fun showFirstRunExplanation() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val recordingDuration = prefs.getInt("recordingDuration", 10)
        
        AlertDialog.Builder(this)
            .setTitle("How This App Works")
            .setMessage("""
                Welcome to Motorcycle Voice Notes!
                
                Here's what happens when you launch the app:
                
                1. 📍 GPS location is acquired
                2. 🎤 Records $recordingDuration seconds of audio in MP3 format
                3. 🗣️ Audio is transcribed to text in real-time
                4. 💾 Saved with GPS coordinates in filename
                5. 📌 Waypoint created in GPX file using transcribed text
                6. 🚀 Your chosen app launches automatically
                
                The app prefers Bluetooth microphones if connected.
                
                Perfect for quick voice notes while riding!
            """.trimIndent())
            .setPositiveButton("Start Recording") { _, _ ->
                markAsRun()
                startRecordingProcess()
            }
            .setCancelable(false)
            .show()
    }

    private fun showSetupDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.setup_required)
            .setMessage(R.string.complete_setup)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
            }
            .setCancelable(false)
            .show()
    }

    private fun startRecordingProcess() {
        if (!checkPermissions()) {
            requestPermissions()
            return
        }

        infoText.text = "Acquiring location..."
        progressBar.visibility = View.VISIBLE

        acquireLocation()
    }

    private fun checkPermissions(): Boolean {
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSIONS_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startRecordingProcess()
            } else {
                AlertDialog.Builder(this)
                    .setTitle(R.string.permission_required)
                    .setMessage(R.string.grant_permissions)
                    .setPositiveButton("OK") { _, _ -> finish() }
                    .show()
            }
        }
    }

    private fun acquireLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show()
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
        progressBar.visibility = View.GONE
        infoText.text = "Location acquired"

        // Speak and start recording
        speakAndRecord(getString(R.string.location_acquired))
    }

    private fun onLocationFailed() {
        progressBar.visibility = View.GONE
        infoText.text = getString(R.string.location_failed)
        Toast.makeText(this, R.string.location_failed, Toast.LENGTH_LONG).show()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
        }
    }

    private fun speakAndRecord(text: String) {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // Speech started
            }

            override fun onDone(utteranceId: String?) {
                // Speech finished, start recording
                runOnUiThread {
                    startRecording()
                }
            }

            override fun onError(utteranceId: String?) {
                // Error in speech, still try to record
                runOnUiThread {
                    startRecording()
                }
            }
        })

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "recording_start")
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "recording_start")
    }

    private fun startRecording() {
        try {
            val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
            val saveDir = prefs.getString("saveDirectory", null)
            val recordingDuration = prefs.getInt("recordingDuration", 10)

            if (saveDir.isNullOrEmpty()) {
                Toast.makeText(this, "Save directory not configured", Toast.LENGTH_SHORT).show()
                return
            }

            val location = currentLocation ?: run {
                Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show()
                return
            }

            // Create filename with coordinates and timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val lat = String.format("%.6f", location.latitude)
            val lng = String.format("%.6f", location.longitude)
            val fileName = "${lat},${lng}_${timestamp}.mp3"

            val directory = File(saveDir)
            if (!directory.exists()) {
                directory.mkdirs()
            }

            recordingFilePath = File(directory, fileName).absolutePath
            
            // Set audio source to Bluetooth if available
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

            infoText.text = "Recording for $recordingDuration seconds..."

            // Start live speech recognition during recording
            startLiveSpeechRecognition()

            // Stop recording after configured duration
            handler.postDelayed({
                stopRecording()
            }, recordingDuration * 1000L)

        } catch (e: Exception) {
            Toast.makeText(this, "Recording failed: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
    
    private fun getPreferredAudioSource(): Int {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        
        // Check Bluetooth permission for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                // Permission not granted, use regular microphone
                return MediaRecorder.AudioSource.MIC
            }
        }
        
        // Check if Bluetooth SCO (Synchronous Connection Oriented) is available
        return if (audioManager.isBluetoothScoAvailableOffCall) {
            // Try to use Bluetooth microphone
            audioManager.startBluetoothSco()
            MediaRecorder.AudioSource.MIC // Still use MIC as source, but with Bluetooth routing
        } else {
            MediaRecorder.AudioSource.MIC
        }
    }

    private fun startLiveSpeechRecognition() {
        try {
            // Reset transcribedText for new recording
            transcribedText = null
            
            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    // Speech recognition ready
                }
                
                override fun onBeginningOfSpeech() {
                    // User started speaking
                }
                
                override fun onRmsChanged(rmsdB: Float) {
                    // Audio level changed
                }
                
                override fun onBufferReceived(buffer: ByteArray?) {
                    // Audio buffer received
                }
                
                override fun onEndOfSpeech() {
                    // User stopped speaking
                }
                
                override fun onError(error: Int) {
                    // Speech recognition error - will use filename as fallback
                }
                
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        transcribedText = matches[0]
                    }
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    // Update with partial results for better UX
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        transcribedText = matches[0]
                    }
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {
                    // Speech recognition event
                }
            })
            
            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            // Silently fail - will use filename as fallback
        }
    }
    
    private fun stopRecording() {
        try {
            // Stop speech recognition first
            speechRecognizer?.stopListening()
            
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            infoText.text = "Recording saved"

            // Finish the recording process
            finishRecordingProcess()

        } catch (e: Exception) {
            Toast.makeText(this, "Error stopping recording: ${e.message}", Toast.LENGTH_SHORT).show()
            // Continue with GPX creation even if recording stop failed
            finishRecordingProcess()
        }
    }
    
    private fun finishRecordingProcess() {
        // Create or update GPX file with coordinates in waypoint name and transcribed text as description
        recordingFilePath?.let { filePath ->
            val fileName = File(filePath).name
            currentLocation?.let { location ->
                val lat = String.format("%.6f", location.latitude)
                val lng = String.format("%.6f", location.longitude)
                val waypointName = "VoiceNote: ${lat},${lng}"
                val waypointDesc = transcribedText ?: fileName
                createOrUpdateGpxFile(location, waypointName, waypointDesc)
            }
        }

        // Speak recording stopped
        speakRecordingStopped()
    }

    private fun speakRecordingStopped() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    launchTriggerApp()
                }
            }

            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    launchTriggerApp()
                }
            }
        })

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "recording_stop")
        textToSpeech?.speak(getString(R.string.recording_stopped),
            TextToSpeech.QUEUE_FLUSH, params, "recording_stop")
    }

    private fun launchTriggerApp() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val triggerApp = prefs.getString("triggerApp", null)

        if (!triggerApp.isNullOrEmpty()) {
            try {
                val intent = packageManager.getLaunchIntentForPackage(triggerApp)
                if (intent != null) {
                    startActivity(intent)
                    // Don't finish() here so we can continue in background
                } else {
                    Toast.makeText(this, "Cannot launch trigger app", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error launching app: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun createOrUpdateGpxFile(location: Location?, waypointName: String, waypointDesc: String) {
        try {
            // Add null check at the beginning
            if (location == null) {
                Toast.makeText(this, "Location not available for GPX", Toast.LENGTH_SHORT).show()
                return
            }
            
            val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
            val saveDir = prefs.getString("saveDirectory", null) ?: return
            
            val gpxFile = File(saveDir, "acquired_locations.gpx")
            
            if (gpxFile.exists()) {
                // Read existing file and add new waypoint
                val content = gpxFile.readText()
                val gpxEndTag = "</gpx>"
                
                if (content.contains(gpxEndTag)) {
                    val waypoint = createWaypointXml(location, waypointName, waypointDesc)
                    val updatedContent = content.replace(gpxEndTag, "$waypoint\n$gpxEndTag")
                    gpxFile.writeText(updatedContent)
                } else {
                    // File is corrupted or incomplete, recreate it with current waypoint
                    val gpxContent = createGpxFile(location, waypointName, waypointDesc)
                    gpxFile.writeText(gpxContent)
                }
            } else {
                // Create new GPX file
                val gpxContent = createGpxFile(location, waypointName, waypointDesc)
                gpxFile.writeText(gpxContent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error creating GPX file: ${e.message}", Toast.LENGTH_SHORT).show()
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

    override fun onDestroy() {
        textToSpeech?.shutdown()
        mediaRecorder?.release()
        speechRecognizer?.destroy()
        
        // Stop Bluetooth SCO if it was started
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (audioManager.isBluetoothScoOn) {
            audioManager.stopBluetoothSco()
        }
        
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // Restart the process if setup is now complete
        if (!isFirstRun() && currentLocation == null) {
            startRecordingProcess()
        }
    }
}
