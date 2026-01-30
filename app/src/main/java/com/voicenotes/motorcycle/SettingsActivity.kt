package com.voicenotes.motorcycle

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val FALLBACK_VERSION = "Version 0.0.0-unknown"
    }

    private lateinit var durationValueText: TextView
    private lateinit var durationNumberPicker: NumberPicker
    private lateinit var requestPermissionsButton: MaterialButton
    private lateinit var permissionStatusList: TextView
    private lateinit var quitButton: MaterialButton
    private lateinit var openFolderButton: MaterialButton
    private lateinit var buttonDebugLog: MaterialButton
    private lateinit var appVersionText: TextView

    private val PERMISSIONS_REQUEST_CODE = 200
    private val OVERLAY_PERMISSION_REQUEST_CODE = 201
    private val BATTERY_OPTIMIZATION_REQUEST_CODE = 202

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // Enable Up navigation
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        durationValueText = findViewById(R.id.durationValueText)
        durationNumberPicker = findViewById(R.id.durationNumberPicker)
        requestPermissionsButton = findViewById(R.id.requestPermissionsButton)
        permissionStatusList = findViewById(R.id.permissionStatusList)
        quitButton = findViewById(R.id.quitButton)
        openFolderButton = findViewById(R.id.openFolderButton)
        buttonDebugLog = findViewById(R.id.buttonDebugLog)
        appVersionText = findViewById(R.id.appVersionText)

        // Configure NumberPicker
        durationNumberPicker.minValue = 1
        durationNumberPicker.maxValue = 99
        durationNumberPicker.wrapSelectorWheel = false
        
        // Auto-save when value changes
        durationNumberPicker.setOnValueChangedListener { _, _, newVal ->
            saveDuration()
        }
        
        // Display app version
        appVersionText.text = getAppVersion()

        loadCurrentSettings()

        requestPermissionsButton.setOnClickListener {
            requestAllPermissions()
        }

        quitButton.setOnClickListener {
            finishAffinity()
        }
        
        openFolderButton.setOnClickListener {
            val intent = Intent(this, RecordingManagerActivity::class.java)
            startActivity(intent)
        }
        
        buttonDebugLog.setOnClickListener {
            showDebugLog()
        }
    }

    private fun getAppVersion(): String {
        return try {
            // Try BuildConfig first
            val buildConfigVersion = BuildConfig.VERSION_NAME
            
            if (!buildConfigVersion.isNullOrEmpty() && buildConfigVersion != "null") {
                Log.d("SettingsActivity", "Version source: BuildConfig.VERSION_NAME = $buildConfigVersion")
                return "Version $buildConfigVersion"
            }
            
            // Fallback: Try to get from PackageManager
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            
            if (!versionName.isNullOrEmpty() && versionName != "null") {
                Log.d("SettingsActivity", "Version source: PackageManager.versionName = $versionName")
                return "Version $versionName"
            }
            
            // Final fallback
            Log.d("SettingsActivity", "Version source: FALLBACK_VERSION = $FALLBACK_VERSION")
            FALLBACK_VERSION
            
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error getting version", e)
            Log.d("SettingsActivity", "Version source: Exception fallback = $FALLBACK_VERSION")
            FALLBACK_VERSION
        }
    }

    private fun getDefaultSavePath(): String {
        // Use app-specific external files directory (doesn't require storage permissions)
        // This directory is cleared when the app is uninstalled
        val externalDir = getExternalFilesDir(null)
        return if (externalDir != null) {
            "${externalDir.absolutePath}/VoiceNotes"
        } else {
            // Fallback to internal files directory if external is not available
            "${filesDir.absolutePath}/VoiceNotes"
        }
    }
    
    private fun showDebugLog() {
        val intent = Intent(this, DebugLogActivity::class.java)
        startActivity(intent)
    }

    private fun loadCurrentSettings() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val recordingDuration = prefs.getInt("recordingDuration", 10)
        
        // Always use fixed internal storage path
        val defaultPath = getDefaultSavePath()
        val saveDir = prefs.getString("saveDirectory", null)
        
        // If no directory configured, set it now
        if (saveDir.isNullOrEmpty()) {
            prefs.edit().putString("saveDirectory", defaultPath).apply()
        }
        
        durationValueText.text = "$recordingDuration seconds"
        durationNumberPicker.value = recordingDuration
        
        // Update permission status list
        updatePermissionStatusList()
    }
    
    private fun updatePermissionStatusList() {
        val statusLines = mutableListOf<String>()
        
        // Check microphone permission
        val hasMicrophone = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        statusLines.add(if (hasMicrophone) {
            getString(R.string.permission_granted, getString(R.string.permission_microphone))
        } else {
            getString(R.string.permission_not_granted, getString(R.string.permission_microphone))
        })
        
        // Check location permission
        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        statusLines.add(if (hasLocation) {
            getString(R.string.permission_granted, getString(R.string.permission_location))
        } else {
            getString(R.string.permission_not_granted, getString(R.string.permission_location))
        })
        
        // Check Bluetooth permission
        val hasBluetooth = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        statusLines.add(if (hasBluetooth) {
            getString(R.string.permission_granted, getString(R.string.permission_bluetooth))
        } else {
            getString(R.string.permission_not_granted, getString(R.string.permission_bluetooth))
        })
        
        // Check overlay permission
        val hasOverlay = Settings.canDrawOverlays(this)
        statusLines.add(if (hasOverlay) {
            getString(R.string.permission_granted, getString(R.string.permission_overlay))
        } else {
            getString(R.string.permission_not_granted, getString(R.string.permission_overlay))
        })

        // Check battery optimization
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val hasBatteryExemption = powerManager.isIgnoringBatteryOptimizations(packageName)
        statusLines.add(if (hasBatteryExemption) {
            "✓ Battery optimization (disabled)"
        } else {
            "✗ Battery optimization (enabled - may interrupt recording)"
        })

        permissionStatusList.text = statusLines.joinToString("\n")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            updatePermissionStatusList()
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show()
                // Continue permission flow - check battery optimization
                checkAndRequestBatteryOptimization()
            } else {
                Toast.makeText(this, "Overlay permission is required for the app to work", Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == BATTERY_OPTIMIZATION_REQUEST_CODE) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(this, "Battery optimization disabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Background recording may be interrupted", Toast.LENGTH_LONG).show()
            }
            updatePermissionStatusList()
        }
    }

    private fun saveDuration() {
        val duration = durationNumberPicker.value

        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        prefs.edit().putInt("recordingDuration", duration).apply()

        durationValueText.text = "$duration seconds"
        Toast.makeText(this, getString(R.string.duration_saved, duration), Toast.LENGTH_SHORT).show()
    }

    private fun requestAllPermissions() {
        // First, request runtime permissions
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSIONS_REQUEST_CODE)
        } else {
            // All runtime permissions granted, check overlay and storage permissions
            checkAndRequestOverlayPermission()
        }
    }
    
    private fun checkAndRequestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.overlay_permission_required)
                .setMessage(R.string.overlay_permission_message)
                .setPositiveButton("Grant") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
                }
                .setNegativeButton("Cancel") { _, _ ->
                    updatePermissionStatusList()
                }
                .show()
        } else {
            // Check battery optimization permission next
            checkAndRequestBatteryOptimization()
        }
    }

    private fun checkAndRequestBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = packageName

        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Battery Optimization")
                .setMessage("To ensure reliable background recording, this app needs unrestricted battery access.\n\n" +
                        "This will prevent Android from stopping the recording when the screen is off or the app is in the background.")
                .setPositiveButton("Grant") { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:$packageName")
                        startActivityForResult(intent, BATTERY_OPTIMIZATION_REQUEST_CODE)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Unable to open battery settings", Toast.LENGTH_SHORT).show()
                        updatePermissionStatusList()
                    }
                }
                .setNegativeButton("Skip") { _, _ ->
                    Toast.makeText(this, "Background recording may be interrupted", Toast.LENGTH_LONG).show()
                    updatePermissionStatusList()
                }
                .show()
        } else {
            Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
            updatePermissionStatusList()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            updatePermissionStatusList()
            
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                Toast.makeText(this, "Some permissions were denied", Toast.LENGTH_LONG).show()
            }
            
            // Always check overlay permission after runtime permissions are handled
            checkAndRequestOverlayPermission()
        }
    }
    
    override fun onResume() {
        super.onResume()
        loadCurrentSettings()
    }
    
    // Handle Up navigation
    // Note: This handles the Up/back button in the action bar
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    // Handle home/up button in options menu
    // Note: This provides additional compatibility for older Android versions
    // where onSupportNavigateUp() might not be called
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
