package com.runanywhere.startup_hackathon20.ai

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AIProviderManager(private val context: Context) {

    companion object {
        private const val TAG = "AIProviderManager"
        private const val PREFERENCES_NAME = "ai_provider_preferences"

        private val SELECTED_PROVIDER_KEY = stringPreferencesKey("selected_provider")
        private val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")

        private val Context.dataStore by preferencesDataStore(name = PREFERENCES_NAME)
    }

    private val dataStore = context.dataStore

    private val runAnywhereProvider = RunAnywhereProvider(context)
    private val geminiProvider = GeminiProviderProduction()

    private var currentProvider: AIProvider? = null

    suspend fun initialize(): AIProviderResult<Unit> {
        return try {
            val runAnywhereResult = runAnywhereProvider.initialize()
            val savedApiKey = getGeminiApiKey()
            geminiProvider.initializeWithApiKey(savedApiKey)

            val savedProvider = getSavedProvider()

            when (savedProvider) {
                AIProviderType.GEMINI -> {
                    if (geminiProvider.isInitialized) {
                        currentProvider = geminiProvider
                        Log.d(TAG, "Initialized with Gemini provider")
                    } else {
                        currentProvider = runAnywhereProvider
                        Log.d(TAG, "Gemini failed, falling back to RunAnywhere")
                    }
                }
                else -> {
                    currentProvider = runAnywhereProvider
                    Log.d(TAG, "Initialized with RunAnywhere provider")
                }
            }

            AIProviderResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AI provider manager", e)
            AIProviderResult.Error("Failed to initialize: ${e.message}", e)
        }
    }

    suspend fun switchProvider(providerType: AIProviderType): AIProviderResult<Unit> {
        return try {
            val newProvider = when (providerType) {
                AIProviderType.RUN_ANYWHERE -> runAnywhereProvider
                AIProviderType.GEMINI -> geminiProvider
            }

            if (!newProvider.isInitialized) {
                return AIProviderResult.Error("Provider ${providerType.name} is not initialized")
            }

            if (currentProvider != newProvider) {
                currentProvider?.cleanup()
            }

            currentProvider = newProvider
            saveProvider(providerType)

            Log.d(TAG, "Switched to ${providerType.name} provider")
            AIProviderResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch provider to ${providerType.name}", e)
            AIProviderResult.Error("Failed to switch provider: ${e.message}", e)
        }
    }

    fun getCurrentProvider(): AIProvider? = currentProvider

    fun getCurrentProviderType(): AIProviderType? = currentProvider?.providerType

    fun getAvailableProviders(): List<Pair<AIProviderType, Boolean>> {
        return listOf(
            AIProviderType.RUN_ANYWHERE to runAnywhereProvider.isInitialized,
            AIProviderType.GEMINI to geminiProvider.isInitialized
        )
    }

    suspend fun updateGeminiApiKey(apiKey: String): AIProviderResult<Unit> {
        return try {
            if (apiKey.isBlank()) {
                return AIProviderResult.Error("API key cannot be empty")
            }

            dataStore.edit { preferences ->
                preferences[GEMINI_API_KEY] = apiKey
            }
            Log.d(TAG, "Saved Gemini API key to DataStore")

            if (geminiProvider.updateApiKey(apiKey)) {
                if (!geminiProvider.isInitialized) {
                    geminiProvider.initializeWithApiKey(apiKey)
                }
                Log.d(TAG, "Gemini API key updated successfully in provider")
                AIProviderResult.Success(Unit)
            } else {
                AIProviderResult.Error("Failed to update API key in provider")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update Gemini API key", e)
            AIProviderResult.Error("Failed to update API key: ${e.message}", e)
        }
    }

    suspend fun getGeminiApiKey(): String? {
        return try {
            dataStore.data.first()[GEMINI_API_KEY]
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Gemini API key", e)
            null
        }
    }

    suspend fun getAvailableModels(): AIProviderResult<List<AIModel>> {
        return currentProvider?.getAvailableModels() ?: AIProviderResult.Error("No provider available")
    }

    suspend fun downloadModel(modelId: String): Flow<Float> {
        return currentProvider?.downloadModel(modelId) ?: kotlinx.coroutines.flow.flow { emit(0f) }
    }

    suspend fun loadModel(modelId: String): AIProviderResult<Unit> {
        return currentProvider?.loadModel(modelId) ?: AIProviderResult.Error("No provider available")
    }

    suspend fun generateStream(prompt: String): Flow<String> {
        return currentProvider?.generateStream(prompt) ?: kotlinx.coroutines.flow.flow { emit("No provider available") }
    }

    suspend fun generate(prompt: String): AIProviderResult<String> {
        return currentProvider?.generate(prompt) ?: AIProviderResult.Error("No provider available")
    }

    fun getCurrentModel(): AIModel? {
        return currentProvider?.getCurrentModel()
    }

    fun isReady(): Boolean {
        return currentProvider?.isReady() ?: false
    }

    suspend fun cleanup() {
        runAnywhereProvider.cleanup()
        geminiProvider.cleanup()
        currentProvider = null
    }

    private suspend fun saveProvider(providerType: AIProviderType) {
        dataStore.edit { preferences ->
            preferences[SELECTED_PROVIDER_KEY] = providerType.name
        }
    }

    private suspend fun getSavedProvider(): AIProviderType? {
        return try {
            val savedName = dataStore.data.first()[SELECTED_PROVIDER_KEY]
            savedName?.let { AIProviderType.valueOf(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get saved provider", e)
            null
        }
    }
}
