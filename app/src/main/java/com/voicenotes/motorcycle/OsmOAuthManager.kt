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
        private val CLIENT_ID = BuildConfig.OSM_CLIENT_ID
        private const val REDIRECT_URI = "app.voicenotes.motorcycle://oauth"
        
        private const val PREF_ACCESS_TOKEN = "osm_access_token"
        private const val PREF_REFRESH_TOKEN = "osm_refresh_token"
        private const val PREF_USERNAME = "osm_username"
    }
    
    private val authService = AuthorizationService(context)
    
    fun startOAuthFlow(launcher: ActivityResultLauncher<Intent>) {
        if (CLIENT_ID.isBlank() || CLIENT_ID == "your_osm_client_id") {
            throw IllegalStateException("OSM Client ID not configured")
        }
        
        DebugLogger.logInfo(
            service = "OSM OAuth",
            message = "Starting OAuth flow with client ID: ${CLIENT_ID.take(10)}..."
        )
        
        val serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse(OSM_AUTH_ENDPOINT),
            Uri.parse(OSM_TOKEN_ENDPOINT)
        )
        
        // Generate code verifier for PKCE
        val codeVerifier = CodeVerifierUtil.generateRandomCodeVerifier()
        
        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI)
        )
            .setScope("read_prefs write_notes")
            .setCodeVerifier(codeVerifier)  // Enable PKCE
            .build()
        
        DebugLogger.logApiRequest(
            service = "OSM OAuth",
            method = "GET",
            url = OSM_AUTH_ENDPOINT,
            headers = mapOf("scope" to "read_prefs write_notes")
        )
        
        val authIntent = authService.getAuthorizationRequestIntent(authRequest)
        launcher.launch(authIntent)
    }
    
    fun handleOAuthResponse(intent: Intent, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)
        
        if (response != null) {
            DebugLogger.logInfo(
                service = "OSM OAuth",
                message = "Authorization code received, exchanging for token"
            )
            
            // Exchange code for token
            authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, ex ->
                if (tokenResponse != null) {
                    val accessToken = tokenResponse.accessToken ?: ""
                    val refreshToken = tokenResponse.refreshToken ?: ""
                    
                    DebugLogger.logApiResponse(
                        service = "OSM OAuth",
                        statusCode = 200,
                        responseBody = "Token exchange successful"
                    )
                    
                    // Save tokens securely
                    saveTokensToKeystore(accessToken, refreshToken)
                    
                    // Fetch username
                    fetchUsername(accessToken) { username ->
                        saveUsername(username)
                        onSuccess(username)
                    }
                } else {
                    DebugLogger.logError(
                        service = "OSM OAuth",
                        error = "Token exchange failed",
                        exception = ex
                    )
                    onFailure(ex ?: Exception("Token exchange failed"))
                }
            }
        } else {
            DebugLogger.logError(
                service = "OSM OAuth",
                error = "Authorization failed",
                exception = exception
            )
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
