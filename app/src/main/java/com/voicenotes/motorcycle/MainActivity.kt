package com.voicenotes.motorcycle

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.voicenotes.motorcycle.database.RecordingMigration
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_SHOW_UI = "show_ui"
    }

    private lateinit var infoText: TextView
    private lateinit var progressBar: ProgressBar

    private val PERMISSIONS_REQUEST_CODE = 100
    private val OVERLAY_PERMISSION_REQUEST_CODE = 101
    
    private lateinit var finishReceiver: FinishActivityReceiver
    private var isReceiverRegistered = false
    
    private var shouldShowUI = false
    
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val initializationTimeout = Runnable {
        Log.e(TAG, "Initialization timeout - app stuck!")
        runOnUiThread {
            progressBar.visibility = View.GONE
            infoText.text = "Initialization failed. Please try again."
            
            AlertDialog.Builder(this)
                .setTitle("Initialization Error")
                .setMessage("The app failed to initialize. Please restart and try again.")
                .setPositiveButton("Restart") { _, _ ->
                    val intent = Intent(this, SettingsActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                .setCancelable(false)
                .show()
        }
    }

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate() called")
        
        // Initialize AppContextHolder for DebugLogger
        AppContextHolder.context = applicationContext

        // Check if we should show UI (explicit request from user)
        shouldShowUI = intent?.getBooleanExtra(EXTRA_SHOW_UI, false) ?: false
        
        // Check if user is coming from settings or explicitly opening the app UI
        val fromSettings = intent?.getBooleanExtra("fromSettings", false) ?: false

        // Determine if this is an explicit UI request
        val explicitUIRequest = shouldShowUI || fromSettings
        
        // For normal launch (not explicit UI request), start service immediately and finish
        if (!explicitUIRequest) {
            Log.d(TAG, "Normal launch (headless mode) - starting OverlayService and finishing")
            
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
        
        // Below this point: explicit UI request only (settings/manager flow)
        Log.d(TAG, "Explicit UI request - showing configuration UI")
        
        // Show UI for setup/permissions
        setContentView(R.layout.activity_main)
        
        infoText = findViewById(R.id.infoText)
        progressBar = findViewById(R.id.progressBar)

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

        // Show "Initializing..." message
        Log.d(TAG, "Showing initializing message")
        infoText.text = getString(R.string.initializing)
        progressBar.visibility = View.VISIBLE
        
        // Set 10-second timeout for initialization
        timeoutHandler.postDelayed(initializationTimeout, 10000)

        // Check overlay permission and start recording
        Log.d(TAG, "Checking overlay permission")
        checkOverlayPermission()
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // Check if recording is currently active
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        if (prefs.getBoolean("isCurrentlyRecording", false)) {
            extendRecording()
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
        cancelInitializationTimeout() // Cancel timeout once we're progressing

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
    
    private fun cancelInitializationTimeout() {
        timeoutHandler.removeCallbacks(initializationTimeout)
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
        // Cancel initialization timeout to prevent memory leaks
        cancelInitializationTimeout()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume() called")

        // Update UI status
        progressBar.visibility = View.GONE
        infoText.text = "Ready"

        if (Settings.canDrawOverlays(this)) {
            Log.d(TAG, "Ready to record in onResume")
            // Don't auto-start, just update UI
        }
    }
}
