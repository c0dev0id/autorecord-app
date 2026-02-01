package com.voicenotes.main

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.location.Location
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
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
import com.voicenotes.main.database.Recording
import com.voicenotes.main.database.RecordingDatabase
import com.voicenotes.main.database.V2SStatus
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Overlay service for Voice Notes recording functionality.
 * 
 * This service is responsible for:
 * 1. Recording Lifecycle: Manages the complete recording lifecycle including:
 *    - GPS location acquisition
 *    - Audio recording with MediaRecorder
 *    - Visual feedback via overlay bubble
 *    - Text-to-speech announcements
 *    - Recording countdown and automatic stop
 *    - Saving recordings with metadata to database
 * 
 * 2. Bluetooth Audio Integration: Handles Bluetooth SCO (Synchronous Connection-Oriented) setup
 *    for routing audio to/from Bluetooth devices.
 * 
 * The service never launches activities or switches to any UI screen. It operates entirely in
 * the background with only overlay bubble feedback. All configuration and permission validation
 * is performed by MainActivity before this service is started.
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
    
    /**
     * Creates a localized context based on the app_language SharedPreference.
     * This is necessary because Services don't automatically get the updated locale
     * when AppCompatDelegate.setApplicationLocales() is called.
     */
    private fun getLocalizedContext(): Context {
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val appLangPref = prefs.getString("app_language", "system") ?: "system"
        
        if (appLangPref == "system" || appLangPref.isBlank()) {
            return this // Use default context for system language
        }
        
        val locale = java.util.Locale.forLanguageTag(appLangPref)
        val config = resources.configuration
        config.setLocale(locale)
        return createConfigurationContext(config)
    }
    
    /**
     * Gets a localized string resource based on the user's language preference.
     * Uses getLocalizedContext() to ensure the correct locale is used.
     */
    private fun getLocalizedString(resId: Int): String {
        return getLocalizedContext().getString(resId)
    }
    
    /**
     * Gets a localized formatted string resource based on the user's language preference.
     * Uses getLocalizedContext() to ensure the correct locale is used.
     */
    private fun getLocalizedString(resId: Int, vararg formatArgs: Any): String {
        return getLocalizedContext().getString(resId, *formatArgs)
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
                error = getLocalizedString(R.string.overlay_permission_missing)
            )
            stopSelf()
            return
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Create overlay first
        createOverlay()
        
        // Initialize TTS (but only if permissions are present will we use it)
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
        
        updateOverlay(getLocalizedString(R.string.acquiring_location))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        // Intent extra key "additionalDuration" kept for backward compatibility
        // Variable named resetDuration to reflect actual behavior (reset, not add)
        val resetDuration = intent?.getIntExtra("additionalDuration", -1) ?: -1
        
        if (resetDuration > 0) {
            // This is an extension request - reset timer to the duration from the intent
            extendRecordingDuration(resetDuration)
            return START_NOT_STICKY
        }
        
        // Normal startup flow - wait for TTS initialization to start recording
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        ttsTimeoutRunnable?.let { handler.removeCallbacks(it) }
        
        if (status == TextToSpeech.SUCCESS) {
            // Minimal change: prefer app language preference stored in SharedPreferences ("AppPrefs" -> "app_language"),
            // fall back to app/system locale when preference == "system", and finally fall back to device default / Locale.US.
            val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val appLangPref = prefs.getString("app_language", "system") ?: "system"

            val desiredLocale: Locale = if (appLangPref == "system" || appLangPref.isBlank()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) resources.configuration.locales.get(0)
                else @Suppress("DEPRECATION") resources.configuration.locale
            } else {
                Locale.forLanguageTag(appLangPref)
            }

            Log.d("OverlayService", "TTS: requested locale from prefs: $appLangPref -> $desiredLocale")

            val setResult = textToSpeech?.setLanguage(desiredLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            isTtsInitialized = (setResult != TextToSpeech.LANG_MISSING_DATA && setResult != TextToSpeech.LANG_NOT_SUPPORTED)

            if (!isTtsInitialized) {
                // Try device default then Locale.US as minimal fallbacks
                val deviceResult = textToSpeech?.setLanguage(Locale.getDefault()) ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (deviceResult != TextToSpeech.LANG_MISSING_DATA && deviceResult != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isTtsInitialized = true
                    Log.w("OverlayService", "TTS: falling back to device locale")
                } else {
                    val finalResult = textToSpeech?.setLanguage(Locale.US) ?: TextToSpeech.LANG_NOT_SUPPORTED
                    isTtsInitialized = (finalResult != TextToSpeech.LANG_MISSING_DATA && finalResult != TextToSpeech.LANG_NOT_SUPPORTED)
                    if (isTtsInitialized) Log.w("OverlayService", "TTS: falling back to Locale.US")
                }
            }
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
        updateOverlay(getLocalizedString(R.string.acquiring_location))
        acquireLocation()
    }

    private fun acquireLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            updateOverlay(getLocalizedString(R.string.location_permission_not_granted))
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
                updateOverlay(getLocalizedString(R.string.using_last_known_location))
                DebugLogger.logInfo(
                    service = "OverlayService",
                    message = "Using last known location: ${location.latitude}, ${location.longitude}"
                )
                handler.postDelayed({
                    onLocationAcquired()
                }, 1000)
            } else {
                updateOverlay(getLocalizedString(R.string.location_unavailable_enable_gps))
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
            updateOverlay(getLocalizedString(R.string.location_acquired_coords, coords))
        }
        
        // First, prepare the MediaRecorder (but don't start yet)
        prepareRecording()
        
        // THEN speak announcements
        speakText(getLocalizedString(R.string.location_acquired)) {
            Log.d("OverlayService", "TTS: 'Location acquired' completed")
            speakText(getLocalizedString(R.string.recording_started)) {
                Log.d("OverlayService", "TTS: 'Recording started' completed")
                // ONLY start recording AFTER TTS completes
                startRecordingAndCountdown()
            }
        }
    }

    private fun onLocationFailed() {
        updateOverlay(getLocalizedString(R.string.location_failed))
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

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e("OverlayService", "TTS: onError (deprecated) for: '$text'")
                handler.post { onComplete() }
            }

            // Use the newer onError with errorCode (API 21+) for detailed error information
            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e("OverlayService", "TTS: onError for: '$text', errorCode: $errorCode")
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
                updateOverlay(getLocalizedString(R.string.location_not_available))
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
            updateOverlay(getLocalizedString(R.string.recording_failed_invalid_state))
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
                e.message?.contains("start failed") == true -> getLocalizedString(R.string.recording_failed_mic_in_use)
                e.message?.contains("audio") == true -> getLocalizedString(R.string.recording_failed_audio_unavailable)
                else -> getLocalizedString(R.string.recording_failed_generic, e.message ?: "Unknown error")
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
            updateOverlay(getLocalizedString(R.string.recording_failed_generic, e.message ?: "Unknown error"))
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
            updateOverlay(getLocalizedString(R.string.recording_failed_invalid_state))
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
                e.message?.contains("start failed") == true -> getLocalizedString(R.string.recording_failed_mic_in_use)
                e.message?.contains("audio") == true -> getLocalizedString(R.string.recording_failed_audio_unavailable)
                else -> getLocalizedString(R.string.recording_failed_generic, e.message ?: "Unknown error")
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
            updateOverlay(getLocalizedString(R.string.recording_failed_generic, e.message ?: "Unknown error"))
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
            
            // Use modern setCommunicationDevice API on API 31+ (replaces deprecated startBluetoothSco)
            return setupBluetoothCommunicationDevice(audioManager)
        }
        
        // For API 26-30, use the legacy SCO methods (deprecated but only option for these API levels)
        @Suppress("DEPRECATION")
        return if (audioManager.isBluetoothScoAvailableOffCall) {
            Log.d("OverlayService", "Bluetooth SCO available (legacy), starting...")
            @Suppress("DEPRECATION")
            audioManager.startBluetoothSco()
            
            // Store timeout reference
            bluetoothScoTimeoutRunnable = Runnable {
                @Suppress("DEPRECATION")
                if (!audioManager.isBluetoothScoOn) {
                    Log.d("OverlayService", "Bluetooth SCO timeout (legacy)")
                }
            }
            handler.postDelayed(bluetoothScoTimeoutRunnable!!, 5000)
            
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        } else {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        }
    }
    
    /**
     * Set up Bluetooth communication device using the modern API (API 31+).
     * Uses setCommunicationDevice instead of deprecated startBluetoothSco.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun setupBluetoothCommunicationDevice(audioManager: AudioManager): Int {
        try {
            val devices = audioManager.availableCommunicationDevices
            val scoDevice = devices.find { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            
            if (scoDevice != null) {
                val success = audioManager.setCommunicationDevice(scoDevice)
                if (success) {
                    Log.d("OverlayService", "Bluetooth communication device set successfully")
                } else {
                    Log.w("OverlayService", "Failed to set Bluetooth communication device")
                }
            } else {
                Log.d("OverlayService", "No Bluetooth SCO device available")
            }
        } catch (e: Exception) {
            Log.e("OverlayService", "Error setting up Bluetooth communication device", e)
        }
        
        return MediaRecorder.AudioSource.VOICE_COMMUNICATION
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
                    updateOverlay(getLocalizedString(R.string.recording_countdown, remainingSeconds))
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
        updateOverlay(getLocalizedString(R.string.recording_extended, remainingSeconds))
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

            updateOverlay(getLocalizedString(R.string.recording_stopped_msg))
            
            // Save recording to database
            saveRecordingToDatabase()

            // Speak "Recording stopped"
            speakText(getLocalizedString(R.string.recording_stopped)) {
                finishRecordingProcess()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            updateOverlay(getLocalizedString(R.string.recording_failed))
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
        updateOverlay(getLocalizedString(R.string.recording_saved))

        // Recording complete - all processing handled in Recording Manager
        Log.d("OverlayService", "Recording complete - quitting")
        handler.postDelayed({ stopSelfAndFinish() }, 2000)
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
        
        // Clean up Bluetooth audio routing
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Use modern API to clear communication device
            audioManager.clearCommunicationDevice()
            Log.d("OverlayService", "Cleared communication device")
        } else {
            // Use legacy API for older devices
            @Suppress("DEPRECATION")
            if (audioManager.isBluetoothScoOn) {
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
            }
        }
        
        // Send broadcast to finish MainActivity
        val finishIntent = Intent("com.voicenotes.main.FINISH_ACTIVITY")
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
