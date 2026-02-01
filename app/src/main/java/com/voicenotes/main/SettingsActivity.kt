package com.voicenotes.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.voicenotes.main.database.RecordingDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings Activity using PreferenceFragmentCompat for modern preference management.
 * 
 * Features:
 * - Recording Manager integration
 * - Language preferences (app UI and STT)
 * - Permission management
 * - Debug logging
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable Up navigation
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        setContentView(R.layout.activity_settings)
        
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
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

/**
 * Main settings fragment displaying all preferences.
 */
class SettingsFragment : PreferenceFragmentCompat() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            checkAndRequestOverlayPermission()
        } else {
            Toast.makeText(requireContext(), "Some permissions were denied", Toast.LENGTH_LONG).show()
            updatePermissionStatus()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionStatus()
        if (Settings.canDrawOverlays(requireContext())) {
            Toast.makeText(requireContext(), "Overlay permission granted", Toast.LENGTH_SHORT).show()
            checkAndRequestBatteryOptimization()
        } else {
            Toast.makeText(requireContext(), "Overlay permission is required for the app to work", Toast.LENGTH_LONG).show()
        }
    }

    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)) {
            Toast.makeText(requireContext(), "Battery optimization disabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Background recording may be interrupted", Toast.LENGTH_LONG).show()
        }
        updatePermissionStatus()
    }

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Use "AppPrefs" to maintain compatibility with existing code
        preferenceManager.sharedPreferencesName = "AppPrefs"
        setPreferencesFromResource(R.xml.preferences_settings, rootKey)
        
        setupPreferenceListeners()
        updateRecordingsCount()
        updatePermissionStatus()
    }

    private fun setupPreferenceListeners() {
        // Recording Manager Open
        findPreference<Preference>("recording_manager_open")?.setOnPreferenceClickListener {
            val intent = Intent(requireContext(), RecordingManagerActivity::class.java)
            startActivity(intent)
            true
        }

        // Grant Permissions
        findPreference<Preference>("grant_permissions")?.setOnPreferenceClickListener {
            requestAllPermissions()
            true
        }

        // Show Debug Log
        findPreference<Preference>("show_debug_log")?.setOnPreferenceClickListener {
            val intent = Intent(requireContext(), DebugLogActivity::class.java)
            startActivity(intent)
            true
        }

        // App Language Change
        findPreference<ListPreference>("app_language")?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            setOnPreferenceChangeListener { _, newValue ->
                val languageTag = newValue as String
                if (languageTag == "system") {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                } else {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
                }
                true
            }
        }

        // Primary STT Language
        findPreference<ListPreference>("stt_primary_language")?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }

        // Secondary STT Language
        findPreference<ListPreference>("stt_secondary_language")?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }

        // Recording Duration Validation
        findPreference<SeekBarPreference>("recording_duration")?.setOnPreferenceChangeListener { _, newValue ->
            val duration = newValue as Int
            if (duration in 1..99) {
                Toast.makeText(requireContext(), getString(R.string.duration_saved, duration), Toast.LENGTH_SHORT).show()
                true
            } else {
                Toast.makeText(requireContext(), getString(R.string.invalid_duration), Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    private fun updateRecordingsCount() {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                val db = RecordingDatabase.getDatabase(requireContext())
                db.recordingDao().getRecordingCount()
            }
            
            findPreference<Preference>("recordings_count")?.summary = 
                getString(R.string.pref_recordings_count_summary, count)
        }
    }

    private fun updatePermissionStatus() {
        val statusLines = mutableListOf<String>()
        
        val hasMicrophone = ContextCompat.checkSelfPermission(
            requireContext(), 
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        statusLines.add(if (hasMicrophone) {
            getString(R.string.permission_granted, getString(R.string.permission_microphone))
        } else {
            getString(R.string.permission_not_granted, getString(R.string.permission_microphone))
        })
        
        val hasLocation = ContextCompat.checkSelfPermission(
            requireContext(), 
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        statusLines.add(if (hasLocation) {
            getString(R.string.permission_granted, getString(R.string.permission_location))
        } else {
            getString(R.string.permission_not_granted, getString(R.string.permission_location))
        })
        
        val hasBluetooth = ContextCompat.checkSelfPermission(
            requireContext(), 
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        statusLines.add(if (hasBluetooth) {
            getString(R.string.permission_granted, getString(R.string.permission_bluetooth))
        } else {
            getString(R.string.permission_not_granted, getString(R.string.permission_bluetooth))
        })
        
        val hasOverlay = Settings.canDrawOverlays(requireContext())
        statusLines.add(if (hasOverlay) {
            getString(R.string.permission_granted, getString(R.string.permission_overlay))
        } else {
            getString(R.string.permission_not_granted, getString(R.string.permission_overlay))
        })

        val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        val hasBatteryExemption = powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)
        statusLines.add(if (hasBatteryExemption) {
            "✓ Battery optimization (disabled)"
        } else {
            "✗ Battery optimization (enabled - may interrupt recording)"
        })

        findPreference<Preference>("permission_status")?.summary = statusLines.joinToString("\n")
    }

    private fun requestAllPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        } else {
            checkAndRequestOverlayPermission()
        }
    }

    private fun checkAndRequestOverlayPermission() {
        if (!Settings.canDrawOverlays(requireContext())) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.overlay_permission_required)
                .setMessage(R.string.overlay_permission_message)
                .setPositiveButton("Grant") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${requireContext().packageName}")
                    )
                    overlayPermissionLauncher.launch(intent)
                }
                .setNegativeButton("Cancel") { _, _ ->
                    updatePermissionStatus()
                }
                .show()
        } else {
            checkAndRequestBatteryOptimization()
        }
    }

    private fun checkAndRequestBatteryOptimization() {
        val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = requireContext().packageName

        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Battery Optimization")
                .setMessage("To ensure reliable background recording, this app needs unrestricted battery access.\n\n" +
                        "This will prevent Android from stopping the recording when the screen is off or the app is in the background.")
                .setPositiveButton("Grant") { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:$packageName")
                        batteryOptimizationLauncher.launch(intent)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Unable to open battery settings", Toast.LENGTH_SHORT).show()
                        updatePermissionStatus()
                    }
                }
                .setNegativeButton("Skip") { _, _ ->
                    Toast.makeText(requireContext(), "Background recording may be interrupted", Toast.LENGTH_LONG).show()
                    updatePermissionStatus()
                }
                .show()
        } else {
            Toast.makeText(requireContext(), "All permissions granted", Toast.LENGTH_SHORT).show()
            updatePermissionStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        updateRecordingsCount()
        updatePermissionStatus()
    }

    override fun onPause() {
        super.onPause()
    }
}
