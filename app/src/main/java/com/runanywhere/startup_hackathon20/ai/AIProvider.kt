package com.runanywhere.startup_hackathon20.ai

import kotlinx.coroutines.flow.Flow

/**
 * Enumeration of available AI providers
 */
enum class AIProviderType {
    RUN_ANYWHERE,
    GEMINI
}

/**
 * Data class representing an AI model
 */
data class AIModel(
    val id: String,
    val name: String,
    val description: String = "",
    val isDownloaded: Boolean = false,
    val isLoaded: Boolean = false,
    val providerType: AIProviderType
)

/**
 * Result of AI provider operations
 */
sealed class AIProviderResult<T> {
    data class Success<T>(val data: T) : AIProviderResult<T>()
    data class Error<T>(val message: String, val exception: Throwable? = null) :
        AIProviderResult<T>()
}

/**
 * Abstract interface for AI providers
 * This allows easy switching between RunAnywhere SDK and external APIs like Gemini
 */
interface AIProvider {
    val providerType: AIProviderType
    val isInitialized: Boolean

    /**
     * Initialize the provider with necessary configuration
     */
    suspend fun initialize(): AIProviderResult<Unit>

    /**
     * Get list of available models
     */
    suspend fun getAvailableModels(): AIProviderResult<List<AIModel>>

    /**
     * Download a model (for local providers like RunAnywhere)
     * For cloud providers, this might just validate the model exists
     */
    suspend fun downloadModel(modelId: String): Flow<Float>

    /**
     * Load/activate a model for use
     */
    suspend fun loadModel(modelId: String): AIProviderResult<Unit>

    /**
     * Generate text response as a stream
     */
    suspend fun generateStream(prompt: String): Flow<String>

    /**
     * Generate complete text response
     */
    suspend fun generate(prompt: String): AIProviderResult<String>

    /**
     * Get current active model information
     */
    fun getCurrentModel(): AIModel?

    /**
     * Check if provider is ready to generate content
     */
    fun isReady(): Boolean

    /**
     * Clean up resources
     */
    suspend fun cleanup()
}