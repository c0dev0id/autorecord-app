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

    private lateinit var statusText: TextView
    private lateinit var infoText: TextView
    private lateinit var progressBar: ProgressBar

    private val PERMISSIONS_REQUEST_CODE = 100
    private val OVERLAY_PERMISSION_REQUEST_CODE = 101
    
    private lateinit var finishReceiver: FinishActivityReceiver

    private val requiredPermissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
        infoText.text = getString(R.string.initializing)
        progressBar.visibility = View.VISIBLE
        
        // Check if first run
        if (isFirstRun()) {
            showSetupDialog()
        } else {
            // Check overlay permission
            checkOverlayPermission()
        }
    }

    private fun isFirstRun(): Boolean {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val saveDir = prefs.getString("saveDirectory", null)
        
        // Check if save directory is configured and exists
        val folderMissing = if (!saveDir.isNullOrEmpty()) {
            val dir = File(saveDir)
            !dir.exists()
        } else {
            true // No directory configured
        }
        
        // Check if permissions are missing
        val permissionsMissing = !checkPermissions()
        
        return folderMissing || permissionsMissing
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.overlay_permission_required)
                .setMessage(R.string.overlay_permission_message)
                .setPositiveButton("OK") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
                }
                .setCancelable(false)
                .show()
        } else {
            startRecordingProcess()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startRecordingProcess()
            } else {
                Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun startRecordingProcess() {
        if (!checkPermissions()) {
            requestPermissions()
            return
        }

        // Start overlay service
        val serviceIntent = Intent(this, OverlayService::class.java)
        startService(serviceIntent)
        
        // Minimize this activity to background
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
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // Check if coming back from settings
        if (!isFirstRun() && !intent.hasExtra("fromSettings")) {
            // Don't restart if we're already running the service
        }
    }
}
