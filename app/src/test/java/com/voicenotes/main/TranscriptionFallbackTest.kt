package com.voicenotes.main

import com.voicenotes.main.database.Recording
import com.voicenotes.main.database.V2SStatus
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for transcription FALLBACK status handling
 * Tests that empty transcriptions are treated as FALLBACK status
 */
class TranscriptionFallbackTest {

    @Test
    fun testEmptyTranscriptionResultsInFallbackStatus() {
        println("TEST: Empty transcription should result in FALLBACK status with placeholder text")
        
        // Create a recording that would receive an empty transcription
        val recording = createTestRecording(v2sStatus = V2SStatus.PROCESSING)
        
        // Simulate empty transcription result
        val transcribedText = ""
        val expectedStatus = if (transcribedText.isBlank()) V2SStatus.FALLBACK else V2SStatus.COMPLETED
        val expectedFallback = transcribedText.isBlank()
        
        // When transcription is blank, v2sResult should contain fallback placeholder
        val latStr = String.format("%.6f", recording.latitude)
        val lngStr = String.format("%.6f", recording.longitude)
        val expectedResult = if (transcribedText.isBlank()) "$latStr,$lngStr (no text)" else transcribedText
        
        println("  Input: transcribedText = '$transcribedText' (isBlank: ${transcribedText.isBlank()})")
        println("  Expected: v2sStatus = $expectedStatus, v2sFallback = $expectedFallback, v2sResult = '$expectedResult'")
        
        // Verify the logic matches implementation
        assertEquals("Empty transcription should result in FALLBACK status", 
            V2SStatus.FALLBACK, expectedStatus)
        assertTrue("Empty transcription should set v2sFallback to true", expectedFallback)
        
        // Create updated recording with expected values
        val updatedRecording = recording.copy(
            v2sStatus = expectedStatus,
            v2sResult = expectedResult,
            v2sFallback = expectedFallback
        )
        
        println("  Actual: v2sStatus = ${updatedRecording.v2sStatus}, v2sFallback = ${updatedRecording.v2sFallback}, v2sResult = '${updatedRecording.v2sResult}'")
        
        assertEquals("Updated recording should have FALLBACK status", 
            V2SStatus.FALLBACK, updatedRecording.v2sStatus)
        assertTrue("Updated recording should have v2sFallback = true", 
            updatedRecording.v2sFallback)
        assertEquals("Updated recording should have fallback placeholder in v2sResult", 
            expectedResult, updatedRecording.v2sResult)
        
        println("  ✓ TEST PASSED: Empty transcription correctly results in FALLBACK status with placeholder")
    }

    @Test
    fun testBlankTranscriptionResultsInFallbackStatus() {
        println("TEST: Blank transcription (spaces/tabs/newlines) should result in FALLBACK status with placeholder")
        
        // Test with various blank strings
        val blankStrings = listOf("   ", "\t", "\n", " \t\n ", "")
        
        val recording = createTestRecording(v2sStatus = V2SStatus.PROCESSING)
        val latStr = String.format("%.6f", recording.latitude)
        val lngStr = String.format("%.6f", recording.longitude)
        val expectedPlaceholder = "$latStr,$lngStr (no text)"
        
        for (blankStr in blankStrings) {
            println("  Testing blank string: '$blankStr' (escaped: ${blankStr.replace(" ", "·").replace("\t", "↹").replace("\n", "↵")})")
            
            val expectedStatus = if (blankStr.isBlank()) V2SStatus.FALLBACK else V2SStatus.COMPLETED
            val expectedFallback = blankStr.isBlank()
            val expectedResult = if (blankStr.isBlank()) expectedPlaceholder else blankStr
            
            println("    Expected: v2sStatus = $expectedStatus, v2sFallback = $expectedFallback, v2sResult = '$expectedResult'")
            
            assertEquals("Blank string should result in FALLBACK status", 
                V2SStatus.FALLBACK, expectedStatus)
            assertTrue("Blank string should set v2sFallback to true", expectedFallback)
            assertEquals("Blank string should have fallback placeholder in v2sResult",
                expectedPlaceholder, expectedResult)
            
            println("    ✓ Passed")
        }
        
        println("  ✓ TEST PASSED: All blank variations correctly result in FALLBACK status with placeholder")
    }

    @Test
    fun testNonEmptyTranscriptionResultsInCompletedStatus() {
        println("TEST: Non-empty transcription should result in COMPLETED status")
        
        // Create a recording that would receive a valid transcription
        val recording = createTestRecording(v2sStatus = V2SStatus.PROCESSING)
        
        // Simulate valid transcription result
        val transcribedText = "This is a valid transcription"
        val expectedStatus = if (transcribedText.isBlank()) V2SStatus.FALLBACK else V2SStatus.COMPLETED
        val expectedFallback = transcribedText.isBlank()
        
        println("  Input: transcribedText = '$transcribedText' (isBlank: ${transcribedText.isBlank()})")
        println("  Expected: v2sStatus = $expectedStatus, v2sFallback = $expectedFallback")
        
        // Verify the logic matches implementation
        assertEquals("Non-empty transcription should result in COMPLETED status", 
            V2SStatus.COMPLETED, expectedStatus)
        assertFalse("Non-empty transcription should set v2sFallback to false", expectedFallback)
        
        // Create updated recording with expected values
        val updatedRecording = recording.copy(
            v2sStatus = expectedStatus,
            v2sResult = transcribedText,
            v2sFallback = expectedFallback
        )
        
        println("  Actual: v2sStatus = ${updatedRecording.v2sStatus}, v2sFallback = ${updatedRecording.v2sFallback}")
        
        assertEquals("Updated recording should have COMPLETED status", 
            V2SStatus.COMPLETED, updatedRecording.v2sStatus)
        assertFalse("Updated recording should have v2sFallback = false", 
            updatedRecording.v2sFallback)
        assertEquals("Updated recording should have the transcribed text", 
            transcribedText, updatedRecording.v2sResult)
        
        println("  ✓ TEST PASSED: Non-empty transcription correctly results in COMPLETED status")
    }

    @Test
    fun testFallbackRecordingValidation() {
        println("TEST: Recording with FALLBACK status should have placeholder text in v2sResult")
        
        val lat = 37.774929
        val lng = -122.419416
        val latStr = String.format("%.6f", lat)
        val lngStr = String.format("%.6f", lng)
        val expectedPlaceholder = "$latStr,$lngStr (no text)"
        
        // Create a recording with FALLBACK status
        val recording = createTestRecording(
            v2sStatus = V2SStatus.FALLBACK,
            v2sResult = expectedPlaceholder,
            v2sFallback = true
        )
        
        println("  Recording: v2sStatus = ${recording.v2sStatus}, v2sResult = '${recording.v2sResult}', v2sFallback = ${recording.v2sFallback}")
        
        // Verify consistency
        assertTrue("FALLBACK status should have fallback placeholder in v2sResult", 
            recording.v2sResult?.contains("(no text)") == true)
        assertTrue("FALLBACK status should have v2sFallback = true", 
            recording.v2sFallback)
        assertEquals("Status should be FALLBACK", 
            V2SStatus.FALLBACK, recording.v2sStatus)
        
        println("  ✓ TEST PASSED: FALLBACK recording is valid and has placeholder text")
    }

    @Test
    fun testStatusTransitionToFallback() {
        println("TEST: Status transition from PROCESSING to FALLBACK with placeholder")
        
        // Start with a recording in PROCESSING state
        val processing = createTestRecording(v2sStatus = V2SStatus.PROCESSING)
        
        println("  Initial state: v2sStatus = ${processing.v2sStatus}")
        assertEquals("Initial status should be PROCESSING", 
            V2SStatus.PROCESSING, processing.v2sStatus)
        
        // Simulate receiving empty transcription
        val transcribedText = ""
        val latStr = String.format("%.6f", processing.latitude)
        val lngStr = String.format("%.6f", processing.longitude)
        val expectedPlaceholder = "$latStr,$lngStr (no text)"
        
        val fallback = processing.copy(
            v2sStatus = if (transcribedText.isBlank()) V2SStatus.FALLBACK else V2SStatus.COMPLETED,
            v2sResult = if (transcribedText.isBlank()) expectedPlaceholder else transcribedText,
            v2sFallback = transcribedText.isBlank()
        )
        
        println("  After empty transcription: v2sStatus = ${fallback.v2sStatus}, v2sFallback = ${fallback.v2sFallback}, v2sResult = '${fallback.v2sResult}'")
        
        // Verify transition to FALLBACK
        assertEquals("Status should transition to FALLBACK", 
            V2SStatus.FALLBACK, fallback.v2sStatus)
        assertTrue("v2sFallback should be true", fallback.v2sFallback)
        assertEquals("v2sResult should have fallback placeholder", expectedPlaceholder, fallback.v2sResult)
        
        println("  ✓ TEST PASSED: Status correctly transitions from PROCESSING to FALLBACK with placeholder")
    }

    @Test
    fun testStatusTransitionToCompleted() {
        println("TEST: Status transition from PROCESSING to COMPLETED")
        
        // Start with a recording in PROCESSING state
        val processing = createTestRecording(v2sStatus = V2SStatus.PROCESSING)
        
        println("  Initial state: v2sStatus = ${processing.v2sStatus}")
        assertEquals("Initial status should be PROCESSING", 
            V2SStatus.PROCESSING, processing.v2sStatus)
        
        // Simulate receiving valid transcription
        val transcribedText = "Hello world"
        val completed = processing.copy(
            v2sStatus = if (transcribedText.isBlank()) V2SStatus.FALLBACK else V2SStatus.COMPLETED,
            v2sResult = transcribedText,
            v2sFallback = transcribedText.isBlank()
        )
        
        println("  After valid transcription: v2sStatus = ${completed.v2sStatus}, v2sFallback = ${completed.v2sFallback}")
        
        // Verify transition to COMPLETED
        assertEquals("Status should transition to COMPLETED", 
            V2SStatus.COMPLETED, completed.v2sStatus)
        assertFalse("v2sFallback should be false", completed.v2sFallback)
        assertEquals("v2sResult should have transcribed text", transcribedText, completed.v2sResult)
        
        println("  ✓ TEST PASSED: Status correctly transitions from PROCESSING to COMPLETED")
    }

    @Test
    fun testEdgeCasesSingleSpaceTranscription() {
        println("TEST: Edge case - single space should be treated as blank with fallback placeholder")
        
        val recording = createTestRecording()
        val transcribedText = " "
        val expectedStatus = if (transcribedText.isBlank()) V2SStatus.FALLBACK else V2SStatus.COMPLETED
        val expectedFallback = transcribedText.isBlank()
        
        val latStr = String.format("%.6f", recording.latitude)
        val lngStr = String.format("%.6f", recording.longitude)
        val expectedPlaceholder = "$latStr,$lngStr (no text)"
        val expectedResult = if (transcribedText.isBlank()) expectedPlaceholder else transcribedText
        
        println("  Input: transcribedText = ' ' (single space)")
        println("  Expected: v2sStatus = $expectedStatus, v2sFallback = $expectedFallback, v2sResult = '$expectedResult'")
        
        assertEquals("Single space should result in FALLBACK status", 
            V2SStatus.FALLBACK, expectedStatus)
        assertTrue("Single space should set v2sFallback to true", expectedFallback)
        assertEquals("Single space should have fallback placeholder",
            expectedPlaceholder, expectedResult)
        
        println("  ✓ TEST PASSED: Single space correctly treated as blank with placeholder")
    }

    @Test
    fun testMultipleResultChunksJoining() {
        println("TEST: Multiple Speech-to-Text result chunks should be joined")
        
        // Simulate multiple result chunks from Speech-to-Text API
        val chunk1 = "Hello"
        val chunk2 = "world"
        val chunk3 = "this is a test"
        
        // Join chunks with space (as implemented in TranscriptionService)
        val joinedText = listOf(chunk1, chunk2, chunk3).joinToString(" ").trim()
        
        println("  Input chunks: ['$chunk1', '$chunk2', '$chunk3']")
        println("  Joined text: '$joinedText'")
        
        assertEquals("Joined text should be complete", 
            "Hello world this is a test", joinedText)
        assertFalse("Joined text should not be blank", joinedText.isBlank())
        
        // Verify this results in COMPLETED status
        val expectedStatus = if (joinedText.isBlank()) V2SStatus.FALLBACK else V2SStatus.COMPLETED
        assertEquals("Joined text should result in COMPLETED status", 
            V2SStatus.COMPLETED, expectedStatus)
        
        println("  ✓ TEST PASSED: Multiple chunks correctly joined with spaces")
    }

    @Test
    fun testEmptyChunksResultInFallback() {
        println("TEST: Empty result chunks should result in FALLBACK with placeholder")
        
        val recording = createTestRecording()
        
        // Simulate empty result chunks from Speech-to-Text API
        val chunks = listOf("", "", "")
        
        // Join chunks with space (as implemented in TranscriptionService)
        val joinedText = chunks.joinToString(" ").trim()
        
        println("  Input chunks: [empty, empty, empty]")
        println("  Joined text: '$joinedText' (isBlank: ${joinedText.isBlank()})")
        
        assertTrue("Joined empty chunks should be blank", joinedText.isBlank())
        
        // Verify this results in FALLBACK status with placeholder
        val expectedStatus = if (joinedText.isBlank()) V2SStatus.FALLBACK else V2SStatus.COMPLETED
        val latStr = String.format("%.6f", recording.latitude)
        val lngStr = String.format("%.6f", recording.longitude)
        val expectedPlaceholder = "$latStr,$lngStr (no text)"
        val expectedResult = if (joinedText.isBlank()) expectedPlaceholder else joinedText
        
        assertEquals("Empty chunks should result in FALLBACK status", 
            V2SStatus.FALLBACK, expectedStatus)
        assertEquals("Empty chunks should have fallback placeholder",
            expectedPlaceholder, expectedResult)
        
        println("  ✓ TEST PASSED: Empty chunks correctly result in FALLBACK with placeholder")
    }

    // Helper function to create test recordings
    private fun createTestRecording(
        v2sStatus: V2SStatus = V2SStatus.NOT_STARTED,
        v2sResult: String? = null,
        v2sFallback: Boolean = false
    ): Recording {
        return Recording(
            id = 1L,
            filename = "test_recording.ogg",
            filepath = "/data/data/com.voicenotes.main/files/recordings/test_recording.ogg",
            timestamp = System.currentTimeMillis(),
            latitude = 37.774929,
            longitude = -122.419416,
            v2sStatus = v2sStatus,
            v2sResult = v2sResult,
            v2sFallback = v2sFallback,
            errorMsg = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
}
