package com.voicenotes.motorcycle.osm

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.openid.appauth.AuthState
import org.json.JSONException

/**
 * Secure storage and management of OSM OAuth tokens using EncryptedSharedPreferences
 */
class OsmTokenManager(context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "osm_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    companion object {
        private const val KEY_AUTH_STATE = "auth_state"
        private const val KEY_USERNAME = "osm_username"
    }
    
    /**
     * Save the OAuth authentication state
     */
    fun saveAuthState(authState: AuthState) {
        encryptedPrefs.edit()
            .putString(KEY_AUTH_STATE, authState.jsonSerializeString())
            .apply()
    }
    
    /**
     * Retrieve the OAuth authentication state
     */
    fun getAuthState(): AuthState? {
        val json = encryptedPrefs.getString(KEY_AUTH_STATE, null) ?: return null
        return try {
            AuthState.jsonDeserialize(json)
        } catch (e: JSONException) {
            null
        }
    }
    
    /**
     * Check if user is authenticated (has valid or refreshable tokens)
     */
    fun isAuthenticated(): Boolean {
        val authState = getAuthState()
        return authState?.isAuthorized == true
    }
    
    /**
     * Get the current access token
     */
    fun getAccessToken(): String? {
        return getAuthState()?.accessToken
    }
    
    /**
     * Save the OSM username
     */
    fun saveUsername(username: String) {
        encryptedPrefs.edit()
            .putString(KEY_USERNAME, username)
            .apply()
    }
    
    /**
     * Get the saved OSM username
     */
    fun getUsername(): String? {
        return encryptedPrefs.getString(KEY_USERNAME, null)
    }
    
    /**
     * Clear all stored authentication data (logout)
     */
    fun clearAuth() {
        encryptedPrefs.edit()
            .remove(KEY_AUTH_STATE)
            .remove(KEY_USERNAME)
            .apply()
    }
}
