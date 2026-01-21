package com.voicenotes.motorcycle

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class OsmNotesService {
    
    private val client = OkHttpClient()
    
    suspend fun createNote(
        lat: Double,
        lon: Double,
        text: String,
        accessToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
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
            val responseBody = response.body?.string()
            
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
        } catch (e: IOException) {
            Log.e("OsmNotesService", "Network error creating note", e)
            DebugLogger.logError(
                service = "OSM Notes API",
                error = "Network error creating note",
                exception = e
            )
            Result.failure(e)
        }
    }
    
    private fun String.urlEncode(): String {
        return java.net.URLEncoder.encode(this, "UTF-8")
    }
}
