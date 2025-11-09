package com.runanywhere.startup_hackathon20.data.repository

import android.util.Log
import com.runanywhere.startup_hackathon20.data.api.RetrofitClient
import com.runanywhere.startup_hackathon20.data.local.TokenManager
import com.runanywhere.startup_hackathon20.data.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class UserResult {
    data class Success(val users: List<User>) : UserResult()
    data class Error(val message: String) : UserResult()
}

class UserRepository(private val tokenManager: TokenManager) {

    private val apiService = RetrofitClient.apiService
    private var cachedDevelopers: List<User>? = null

    suspend fun getDevelopers(forceRefresh: Boolean = false): UserResult {
        if (!forceRefresh) {
            cachedDevelopers?.let { cached ->
                return UserResult.Success(cached)
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                Log.d("UserRepository", "Fetching developers from API")
                val response = apiService.getDevelopers()

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val developers = body.data.orEmpty()
                    cachedDevelopers = developers
                    UserResult.Success(developers)
                } else {
                    val message = "HTTP ${response.code()}: ${response.message()}"
                    Log.e("UserRepository", "Failed to fetch developers: $message")
                    UserResult.Error(message)
                }
            } catch (e: Exception) {
                Log.e("UserRepository", "Error fetching developers", e)
                UserResult.Error(e.message ?: "Network error occurred")
            }
        }
    }

    fun clearCache() {
        cachedDevelopers = null
    }
}

