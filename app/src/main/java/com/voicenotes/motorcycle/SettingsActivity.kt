package com.voicenotes.motorcycle

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var directoryPathText: TextView
    private lateinit var triggerAppText: TextView
    private lateinit var durationValueText: TextView
    private lateinit var durationEditText: EditText
    private lateinit var chooseDirectoryButton: Button
    private lateinit var chooseTriggerAppButton: Button
    private lateinit var setDurationButton: Button
    private lateinit var requestPermissionsButton: Button
    private lateinit var permissionStatusList: TextView
    private lateinit var quitButton: Button

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
        triggerAppText = findViewById(R.id.triggerAppText)
        durationValueText = findViewById(R.id.durationValueText)
        durationEditText = findViewById(R.id.durationEditText)
        chooseDirectoryButton = findViewById(R.id.chooseDirectoryButton)
        chooseTriggerAppButton = findViewById(R.id.chooseTriggerAppButton)
        setDurationButton = findViewById(R.id.setDurationButton)
        requestPermissionsButton = findViewById(R.id.requestPermissionsButton)
        permissionStatusList = findViewById(R.id.permissionStatusList)
        quitButton = findViewById(R.id.quitButton)

        loadCurrentSettings()
        
        // Auto-configure DMD2 if installed and no trigger app is set
        autoConfigureDMD2IfAvailable()

        chooseDirectoryButton.setOnClickListener {
            openDirectoryPicker()
        }

        chooseTriggerAppButton.setOnClickListener {
            showAppChooserDialog()
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
    }
    
    private fun autoConfigureDMD2IfAvailable() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val triggerApp = prefs.getString("triggerApp", null)
        
        // Only auto-configure if no trigger app is set
        if (triggerApp.isNullOrEmpty()) {
            val dmd2Package = "com.riser.dmd2"
            if (isAppInstalled(dmd2Package)) {
                try {
                    val pm = packageManager
                    val dmd2App = pm.getApplicationInfo(dmd2Package, PackageManager.GET_META_DATA)
                    val dmd2Name = dmd2App.loadLabel(pm).toString()
                    
                    saveTriggerApp(dmd2Package, dmd2Name)
                    Toast.makeText(
                        this,
                        "DMD2 detected and set as default trigger app",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: PackageManager.NameNotFoundException) {
                    // App was uninstalled between check and retrieval, ignore
                }
            }
        }
    }
    
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun loadCurrentSettings() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val saveDir = prefs.getString("saveDirectory", null)
        val triggerApp = prefs.getString("triggerApp", null)
        val triggerAppName = prefs.getString("triggerAppName", null)
        val recordingDuration = prefs.getInt("recordingDuration", 10)

        directoryPathText.text = saveDir ?: getString(R.string.not_set)
        triggerAppText.text = triggerAppName ?: getString(R.string.not_set)
        durationValueText.text = "$recordingDuration seconds"
        durationEditText.setText(recordingDuration.toString())
        
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

            // Show options to user: Use default or choose custom
            AlertDialog.Builder(this)
                .setTitle("Choose Storage Location")
                .setMessage("Select where to save recordings:")
                .setPositiveButton("Use Default") { _, _ ->
                    val defaultPath = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_MUSIC
                    ).absolutePath + "/VoiceNotes"
                    saveDirectoryPath(defaultPath)
                    Toast.makeText(this, "Using default directory", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Choose Custom") { _, _ ->
                    // Use the document picker for custom location
                    directoryPickerLauncher.launch(null)
                }
                .setNeutralButton("Cancel", null)
                .show()

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
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_MUSIC
                    ).absolutePath + "/VoiceNotes"
                }
            }

            saveDirectoryPath(path)
            Toast.makeText(this, "Custom directory set", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error selecting directory: ${e.message}", Toast.LENGTH_LONG).show()
            // Fallback to default
            val defaultPath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MUSIC
            ).absolutePath + "/VoiceNotes"
            saveDirectoryPath(defaultPath)
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

    private fun showAppChooserDialog() {
        try {
            val installedApps = getInstalledApps()

            val appNames = installedApps.map { it.name }.toTypedArray()
            val appPackages = installedApps.map { it.packageName }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle("Select Trigger App")
                .setItems(appNames) { _, which ->
                    saveTriggerApp(appPackages[which], appNames[which])
                }
                .setNegativeButton("Cancel", null)
                .show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        return apps
            .filter { app ->
                // Only show apps that can be launched
                pm.getLaunchIntentForPackage(app.packageName) != null &&
                        app.packageName != packageName // Exclude this app
            }
            .map { app ->
                AppInfo(
                    name = app.loadLabel(pm).toString(),
                    packageName = app.packageName
                )
            }
            .sortedBy { it.name }
    }

    private fun saveTriggerApp(packageName: String, appName: String) {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        prefs.edit()
            .putString("triggerApp", packageName)
            .putString("triggerAppName", appName)
            .apply()
        triggerAppText.text = appName
        Toast.makeText(this, "Trigger app set to: $appName", Toast.LENGTH_SHORT).show()
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

    override fun onResume() {
        super.onResume()
        loadCurrentSettings()
    }

    data class AppInfo(
        val name: String,
        val packageName: String
    )
}
