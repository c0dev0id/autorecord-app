package com.vnmanager.opener

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Minimal launcher activity that opens the SettingsActivity of the main Voice Notes app.
 * 
 * This activity serves as a companion launcher that directly launches the Voice Notes Manager
 * (SettingsActivity) from the main app.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TARGET_PACKAGE = "com.voicenotes.motorcycle"
        private const val TARGET_ACTIVITY = "com.voicenotes.motorcycle.SettingsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // Create explicit intent to launch the main app's SettingsActivity
            val intent = Intent().apply {
                component = ComponentName(TARGET_PACKAGE, TARGET_ACTIVITY)
            }
            
            startActivity(intent)
            finish()
        } catch (e: ActivityNotFoundException) {
            // Show error toast if the main app is not installed or activity not found
            Toast.makeText(
                this,
                R.string.error_app_not_found,
                Toast.LENGTH_LONG
            ).show()
            
            // Delay finish to allow toast to be visible
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
            }, 2000)
        } catch (e: SecurityException) {
            // Handle case where activity cannot be accessed due to permission issues
            Toast.makeText(
                this,
                R.string.error_permission_denied,
                Toast.LENGTH_LONG
            ).show()
            
            // Delay finish to allow toast to be visible
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
            }, 2000)
        }
    }
}
