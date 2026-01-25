package com.voicenotes.motorcycle

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var durationValueText: TextView
    private lateinit var durationEditText: EditText
    private lateinit var setDurationButton: Button
    private lateinit var requestPermissionsButton: Button
    private lateinit var permissionStatusList: TextView
    private lateinit var quitButton: Button
    private lateinit var openFolderButton: Button
    private lateinit var buttonDebugLog: Button
    private lateinit var appVersionText: TextView
    
    private lateinit var checkboxAddOsmNote: CheckBox
    private lateinit var buttonOsmAccount: Button
    private lateinit var textOsmAccountStatus: TextView

    private lateinit var oauthManager: OsmOAuthManager
    private lateinit var oauthLauncher: ActivityResultLauncher<Intent>

    private val PERMISSIONS_REQUEST_CODE = 200
    private val OVERLAY_PERMISSION_REQUEST_CODE = 201

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        durationValueText = findViewById(R.id.durationValueText)
        durationEditText = findViewById(R.id.durationEditText)
        setDurationButton = findViewById(R.id.setDurationButton)
        requestPermissionsButton = findViewById(R.id.requestPermissionsButton)
        permissionStatusList = findViewById(R.id.permissionStatusList)
        quitButton = findViewById(R.id.quitButton)
        openFolderButton = findViewById(R.id.openFolderButton)
        buttonDebugLog = findViewById(R.id.buttonDebugLog)
        appVersionText = findViewById(R.id.appVersionText)
        
        checkboxAddOsmNote = findViewById(R.id.checkboxAddOsmNote)
        buttonOsmAccount = findViewById(R.id.buttonOsmAccount)
        textOsmAccountStatus = findViewById(R.id.textOsmAccountStatus)
        
        oauthManager = OsmOAuthManager(this)
        
        // Display app version
        appVersionText.text = getAppVersion()
        
        // Setup OAuth launcher
        oauthLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                oauthManager.handleOAuthResponse(
                    result.data!!,
                    onSuccess = { username ->
                        runOnUiThread {
                            updateOsmAccountUI(username)
                            Toast.makeText(this, "Account bound: $username", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFailure = { error ->
                        runOnUiThread {
                            Toast.makeText(this, "OAuth failed: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        loadCurrentSettings()

        requestPermissionsButton.setOnClickListener {
            requestAllPermissions()
        }

        setDurationButton.setOnClickListener {
            saveDuration()
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
        
        checkboxAddOsmNote.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
            prefs.edit().putBoolean("addOsmNote", isChecked).apply()
        }
        
        buttonOsmAccount.setOnClickListener {
            if (oauthManager.isAuthenticated()) {
                // Remove account
                removeOsmAccount()
            } else {
                // Check if OSM client ID is properly configured
                if (!isOsmClientIdConfigured()) {
                    AlertDialog.Builder(this)
                        .setTitle("OSM Integration Not Configured")
                        .setMessage("OSM integration is not configured. The OSM Client ID is set to a placeholder value.\n\n" +
                                "To enable OSM features:\n" +
                                "1. Register an OAuth 2.0 application at https://www.openstreetmap.org/oauth2/applications\n" +
                                "2. Set redirect URI to: app.voicenotes.motorcycle://oauth\n" +
                                "3. Add your Client ID to gradle.properties\n" +
                                "4. Rebuild the app\n\n" +
                                "See BUILD_INSTRUCTIONS.md for detailed setup instructions.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@setOnClickListener
                }
                
                // Bind account
                try {
                    oauthManager.startOAuthFlow(oauthLauncher)
                } catch (e: IllegalStateException) {
                    Toast.makeText(this, "OSM Client ID not configured", Toast.LENGTH_LONG).show()
                    android.util.Log.e("SettingsActivity", "OSM Client ID not configured", e)
                }
            }
        }
    }

    private fun getAppVersion(): String {
        return try {
            // Try to get git tag or commit hash from BuildConfig
            val buildConfigVersion = BuildConfig.VERSION_NAME
            
            // Handle null or empty VERSION_NAME
            if (buildConfigVersion.isNullOrEmpty()) {
                return "Version 1.0.0"
            }
            
            // Check if VERSION_NAME is set to a git value
            if (buildConfigVersion.startsWith("v") || buildConfigVersion.contains("dev-")) {
                "Version $buildConfigVersion"
            } else {
                // Fallback to package version
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                "Version ${packageInfo.versionName ?: "1.0.0"}"
            }
        } catch (e: Exception) {
            "Version 1.0.0"
        }
    }

    private fun getDefaultSavePath(): String {
        return Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MUSIC
        ).absolutePath + "/VoiceNotes"
    }
    
    private fun showDebugLog() {
        val intent = Intent(this, DebugLogActivity::class.java)
        startActivity(intent)
    }

    private fun checkStoragePermissions() {
        // Check if we need MANAGE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showManageStorageDialog()
            }
        }
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
        durationEditText.setText(recordingDuration.toString())

        checkboxAddOsmNote.isChecked = prefs.getBoolean("addOsmNote", false)
        
        // Update OSM UI based on auth status
        if (oauthManager.isAuthenticated()) {
            val username = oauthManager.getUsername() ?: "Unknown"
            updateOsmAccountUI(username)
        }
        
        // Update permission status list
        updatePermissionStatusList()
    }
    
    private fun isOsmClientIdConfigured(): Boolean {
        val clientId = BuildConfig.OSM_CLIENT_ID
        return clientId.isNotBlank() && clientId != OsmOAuthManager.DEFAULT_CLIENT_ID_PLACEHOLDER
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
        
        // Check storage permission
        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // For API < 30, consider storage permission granted
            true
        }
        statusLines.add(if (hasStorage) {
            getString(R.string.permission_granted, getString(R.string.permission_storage))
        } else {
            getString(R.string.permission_not_granted, getString(R.string.permission_storage))
        })
        
        // Check overlay permission
        val hasOverlay = Settings.canDrawOverlays(this)
        statusLines.add(if (hasOverlay) {
            getString(R.string.permission_granted, getString(R.string.permission_overlay))
        } else {
            getString(R.string.permission_not_granted, getString(R.string.permission_overlay))
        })
        
        permissionStatusList.text = statusLines.joinToString("\n")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            updatePermissionStatusList()
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show()
                // Continue permission flow - check storage permissions
                checkStoragePermissions()
            } else {
                Toast.makeText(this, "Overlay permission is required for the app to work", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showManageStorageDialog() {
        AlertDialog.Builder(this)
            .setTitle("Storage Permission Required")
            .setMessage("This app needs permission to manage storage. Please grant 'All files access' permission.")
            .setPositiveButton("Grant") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveDuration() {
        val durationStr = durationEditText.text.toString()
        
        if (durationStr.isEmpty()) {
            Toast.makeText(this, R.string.invalid_duration, Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val duration = durationStr.toInt()
            
            if (duration < 1 || duration > 99) {
                Toast.makeText(this, R.string.invalid_duration, Toast.LENGTH_SHORT).show()
                return
            }
            
            val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
            prefs.edit().putInt("recordingDuration", duration).apply()
            
            durationValueText.text = "$duration seconds"
            Toast.makeText(this, getString(R.string.duration_saved, duration), Toast.LENGTH_SHORT).show()
            
        } catch (e: NumberFormatException) {
            Toast.makeText(this, R.string.invalid_duration, Toast.LENGTH_SHORT).show()
        }
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
            AlertDialog.Builder(this)
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
            // Check storage permissions next
            checkStoragePermissions()
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
    
    private fun updateOsmAccountUI(username: String) {
        textOsmAccountStatus.text = "Account bound: $username"
        textOsmAccountStatus.visibility = View.VISIBLE
        buttonOsmAccount.text = "Remove OSM Account"
        checkboxAddOsmNote.isEnabled = true
    }
    
    private fun removeOsmAccount() {
        oauthManager.removeTokens()
        textOsmAccountStatus.visibility = View.GONE
        buttonOsmAccount.text = "Bind OSM Account"
        checkboxAddOsmNote.isEnabled = false
        checkboxAddOsmNote.isChecked = false
        Toast.makeText(this, "OSM account removed", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        loadCurrentSettings()
    }
}
