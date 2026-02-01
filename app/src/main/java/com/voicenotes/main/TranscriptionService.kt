package com.voicenotes.main

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
     * Get device language code for speech recognition
     * Returns a BCP-47 language code based on device locale
     * 
     * When the device has a complete locale (language + country), uses that directly.
     * When only language is available, defaults to common regions for Google Cloud STT support:
     * - English -> en-US (most widely supported)
     * - German -> de-DE (Germany)
     * - Spanish -> es-ES (Spain)
     * - French -> fr-FR (France)
     * - Italian -> it-IT (Italy)
     * - Portuguese -> pt-BR (Brazil - largest Portuguese-speaking population)
     * - Japanese -> ja-JP (Japan)
     * - Korean -> ko-KR (South Korea)
     * - Chinese -> zh-CN (Mainland China - Simplified)
     * - Others -> en-US (fallback)
     */
    private fun getDeviceLanguageCode(): String {
        val locale = java.util.Locale.getDefault()
        
        // If locale has both language and country, use the properly formatted BCP-47 tag
        if (locale.country.isNotEmpty()) {
            return locale.toLanguageTag()
        }
        
        // Otherwise, provide sensible defaults for common languages
        // These defaults prioritize the most populous or widely-supported variants
        return when (locale.language) {
            "en" -> "en-US"
            "de" -> "de-DE"
            "es" -> "es-ES"
            "fr" -> "fr-FR"
            "it" -> "it-IT"
            "pt" -> "pt-BR"
            "ja" -> "ja-JP"
            "ko" -> "ko-KR"
            "zh" -> "zh-CN"
            else -> "en-US" // Fallback to English US
        }
    }

    /**
     * Transcribes an audio file using Google Cloud Speech-to-Text API
     * Supports both OGG_OPUS (.ogg) and M4A/AAC (.m4a) formats
     * 
     * @param filePath Absolute path to the audio file
     * @return Result containing transcribed text or error
     */
    suspend fun transcribeAudioFile(filePath: String): Result<String> {
        return try {
            withTimeout(20000) { // 20 second timeout
                transcribeAudioFileInternal(filePath)
            }
        } catch (e: TimeoutCancellationException) {
            DebugLogger.logError(
                service = "Google Cloud Speech",
                error = "Transcription timeout after 20 seconds",
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
                // Read language preferences from SharedPreferences
                val sharedPrefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                val rawPrimaryLanguage = sharedPrefs.getString("stt_primary_language", "system") ?: "system"
                val rawSecondaryLanguage = sharedPrefs.getString("stt_secondary_language", "") ?: ""
                
                // Convert "system" to device locale language code
                val primaryLanguage = if (rawPrimaryLanguage == "system") {
                    getDeviceLanguageCode()
                } else {
                    rawPrimaryLanguage
                }
                
                // Only convert "system" for secondary language if explicitly set, not if empty
                val secondaryLanguage = if (rawSecondaryLanguage == "system") {
                    getDeviceLanguageCode()
                } else {
                    rawSecondaryLanguage
                }
                
                DebugLogger.logInfo(
                    service = "Google Cloud Speech-to-Text",
                    message = "Using language codes - Primary: $primaryLanguage, Secondary: $secondaryLanguage"
                )
                
                // Detect audio format from file extension
                val isOggOpus = filePath.endsWith(".ogg", ignoreCase = true)
                
                // Configure recognition based on file format
                val encoding = if (isOggOpus) {
                    RecognitionConfig.AudioEncoding.OGG_OPUS
                } else {
                    RecognitionConfig.AudioEncoding.FLAC  // FLAC works for M4A/AAC
                }
                val sampleRate = if (isOggOpus) 48000 else 44100
                
                val configBuilder = RecognitionConfig.newBuilder()
                    .setEncoding(encoding)
                    .setSampleRateHertz(sampleRate)
                    .setLanguageCode(primaryLanguage)
                    .setEnableAutomaticPunctuation(true)
                    .setModel("phone_call")
                    .setUseEnhanced(true)
                
                // Add secondary language if configured
                if (secondaryLanguage.isNotEmpty()) {
                    configBuilder.addAllAlternativeLanguageCodes(listOf(secondaryLanguage))
                }
                
                val recognitionConfig = configBuilder.build()

                val audio = RecognitionAudio.newBuilder()
                    .setContent(audioByteString)
                    .build()

                // Perform recognition
                val response = speechClient.recognize(recognitionConfig, audio)
                
                // Extract transcribed text - join all results for complete transcription
                val transcribedText = response.resultsList
                    .joinToString(" ") { result ->
                        result.alternativesList.firstOrNull()?.transcript ?: ""
                    }
                    .trim()

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
