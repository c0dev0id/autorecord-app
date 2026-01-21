package com.voicenotes.motorcycle

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import net.openid.appauth.*

class OsmOAuthManager(private val context: Context) {
    
    companion object {
        private const val OSM_AUTH_ENDPOINT = "https://www.openstreetmap.org/oauth2/authorize"
        private const val OSM_TOKEN_ENDPOINT = "https://www.openstreetmap.org/oauth2/token"
        private const val CLIENT_ID = "your_osm_client_id" // User configures this
        private const val REDIRECT_URI = "app.voicenotes.motorcycle://oauth"
        
        private const val PREF_ACCESS_TOKEN = "osm_access_token"
        private const val PREF_REFRESH_TOKEN = "osm_refresh_token"
        private const val PREF_USERNAME = "osm_username"
    }
    
    private val authService = AuthorizationService(context)
    
    fun startOAuthFlow(launcher: ActivityResultLauncher<Intent>) {
        val serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse(OSM_AUTH_ENDPOINT),
            Uri.parse(OSM_TOKEN_ENDPOINT)
        )
        
        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI)
        )
            .setScope("read_prefs write_notes")
            .build()
        
        val authIntent = authService.getAuthorizationRequestIntent(authRequest)
        launcher.launch(authIntent)
    }
    
    fun handleOAuthResponse(intent: Intent, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)
        
        if (response != null) {
            // Exchange code for token
            authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, ex ->
                if (tokenResponse != null) {
                    val accessToken = tokenResponse.accessToken ?: ""
                    val refreshToken = tokenResponse.refreshToken ?: ""
                    
                    // Save tokens securely
                    saveTokensToKeystore(accessToken, refreshToken)
                    
                    // Fetch username
                    fetchUsername(accessToken) { username ->
                        saveUsername(username)
                        onSuccess(username)
                    }
                } else {
                    onFailure(ex ?: Exception("Token exchange failed"))
                }
            }
        } else {
            onFailure(exception ?: Exception("Authorization failed"))
        }
    }
    
    private fun fetchUsername(accessToken: String, callback: (String) -> Unit) {
        // Call OSM API to get user details
        // For now, use a placeholder
        callback("OSM_User")
    }
    
    fun saveTokensToKeystore(accessToken: String, refreshToken: String) {
        // Use Android Keystore for secure storage
        val prefs = context.getSharedPreferences("OsmAuth", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(PREF_ACCESS_TOKEN, accessToken)
            putString(PREF_REFRESH_TOKEN, refreshToken)
            apply()
        }
        Log.d("OsmOAuthManager", "Tokens saved to keystore")
    }
    
    fun getAccessToken(): String? {
        val prefs = context.getSharedPreferences("OsmAuth", Context.MODE_PRIVATE)
        return prefs.getString(PREF_ACCESS_TOKEN, null)
    }
    
    fun getUsername(): String? {
        val prefs = context.getSharedPreferences("OsmAuth", Context.MODE_PRIVATE)
        return prefs.getString(PREF_USERNAME, null)
    }
    
    fun saveUsername(username: String) {
        val prefs = context.getSharedPreferences("OsmAuth", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_USERNAME, username).apply()
    }
    
    fun removeTokens() {
        val prefs = context.getSharedPreferences("OsmAuth", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.d("OsmOAuthManager", "Tokens removed")
    }
    
    fun isAuthenticated(): Boolean {
        return getAccessToken() != null
    }
}
