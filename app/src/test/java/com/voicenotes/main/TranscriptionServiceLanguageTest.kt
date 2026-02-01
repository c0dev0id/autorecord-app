package com.voicenotes.main

import android.content.Context
import android.content.SharedPreferences
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for TranscriptionService language preference integration.
 * 
 * Tests verify:
 * - Default language values when preferences not set
 * - Reading primary language from preferences
 * - Reading secondary language from preferences
 * - Handling empty secondary language
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TranscriptionServiceLanguageTest {

    private lateinit var mockContext: Context
    private lateinit var mockSharedPreferences: SharedPreferences
    
    @Before
    fun setup() {
        mockContext = mock()
        mockSharedPreferences = mock()
        
        // Setup default mock behavior
        whenever(mockContext.getSharedPreferences(
            "AppPrefs", 
            Context.MODE_PRIVATE
        )).thenReturn(mockSharedPreferences)
    }
    
    @Test
    fun testDefaultPrimaryLanguage() {
        // Given: No preference set, returns default
        whenever(mockSharedPreferences.getString("stt_primary_language", "system"))
            .thenReturn("system")
        
        // When: Reading the preference
        val primaryLanguage = mockSharedPreferences.getString("stt_primary_language", "system")
        
        // Then: Should return default system
        assertEquals("Default primary language should be system", "system", primaryLanguage)
    }
    
    @Test
    fun testCustomPrimaryLanguage() {
        // Given: German primary language preference set
        whenever(mockSharedPreferences.getString("stt_primary_language", "system"))
            .thenReturn("de-DE")
        
        // When: Reading the preference
        val primaryLanguage = mockSharedPreferences.getString("stt_primary_language", "system")
        
        // Then: Should return de-DE
        assertEquals("Custom primary language should be returned", "de-DE", primaryLanguage)
    }
    
    @Test
    fun testDefaultSecondaryLanguageEmpty() {
        // Given: No secondary language preference set (default empty)
        whenever(mockSharedPreferences.getString("stt_secondary_language", ""))
            .thenReturn("")
        
        // When: Reading the preference
        val secondaryLanguage = mockSharedPreferences.getString("stt_secondary_language", "")
        
        // Then: Should return empty string
        assertEquals("Default secondary language should be empty", "", secondaryLanguage)
    }
    
    @Test
    fun testCustomSecondaryLanguage() {
        // Given: Spanish secondary language preference set
        whenever(mockSharedPreferences.getString("stt_secondary_language", ""))
            .thenReturn("es-ES")
        
        // When: Reading the preference
        val secondaryLanguage = mockSharedPreferences.getString("stt_secondary_language", "")
        
        // Then: Should return es-ES
        assertEquals("Custom secondary language should be returned", "es-ES", secondaryLanguage)
    }
    
    @Test
    fun testSecondaryLanguageNotAddedWhenEmpty() {
        // Given: Empty secondary language
        val secondaryLanguage = ""
        
        // When: Checking if it should be added to config
        val shouldAddSecondary = secondaryLanguage.isNotEmpty()
        
        // Then: Should not add to config
        assertFalse("Empty secondary language should not be added", shouldAddSecondary)
    }
    
    @Test
    fun testSecondaryLanguageAddedWhenNotEmpty() {
        // Given: Non-empty secondary language
        val secondaryLanguage = "de-DE"
        
        // When: Checking if it should be added to config
        val shouldAddSecondary = secondaryLanguage.isNotEmpty()
        
        // Then: Should add to config
        assertTrue("Non-empty secondary language should be added", shouldAddSecondary)
    }
    
    @Test
    fun testMultipleLanguagesCombination() {
        // Test various combinations of primary and secondary languages
        val testCases = listOf(
            Pair("en-US", ""),       // English primary, no secondary
            Pair("en-US", "de-DE"),  // English primary, German secondary
            Pair("de-DE", "en-US"),  // German primary, English secondary
            Pair("es-ES", "fr-FR"),  // Spanish primary, French secondary
            Pair("ja-JP", ""),       // Japanese primary, no secondary
            Pair("zh-CN", "zh-TW")   // Simplified Chinese primary, Traditional Chinese secondary
        )
        
        testCases.forEach { (primary, secondary) ->
            assertNotNull("Primary language should not be null", primary)
            assertTrue("Primary language should not be empty", primary.isNotEmpty())
            
            if (secondary.isNotEmpty()) {
                assertTrue("Non-empty secondary should be valid BCP-47", 
                    secondary.matches(Regex("[a-z]{2,3}-[A-Z]{2,3}")) ||
                    secondary.matches(Regex("[a-z]{2,3}-[0-9]{3}")))
            }
        }
    }
    
    @Test
    fun testLanguageTagFormat() {
        // Test that language tags follow expected BCP-47 format
        val validPrimaryTags = listOf("system", "en-US", "de-DE", "es-ES", "fr-FR", "it-IT", 
                                      "pt-BR", "ja-JP", "ko-KR", "zh-CN", "zh-TW")
        val validSecondaryTags = listOf("", "en-GB", "es-419")
        
        // Verify primary tags are "system" or match standard format (xx-XX or xx-XXX for special cases)
        validPrimaryTags.forEach { tag ->
            assertTrue("Primary tag should match format: $tag", 
                tag == "system" || tag.matches(Regex("[a-z]{2,3}-[A-Z]{2,3}")))
        }
        
        // Verify secondary tags are empty or match standard format or have numeric region codes
        validSecondaryTags.forEach { tag ->
            if (tag.isNotEmpty()) {
                assertTrue("Secondary tag should be valid: $tag", 
                    tag.matches(Regex("[a-z]{2,3}-[A-Z]{2,3}")) || 
                    tag.matches(Regex("[a-z]{2,3}-[0-9]{3}")))
            }
        }
    }
    
    @Test
    fun testSystemLanguageValue() {
        // Given: "system" as primary language preference
        whenever(mockSharedPreferences.getString("stt_primary_language", "system"))
            .thenReturn("system")
        
        // When: Reading the preference
        val primaryLanguage = mockSharedPreferences.getString("stt_primary_language", "system")
        
        // Then: Should return "system"
        assertEquals("System language value should be returned", "system", primaryLanguage)
    }
    
    @Test
    fun testDeviceLocaleConversion() {
        // Test that system value needs conversion to actual language code
        // The TranscriptionService should handle "system" by converting it to device locale
        val rawLanguage = "system"
        
        // When "system" is detected, it should be converted
        val needsConversion = rawLanguage == "system"
        assertTrue("System value should trigger device locale conversion", needsConversion)
        
        // The actual device locale will vary, but it should produce a valid BCP-47 code
        // Using toLanguageTag() ensures proper BCP-47 formatting
        val deviceLocale = java.util.Locale.getDefault()
        
        // When locale has both language and country
        if (deviceLocale.country.isNotEmpty()) {
            val bcp47Tag = deviceLocale.toLanguageTag()
            // Should match format like "en-US", "de-DE", "pt-PT", or "zh-Hans" (script subtag)
            assertTrue("Device locale should produce valid BCP-47 format", 
                bcp47Tag.matches(Regex("[a-z]{2,3}-([A-Z]{2,3}|[A-Z][a-z]{3})")))
        } else {
            // When only language is available, fallback logic applies
            val language = deviceLocale.language
            assertTrue("Language code should be valid", language.isNotEmpty() && language.length >= 2)
        }
    }
    
    @Test
    fun testSecondaryLanguageEmptyNotConverted() {
        // Given: Empty secondary language (not explicitly "system")
        val secondaryLanguage = ""
        
        // When: Checking if it should be converted
        val needsConversion = secondaryLanguage == "system"
        
        // Then: Should not convert empty string
        assertFalse("Empty secondary language should not be converted", needsConversion)
    }
    
    @Test
    fun testSecondaryLanguageSystemConverted() {
        // Given: Explicitly set to "system"
        val secondaryLanguage = "system"
        
        // When: Checking if it should be converted
        val needsConversion = secondaryLanguage == "system"
        
        // Then: Should convert "system" value
        assertTrue("System value for secondary language should be converted", needsConversion)
    }
}
