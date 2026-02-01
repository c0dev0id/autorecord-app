package com.voicenotes.main

import com.voicenotes.main.database.Recording
import com.voicenotes.main.database.V2SStatus
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for Recording data validation
 * Tests validation of recording data fields and constraints
 */
class RecordingValidationTest {

    @Test
    fun testValidRecording() {
        // Create a valid recording
        val recording = Recording(
            id = 1L,
            filename = "37.774929,-122.419416_20240126_120000.ogg",
            filepath = "/data/data/com.voicenotes.main/files/recordings/37.774929,-122.419416_20240126_120000.ogg",
            timestamp = System.currentTimeMillis(),
            latitude = 37.774929,
            longitude = -122.419416,
            v2sStatus = V2SStatus.NOT_STARTED,
            v2sResult = null,
            v2sFallback = false,
            errorMsg = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        assertTrue("Valid recording should pass validation", validateRecording(recording))
        assertNull("Valid recording should have no validation error", getValidationError(recording))
    }

    @Test
    fun testEmptyAudioFilePath() {
        // Recording with empty filepath
        val recording = Recording(
            id = 1L,
            filename = "test.ogg",
            filepath = "",
            timestamp = System.currentTimeMillis(),
            latitude = 37.774929,
            longitude = -122.419416,
            v2sStatus = V2SStatus.NOT_STARTED,
            v2sResult = null,
            v2sFallback = false,
            errorMsg = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        assertFalse("Recording with empty filepath should fail validation", validateRecording(recording))
        assertNotNull("Empty filepath should have validation error", getValidationError(recording))
        assertTrue("Error should mention filepath", getValidationError(recording)!!.contains("filepath"))
    }

    @Test
    fun testInvalidCoordinateRange() {
        // Recording with invalid latitude (> 90)
        val recording1 = Recording(
            id = 1L,
            filename = "test.ogg",
            filepath = "/path/to/file.ogg",
            timestamp = System.currentTimeMillis(),
            latitude = 91.0,
            longitude = 0.0,
            v2sStatus = V2SStatus.NOT_STARTED,
            v2sResult = null,
            v2sFallback = false,
            errorMsg = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        assertFalse("Recording with latitude > 90 should fail validation", validateRecording(recording1))
        assertTrue("Error should mention latitude", getValidationError(recording1)!!.contains("latitude"))
        
        // Recording with invalid latitude (< -90)
        val recording2 = recording1.copy(latitude = -91.0)
        assertFalse("Recording with latitude < -90 should fail validation", validateRecording(recording2))
        
        // Recording with invalid longitude (> 180)
        val recording3 = recording1.copy(latitude = 0.0, longitude = 181.0)
        assertFalse("Recording with longitude > 180 should fail validation", validateRecording(recording3))
        assertTrue("Error should mention longitude", getValidationError(recording3)!!.contains("longitude"))
        
        // Recording with invalid longitude (< -180)
        val recording4 = recording1.copy(latitude = 0.0, longitude = -181.0)
        assertFalse("Recording with longitude < -180 should fail validation", validateRecording(recording4))
        
        // Edge cases - valid boundaries
        val recording5 = recording1.copy(latitude = 90.0, longitude = 180.0)
        assertTrue("Recording at boundary coordinates should be valid", validateRecording(recording5))
        
        val recording6 = recording1.copy(latitude = -90.0, longitude = -180.0)
        assertTrue("Recording at negative boundary coordinates should be valid", validateRecording(recording6))
    }

    @Test
    fun testNullTranscription() {
        // Recording with null transcription (valid, not started)
        val recording1 = Recording(
            id = 1L,
            filename = "test.ogg",
            filepath = "/path/to/file.ogg",
            timestamp = System.currentTimeMillis(),
            latitude = 37.774929,
            longitude = -122.419416,
            v2sStatus = V2SStatus.NOT_STARTED,
            v2sResult = null,
            v2sFallback = false,
            errorMsg = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        assertTrue("Recording with null transcription and NOT_STARTED status should be valid", 
            validateRecording(recording1))
        
        // Recording marked as COMPLETED but null transcription (invalid)
        val recording2 = recording1.copy(v2sStatus = V2SStatus.COMPLETED, v2sResult = null)
        assertFalse("Recording with COMPLETED status but null transcription should be invalid",
            validateRecording(recording2))
        assertTrue("Error should mention transcription", 
            getValidationError(recording2)!!.contains("transcription") || 
            getValidationError(recording2)!!.contains("v2sResult"))
        
        // Recording with empty transcription and COMPLETED status (valid - could be silent audio)
        val recording3 = recording1.copy(v2sStatus = V2SStatus.COMPLETED, v2sResult = "")
        assertTrue("Recording with COMPLETED status and empty transcription should be valid",
            validateRecording(recording3))
    }

    @Test
    fun testVeryLongTranscription() {
        // Recording with very long transcription (should be valid but noted)
        val longText = "A".repeat(10000) // 10,000 characters
        val recording = Recording(
            id = 1L,
            filename = "test.ogg",
            filepath = "/path/to/file.ogg",
            timestamp = System.currentTimeMillis(),
            latitude = 37.774929,
            longitude = -122.419416,
            v2sStatus = V2SStatus.COMPLETED,
            v2sResult = longText,
            v2sFallback = false,
            errorMsg = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        assertTrue("Recording with very long transcription should be valid", validateRecording(recording))
        
        // Recording with extremely long transcription (warn but still valid)
        val extremelyLongText = "A".repeat(100000) // 100,000 characters
        val recording2 = recording.copy(v2sResult = extremelyLongText)
        assertTrue("Recording with extremely long transcription should still be valid", 
            validateRecording(recording2))
    }

    @Test
    fun testInvalidTimestamp() {
        // Recording with negative timestamp (invalid)
        val recording1 = Recording(
            id = 1L,
            filename = "test.ogg",
            filepath = "/path/to/file.ogg",
            timestamp = -1L,
            latitude = 37.774929,
            longitude = -122.419416,
            v2sStatus = V2SStatus.NOT_STARTED,
            v2sResult = null,
            v2sFallback = false,
            errorMsg = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        assertFalse("Recording with negative timestamp should be invalid", validateRecording(recording1))
        assertTrue("Error should mention timestamp", getValidationError(recording1)!!.contains("timestamp"))
        
        // Recording with zero timestamp (valid - Unix epoch)
        val recording2 = recording1.copy(timestamp = 0L)
        assertTrue("Recording with zero timestamp should be valid", validateRecording(recording2))
        
        // Recording with future timestamp (valid - could be system clock issue)
        val futureTimestamp = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000) // 1 year ahead
        val recording3 = recording1.copy(timestamp = futureTimestamp)
        assertTrue("Recording with future timestamp should be valid", validateRecording(recording3))
    }

    @Test
    fun testEmptyFilename() {
        // Recording with empty filename (invalid)
        val recording = Recording(
            id = 1L,
            filename = "",
            filepath = "/path/to/file.ogg",
            timestamp = System.currentTimeMillis(),
            latitude = 37.774929,
            longitude = -122.419416,
            v2sStatus = V2SStatus.NOT_STARTED,
            v2sResult = null,
            v2sFallback = false,
            errorMsg = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        assertFalse("Recording with empty filename should be invalid", validateRecording(recording))
        assertTrue("Error should mention filename", getValidationError(recording)!!.contains("filename"))
    }

    // Helper functions for validation
    private fun validateRecording(recording: Recording): Boolean {
        return getValidationError(recording) == null
    }

    private fun getValidationError(recording: Recording): String? {
        // Validate filepath
        if (recording.filepath.isBlank()) {
            return "Invalid recording: filepath cannot be empty"
        }
        
        // Validate filename
        if (recording.filename.isBlank()) {
            return "Invalid recording: filename cannot be empty"
        }
        
        // Validate coordinates
        if (recording.latitude < -90.0 || recording.latitude > 90.0) {
            return "Invalid recording: latitude must be between -90 and 90"
        }
        if (recording.longitude < -180.0 || recording.longitude > 180.0) {
            return "Invalid recording: longitude must be between -180 and 180"
        }
        
        // Validate timestamp
        if (recording.timestamp < 0) {
            return "Invalid recording: timestamp cannot be negative"
        }
        
        // Validate transcription status consistency
        if (recording.v2sStatus == V2SStatus.COMPLETED && recording.v2sResult == null) {
            return "Invalid recording: COMPLETED status requires non-null v2sResult"
        }
        
        return null
    }
}
