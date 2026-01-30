package com.voicenotes.motorcycle

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle

import android.provider.Settings
import android.util.Log

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.voicenotes.motorcycle.database.RecordingMigration
import kotlinx.coroutines.launch

/**
 * Main activity for Voice Notes app.
 * 
 * This activity has two distinct launch modes:
 * 
 * 1. Normal Launch (Headless Mode): When launched from the app icon or background trigger,
 *    immediately starts OverlayService in the background via ContextCompat.startForegroundService
 *    and finishes. No UI is shown. No configuration or permission checks are performed here - 
 *    those are handled entirely by OverlayService on startup.
 * 
 * 2. Explicit UI Mode: When launched with EXTRA_SHOW_UI=true, shows UI for permission management 
 *    and configuration. This is triggered via long-press "Manage" action or VN Manager app.
 * 
 * Separation of Responsibilities:
 * - MainActivity: Only handles explicit UI requests (EXTRA_SHOW_UI=true) for settings/permission 
 *   management. Never checks for "unconfigured" state or displays configuration warnings.
 * 
 * - OverlayService: Owns all configuration/permission validation on service start. Displays brief 
 *   overlay messages if unconfigured, then auto-stops. MainActivity never performs unconfigured 
 *   detection on normal launches.
 * 
 * This design ensures the app remains truly headless on normal launch with no UI footprint,
 * while still providing clear feedback about configuration issues via overlay bubbles.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_SHOW_UI = "show_ui"
    }

    private val PERMISSIONS_REQUEST_CODE = 100
    private val OVERLAY_PERMISSION_REQUEST_CODE = 101
    
    private lateinit var finishReceiver: FinishActivityReceiver
    private var isReceiverRegistered = false
    
    private var shouldShowUI = false

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_CONNECT
    )

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
        val extras = intent.extras?.keySet()?.joinToString(", ") { key ->
            "$key=${intent.extras?.get(key)}"
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
        
        // For normal launch (not explicit UI request), start service immediately and finish
        // IMPORTANT: No configuration or permission checks are done here. OverlayService
        // owns all unconfigured detection and displays appropriate feedback if needed.
        if (!shouldShowUI) {
            Log.d(TAG, "Headless mode: Starting OverlayService and finishing immediately")
            
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
            val filter = IntentFilter("com.voicenotes.motorcycle.FINISH_ACTIVITY")
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
            Toast.makeText(this, "Failed to initialize UI: ${e.message}", Toast.LENGTH_LONG).show()
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
                                "Migrated $count existing recording${if (count > 1) "s" else ""}",
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
            AlertDialog.Builder(this)
                .setTitle(R.string.overlay_permission_required)
                .setMessage(R.string.overlay_permission_message)
                .setPositiveButton("OK") { _, _ ->
                    Log.d(TAG, "User clicked OK, launching overlay permission screen")
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
                }
                .setCancelable(false)
                .show()
        } else {
            Log.d(TAG, "Overlay permission granted, starting recording process")
            startRecordingProcess()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult() requestCode: $requestCode, resultCode: $resultCode")
        
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                Log.d(TAG, "Overlay permission granted after settings")
                startRecordingProcess()
            } else {
                Log.d(TAG, "Overlay permission still not granted")
                Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_LONG).show()
                finish()
            }
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
        startService(serviceIntent)

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
        startService(serviceIntent)

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
                AlertDialog.Builder(this)
                    .setTitle(R.string.permission_required)
                    .setMessage(R.string.grant_permissions)
                    .setPositiveButton("OK") { _, _ -> finish() }
                    .show()
            }
        }
    }

    override fun onDestroy() {
        if (isReceiverRegistered) {
            unregisterReceiver(finishReceiver)
            isReceiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume() called")

        if (Settings.canDrawOverlays(this)) {
            Log.d(TAG, "Ready to record in onResume")
            // Don't auto-start, just update UI
        }
    }
}
