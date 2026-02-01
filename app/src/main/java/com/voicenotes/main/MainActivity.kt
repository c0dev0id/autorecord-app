package com.voicenotes.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.voicenotes.main.database.RecordingMigration
import kotlinx.coroutines.launch

/**
 * Main activity for Voice Notes app.
 * 
 * This activity has two distinct launch modes:
 * 
 * 1. Normal Launch (Headless Mode): When launched from the app icon or background trigger,
 *    performs a permission guard check for required runtime permissions (RECORD_AUDIO, 
 *    ACCESS_FINE_LOCATION, BLUETOOTH_CONNECT). If any are missing, logs and finishes immediately
 *    without starting services or showing UI. If all permissions are granted, starts OverlayService 
 *    in the background via ContextCompat.startForegroundService and finishes immediately. No UI 
 *    is shown.
 * 
 * 2. Explicit UI Mode: When launched with EXTRA_SHOW_UI=true, shows UI for permission management 
 *    and configuration. This is triggered via long-press "Manage" action or VN Manager app.
 * 
 * Separation of Responsibilities:
 * - MainActivity: Handles explicit UI requests (EXTRA_SHOW_UI=true) for settings/permission 
 *   management. On normal (headless) launches, performs a permission guard check and finishes 
 *   immediately if any required runtime permissions are missing. Does not check for "unconfigured" 
 *   state or display configuration warnings.
 * 
 * - OverlayService: Owns all other configuration/permission validation on service start (overlay 
 *   permission, save directory, etc.). Displays brief overlay messages if unconfigured, then 
 *   auto-stops. MainActivity only performs the runtime permission guard check on normal launches.
 * 
 * This design ensures the app remains truly headless on normal launch with no UI footprint,
 * while preventing crashes from missing required runtime permissions, and still providing clear 
 * feedback about other configuration issues via overlay bubbles.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_SHOW_UI = "show_ui"
        
        // Transient UI configuration
        private const val UNCONFIGURED_UI_AUTO_CLOSE_DURATION_MS = 10000L
        private const val COUNTDOWN_INTERVAL_MS = 1000L
    }

    private val PERMISSIONS_REQUEST_CODE = 100
    
    private lateinit var finishReceiver: FinishActivityReceiver
    private var isReceiverRegistered = false
    
    private var shouldShowUI = false
    private var countDownTimer: CountDownTimer? = null
    
    // Activity Result launcher for overlay permission (replaces deprecated startActivityForResult)
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // The result code is not meaningful for overlay permission - check the actual permission state
        Log.d(TAG, "Overlay permission activity returned")
        if (Settings.canDrawOverlays(this)) {
            Log.d(TAG, "Overlay permission granted after settings")
            startRecordingProcess()
        } else {
            Log.d(TAG, "Overlay permission still not granted")
            Toast.makeText(this, getString(R.string.overlay_permission_is_required), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // Required runtime permissions for headless mode
    // Note: BLUETOOTH_CONNECT is only required on API 31+ (Android 12+)
    // Using lazy initialization to avoid creating new arrays on each access
    private val requiredPermissions: Array<String> by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    /**
     * Logs intent details for debugging both UI and headless launch modes.
     * Helps troubleshoot intent routing and decision making.
     */
    private fun logIntentDetails(context: String, intent: Intent?, showingUI: Boolean) {
        if (intent == null) {
            Log.d(TAG, "$context: Intent is null, showingUI=$showingUI")
            return
        }
        
        val action = intent.action ?: "null"
        val categories = intent.categories?.joinToString(", ") ?: "none"
        val extras = intent.extras?.let { bundle ->
            bundle.keySet().joinToString(", ") { key ->
                // For debug logging, handle the types this app commonly uses in intents
                // getString returns null for non-string types, so we check boolean explicitly
                val value = bundle.getString(key) 
                    ?: if (bundle.containsKey(key)) bundle.getBoolean(key).toString() else "(unknown)"
                "$key=$value"
            }
        } ?: "none"
        
        Log.d(TAG, "$context: Intent details - " +
                "action=$action, " +
                "categories=$categories, " +
                "extras=[$extras], " +
                "showingUI=$showingUI")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize AppContextHolder for DebugLogger
        AppContextHolder.context = applicationContext

        // Check if we should show UI (explicit request via EXTRA_SHOW_UI only)
        shouldShowUI = intent?.getBooleanExtra(EXTRA_SHOW_UI, false) ?: false
        
        // Log intent details for debugging both UI and headless modes
        logIntentDetails("onCreate", intent, shouldShowUI)
        
        // For normal launch (not explicit UI request), guard permissions and start service
        // IMPORTANT: We check required runtime permissions here as a guard. If any are missing,
        // we show a transient UI that auto-closes after 10 seconds with option to open settings.
        // OverlayService owns all other configuration checks (overlay permission, save directory).
        if (!shouldShowUI) {
            Log.d(TAG, "Headless mode: Checking required runtime permissions")
            
            // Permission guard: Check if all required runtime permissions are granted
            val missingPermissions = requiredPermissions.filter { permission ->
                ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
            }
            
            if (missingPermissions.isNotEmpty()) {
                Log.w(TAG, "Headless mode blocked: Missing required permissions: ${missingPermissions.joinToString()}")
                Log.w(TAG, "Showing transient unconfigured UI with 10-second auto-close")
                
                // Show transient UI with auto-close
                try {
                    setContentView(R.layout.activity_unconfigured)
                    
                    val countdownTextView = findViewById<TextView>(R.id.unconfiguredCountdown)
                    val settingsButton = findViewById<Button>(R.id.openSettingsButton)
                    
                    // Start 10-second countdown timer
                    countDownTimer = object : CountDownTimer(UNCONFIGURED_UI_AUTO_CLOSE_DURATION_MS, COUNTDOWN_INTERVAL_MS) {
                        override fun onTick(millisUntilFinished: Long) {
                            val secondsLeft = (millisUntilFinished / 1000).toInt()
                            countdownTextView.text = getString(R.string.auto_closing_countdown, secondsLeft)
                        }
                        
                        override fun onFinish() {
                            Log.d(TAG, "Countdown finished, finishing MainActivity")
                            finish()
                        }
                    }.start()
                    
                    // Wire settings button to launch SettingsActivity
                    // Note: Not using Intent.FLAG_ACTIVITY_NEW_TASK as we're launching from Activity context
                    settingsButton.setOnClickListener {
                        Log.d(TAG, "Settings button clicked, launching SettingsActivity")
                        countDownTimer?.cancel()
                        
                        val settingsIntent = Intent(this, SettingsActivity::class.java)
                        settingsIntent.putExtra(EXTRA_SHOW_UI, true)
                        startActivity(settingsIntent)
                        
                        finish()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to inflate transient UI, finishing immediately", e)
                    finish()
                }
                return
            }
            
            Log.d(TAG, "All required runtime permissions granted, starting OverlayService")
            
            // Check if recording is currently active
            val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
            if (prefs.getBoolean("isCurrentlyRecording", false)) {
                Log.d(TAG, "Recording in progress - extending recording")
                // Start service with extension request
                val serviceIntent = Intent(this, OverlayService::class.java)
                val configuredDuration = prefs.getInt("recordingDuration", 10)
                serviceIntent.putExtra("additionalDuration", configuredDuration)
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                // Normal startup - start service
                val serviceIntent = Intent(this, OverlayService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
            }
            
            finish()
            return
        }
        
        // Below this point: explicit UI request only (EXTRA_SHOW_UI=true)
        Log.d(TAG, "UI mode: Showing configuration UI")
        
        // Defensive UI inflation with error handling
        try {
            setContentView(R.layout.activity_main)

            // Register broadcast receiver
            finishReceiver = FinishActivityReceiver(this)
            val filter = IntentFilter("com.voicenotes.main.FINISH_ACTIVITY")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(finishReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(finishReceiver, filter)
            }
            isReceiverRegistered = true

            // Check overlay permission and start recording
            Log.d(TAG, "Checking overlay permission")
            checkOverlayPermission()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inflate UI or initialize activity", e)
            Toast.makeText(this, getString(R.string.failed_to_initialize_ui, e.message), Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // Log intent details for debugging
        logIntentDetails("onNewIntent", intent, false)
        
        // Keep existing behavior: extend recording if currently recording
        // Do NOT trigger UI inflation or other configuration flows from new intents
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        if (prefs.getBoolean("isCurrentlyRecording", false)) {
            Log.d(TAG, "onNewIntent: Recording in progress - extending")
            extendRecording()
        } else {
            Log.d(TAG, "onNewIntent: No recording in progress - ignoring")
        }
    }

    private fun checkOverlayPermission() {
        Log.d(TAG, "checkOverlayPermission() called")
        
        // Check and run migration if needed (best-effort, doesn't block user)
        val migration = RecordingMigration(this)
        if (!migration.isMigrationComplete()) {
            lifecycleScope.launch {
                try {
                    val count = migration.migrateExistingRecordings()
                    if (count > 0) {
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.migrated_recordings, count, if (count > 1) "s" else ""),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Migration failed", e)
                    DebugLogger.logError(
                        service = "MainActivity",
                        error = "Migration failed: ${e.message}",
                        exception = e
                    )
                }
            }
        }
        
        if (!Settings.canDrawOverlays(this)) {
            Log.d(TAG, "Overlay permission NOT granted, showing dialog")
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.overlay_permission_required)
                .setMessage(R.string.overlay_permission_message)
                .setPositiveButton(getString(R.string.ok)) { _, _ ->
                    Log.d(TAG, "User clicked OK, launching overlay permission screen")
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    overlayPermissionLauncher.launch(intent)
                }
                .setCancelable(false)
                .show()
        } else {
            Log.d(TAG, "Overlay permission granted, starting recording process")
            startRecordingProcess()
        }
    }

    private fun startRecordingProcess() {
        Log.d(TAG, "startRecordingProcess() called")

        if (!checkPermissions()) {
            Log.d(TAG, "Permissions not granted, requesting")
            requestPermissions()
            return
        }

        Log.d(TAG, "All permissions granted")

        // Check if already recording - if so, extend instead
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        if (prefs.getBoolean("isCurrentlyRecording", false)) {
            Log.d(TAG, "Already recording, extending")
            extendRecording()
            return
        }

        // Start overlay service
        Log.d(TAG, "Starting OverlayService")
        val serviceIntent = Intent(this, OverlayService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        // Immediately finish - don't keep MainActivity around
        Log.d(TAG, "Finishing MainActivity immediately")
        finish()
    }
    
    private fun extendRecording() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val configuredDuration = prefs.getInt("recordingDuration", 10)

        // Update the initial duration to the configured duration (reset, don't add)
        prefs.edit().apply {
            putInt("initialRecordingDuration", configuredDuration)
            apply()
        }

        // Send intent to OverlayService with configured duration to reset to
        // Note: Intent extra key is "additionalDuration" for backward compatibility
        val serviceIntent = Intent(this, OverlayService::class.java)
        serviceIntent.putExtra("additionalDuration", configuredDuration)
        ContextCompat.startForegroundService(this, serviceIntent)

        // Finish immediately - don't keep MainActivity around
        Log.d(TAG, "Finishing MainActivity after extending recording")
        finish()
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
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.permission_required)
                    .setMessage(R.string.grant_permissions)
                    .setPositiveButton(getString(R.string.ok)) { _, _ -> finish() }
                    .show()
            }
        }
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        if (isReceiverRegistered) {
            unregisterReceiver(finishReceiver)
            isReceiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume() called")
    }
}
