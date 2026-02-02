package com.voicenotes.main

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for VersionUtils.
 * 
 * Tests cover:
 * - Version string is not empty
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class VersionUtilsTest {
    
    @Test
    fun testGetVersionString_returnsNonEmptyString() {
        // When: Getting the version string
        val version = VersionUtils.getVersionString()
        
        // Then: Should not be empty
        assertNotNull("Version string should not be null", version)
        assertTrue("Version string should not be empty", version.isNotEmpty())
    }
}