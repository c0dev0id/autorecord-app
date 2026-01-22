package com.voicenotes.motorcycle

import android.content.Context
import android.util.Log
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.speech.v1.*
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

class TranscriptionService(private val context: Context) {

    companion object {
        /**
         * Decode base64-encoded service account JSON
         */
        private fun decodeServiceAccountJson(base64: String): String? {
            return try {
                if (base64.isBlank()) return null
                String(android.util.Base64.decode(base64, android.util.Base64.NO_WRAP))
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Check if Google Cloud credentials are properly configured
         */
        fun isConfigured(): Boolean {
            val serviceAccountJsonBase64 = BuildConfig.GOOGLE_CLOUD_SERVICE_ACCOUNT_JSON_BASE64
            val serviceAccountJson = decodeServiceAccountJson(serviceAccountJsonBase64) ?: return false
            
            return serviceAccountJson.contains("\"type\"") &&
                   serviceAccountJson.contains("\"project_id\"") &&
                   serviceAccountJson.contains("\"private_key\"")
        }
    }

    /**
     * Transcribes an m4a audio file using Google Cloud Speech-to-Text API
     * 
     * @param filePath Absolute path to the m4a file
     * @return Result containing transcribed text or error
     */
    suspend fun transcribeAudioFile(filePath: String): Result<String> {
        return try {
            withTimeout(60000) { // 60 second timeout
                transcribeAudioFileInternal(filePath)
            }
        } catch (e: TimeoutCancellationException) {
            DebugLogger.logError(
                service = "Google Cloud Speech",
                error = "Transcription timeout after 60 seconds",
                exception = e
            )
            Result.failure(Exception("Transcription timeout - network too slow or file too large"))
        } catch (e: Exception) {
            DebugLogger.logError(
                service = "Google Cloud Speech",
                error = "Transcription failed",
                exception = e
            )
            Result.failure(e)
        }
    }

    private suspend fun transcribeAudioFileInternal(filePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val serviceAccountJsonBase64 = BuildConfig.GOOGLE_CLOUD_SERVICE_ACCOUNT_JSON_BASE64
            
            DebugLogger.logInfo(
                service = "Google Cloud Speech-to-Text",
                message = "Starting transcription for file: $filePath"
            )
            
            // Decode base64 credentials
            val serviceAccountJson = decodeServiceAccountJson(serviceAccountJsonBase64)
            if (serviceAccountJson == null) {
                val errorMsg = "Google Cloud credentials not configured. " +
                    "Transcription is disabled. See Settings > Online Processing for setup instructions."
                Log.e("TranscriptionService", errorMsg)
                DebugLogger.logError(
                    service = "Google Cloud Speech-to-Text",
                    error = errorMsg
                )
                return@withContext Result.failure(Exception(errorMsg))
            }

            // Validate service account JSON format with detailed error
            if (!serviceAccountJson.contains("\"type\"") || 
                !serviceAccountJson.contains("\"project_id\"") || 
                !serviceAccountJson.contains("\"private_key\"")) {
                val errorMsg = "Invalid Google Cloud service account JSON format. " +
                    "The JSON must contain 'type', 'project_id', and 'private_key' fields. " +
                    "Please check your configuration in gradle.properties or GitHub Secrets."
                Log.e("TranscriptionService", errorMsg)
                DebugLogger.logError(
                    service = "Google Cloud Speech-to-Text",
                    error = errorMsg
                )
                return@withContext Result.failure(Exception(errorMsg))
            }

            // Read audio file
            val audioFile = File(filePath)
            if (!audioFile.exists()) {
                val errorMsg = "Audio file not found: $filePath"
                DebugLogger.logError(
                    service = "Google Cloud Speech-to-Text",
                    error = errorMsg
                )
                return@withContext Result.failure(Exception(errorMsg))
            }

            val audioBytes = FileInputStream(audioFile).use { it.readBytes() }
            val audioByteString = ByteString.copyFrom(audioBytes)
            
            DebugLogger.logApiRequest(
                service = "Google Cloud Speech-to-Text",
                method = "POST",
                url = "https://speech.googleapis.com/v1/speech:recognize",
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "Audio-Size" to "${audioBytes.size} bytes"
                )
            )

            // Configure speech client with service account credentials
            val credentials = GoogleCredentials.fromStream(
                ByteArrayInputStream(serviceAccountJson.toByteArray(Charsets.UTF_8))
            )
            
            val speechSettings = SpeechSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build()

            val speechClient = SpeechClient.create(speechSettings)

            try {
                // Configure recognition
                val recognitionConfig = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.FLAC)  // FLAC works for M4A/AAC
                    .setSampleRateHertz(44100)
                    .setLanguageCode("en-US")
                    .setEnableAutomaticPunctuation(true)
                    .setModel("default")
                    .build()

                val audio = RecognitionAudio.newBuilder()
                    .setContent(audioByteString)
                    .build()

                // Perform recognition
                val response = speechClient.recognize(recognitionConfig, audio)
                
                // Extract transcribed text
                val transcribedText = response.resultsList
                    .flatMap { it.alternativesList }
                    .firstOrNull()
                    ?.transcript
                    ?: ""

                Log.d("TranscriptionService", "Transcription result: '$transcribedText'")
                
                DebugLogger.logApiResponse(
                    service = "Google Cloud Speech-to-Text",
                    statusCode = 200,
                    responseBody = "Transcription successful: ${transcribedText.take(100)}${if (transcribedText.length > 100) "..." else ""}"
                )
                
                Result.success(transcribedText)
                
            } finally {
                speechClient.close()
            }

        } catch (e: Exception) {
            Log.e("TranscriptionService", "Transcription failed", e)
            DebugLogger.logError(
                service = "Google Cloud Speech-to-Text",
                error = "Transcription failed: ${e.message}",
                exception = e
            )
            Result.failure(e)
        }
    }
}
