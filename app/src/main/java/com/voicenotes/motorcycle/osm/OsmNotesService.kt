package com.voicenotes.motorcycle.osm

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Header
import java.util.concurrent.TimeUnit

/**
 * Retrofit service interface for OpenStreetMap Notes API
 * Documentation: https://wiki.openstreetmap.org/wiki/API_v0.6#Notes_2
 */
interface OsmNotesService {
    
    /**
     * Create a new note at the specified location
     * 
     * @param lat Latitude of the note location
     * @param lon Longitude of the note location
     * @param text Text content of the note
     * @param authorization OAuth 2.0 Bearer token
     * @return XML response from OSM API
     */
    @POST("api/0.6/notes")
    fun createNote(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("text") text: String,
        @Header("Authorization") authorization: String
    ): Call<String>
    
    companion object {
        private const val BASE_URL = "https://api.openstreetmap.org/"
        
        fun create(): OsmNotesService {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            
            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
            
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()
            
            return retrofit.create(OsmNotesService::class.java)
        }
    }
}
