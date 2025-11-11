package com.runanywhere.startup_hackathon20.ai

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockReason
import com.google.ai.client.generativeai.type.FinishReason
import com.runanywhere.startup_hackathon20.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class GeminiProviderProduction : AIProvider {

    override val providerType = AIProviderType.GEMINI
    override var isInitialized: Boolean = false
        private set

    private var generativeModel: GenerativeModel? = null
    private var currentModel: AIModel? = null
    private var apiKey: String? = null

    companion object {
        private const val TAG = "GeminiProvider"
    }

    override suspend fun initialize(): AIProviderResult<Unit> {
        return initializeWithApiKey(null)
    }

    fun initializeWithApiKey(apiKey: String?): AIProviderResult<Unit> {
        this.apiKey = apiKey ?: BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() }
        return if (!this.apiKey.isNullOrBlank()) {
            isInitialized = true
            Log.i(TAG, "Gemini Provider initialized successfully.")
            AIProviderResult.Success(Unit)
        } else {
            Log.w(TAG, "Gemini API key is not available.")
            AIProviderResult.Error("API key not found.")
        }
    }

    override suspend fun getAvailableModels(): AIProviderResult<List<AIModel>> {
        return if (!isInitialized) AIProviderResult.Error("Provider not initialized")
        else AIProviderResult.Success(
            listOf(
                AIModel("gemini-1.5-flash-latest", "Gemini 1.5 Flash", providerType = providerType, isDownloaded = true),
                AIModel("gemini-1.5-pro-latest", "Gemini 1.5 Pro", providerType = providerType, isDownloaded = true),
                AIModel("gemini-1.0-pro", "Gemini 1.0 Pro", providerType = providerType, isDownloaded = true)
            )
        )
    }

    override suspend fun downloadModel(modelId: String): Flow<Float> = flow { emit(1.0f) }

    override suspend fun loadModel(modelId: String): AIProviderResult<Unit> {
        if (!isInitialized || apiKey.isNullOrBlank()) return AIProviderResult.Error("Provider not initialized or API key not set.")

        val modelInfo = getAvailableModels().let { if (it is AIProviderResult.Success) it.data.find { m -> m.id == modelId } else null }
            ?: return AIProviderResult.Error("Model not found: $modelId")

        generativeModel = GenerativeModel(modelName = modelId, apiKey = apiKey!!)
        currentModel = modelInfo.copy(isLoaded = true)
        Log.i(TAG, "Gemini model loaded: $modelId")
        return AIProviderResult.Success(Unit)
    }

    override suspend fun generateStream(prompt: String): Flow<String> {
        if (!isReady()) return flow { throw IllegalStateException("Gemini provider is not ready.") }
        return generativeModel!!.generateContentStream(prompt).map {
            val reason = it.candidates.firstOrNull()?.finishReason
            if (reason == FinishReason.SAFETY || reason == FinishReason.RECITATION) {
                throw IllegalStateException("Content generation stopped for safety reasons.")
            }
            it.text ?: ""
        }
    }

    override suspend fun generate(prompt: String): AIProviderResult<String> {
        if (!isReady()) return AIProviderResult.Error("Gemini provider is not ready.")
        return try {
            val response = generativeModel!!.generateContent(prompt)
            val reason = response.candidates.firstOrNull()?.finishReason
            if (reason == FinishReason.SAFETY || reason == FinishReason.RECITATION) {
                return AIProviderResult.Error("Content generation stopped for safety reasons.")
            }
            AIProviderResult.Success(response.text ?: "")
        } catch (e: Exception) {
            AIProviderResult.Error(e.message ?: "Unknown error", e)
        }
    }

    override fun getCurrentModel(): AIModel? = currentModel

    override fun isReady(): Boolean = isInitialized && generativeModel != null && currentModel != null

    override suspend fun cleanup() {
        generativeModel = null
        currentModel = null
    }

    fun updateApiKey(newApiKey: String): Boolean {
        return if (newApiKey.isNotBlank()) {
            apiKey = newApiKey
            isInitialized = true
            generativeModel = null
            currentModel = null
            true
        } else false
    }

    fun hasApiKey(): Boolean = !apiKey.isNullOrBlank()
}
