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
            
            val request = Request.Builder()
                .url(url)
                .post("".toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                Log.d("OsmNotesService", "Note created successfully")
                Result.success(Unit)
            } else {
                val error = "Failed to create note: ${response.code} ${response.message}"
                Log.e("OsmNotesService", error)
                Result.failure(Exception(error))
            }
        } catch (e: IOException) {
            Log.e("OsmNotesService", "Network error creating note", e)
            Result.failure(e)
        }
    }
    
    private fun String.urlEncode(): String {
        return java.net.URLEncoder.encode(this, "UTF-8")
    }
}
