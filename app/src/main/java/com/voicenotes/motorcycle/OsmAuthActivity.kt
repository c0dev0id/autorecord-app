package com.voicenotes.motorcycle

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.voicenotes.motorcycle.osm.OsmAuthConfig
import com.voicenotes.motorcycle.osm.OsmTokenManager
import net.openid.appauth.*

/**
 * Activity that handles OAuth 2.0 authentication flow with OpenStreetMap
 */
class OsmAuthActivity : AppCompatActivity() {
    
    private lateinit var authService: AuthorizationService
    private lateinit var tokenManager: OsmTokenManager
    
    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.data != null) {
            handleAuthorizationResponse(result.data!!)
        } else {
            finishWithError("Authorization cancelled")
        }
    }
    
    companion object {
        private const val TAG = "OsmAuthActivity"
        const val EXTRA_AUTH_COMPLETED = "auth_completed"
        const val EXTRA_AUTH_ERROR = "auth_error"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        authService = AuthorizationService(this)
        tokenManager = OsmTokenManager(this)
        
        // Check if this is a redirect from OSM
        val data: Uri? = intent.data
        if (data != null && data.scheme == "com.voicenotes.motorcycle") {
            handleAuthorizationResponse(intent)
        } else {
            // Start new authorization flow
            startAuthorizationFlow()
        }
    }
    
    private fun startAuthorizationFlow() {
        val serviceConfig = OsmAuthConfig.getServiceConfig()
        
        val authRequestBuilder = AuthorizationRequest.Builder(
            serviceConfig,
            OsmAuthConfig.CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(OsmAuthConfig.REDIRECT_URI)
        )
        
        authRequestBuilder.setScope(OsmAuthConfig.SCOPE)
        val authRequest = authRequestBuilder.build()
        
        val authIntent = authService.getAuthorizationRequestIntent(authRequest)
        authLauncher.launch(authIntent)
    }
    
    private fun handleAuthorizationResponse(intent: Intent) {
        val authResponse = AuthorizationResponse.fromIntent(intent)
        val authException = AuthorizationException.fromIntent(intent)
        
        if (authException != null) {
            Log.e(TAG, "Authorization failed: ${authException.message}")
            finishWithError(authException.message ?: "Authorization failed")
            return
        }
        
        if (authResponse != null) {
            val authState = AuthState(authResponse, authException)
            
            // Exchange authorization code for tokens
            authService.performTokenRequest(authResponse.createTokenExchangeRequest()) { tokenResponse, tokenException ->
                if (tokenException != null) {
                    Log.e(TAG, "Token exchange failed: ${tokenException.message}")
                    finishWithError(tokenException.message ?: "Token exchange failed")
                    return@performTokenRequest
                }
                
                if (tokenResponse != null) {
                    authState.update(tokenResponse, tokenException)
                    tokenManager.saveAuthState(authState)
                    
                    Log.d(TAG, "Authentication successful")
                    
                    // Fetch user details to get username
                    fetchUserDetails(authState.accessToken)
                } else {
                    finishWithError("No token received")
                }
            }
        } else {
            finishWithError("No authorization response")
        }
    }
    
    private fun fetchUserDetails(accessToken: String?) {
        if (accessToken == null) {
            // No username, but still successful auth
            finishWithSuccess()
            return
        }
        
        try {
            val userService = com.voicenotes.motorcycle.osm.OsmUserService.create()
            val call = userService.getUserDetails("Bearer $accessToken")
            
            call.enqueue(object : retrofit2.Callback<String> {
                override fun onResponse(call: retrofit2.Call<String>, response: retrofit2.Response<String>) {
                    if (response.isSuccessful) {
                        val xmlBody = response.body()
                        if (xmlBody != null) {
                            // Parse username from XML
                            val username = extractUsername(xmlBody)
                            if (username != null) {
                                tokenManager.saveUsername(username)
                                Log.d(TAG, "Fetched username: $username")
                            }
                        }
                    }
                    finishWithSuccess()
                }
                
                override fun onFailure(call: retrofit2.Call<String>, t: Throwable) {
                    Log.e(TAG, "Failed to fetch user details: ${t.message}")
                    // Still successful auth, just no username
                    finishWithSuccess()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user details: ${e.message}")
            finishWithSuccess()
        }
    }
    
    private fun extractUsername(xml: String): String? {
        // Simple XML parsing to extract username from <user display_name="...">
        return try {
            val displayNamePattern = """display_name="([^"]+)"""".toRegex()
            val match = displayNamePattern.find(xml)
            match?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun finishWithSuccess() {
        val resultIntent = Intent().apply {
            putExtra(EXTRA_AUTH_COMPLETED, true)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        Toast.makeText(this, "Connected to OpenStreetMap", Toast.LENGTH_SHORT).show()
        finish()
    }
    
    private fun finishWithError(error: String) {
        val resultIntent = Intent().apply {
            putExtra(EXTRA_AUTH_COMPLETED, false)
            putExtra(EXTRA_AUTH_ERROR, error)
        }
        setResult(Activity.RESULT_CANCELED, resultIntent)
        Toast.makeText(this, "Authentication failed: $error", Toast.LENGTH_LONG).show()
        finish()
    }
    
    override fun onDestroy() {
        authService.dispose()
        super.onDestroy()
    }
}
