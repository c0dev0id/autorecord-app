package com.voicenotes.motorcycle

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var statusText: TextView
    private lateinit var infoText: TextView
    private lateinit var progressBar: ProgressBar

    private val PERMISSIONS_REQUEST_CODE = 100
    private val OVERLAY_PERMISSION_REQUEST_CODE = 101
    
    private lateinit var finishReceiver: FinishActivityReceiver
    
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

    private val requiredPermissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        // Add storage permissions for public Music directory
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ needs MANAGE_EXTERNAL_STORAGE for Music directory
            // This will be handled separately with ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
        } else {
            // Android 10 and below need WRITE_EXTERNAL_STORAGE
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate() called")
        setContentView(R.layout.activity_main)
        
        // Initialize AppContextHolder for DebugLogger
        AppContextHolder.context = applicationContext

        statusText = findViewById(R.id.statusText)
        infoText = findViewById(R.id.infoText)
        progressBar = findViewById(R.id.progressBar)

        // Register broadcast receiver
        finishReceiver = FinishActivityReceiver(this)
        val filter = IntentFilter("com.voicenotes.motorcycle.FINISH_ACTIVITY")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(finishReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(finishReceiver, filter)
        }

        // Show "Initializing..." message
        Log.d(TAG, "Showing initializing message")
        infoText.text = getString(R.string.initializing)
        progressBar.visibility = View.VISIBLE
        
        // Set 10-second timeout for initialization
        timeoutHandler.postDelayed(initializationTimeout, 10000)
        
        // Check if first run
        if (isFirstRun()) {
            Log.d(TAG, "First run detected, showing setup dialog")
            showSetupDialog()
        } else {
            // Check overlay permission
            Log.d(TAG, "Not first run, checking overlay permission")
            checkOverlayPermission()
        }
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

    private fun isFirstRun(): Boolean {
        Log.d(TAG, "isFirstRun() called")
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val saveDir = prefs.getString("saveDirectory", null)
        
        Log.d(TAG, "Save directory from prefs: $saveDir")
        
        // Always use fixed internal storage path
        val defaultPath = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MUSIC
        ).absolutePath + "/VoiceNotes"
        
        // If no directory configured, set it now
        if (saveDir.isNullOrEmpty()) {
            Log.d(TAG, "No directory configured, setting default: $defaultPath")
            prefs.edit().putString("saveDirectory", defaultPath).apply()
            
            // Try to create directory
            try {
                val dir = File(defaultPath)
                if (!dir.exists()) {
                    val created = dir.mkdirs()
                    Log.d(TAG, "Created directory: $created")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create directory", e)
            }
        }
        
        // Directory should now be configured
        val finalSaveDir = prefs.getString("saveDirectory", null)
        val dir = File(finalSaveDir ?: defaultPath)
        
        Log.d(TAG, "Final save directory: ${dir.absolutePath}")
        
        // Check if directory exists or can be created
        if (!dir.exists()) {
            Log.d(TAG, "Directory doesn't exist, attempting to create")
            try {
                dir.mkdirs()
            } catch (e: Exception) {
                Log.e(TAG, "Cannot create directory", e)
            }
        }
        
        val permissionsMissing = !checkPermissions()
        Log.d(TAG, "Permissions missing: $permissionsMissing")
        
        return permissionsMissing
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
    
    private fun checkOverlayPermission() {
        Log.d(TAG, "checkOverlayPermission() called")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
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
        
        // Minimize this activity to background
        Log.d(TAG, "Moving task to back")
        moveTaskToBack(true)
    }
    
    private fun cancelInitializationTimeout() {
        timeoutHandler.removeCallbacks(initializationTimeout)
    }
    
    private fun extendRecording() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val startTime = prefs.getLong("recordingStartTime", 0)
        val initialDuration = prefs.getInt("initialRecordingDuration", 10)
        val configuredDuration = prefs.getInt("recordingDuration", 10)
        
        // Calculate elapsed time in seconds
        val elapsed = (System.currentTimeMillis() - startTime) / 1000
        val remaining = maxOf(0, initialDuration - elapsed.toInt())
        
        // Calculate new extended duration for tracking
        val extendedDuration = remaining + configuredDuration
        
        // Update the initial duration for this recording session (keep the original start time)
        prefs.edit().apply {
            putInt("initialRecordingDuration", extendedDuration)
            apply()
        }
        
        // Send intent to OverlayService with configured duration to add
        val serviceIntent = Intent(this, OverlayService::class.java)
        serviceIntent.putExtra("extendedDuration", configuredDuration)
        startService(serviceIntent)
        
        // Move to background again
        moveTaskToBack(true)
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
        try {
            unregisterReceiver(finishReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        // Cancel initialization timeout to prevent memory leaks
        cancelInitializationTimeout()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume() called")
        
        // Check if coming back from settings
        if (!isFirstRun()) {
            Log.d(TAG, "Setup complete in onResume, checking overlay")
            progressBar.visibility = View.GONE
            infoText.text = "Ready"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                Log.d(TAG, "Ready to record in onResume")
                // Don't auto-start, just update UI
            }
        }
    }
}
