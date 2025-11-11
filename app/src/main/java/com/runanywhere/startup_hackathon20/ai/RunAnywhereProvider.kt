package com.runanywhere.startup_hackathon20.ai

import android.content.Context
import android.util.Log
import com.runanywhere.sdk.data.models.SDKEnvironment
import com.runanywhere.sdk.llm.llamacpp.LlamaCppServiceProvider
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.addModelFromURL
import com.runanywhere.sdk.public.extensions.listAvailableModels
import kotlinx.coroutines.flow.Flow

class RunAnywhereProvider(private val context: Context) : AIProvider {

    override val providerType = AIProviderType.RUN_ANYWHERE
    override var isInitialized: Boolean = false
        private set

    companion object {
        private const val TAG = "RunAnywhereProvider"
    }

    override suspend fun initialize(): AIProviderResult<Unit> {
        return try {
            RunAnywhere.initialize(
                context = context,
                apiKey = "dev",
                environment = SDKEnvironment.DEVELOPMENT
            )
            LlamaCppServiceProvider.register()
            registerModels()
            RunAnywhere.scanForDownloadedModels()
            isInitialized = true
            Log.i(TAG, "RunAnywhere Provider initialized successfully.")
            AIProviderResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "RunAnywhere initialization failed", e)
            AIProviderResult.Error(e.message ?: "Unknown error", e)
        }
    }

    private suspend fun registerModels() {
        addModelFromURL(
            url = "https://huggingface.co/Triangle104/Qwen2.5-0.5B-Instruct-Q6_K-GGUF/resolve/main/qwen2.5-0.5b-instruct-q6_k.gguf",
            name = "Qwen 2.5 0.5B Instruct Q6_K",
            type = "LLM"
        )
        addModelFromURL(
            url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q6_k.gguf",
            name = "Qwen 2.5 1.5B Instruct Q6_K",
            type = "LLM"
        )
    }

    override suspend fun getAvailableModels(): AIProviderResult<List<AIModel>> {
        if (!isInitialized) return AIProviderResult.Error("Provider not initialized")
        return try {
            val models = RunAnywhere.listAvailableModels().map {
                AIModel(it.id, it.name, it.description, providerType, it.isDownloaded, it.isLoaded)
            }
            AIProviderResult.Success(models)
        } catch (e: Exception) {
            AIProviderResult.Error(e.message ?: "Unknown error", e)
        }
    }

    override suspend fun downloadModel(modelId: String): Flow<Float> {
        return RunAnywhere.downloadModel(modelId)
    }

    override suspend fun loadModel(modelId: String): AIProviderResult<Unit> {
        return try {
            if (RunAnywhere.loadModel(modelId)) {
                AIProviderResult.Success(Unit)
            } else {
                AIProviderResult.Error("Failed to load model")
            }
        } catch (e: Exception) {
            AIProviderResult.Error(e.message ?: "Unknown error", e)
        }
    }

    override suspend fun generateStream(prompt: String): Flow<String> {
        if (!isReady()) return kotlinx.coroutines.flow.flow { throw IllegalStateException("RunAnywhere provider is not ready.") }
        return RunAnywhere.generateStream(prompt)
    }

    override suspend fun generate(prompt: String): AIProviderResult<String> {
        if (!isReady()) return AIProviderResult.Error("RunAnywhere provider is not ready.")
        return try {
            val result = RunAnywhere.generate(prompt)
            AIProviderResult.Success(result)
        } catch (e: Exception) {
            AIProviderResult.Error(e.message ?: "Unknown error", e)
        }
    }

    override fun getCurrentModel(): AIModel? {
        return RunAnywhere.getCurrentModel()?.let {
            AIModel(it.id, it.name, it.description, providerType, it.isDownloaded, it.isLoaded)
        }
    }

    override fun isReady(): Boolean = isInitialized && RunAnywhere.getCurrentModel() != null

    override suspend fun cleanup() {
        RunAnywhere.unloadModel()
    }
}
