package com.vnmanager.opener

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
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
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            startActivity(intent)
        } catch (e: Exception) {
            // Show error toast if the main app is not installed or activity not found
            Toast.makeText(
                this,
                "Voice Notes app not found. Please install Voice Notes first.",
                Toast.LENGTH_LONG
            ).show()
        } finally {
            // Always finish this activity
            finish()
        }
    }
}
