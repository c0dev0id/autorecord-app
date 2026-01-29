package com.voicenotes.motorcycle

import com.voicenotes.motorcycle.database.Recording
import com.voicenotes.motorcycle.database.V2SStatus
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Unit tests for RecordingManager UI logic
 * Tests download button visibility and processing animation conditions
 */
class RecordingManagerUITest {

    @Test
    fun testDownloadButtonVisibility_WithExistingFile() {
        // Create a temporary file for testing
        val tempFile = File.createTempFile("test_recording", ".ogg")
        tempFile.deleteOnExit()
        
        // Test all statuses with existing file
        val testCases = listOf(
            V2SStatus.NOT_STARTED,
            V2SStatus.PROCESSING,
            V2SStatus.COMPLETED,
            V2SStatus.ERROR,
            V2SStatus.FALLBACK,
            V2SStatus.DISABLED
        )
        
        testCases.forEach { status ->
            val recording = createRecording(
                filepath = tempFile.absolutePath,
                v2sStatus = status,
                v2sResult = if (status == V2SStatus.COMPLETED) "transcribed text" else null
            )
            
            val shouldShow = shouldShowDownloadButton(recording)
            
            assertTrue(
                "Download button should be visible for $status when file exists",
                shouldShow
            )
        }
    }

    @Test
    fun testDownloadButtonVisibility_WithNonExistentFile() {
        // Use a file path that doesn't exist
        val nonExistentPath = "/tmp/nonexistent_file_${System.currentTimeMillis()}.ogg"
        
        // Test with different statuses
        val testCases = listOf(
            V2SStatus.NOT_STARTED,
            V2SStatus.PROCESSING,
            V2SStatus.COMPLETED,
            V2SStatus.ERROR
        )
        
        testCases.forEach { status ->
            val recording = createRecording(
                filepath = nonExistentPath,
                v2sStatus = status,
                v2sResult = if (status == V2SStatus.COMPLETED) "transcribed text" else null
            )
            
            val shouldShow = shouldShowDownloadButton(recording)
            
            assertFalse(
                "Download button should not be visible for $status when file doesn't exist",
                shouldShow
            )
        }
    }

    @Test
    fun testDownloadButtonVisibility_CompletedStatusRegardlessOfTranscription() {
        // Create a temporary file
        val tempFile = File.createTempFile("test_completed", ".ogg")
        tempFile.deleteOnExit()
        
        // Test COMPLETED status with transcription
        val recordingWithText = createRecording(
            filepath = tempFile.absolutePath,
            v2sStatus = V2SStatus.COMPLETED,
            v2sResult = "Transcribed text here"
        )
        
        assertTrue(
            "Download button should show for COMPLETED with transcription",
            shouldShowDownloadButton(recordingWithText)
        )
        
        // Test COMPLETED status without transcription (empty result)
        val recordingWithoutText = createRecording(
            filepath = tempFile.absolutePath,
            v2sStatus = V2SStatus.COMPLETED,
            v2sResult = ""
        )
        
        assertTrue(
            "Download button should show for COMPLETED without transcription if file exists",
            shouldShowDownloadButton(recordingWithoutText)
        )
        
        // Test COMPLETED status with null result
        val recordingWithNull = createRecording(
            filepath = tempFile.absolutePath,
            v2sStatus = V2SStatus.COMPLETED,
            v2sResult = null
        )
        
        assertTrue(
            "Download button should show for COMPLETED with null transcription if file exists",
            shouldShowDownloadButton(recordingWithNull)
        )
    }

    @Test
    fun testProcessingAnimationCondition() {
        // Test that PROCESSING status should trigger animation
        val processingRecording = createRecording(
            v2sStatus = V2SStatus.PROCESSING
        )
        
        assertTrue(
            "Processing animation should start for PROCESSING status",
            shouldStartProcessingAnimation(processingRecording)
        )
        
        // Test that other statuses should not trigger animation
        val nonAnimatingStatuses = listOf(
            V2SStatus.NOT_STARTED,
            V2SStatus.COMPLETED,
            V2SStatus.ERROR,
            V2SStatus.FALLBACK,
            V2SStatus.DISABLED
        )
        
        nonAnimatingStatuses.forEach { status ->
            val recording = createRecording(v2sStatus = status)
            
            assertFalse(
                "Processing animation should not start for $status status",
                shouldStartProcessingAnimation(recording)
            )
        }
    }

    @Test
    fun testDownloadButtonVisibility_EdgeCases() {
        // Test with empty filepath
        val recordingEmptyPath = createRecording(
            filepath = "",
            v2sStatus = V2SStatus.COMPLETED,
            v2sResult = "text"
        )
        assertFalse(
            "Download button should not show for empty filepath",
            shouldShowDownloadButton(recordingEmptyPath)
        )
        
        // Test with very long filepath that doesn't exist
        val longPath = "/tmp/" + "a".repeat(1000) + ".ogg"
        val recordingLongPath = createRecording(
            filepath = longPath,
            v2sStatus = V2SStatus.COMPLETED
        )
        assertFalse(
            "Download button should not show for non-existent long filepath",
            shouldShowDownloadButton(recordingLongPath)
        )
    }

    // Helper function to create test recordings
    private fun createRecording(
        id: Long = 1L,
        filename: String = "test.ogg",
        filepath: String = "/tmp/test.ogg",
        timestamp: Long = System.currentTimeMillis(),
        latitude: Double = 37.774929,
        longitude: Double = -122.419416,
        v2sStatus: V2SStatus = V2SStatus.NOT_STARTED,
        v2sResult: String? = null
    ): Recording {
        return Recording(
            id = id,
            filename = filename,
            filepath = filepath,
            timestamp = timestamp,
            latitude = latitude,
            longitude = longitude,
            v2sStatus = v2sStatus,
            v2sResult = v2sResult,
            v2sFallback = false,
            errorMsg = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    // Helper function to determine if download button should be shown
    // This mirrors the logic from shouldShowDownloadButton() in RecordingManagerActivity
    private fun shouldShowDownloadButton(recording: Recording): Boolean {
        // Show download button if the recording file exists
        val file = File(recording.filepath)
        return file.exists()
    }

    // Helper function to determine if processing animation should start
    // This mirrors the logic from updateTranscriptionUI() in RecordingManagerActivity
    private fun shouldStartProcessingAnimation(recording: Recording): Boolean {
        return recording.v2sStatus == V2SStatus.PROCESSING
    }
}
