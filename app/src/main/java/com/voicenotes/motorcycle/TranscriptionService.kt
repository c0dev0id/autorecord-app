package com.voicenotes.motorcycle

import android.content.Context
import android.util.Log
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.speech.v1.*
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

class TranscriptionService(private val context: Context) {

    /**
     * Transcribes an m4a audio file using Google Cloud Speech-to-Text API
     * 
     * @param filePath Absolute path to the m4a file
     * @return Result containing transcribed text or error
     */
    suspend fun transcribeAudioFile(filePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val serviceAccountJson = BuildConfig.GOOGLE_CLOUD_SERVICE_ACCOUNT_JSON
            
            DebugLogger.logInfo(
                service = "Google Cloud Speech-to-Text",
                message = "Starting transcription for file: $filePath"
            )
            
            // Enhanced error checking with specific messages
            if (serviceAccountJson.isBlank() || serviceAccountJson == "{}") {
                val errorMsg = "Google Cloud service account credentials not configured. " +
                    "Please add GOOGLE_CLOUD_SERVICE_ACCOUNT_JSON to gradle.properties (local) or " +
                    "as a GitHub Secret named 'GOOGLE_CLOUD_SERVICE_ACCOUNT_JSON' (CI/CD)."
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
                val errorMsg = "Invalid service account JSON format. " +
                    "The JSON must contain 'type', 'project_id', and 'private_key' fields. " +
                    "Current value starts with: ${serviceAccountJson.take(50)}..."
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
                    .setEncoding(RecognitionConfig.AudioEncoding.ENCODING_UNSPECIFIED) // Let API auto-detect
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
                error = "Transcription failed",
                exception = e
            )
            Result.failure(e)
        }
    }
}
