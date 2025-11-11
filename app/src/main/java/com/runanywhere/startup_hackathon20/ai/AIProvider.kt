package com.runanywhere.startup_hackathon20.ai

import kotlinx.coroutines.flow.Flow

interface AIProvider {
    val providerType: AIProviderType
    val isInitialized: Boolean

    suspend fun initialize(): AIProviderResult<Unit>
    suspend fun getAvailableModels(): AIProviderResult<List<AIModel>>
    suspend fun downloadModel(modelId: String): Flow<Float>
    suspend fun loadModel(modelId: String): AIProviderResult<Unit>
    suspend fun generateStream(prompt: String): Flow<String>
    suspend fun generate(prompt: String): AIProviderResult<String>
    fun getCurrentModel(): AIModel?
    fun isReady(): Boolean
    suspend fun cleanup()
}
