package com.voicenotes.motorcycle.osm

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Call

/**
 * Manager for creating OpenStreetMap notes
 */
class OsmNotesManager(private val context: Context) {
    
    private val tokenManager = OsmTokenManager(context)
    private val notesService = OsmNotesService.create()
    
    companion object {
        private const val TAG = "OsmNotesManager"
    }
    
    /**
     * Check if the device has network connectivity
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }
    
    /**
     * Create a note at the specified location with the given text
     * This is a non-blocking operation that fails silently if there are issues
     * 
     * @param latitude Latitude of the note location
     * @param longitude Longitude of the note location
     * @param text Text content for the note
     */
    suspend fun createNote(latitude: Double, longitude: Double, text: String) {
        withContext(Dispatchers.IO) {
            try {
                // Check if user is authenticated
                if (!tokenManager.isAuthenticated()) {
                    Log.d(TAG, "User not authenticated, skipping note creation")
                    return@withContext
                }
                
                // Check network connectivity
                if (!isNetworkAvailable()) {
                    Log.d(TAG, "No network connectivity, skipping note creation")
                    return@withContext
                }
                
                // Get access token
                val accessToken = tokenManager.getAccessToken()
                if (accessToken == null) {
                    Log.d(TAG, "No access token available")
                    return@withContext
                }
                
                // Create authorization header
                val authHeader = "Bearer $accessToken"
                
                // Truncate text if too long (OSM has limits)
                val noteText = if (text.length > 2000) {
                    text.substring(0, 1997) + "..."
                } else {
                    text
                }
                
                // Make API call
                Log.d(TAG, "Creating OSM note at $latitude, $longitude")
                val call = notesService.createNote(latitude, longitude, noteText, authHeader)
                val response = call.execute()
                
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully created OSM note")
                } else {
                    Log.d(TAG, "Failed to create OSM note: ${response.code()} ${response.message()}")
                }
                
            } catch (e: Exception) {
                // Fail silently - don't block the recording process
                Log.d(TAG, "Error creating OSM note: ${e.message}")
            }
        }
    }
}
