package com.voicenotes.motorcycle

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

    private lateinit var directoryPathText: TextView
    private lateinit var durationValueText: TextView
    private lateinit var durationEditText: EditText
    private lateinit var setDurationButton: Button
    private lateinit var requestPermissionsButton: Button
    private lateinit var permissionStatusList: TextView
    private lateinit var quitButton: Button
    private lateinit var openFolderButton: Button
    private lateinit var buttonDebugLog: Button
    private lateinit var appVersionText: TextView
    
    private lateinit var checkboxOnlineProcessing: CheckBox
    private lateinit var checkboxAddOsmNote: CheckBox
    private lateinit var buttonOsmAccount: Button
    private lateinit var textOsmAccountStatus: TextView
    private lateinit var buttonRunOnlineProcessing: Button
    
    private lateinit var oauthManager: OsmOAuthManager
    private lateinit var oauthLauncher: ActivityResultLauncher<Intent>

    private val PERMISSIONS_REQUEST_CODE = 200
    private val OVERLAY_PERMISSION_REQUEST_CODE = 201

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
        setContentView(R.layout.activity_settings)

        directoryPathText = findViewById(R.id.directoryPathText)
        durationValueText = findViewById(R.id.durationValueText)
        durationEditText = findViewById(R.id.durationEditText)
        setDurationButton = findViewById(R.id.setDurationButton)
        requestPermissionsButton = findViewById(R.id.requestPermissionsButton)
        permissionStatusList = findViewById(R.id.permissionStatusList)
        quitButton = findViewById(R.id.quitButton)
        openFolderButton = findViewById(R.id.openFolderButton)
        buttonDebugLog = findViewById(R.id.buttonDebugLog)
        appVersionText = findViewById(R.id.appVersionText)
        
        checkboxOnlineProcessing = findViewById(R.id.checkboxOnlineProcessing)
        checkboxAddOsmNote = findViewById(R.id.checkboxAddOsmNote)
        buttonOsmAccount = findViewById(R.id.buttonOsmAccount)
        textOsmAccountStatus = findViewById(R.id.textOsmAccountStatus)
        buttonRunOnlineProcessing = findViewById(R.id.buttonRunOnlineProcessing)
        
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
            openStorageFolder()
        }
        
        buttonDebugLog.setOnClickListener {
            showDebugLog()
        }
        
        checkboxOnlineProcessing.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
            prefs.edit().putBoolean("tryOnlineProcessingDuringRide", isChecked).apply()
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
                // Bind account
                try {
                    oauthManager.startOAuthFlow(oauthLauncher)
                } catch (e: IllegalStateException) {
                    Toast.makeText(this, "OSM Client ID not configured", Toast.LENGTH_LONG).show()
                    android.util.Log.e("SettingsActivity", "OSM Client ID not configured", e)
                }
            }
        }
        
        buttonRunOnlineProcessing.setOnClickListener {
            runManualProcessing()
        }
    }

    private fun getAppVersion(): String {
        return try {
            // Try to get git tag or commit hash from BuildConfig
            val buildConfigVersion = BuildConfig.VERSION_NAME
            
            // Check if VERSION_NAME is set to a git value
            if (buildConfigVersion.startsWith("v") || buildConfigVersion.contains("dev-")) {
                "Version $buildConfigVersion"
            } else {
                // Fallback to package version
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                "Version ${packageInfo.versionName}"
            }
        } catch (e: Exception) {
            "Version unknown"
        }
    }

    private fun getDefaultSavePath(): String {
        return Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MUSIC
        ).absolutePath + "/VoiceNotes"
    }
    
    private fun showDebugLog() {
        val logContent = DebugLogger.getLogContent(this)
        
        val textView = TextView(this).apply {
            text = logContent
            setPadding(32, 32, 32, 32)
            textSize = 12f
            setTextIsSelectable(true)
        }
        
        val scrollView = android.widget.ScrollView(this).apply {
            addView(textView)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Debug Log")
            .setView(scrollView)
            .setPositiveButton("Close", null)
            .setNeutralButton("Clear Log") { _, _ ->
                DebugLogger.clearLog(this)
                Toast.makeText(this, "Log cleared", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun openStorageFolder() {
        // Always use the fixed internal storage path
        val saveDir = getDefaultSavePath()
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        prefs.edit().putString("saveDirectory", saveDir).apply()
        
        try {
            val folder = File(saveDir)
            if (!folder.exists()) {
                folder.mkdirs()
            }
            
            // Use file URI to open folder
            val uri = Uri.fromFile(folder)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "vnd.android.document/directory")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback: try generic file manager
                val fallbackIntent = Intent(Intent.ACTION_VIEW)
                fallbackIntent.setDataAndType(uri, "*/*")
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(fallbackIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open folder. Please use your file manager to navigate to: $saveDir", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun checkStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - check if we need MANAGE_EXTERNAL_STORAGE
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
        
        // Display the fixed path
        directoryPathText.text = defaultPath
        
        durationValueText.text = "$recordingDuration seconds"
        durationEditText.setText(recordingDuration.toString())
        
        checkboxOnlineProcessing.isChecked = prefs.getBoolean("tryOnlineProcessingDuringRide", true)
        checkboxAddOsmNote.isChecked = prefs.getBoolean("addOsmNote", false)
        
        // Update OSM UI based on auth status
        if (oauthManager.isAuthenticated()) {
            val username = oauthManager.getUsername() ?: "Unknown"
            updateOsmAccountUI(username)
        }
        
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
        
        // Check Bluetooth permission (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasBluetooth = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            statusLines.add(if (hasBluetooth) {
                getString(R.string.permission_granted, getString(R.string.permission_bluetooth))
            } else {
                getString(R.string.permission_not_granted, getString(R.string.permission_bluetooth))
            })
        }
        
        // Check storage permission
        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: Check Environment.isExternalStorageManager()
            Environment.isExternalStorageManager()
        } else {
            // Android 10-: Check WRITE_EXTERNAL_STORAGE
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        statusLines.add(if (hasStorage) {
            getString(R.string.permission_granted, getString(R.string.permission_storage))
        } else {
            getString(R.string.permission_not_granted, getString(R.string.permission_storage))
        })
        
        // Check overlay permission
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
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
                    checkStoragePermissions()
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
    
    private fun runManualProcessing() {
        // Disable button
        buttonRunOnlineProcessing.isEnabled = false
        buttonRunOnlineProcessing.text = "Processing..."
        
        // Start batch processing service
        val intent = Intent(this, BatchProcessingService::class.java)
        startService(intent)
    }
    
    private val batchReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.voicenotes.motorcycle.BATCH_PROGRESS" -> {
                    val filename = intent.getStringExtra("filename")
                    // Update button text to show current file being processed
                    buttonRunOnlineProcessing.text = "Processing: ${filename ?: "..."}"
                }
                "com.voicenotes.motorcycle.BATCH_COMPLETE" -> {
                    buttonRunOnlineProcessing.isEnabled = true
                    buttonRunOnlineProcessing.text = "Run Online Processing"
                    Toast.makeText(this@SettingsActivity, "Processing complete", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadCurrentSettings()
        
        // Register broadcast receiver
        val filter = IntentFilter()
        filter.addAction("com.voicenotes.motorcycle.BATCH_PROGRESS")
        filter.addAction("com.voicenotes.motorcycle.BATCH_COMPLETE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batchReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(batchReceiver, filter)
        }
    }
    
    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(batchReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }
}
