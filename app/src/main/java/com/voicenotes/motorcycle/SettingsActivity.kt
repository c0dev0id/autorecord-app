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
import androidx.documentfile.provider.DocumentFile
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var directoryPathText: TextView
    private lateinit var durationValueText: TextView
    private lateinit var durationEditText: EditText
    private lateinit var chooseDirectoryButton: Button
    private lateinit var setDurationButton: Button
    private lateinit var requestPermissionsButton: Button
    private lateinit var permissionStatusList: TextView
    private lateinit var quitButton: Button
    
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
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    private val directoryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            handleDirectorySelection(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        directoryPathText = findViewById(R.id.directoryPathText)
        durationValueText = findViewById(R.id.durationValueText)
        durationEditText = findViewById(R.id.durationEditText)
        chooseDirectoryButton = findViewById(R.id.chooseDirectoryButton)
        setDurationButton = findViewById(R.id.setDurationButton)
        requestPermissionsButton = findViewById(R.id.requestPermissionsButton)
        permissionStatusList = findViewById(R.id.permissionStatusList)
        quitButton = findViewById(R.id.quitButton)
        
        checkboxOnlineProcessing = findViewById(R.id.checkboxOnlineProcessing)
        checkboxAddOsmNote = findViewById(R.id.checkboxAddOsmNote)
        buttonOsmAccount = findViewById(R.id.buttonOsmAccount)
        textOsmAccountStatus = findViewById(R.id.textOsmAccountStatus)
        buttonRunOnlineProcessing = findViewById(R.id.buttonRunOnlineProcessing)
        
        oauthManager = OsmOAuthManager(this)
        
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

        chooseDirectoryButton.setOnClickListener {
            openDirectoryPicker()
        }

        requestPermissionsButton.setOnClickListener {
            requestAllPermissions()
        }

        setDurationButton.setOnClickListener {
            saveDuration()
        }

        quitButton.setOnClickListener {
            finishAffinity()
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

    private fun getDefaultSavePath(): String {
        return Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MUSIC
        ).absolutePath + "/VoiceNotes"
    }

    private fun loadCurrentSettings() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val saveDir = prefs.getString("saveDirectory", null)
        val recordingDuration = prefs.getInt("recordingDuration", 10)

        // Display current path or default path if not set
        directoryPathText.text = saveDir ?: getDefaultSavePath()
        
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

    private fun openDirectoryPicker() {
        try {
            // For Android 11+, we need proper storage permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Check if we have MANAGE_EXTERNAL_STORAGE permission
                if (!Environment.isExternalStorageManager()) {
                    showManageStorageDialog()
                    return
                }
            }

            // Launch the directory picker directly
            directoryPickerLauncher.launch(null)

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
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

    private fun handleDirectorySelection(uri: Uri) {
        try {
            // Take persistable URI permission
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            // Convert DocumentFile URI to a file path if possible
            val documentFile = DocumentFile.fromTreeUri(this, uri)
            
            // For custom URIs, we need to store the URI itself since we can't always get a file path
            // But for simplicity, we'll try to extract a path
            val path = when {
                uri.path?.contains("/tree/primary:") == true -> {
                    // Primary storage
                    val relativePath = uri.path!!.substringAfter("/tree/primary:")
                    Environment.getExternalStorageDirectory().absolutePath + "/" + relativePath
                }
                uri.path?.contains("/document/primary:") == true -> {
                    val relativePath = uri.path!!.substringAfter("/document/primary:")
                    Environment.getExternalStorageDirectory().absolutePath + "/" + relativePath
                }
                else -> {
                    // Fallback to default
                    getDefaultSavePath()
                }
            }

            saveDirectoryPath(path)
            Toast.makeText(this, "Custom directory set", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error selecting directory: ${e.message}", Toast.LENGTH_LONG).show()
            // Fallback to default
            saveDirectoryPath(getDefaultSavePath())
        }
    }

    private fun saveDirectoryPath(path: String) {
        try {
            // Create the directory if it doesn't exist
            val directory = File(path)
            if (!directory.exists()) {
                val created = directory.mkdirs()
                if (!created) {
                    Toast.makeText(this, "Warning: Could not create directory", Toast.LENGTH_SHORT).show()
                }
            }
            
            val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
            prefs.edit().putString("saveDirectory", path).apply()
            directoryPathText.text = path
        } catch (e: Exception) {
            Toast.makeText(this, "Error creating directory: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
            // All runtime permissions granted, check overlay permission
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
