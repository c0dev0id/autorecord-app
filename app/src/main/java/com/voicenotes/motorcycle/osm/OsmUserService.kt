package com.voicenotes.motorcycle.osm

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import java.util.concurrent.TimeUnit

/**
 * Retrofit service interface for OpenStreetMap User API
 */
interface OsmUserService {
    
    /**
     * Get user details
     * @param authorization OAuth 2.0 Bearer token
     * @return XML response with user details
     */
    @GET("api/0.6/user/details")
    fun getUserDetails(
        @Header("Authorization") authorization: String
    ): Call<String>
    
    companion object {
        private const val BASE_URL = "https://api.openstreetmap.org/"
        
        fun create(): OsmUserService {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            
            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()
            
            return retrofit.create(OsmUserService::class.java)
        }
    }
}
