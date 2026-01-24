package com.voicenotes.motorcycle

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OsmNotesService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    suspend fun createNote(
        lat: Double,
        lon: Double,
        text: String,
        accessToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Validate coordinates
            if (lat < -90.0 || lat > 90.0) {
                return@withContext Result.failure(IllegalArgumentException("Invalid latitude: $lat"))
            }
            if (lon < -180.0 || lon > 180.0) {
                return@withContext Result.failure(IllegalArgumentException("Invalid longitude: $lon"))
            }
            if (text.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Note text cannot be empty"))
            }
            
            val url = "https://api.openstreetmap.org/api/0.6/notes.json?lat=$lat&lon=$lon&text=${text.urlEncode()}"
            
            // Log the request
            DebugLogger.logApiRequest(
                service = "OSM Notes API",
                method = "POST",
                url = url,
                headers = mapOf("Authorization" to "Bearer $accessToken")
            )
            
            val request = Request.Builder()
                .url(url)
                .post("".toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (response.isSuccessful) {
                Log.d("OsmNotesService", "Note created successfully")
                DebugLogger.logApiResponse(
                    service = "OSM Notes API",
                    statusCode = response.code,
                    responseBody = responseBody
                )
                Result.success(Unit)
            } else {
                val error = "Failed to create note: ${response.code} ${response.message}"
                Log.e("OsmNotesService", error)
                DebugLogger.logApiResponse(
                    service = "OSM Notes API",
                    statusCode = response.code,
                    responseBody = responseBody,
                    error = error
                )
                Result.failure(Exception(error))
            }
        } catch (e: IllegalArgumentException) {
            // Input validation failure
            Log.e("OsmNotesService", "Invalid input: ${e.message}", e)
            DebugLogger.logError(
                service = "OSM Notes API",
                error = "Invalid input: ${e.message}",
                exception = e
            )
            Result.failure(e)
        } catch (e: IOException) {
            Log.e("OsmNotesService", "Network error creating note", e)
            DebugLogger.logError(
                service = "OSM Notes API",
                error = "Network error creating note",
                exception = e
            )
            Result.failure(e)
        } catch (e: Exception) {
            // Unexpected error (e.g., URL encoding issues, malformed response)
            Log.e("OsmNotesService", "Unexpected error creating note", e)
            DebugLogger.logError(
                service = "OSM Notes API",
                error = "Unexpected error creating note",
                exception = e
            )
            Result.failure(e)
        }
    }
    
    private fun String.urlEncode(): String {
        return java.net.URLEncoder.encode(this, "UTF-8")
    }
}
