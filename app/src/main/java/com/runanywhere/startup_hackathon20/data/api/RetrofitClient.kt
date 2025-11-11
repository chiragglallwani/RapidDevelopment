package com.runanywhere.startup_hackathon20.data.api

import android.util.Log
import com.runanywhere.startup_hackathon20.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // BASE_URL Configuration:
    // For Android Emulator: Use 10.0.2.2 (emulator's way to access host machine)
    // For Physical Device: Use your actual local IP address
    // For Production: Use your production server URL

    private const val BASE_URL = "http://10.0.2.2:3001/api/v1/" // For Android emulator
    // For physical device, use: "http://192.168.1.6:3001/api/v1/" (your current local IP)
    // For production: "https://your-backend-url.com/api/v1/"
    
    private var tokenManager: TokenManager? = null
    
    fun initialize(tokenManager: TokenManager) {
        this.tokenManager = tokenManager
        Log.d("RetrofitClient", "Initialized with base URL: $BASE_URL")
    }
    
    private val authInterceptor = Interceptor { chain ->
        val token = tokenManager?.getAccessToken()
        val request = chain.request().newBuilder()

        Log.d("RetrofitClient", "Request URL: ${chain.request().url}")

        if (token != null) {
            Log.d("RetrofitClient", "Adding auth header with token: ${token.substring(0, 10)}...")
            request.addHeader("Authorization", "Bearer $token")
        } else {
            Log.d("RetrofitClient", "No auth token available")
        }

        val finalRequest = request.build()
        val response = chain.proceed(finalRequest)

        Log.d("RetrofitClient", "Response code: ${response.code}")

        response
    }
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val apiService: ApiService = retrofit.create(ApiService::class.java)
}

