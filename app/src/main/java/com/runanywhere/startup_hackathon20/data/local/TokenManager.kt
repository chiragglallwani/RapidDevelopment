package com.runanywhere.startup_hackathon20.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

class TokenManager(context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ROLE = "user_role"
    }
    
    fun saveTokens(accessToken: String, refreshToken: String) {
        sharedPreferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }
    
    fun getAccessToken(): String? {
        return sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
    }
    
    fun getRefreshToken(): String? {
        return sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
    }
    
    fun saveUserInfo(userId: String, name: String, email: String, role: String) {
        sharedPreferences.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_ROLE, role)
            .apply()
    }
    
    fun getUserId(): String? {
        return sharedPreferences.getString(KEY_USER_ID, null)
    }
    
    fun getUserName(): String? {
        return sharedPreferences.getString(KEY_USER_NAME, null)
    }
    
    fun getUserEmail(): String? {
        return sharedPreferences.getString(KEY_USER_EMAIL, null)
    }
    
    fun getUserRole(): String? {
        // First try to get from saved preferences
        val savedRole = sharedPreferences.getString(KEY_USER_ROLE, null)
        if (savedRole != null) {
            return savedRole
        }
        
        // If not saved, try to decode from JWT token
        val token = getAccessToken()
        if (token != null) {
            try {
                val role = decodeRoleFromToken(token)
                if (role != null) {
                    // Save it for future use
                    sharedPreferences.edit()
                        .putString(KEY_USER_ROLE, role)
                        .apply()
                    Log.d("TokenManager", "Extracted role from JWT token: $role")
                    return role
                }
            } catch (e: Exception) {
                Log.e("TokenManager", "Error decoding role from token", e)
            }
        }
        
        return null
    }
    
    /**
     * Decode JWT token and extract role from payload
     * JWT format: header.payload.signature
     * We only need to decode the payload (base64 JSON)
     */
    private fun decodeRoleFromToken(token: String): String? {
        try {
            // Split JWT into parts
            val parts = token.split(".")
            if (parts.size != 3) {
                Log.w("TokenManager", "Invalid JWT token format")
                return null
            }
            
            // Decode the payload (second part)
            val payload = parts[1]
            
            // Add padding if needed (Base64 requires padding)
            val paddedPayload = when (payload.length % 4) {
                2 -> payload + "=="
                3 -> payload + "="
                else -> payload
            }
            
            // Decode base64
            val decodedBytes = Base64.decode(paddedPayload, Base64.URL_SAFE)
            val decodedString = String(decodedBytes, Charsets.UTF_8)
            
            // Parse JSON
            val jsonObject = JSONObject(decodedString)
            
            // Extract role
            return if (jsonObject.has("role")) {
                jsonObject.getString("role")
            } else {
                Log.w("TokenManager", "Role not found in JWT payload")
                null
            }
        } catch (e: Exception) {
            Log.e("TokenManager", "Error decoding JWT token", e)
            return null
        }
    }
    
    fun isLoggedIn(): Boolean {
        return getAccessToken() != null
    }
    
    fun clearTokens() {
        sharedPreferences.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_ROLE)
            .apply()
    }
}

