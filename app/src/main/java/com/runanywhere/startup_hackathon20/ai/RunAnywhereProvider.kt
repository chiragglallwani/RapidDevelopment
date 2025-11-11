package com.runanywhere.startup_hackathon20.ai

import android.util.Log
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.listAvailableModels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * RunAnywhere SDK provider implementation
 */
class RunAnywhereProvider : AIProvider {

    override val providerType = AIProviderType.RUN_ANYWHERE
    override val isInitialized: Boolean = true // RunAnywhere is initialized in Application class

    private var currentModel: AIModel? = null

    companion object {
        private const val TAG = "RunAnywhereProvider"
    }

    override suspend fun initialize(): AIProviderResult<Unit> {
        return try {
            // RunAnywhere is already initialized in the Application class
            Log.d(TAG, "RunAnywhere provider initialized")
            AIProviderResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize RunAnywhere provider", e)
            AIProviderResult.Error("Failed to initialize RunAnywhere: ${e.message}", e)
        }
    }

    override suspend fun getAvailableModels(): AIProviderResult<List<AIModel>> {
        return try {
            val models = listAvailableModels()
            val aiModels = models.map { modelInfo ->
                AIModel(
                    id = modelInfo.id,
                    name = modelInfo.name,
                    description = "RunAnywhere local model",
                    isDownloaded = modelInfo.isDownloaded,
                    isLoaded = false, // We don't have direct loaded status from RunAnywhere
                    providerType = AIProviderType.RUN_ANYWHERE
                )
            }
            Log.d(TAG, "Found ${aiModels.size} RunAnywhere models")
            AIProviderResult.Success(aiModels)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get available models", e)
            AIProviderResult.Error("Failed to get models: ${e.message}", e)
        }
    }

    override suspend fun downloadModel(modelId: String): Flow<Float> {
        return try {
            Log.d(TAG, "Starting download for model: $modelId")
            RunAnywhere.downloadModel(modelId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download model: $modelId", e)
            flow {
                emit(0f) // Emit 0 progress to indicate failure
            }
        }
    }

    override suspend fun loadModel(modelId: String): AIProviderResult<Unit> {
        return try {
            Log.d(TAG, "Loading model: $modelId")
            val success = RunAnywhere.loadModel(modelId)

            if (success) {
                // Update current model
                val models = getAvailableModels()
                if (models is AIProviderResult.Success) {
                    currentModel = models.data.find { it.id == modelId }?.copy(isLoaded = true)
                }
                Log.d(TAG, "Successfully loaded model: $modelId")
                AIProviderResult.Success(Unit)
            } else {
                Log.e(TAG, "Failed to load model (returned false): $modelId")
                AIProviderResult.Error("Failed to load model: $modelId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while loading model: $modelId", e)
            AIProviderResult.Error("Failed to load model: ${e.message}", e)
        }
    }

    override suspend fun generateStream(prompt: String): Flow<String> {
        return try {
            Log.d(TAG, "Starting generation with prompt length: ${prompt.length}")
            RunAnywhere.generateStream(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate stream", e)
            flow {
                emit("Error: ${e.message}")
            }
        }
    }

    override suspend fun generate(prompt: String): AIProviderResult<String> {
        return try {
            Log.d(TAG, "Generating complete response for prompt length: ${prompt.length}")
            var response = ""
            generateStream(prompt).collect { token ->
                response += token
            }
            Log.d(TAG, "Generation completed with response length: ${response.length}")
            AIProviderResult.Success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate complete response", e)
            AIProviderResult.Error("Generation failed: ${e.message}", e)
        }
    }

    override fun getCurrentModel(): AIModel? {
        return currentModel
    }

    override fun isReady(): Boolean {
        return currentModel != null && currentModel!!.isLoaded
    }

    override suspend fun cleanup() {
        Log.d(TAG, "Cleaning up RunAnywhere provider")
        currentModel = null
        // RunAnywhere doesn't seem to have explicit cleanup methods
    }
}