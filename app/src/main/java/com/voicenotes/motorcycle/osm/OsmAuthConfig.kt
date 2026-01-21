package com.voicenotes.motorcycle.osm

import net.openid.appauth.AuthorizationServiceConfiguration
import android.net.Uri

/**
 * OAuth 2.0 configuration for OpenStreetMap
 * Based on: https://wiki.openstreetmap.org/wiki/OAuth_2.0
 */
object OsmAuthConfig {
    // OAuth endpoints
    private const val AUTHORIZATION_ENDPOINT = "https://www.openstreetmap.org/oauth2/authorize"
    private const val TOKEN_ENDPOINT = "https://www.openstreetmap.org/oauth2/token"
    
    // Client configuration
    // Note: OAuth 2.0 client IDs are meant to be public and don't need to be secret
    // They identify the application to the authorization server
    const val CLIENT_ID = "motorcycle-voice-notes"
    const val REDIRECT_URI = "com.voicenotes.motorcycle://oauth2redirect"
    
    // Scopes required
    const val SCOPE = "write_notes read_prefs"
    
    fun getServiceConfig(): AuthorizationServiceConfiguration {
        return AuthorizationServiceConfiguration(
            Uri.parse(AUTHORIZATION_ENDPOINT),
            Uri.parse(TOKEN_ENDPOINT)
        )
    }
}
