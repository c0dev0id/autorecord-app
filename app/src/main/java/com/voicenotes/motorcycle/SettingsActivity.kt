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

class SettingsActivity : AppCompatActivity() {

    private lateinit var directoryPathText: TextView
    private lateinit var triggerAppText: TextView
    private lateinit var durationValueText: TextView
    private lateinit var durationEditText: EditText
    private lateinit var chooseDirectoryButton: Button
    private lateinit var chooseTriggerAppButton: Button
    private lateinit var setDurationButton: Button
    private lateinit var requestPermissionsButton: Button
    private lateinit var quitButton: Button

    private val PERMISSIONS_REQUEST_CODE = 200

    private val requiredPermissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
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
        quitButton = findViewById(R.id.quitButton)

        loadCurrentSettings()

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
    }

    private fun openDirectoryPicker() {
        try {
            // For Android 11+, we need to use the default directory approach
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Check if we have MANAGE_EXTERNAL_STORAGE permission
                if (!Environment.isExternalStorageManager()) {
                    showManageStorageDialog()
                    return
                }
            }

            // Use a default directory for simplicity
            val defaultPath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MUSIC
            ).absolutePath + "/VoiceNotes"

            saveDirectoryPath(defaultPath)
            Toast.makeText(this, "Using directory: $defaultPath", Toast.LENGTH_LONG).show()

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
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            val documentFile = DocumentFile.fromTreeUri(this, uri)
            val path = documentFile?.uri?.path ?: uri.path

            saveDirectoryPath(path ?: "")
            Toast.makeText(this, "Directory selected", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error selecting directory: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveDirectoryPath(path: String) {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        prefs.edit().putString("saveDirectory", path).apply()
        directoryPathText.text = path
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
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isEmpty()) {
            Toast.makeText(this, "All permissions already granted", Toast.LENGTH_SHORT).show()
            return
        }

        ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSIONS_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Some permissions were denied", Toast.LENGTH_LONG).show()
            }
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
