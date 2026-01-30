package com.voicenotes.motorcycle

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.voicenotes.motorcycle.database.Recording
import com.voicenotes.motorcycle.database.RecordingDatabase
import com.voicenotes.motorcycle.database.V2SStatus
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Overlay service for Voice Notes recording functionality.
 * 
 * This service is responsible for:
 * 1. Configuration and Permission Validation: On startup, checks if the app is fully configured
 *    with all required permissions (overlay, microphone, location, Bluetooth) and settings 
 *    (save directory). If not configured, displays a brief, non-intrusive overlay bubble 
 *    message for ~3 seconds, then automatically stops the service.
 * 
 * 2. Recording Lifecycle: If fully configured, manages the complete recording lifecycle including:
 *    - GPS location acquisition
 *    - Audio recording with MediaRecorder
 *    - Visual feedback via overlay bubble
 *    - Text-to-speech announcements
 *    - Recording countdown and automatic stop
 *    - Saving recordings with metadata to database
 * 
 * 3. Bluetooth Audio Integration: Handles Bluetooth SCO (Synchronous Connection-Oriented) setup
 *    for routing audio to/from Bluetooth devices.
 * 
 * The service never launches activities or switches to any UI screen. It operates entirely in
 * the background with only overlay bubble feedback. Configuration must be done through explicit
 * UI mode in MainActivity (via "Manage" action or VN Manager app).
 */
class OverlayService : LifecycleService(), TextToSpeech.OnInitListener {

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "voice_notes_recording"
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
    private var unconfiguredOverlayTimeoutRunnable: Runnable? = null
    
    // Flags to prevent double cleanup
    private var isOverlayRemoved = false
    private var isServiceStopping = false
    private var isUnconfiguredMode = false
    
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

        // Start as foreground service to prevent Android from killing us
        createNotificationChannel()
        val notification = createNotification("Initializing...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Check if overlay permission is granted before creating overlay
        if (!Settings.canDrawOverlays(this)) {
            Log.e("OverlayService", "Overlay permission not granted - cannot start overlay service")
            DebugLogger.logError(
                service = "OverlayService",
                error = getString(R.string.overlay_permission_missing)
            )
            stopSelf()
            return
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Create overlay first
        createOverlay()
        
        // Initialize TTS (but only if permissions are present will we use it)
        textToSpeech = TextToSpeech(this, this)
        
        // Add TTS timeout - only proceeds to recording if permissions are present
        ttsTimeoutRunnable = Runnable {
            if (!isTtsInitialized && !isUnconfiguredMode) {
                Log.w("OverlayService", "TTS initialization timeout - proceeding without TTS")
                DebugLogger.logError(
                    service = "OverlayService",
                    error = "TTS initialization timeout after 10 seconds - proceeding without TTS"
                )
                startRecordingProcess()
            }
        }
        handler.postDelayed(ttsTimeoutRunnable!!, 10000)
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
        super.onStartCommand(intent, flags, startId)
        
        // Intent extra key "additionalDuration" kept for backward compatibility
        // Variable named resetDuration to reflect actual behavior (reset, not add)
        val resetDuration = intent?.getIntExtra("additionalDuration", -1) ?: -1
        
        if (resetDuration > 0) {
            // This is an extension request - verify app is still fully configured
            if (!isAppFullyConfigured()) {
                Log.w("OverlayService", "Extension request but app not fully configured - showing overlay and stopping")
                handleOverlayMessage(getString(R.string.app_not_configured_message), 3000)
                return START_NOT_STICKY
            }
            
            // App still configured - reset timer to the duration from the intent
            extendRecordingDuration(resetDuration)
            return START_NOT_STICKY
        }
        
        // Normal startup flow - first check if app is fully configured
        if (!isAppFullyConfigured()) {
            // App not fully configured - show brief informational overlay and quit
            Log.w("OverlayService", "App not fully configured on startup - showing overlay and stopping")
            handleOverlayMessage(getString(R.string.app_not_configured_message), 3000)
            return START_NOT_STICKY
        }
        
        // App fully configured - wait for TTS initialization to start recording
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        ttsTimeoutRunnable?.let { handler.removeCallbacks(it) }
        
        // Don't proceed with recording if we're in unconfigured mode (missing permissions)
        if (isUnconfiguredMode) {
            Log.d("OverlayService", "TTS initialized but in unconfigured mode (missing permissions), not starting recording")
            return
        }
        
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
        updateOverlay(getString(R.string.acquiring_location))
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
        
        // First, prepare the MediaRecorder (but don't start yet)
        prepareRecording()
        
        // THEN speak announcements
        speakText(getString(R.string.location_acquired)) {
            Log.d("OverlayService", "TTS: 'Location acquired' completed")
            speakText(getString(R.string.recording_started)) {
                Log.d("OverlayService", "TTS: 'Recording started' completed")
                // ONLY start recording AFTER TTS completes
                startRecordingAndCountdown()
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
        
        Log.d("OverlayService", "TTS: Starting to speak: '$text'")
        
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d("OverlayService", "TTS: onStart for: '$text'")
            }

            override fun onDone(utteranceId: String?) {
                Log.d("OverlayService", "TTS: onDone for: '$text'")
                handler.post { onComplete() }
            }

            override fun onError(utteranceId: String?) {
                Log.e("OverlayService", "TTS: onError for: '$text'")
                handler.post { onComplete() }
            }
        })

        val utteranceId = "tts_${System.currentTimeMillis()}"
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    private fun prepareRecording() {
        try {
            val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            recordingDuration = prefs.getInt("recordingDuration", 10)
            
            // Save recording state to SharedPreferences
            prefs.edit().apply {
                putBoolean("isCurrentlyRecording", true)
                putLong("recordingStartTime", System.currentTimeMillis())
                putInt("initialRecordingDuration", recordingDuration)
                apply()
            }

            val location = currentLocation ?: run {
                updateOverlay("Location not available")
                handler.postDelayed({ stopSelfAndFinish() }, 1000)
                return
            }

            // Create filename with coordinates and timestamp
            // Use OGG_OPUS encoding for API 29+ (smaller, better quality for speech)
            // Fall back to AMR_WB for older devices (API 26-28) - compatible with Speech-to-Text API
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val lat = String.format("%.6f", location.latitude)
            val lng = String.format("%.6f", location.longitude)
            val fileExtension = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "ogg" else "amr"
            val fileName = "${lat},${lng}_${timestamp}.${fileExtension}"

            // Use internal app storage instead of external storage
            val directory = File(filesDir, "recordings")
            if (!directory.exists()) {
                directory.mkdirs()
            }

            recordingFilePath = File(directory, fileName).absolutePath
            
            // Set audio source
            val audioSource = getPreferredAudioSource()

            // MediaRecorder constructor requires different signatures based on API level:
            // API 31+ (Android S): MediaRecorder(Context) - required to associate with app context
            // API 26-30: MediaRecorder() - deprecated but required for backward compatibility
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(audioSource)
                
                // Use OGG_OPUS for API 29+ (Android 10+) for better compression and quality
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setOutputFormat(MediaRecorder.OutputFormat.OGG)
                    setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    setAudioEncodingBitRate(32000)  // 32kbps is optimal for speech with Opus
                    setAudioSamplingRate(48000)     // Opus native sample rate
                } else {
                    // Fall back to AMR_WB for older devices (API 26-28)
                    // AMR_WB is compatible with Google Cloud Speech-to-Text API
                    // Uses fixed 16kHz sample rate (optimal for speech recognition)
                    setOutputFormat(MediaRecorder.OutputFormat.AMR_WB)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB)
                }
                
                setOutputFile(recordingFilePath)
                prepare()
            }
            
            Log.d("OverlayService", "MediaRecorder prepared successfully, waiting for TTS to complete before starting")

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
    
    private fun startRecordingAndCountdown() {
        try {
            // NOW actually start the recording
            Log.d("OverlayService", "Starting MediaRecorder.start() - TTS announcements completed")
            mediaRecorder?.start()
            Log.d("OverlayService", "MediaRecorder started successfully")
            
            // And start the countdown
            Log.d("OverlayService", "Starting countdown for $recordingDuration seconds")
            startCountdown()

        } catch (e: IllegalStateException) {
            e.printStackTrace()
            updateOverlay("Recording failed: Invalid state")
            Log.e("OverlayService", "MediaRecorder illegal state on start", e)
            DebugLogger.logError(
                service = "OverlayService",
                error = "MediaRecorder illegal state - recording start failed",
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
            Log.e("OverlayService", "MediaRecorder runtime error on start", e)
            DebugLogger.logError(
                service = "OverlayService",
                error = "MediaRecorder runtime error on start: $errorMsg",
                exception = e
            )
            handler.postDelayed({ stopSelfAndFinish() }, 3000)
        } catch (e: Exception) {
            e.printStackTrace()
            updateOverlay("Recording failed: ${e.message ?: "Unknown error"}")
            Log.e("OverlayService", "MediaRecorder error on start", e)
            DebugLogger.logError(
                service = "OverlayService",
                error = "MediaRecorder error on start: ${e.message ?: "Unknown error"}",
                exception = e
            )
            handler.postDelayed({ stopSelfAndFinish() }, 3000)
        }
    }
    
    private fun getPreferredAudioSource(): Int {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        
        // Check Bluetooth permission (only needed on API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                Log.d("OverlayService", "Bluetooth permission not granted, using VOICE_COMMUNICATION source")
                return MediaRecorder.AudioSource.VOICE_COMMUNICATION
            }
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
            
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        } else {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        }
    }

    private fun startCountdown(resetTimer: Boolean = true) {
        // Cancel any existing countdown first to prevent duplicates
        countdownRunnable?.let { handler.removeCallbacks(it) }
        
        // Only reset the timer if starting a new recording, not when resuming
        if (resetTimer) {
            remainingSeconds = recordingDuration
        }
        
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
    
    private fun extendRecordingDuration(resetSeconds: Int) {
        // Stop current countdown
        countdownRunnable?.let { handler.removeCallbacks(it) }
        
        // Set remaining time to the configured duration (reset, don't add)
        remainingSeconds = resetSeconds
        
        // Restart countdown (resetTimer=false means don't reset recording start time)
        startCountdown(resetTimer = false)
        
        // Update bubble to show extension
        updateOverlay("Recording extended! ${remainingSeconds}s remaining")
    }

    private fun checkAllRequiredPermissions(): Boolean {
        Log.d("OverlayService", "Checking all required permissions")
        
        // Note: Overlay permission already checked in onCreate()
        
        // Check microphone permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w("OverlayService", "Missing permission: Microphone (RECORD_AUDIO)")
            return false
        }
        
        // Check location permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w("OverlayService", "Missing permission: Location (ACCESS_FINE_LOCATION)")
            return false
        }
        
        // Check Bluetooth permission (only on Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                Log.w("OverlayService", "Missing permission: Bluetooth (BLUETOOTH_CONNECT)")
                return false
            }
        }
        
        Log.d("OverlayService", "All required permissions are granted")
        return true
    }
    
    /**
     * Checks if the app is fully configured with all necessary permissions and settings.
     * This includes:
     * - All required runtime permissions (microphone, location, Bluetooth)
     * - Save directory configured in SharedPreferences
     * 
     * Note: Overlay permission is already validated in onCreate() before this is called.
     * Note: Recording duration defaults to 10 seconds if not explicitly set, so we don't
     * check for it here.
     * 
     * @return true if app is fully configured, false otherwise
     */
    private fun isAppFullyConfigured(): Boolean {
        Log.d("OverlayService", "Checking if app is fully configured")
        
        // Check all required runtime permissions (overlay already verified in onCreate)
        if (!checkAllRequiredPermissions()) {
            Log.w("OverlayService", "App not configured: Missing required permissions")
            return false
        }
        
        // Check if save directory is configured
        // Note: SettingsActivity auto-configures this when first opened, so if it's null
        // the user has never opened the settings/manager UI
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val saveDir = prefs.getString("saveDirectory", null)
        if (saveDir.isNullOrEmpty()) {
            Log.w("OverlayService", "App not configured: Save directory not set (user has not opened settings)")
            return false
        }
        
        Log.d("OverlayService", "App is fully configured")
        return true
    }
    
    private fun handleOverlayMessage(message: String, timeoutMs: Long) {
        Log.d("OverlayService", "Handling overlay message display: $message (timeout: ${timeoutMs}ms)")
        
        // Set flag to prevent recording flow from starting
        isUnconfiguredMode = true
        
        // Cancel TTS timeout since we're not using TTS for this flow
        ttsTimeoutRunnable?.let { handler.removeCallbacks(it) }
        
        // Verify overlay was created successfully
        if (overlayView == null || bubbleLine1 == null) {
            Log.e("OverlayService", "Overlay not created - cannot show message")
            stopSelf()
            return
        }
        
        // Show the message in the overlay
        updateOverlay(message)
        
        // Schedule service stop after specified timeout
        unconfiguredOverlayTimeoutRunnable = Runnable { stopSelfAndFinish() }
        handler.postDelayed(unconfiguredOverlayTimeoutRunnable!!, timeoutMs)
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
            
            // Save recording to database
            saveRecordingToDatabase()

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
    
    private fun saveRecordingToDatabase() {
        val filePath = recordingFilePath ?: return
        val location = currentLocation ?: return
        val fileName = File(filePath).name
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = RecordingDatabase.getDatabase(this@OverlayService)
                val recording = Recording(
                    filename = fileName,
                    filepath = filePath,
                    timestamp = System.currentTimeMillis(),
                    latitude = location.latitude,
                    longitude = location.longitude,
                    v2sStatus = V2SStatus.NOT_STARTED
                )
                db.recordingDao().insertRecording(recording)
                Log.d("OverlayService", "Recording saved to database: $fileName")
            } catch (e: Exception) {
                Log.e("OverlayService", "Failed to save recording to database", e)
                DebugLogger.logError(
                    service = "OverlayService",
                    error = "Failed to save recording to database: ${e.message}",
                    exception = e
                )
            }
        }
    }
    
    private fun finishRecordingProcess() {
        // Show recording saved message
        updateOverlay("Recording saved")

        // Recording complete - all processing handled in Recording Manager
        Log.d("OverlayService", "Recording complete - quitting")
        handler.postDelayed({ stopSelfAndFinish() }, 2000)
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
    <name>${escapeXml(name)}</name>
    <desc>${escapeXml(desc)}</desc>
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
    <name>${escapeXml(name)}</name>
    <desc>${escapeXml(desc)}</desc>
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
            
            if (csvFile.exists()) {
                // Read existing content and check for duplicates
                val existingLines = csvFile.readLines()
                val updatedContent = replaceOrAddCsvEntry(existingLines, date, time, coords, text, googleMapsLink)
                csvFile.writeText(updatedContent)
            } else {
                // Create new CSV file with UTF-8 BOM and header
                val csvContent = buildNewCsvFile(date, time, coords, text, googleMapsLink)
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
        googleMapsLink: String
    ): String {
        val utf8Bom = "\uFEFF"
        val newEntry = buildCsvLine(date, time, coords, text, googleMapsLink)
        
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
        googleMapsLink: String
    ): String {
        val utf8Bom = "\uFEFF"
        val header = buildCsvHeader()
        val entry = buildCsvLine(date, time, coords, text, googleMapsLink)
        return utf8Bom + header + "\n" + entry
    }
    
    private fun buildCsvHeader(): String {
        return "Date,Time,Coordinates,Text,Google Maps link"
    }
    
    private fun buildCsvLine(
        date: String,
        time: String,
        coords: String,
        text: String,
        googleMapsLink: String
    ): String {
        return "${escapeCsv(date)},${escapeCsv(time)},${escapeCsv(coords)},${escapeCsv(text)},${escapeCsv(googleMapsLink)}"
    }
    
    private fun escapeCsv(value: String): String {
        // Escape CSV values: wrap in quotes if contains comma, quote, or newline
        return if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
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
            // Also update the notification so users know what's happening
            updateNotification(text)
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
        
        // Cancel unconfigured overlay timeout if running
        unconfiguredOverlayTimeoutRunnable?.let { handler.removeCallbacks(it) }
        
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
        finishIntent.setPackage(packageName) // Make intent explicit for non-exported receiver
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
        unconfiguredOverlayTimeoutRunnable?.let { handler.removeCallbacks(it) }
        
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows recording status"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Notes Recording")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
