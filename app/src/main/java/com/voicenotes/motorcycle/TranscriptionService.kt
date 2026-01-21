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
            
            if (serviceAccountJson.isBlank() || serviceAccountJson == "{}") {
                return@withContext Result.failure(Exception("Google Cloud service account credentials not configured"))
            }

            // Validate service account JSON format
            if (!serviceAccountJson.contains("\"type\"") || 
                !serviceAccountJson.contains("\"project_id\"") || 
                !serviceAccountJson.contains("\"private_key\"")) {
                return@withContext Result.failure(Exception("Invalid service account JSON format. Must contain type, project_id, and private_key fields."))
            }

            // Read audio file
            val audioFile = File(filePath)
            if (!audioFile.exists()) {
                return@withContext Result.failure(Exception("Audio file not found: $filePath"))
            }

            val audioBytes = FileInputStream(audioFile).use { it.readBytes() }
            val audioByteString = ByteString.copyFrom(audioBytes)

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
                
                Result.success(transcribedText)
                
            } finally {
                speechClient.close()
            }

        } catch (e: Exception) {
            Log.e("TranscriptionService", "Transcription failed", e)
            Result.failure(e)
        }
    }
}
