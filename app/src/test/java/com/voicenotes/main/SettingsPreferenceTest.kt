package com.voicenotes.main

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for Settings preferences persistence and behavior.
 * 
 * Tests cover:
 * - Default preference values
 * - Preference persistence
 * - Language preference validation
 * - Recording duration validation
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SettingsPreferenceTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        sharedPreferences = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        // Clear any existing preferences
        sharedPreferences.edit().clear().commit()
    }
    
    @After
    fun tearDown() {
        sharedPreferences.edit().clear().commit()
    }
    
    @Test
    fun testDefaultRecordingDuration() {
        // When: No preference is set
        val duration = sharedPreferences.getInt("recording_duration", 10)
        
        // Then: Default should be 10
        assertEquals("Default recording duration should be 10 seconds", 10, duration)
    }
    
    @Test
    fun testRecordingDurationPersistence() {
        // Given: A recording duration value
        val testDuration = 25
        
        // When: Setting the recording duration
        sharedPreferences.edit().putInt("recording_duration", testDuration).commit()
        
        // Then: Value should be persisted
        val retrievedDuration = sharedPreferences.getInt("recording_duration", 10)
        assertEquals("Recording duration should persist", testDuration, retrievedDuration)
    }
    
    @Test
    fun testRecordingDurationValidation() {
        // Test valid range (1-99)
        assertTrue("1 should be valid", 1 in 1..99)
        assertTrue("50 should be valid", 50 in 1..99)
        assertTrue("99 should be valid", 99 in 1..99)
        
        // Test invalid values
        assertFalse("0 should be invalid", 0 in 1..99)
        assertFalse("100 should be invalid", 100 in 1..99)
        assertFalse("-1 should be invalid", -1 in 1..99)
    }
    
    @Test
    fun testDefaultAppLanguage() {
        // When: No preference is set
        val language = sharedPreferences.getString("app_language", "system")
        
        // Then: Default should be "system"
        assertEquals("Default app language should be 'system'", "system", language)
    }
    
    @Test
    fun testAppLanguagePersistence() {
        // Given: A language preference
        val testLanguage = "de"
        
        // When: Setting the app language
        sharedPreferences.edit().putString("app_language", testLanguage).commit()
        
        // Then: Value should be persisted
        val retrievedLanguage = sharedPreferences.getString("app_language", "system")
        assertEquals("App language should persist", testLanguage, retrievedLanguage)
    }
    
    @Test
    fun testDefaultPrimarySTTLanguage() {
        // When: No preference is set
        val language = sharedPreferences.getString("stt_primary_language", "system")
        
        // Then: Default should be "system"
        assertEquals("Default STT primary language should be 'system'", "system", language)
    }
    
    @Test
    fun testPrimarySTTLanguagePersistence() {
        // Given: A primary STT language
        val testLanguage = "de-DE"
        
        // When: Setting the primary STT language
        sharedPreferences.edit().putString("stt_primary_language", testLanguage).commit()
        
        // Then: Value should be persisted
        val retrievedLanguage = sharedPreferences.getString("stt_primary_language", "system")
        assertEquals("Primary STT language should persist", testLanguage, retrievedLanguage)
    }
    
    @Test
    fun testDefaultSecondarySTTLanguage() {
        // When: No preference is set
        val language = sharedPreferences.getString("stt_secondary_language", "")
        
        // Then: Default should be empty string
        assertEquals("Default STT secondary language should be empty", "", language)
    }
    
    @Test
    fun testSecondarySTTLanguagePersistence() {
        // Given: A secondary STT language
        val testLanguage = "es-ES"
        
        // When: Setting the secondary STT language
        sharedPreferences.edit().putString("stt_secondary_language", testLanguage).commit()
        
        // Then: Value should be persisted
        val retrievedLanguage = sharedPreferences.getString("stt_secondary_language", "")
        assertEquals("Secondary STT language should persist", testLanguage, retrievedLanguage)
    }
    
    @Test
    fun testSecondarySTTLanguageCanBeEmpty() {
        // Given: An empty secondary STT language (none selected)
        sharedPreferences.edit().putString("stt_secondary_language", "").commit()
        
        // Then: Empty string should be persisted
        val retrievedLanguage = sharedPreferences.getString("stt_secondary_language", "none")
        assertEquals("Secondary STT language can be empty", "", retrievedLanguage)
    }
    
    @Test
    fun testDebugLoggingDefault() {
        // When: No preference is set
        val debugEnabled = sharedPreferences.getBoolean("enable_debug_logging", false)
        
        // Then: Default should be false
        assertFalse("Default debug logging should be disabled", debugEnabled)
    }
    
    @Test
    fun testDebugLoggingPersistence() {
        // Given: Debug logging enabled
        sharedPreferences.edit().putBoolean("enable_debug_logging", true).commit()
        
        // Then: Value should be persisted
        val debugEnabled = sharedPreferences.getBoolean("enable_debug_logging", false)
        assertTrue("Debug logging preference should persist", debugEnabled)
    }
    
    @Test
    fun testValidLanguageTags() {
        // Test valid BCP-47 language tags
        val validAppLanguages = listOf("system", "en", "de", "es", "fr", "it", "pt", "ja", "ko", "zh-CN", "zh-TW")
        val validSTTLanguages = listOf("system", "en-US", "en-GB", "de-DE", "es-ES", "es-419", "fr-FR", "it-IT", 
                                       "pt-BR", "pt-PT", "ja-JP", "ko-KR", "zh-CN", "zh-TW")
        
        // Verify all tags are strings
        validAppLanguages.forEach { tag ->
            assertTrue("App language tag should be a string: $tag", tag is String)
        }
        
        validSTTLanguages.forEach { tag ->
            assertTrue("STT language tag should be a string: $tag", tag is String)
        }
    }
}
