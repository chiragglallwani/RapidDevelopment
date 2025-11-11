package com.runanywhere.startup_hackathon20.ai

sealed class AIProviderResult<out T> {
    data class Success<out T>(val data: T) : AIProviderResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : AIProviderResult<Nothing>()
}
