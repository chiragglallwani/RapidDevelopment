package com.runanywhere.startup_hackathon20.ai

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Production-ready Google Gemini API provider implementation
 *
 * This version uses the actual Gemini API when available and falls back gracefully
 * when dependencies are missing.
 */
class GeminiProviderProduction : AIProvider {

    override val providerType = AIProviderType.GEMINI
    override var isInitialized: Boolean = false
        private set

    private var generativeModel: Any? = null
    private var currentModel: AIModel? = null
    private var apiKey: String? = null

    companion object {
        private const val TAG = "GeminiProvider"

        // Available Gemini models
        private val AVAILABLE_MODELS = listOf(
            AIModel(
                id = "gemini-1.5-flash",
                name = "Gemini 1.5 Flash",
                description = "Fast and efficient model for most tasks",
                isDownloaded = true,
                isLoaded = false,
                providerType = AIProviderType.GEMINI
            ),
            AIModel(
                id = "gemini-1.5-pro",
                name = "Gemini 1.5 Pro",
                description = "Advanced model for complex reasoning tasks",
                isDownloaded = true,
                isLoaded = false,
                providerType = AIProviderType.GEMINI
            ),
            AIModel(
                id = "gemini-1.0-pro",
                name = "Gemini 1.0 Pro",
                description = "Previous generation model",
                isDownloaded = true,
                isLoaded = false,
                providerType = AIProviderType.GEMINI
            )
        )

        private fun isGeminiApiAvailable(): Boolean {
            return try {
                // Check for main GenerativeModel class
                Class.forName("com.google.ai.client.generativeai.GenerativeModel")
                // Also check for Builder class (nested class syntax)
                Class.forName("com.google.ai.client.generativeai.GenerativeModel\$Builder")
                Log.d(TAG, "Gemini API classes are available")
                true
            } catch (e: ClassNotFoundException) {
                Log.w(TAG, "Gemini API classes not found: ${e.message}")
                false
            } catch (e: Exception) {
                Log.w(TAG, "Error checking Gemini API availability: ${e.message}")
                false
            }
        }

        private fun getApiKeyFromBuildConfig(): String? {
            return try {
                val buildConfigClass =
                    Class.forName("com.runanywhere.startup_hackathon20.BuildConfig")
                val field = buildConfigClass.getDeclaredField("GEMINI_API_KEY")
                field.get(null) as? String
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun initialize(): AIProviderResult<Unit> {
        return try {
            if (!isGeminiApiAvailable()) {
                return AIProviderResult.Error("Gemini API not available. Please ensure the dependency is properly configured.")
            }

            // Try to get API key from BuildConfig first
            apiKey = getApiKeyFromBuildConfig()?.takeIf { it.isNotBlank() }

            // If not found in BuildConfig, we'll allow initialization to succeed
            // The API key can be set later via updateApiKey() from the UI
            if (apiKey != null) {
                Log.d(TAG, "Gemini provider initialized with API key from BuildConfig")
            } else {
                Log.d(TAG, "Gemini provider initialized without API key. User can configure it via UI.")
            }

            isInitialized = true
            Log.d(TAG, "Gemini provider initialized successfully")
            AIProviderResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Gemini provider", e)
            AIProviderResult.Error("Failed to initialize Gemini: ${e.message}", e)
        }
    }
    
    /**
     * Initialize with an API key (used when loading from DataStore)
     */
    suspend fun initializeWithApiKey(apiKey: String?): AIProviderResult<Unit> {
        return try {
            if (!isGeminiApiAvailable()) {
                return AIProviderResult.Error("Gemini API not available. Please ensure the dependency is properly configured.")
            }

            // Use provided API key, or fall back to BuildConfig
            this.apiKey = apiKey?.takeIf { it.isNotBlank() } 
                ?: getApiKeyFromBuildConfig()?.takeIf { it.isNotBlank() }

            if (this.apiKey != null) {
                Log.d(TAG, "Gemini provider initialized with API key")
            } else {
                Log.d(TAG, "Gemini provider initialized without API key. User can configure it via UI.")
            }

            isInitialized = true
            Log.d(TAG, "Gemini provider initialized successfully")
            AIProviderResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Gemini provider", e)
            AIProviderResult.Error("Failed to initialize Gemini: ${e.message}", e)
        }
    }

    override suspend fun getAvailableModels(): AIProviderResult<List<AIModel>> {
        return if (isInitialized || isGeminiApiAvailable()) {
            AIProviderResult.Success(AVAILABLE_MODELS)
        } else {
            AIProviderResult.Error("Provider not initialized")
        }
    }

    override suspend fun downloadModel(modelId: String): Flow<Float> {
        return flow { emit(1.0f) }
    }

    override suspend fun loadModel(modelId: String): AIProviderResult<Unit> {
        return try {
            if (!isInitialized) {
                return AIProviderResult.Error("Provider not initialized")
            }

            // Check if API key is configured
            if (apiKey.isNullOrBlank()) {
                return AIProviderResult.Error("Gemini API key not configured. Please configure it in:\n1. gradle.properties file (GEMINI_API_KEY=your_key)\n2. Or in AI Settings → Gemini → Configure API Key")
            }

            // Check if Gemini API is available before trying to load model
            if (!isGeminiApiAvailable()) {
                return AIProviderResult.Error("Gemini API dependency not available. Please add the Gemini SDK dependency to your build.gradle file:\nimplementation(\"com.google.ai.client.generativeai:generativeai:0.7.0\")")
            }

            val modelInfo = AVAILABLE_MODELS.find { it.id == modelId }
                ?: return AIProviderResult.Error("Model not found: $modelId")

            Log.d(TAG, "Loading Gemini model: $modelId")

            // Use reflection to create the actual GenerativeModel
            val model = createActualGenerativeModel(modelId, apiKey!!)
            if (model != null) {
                generativeModel = model
                currentModel = modelInfo.copy(isLoaded = true)
                Log.d(TAG, "Successfully loaded Gemini model: $modelId")
                AIProviderResult.Success(Unit)
            } else {
                AIProviderResult.Error("Failed to create Gemini model. The Gemini SDK dependency may not be properly configured.")
            }
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Gemini API classes not found when loading model: $modelId", e)
            AIProviderResult.Error("Gemini API dependency not available. Please add 'com.google.ai.client.generativeai:generativeai:0.7.0' to your dependencies.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Gemini model: $modelId", e)
            AIProviderResult.Error("Failed to load model: ${e.message}", e)
        }
    }

    override suspend fun generateStream(prompt: String): Flow<String> {
        return try {
            Log.d(TAG, "generateStream called with prompt length: ${prompt.length}")
            val model = generativeModel
            if (model == null) {
                Log.e(TAG, "No model loaded! generativeModel is null")
                return flow { emit("Error: No model loaded. Please load a model first.") }
            }

            Log.d(TAG, "Model is loaded, checking if Gemini API is available...")
            val apiAvailable = isGeminiApiAvailable()
            Log.d(TAG, "Gemini API available: $apiAvailable")

            if (apiAvailable) {
                Log.d(TAG, "Calling generateStreamWithActualApi...")
                generateStreamWithActualApi(model, prompt)
            } else {
                Log.w(TAG, "Gemini API not available - returning fallback response")
                // Fallback
                flow { emit("Error: Gemini API not available. Please ensure the Gemini dependency is properly configured. Your prompt was: $prompt") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate stream", e)
            flow { emit("Error: ${e.message}") }
        }
    }

    override suspend fun generate(prompt: String): AIProviderResult<String> {
        return try {
            val model = generativeModel ?: return AIProviderResult.Error("No model loaded")

            if (isGeminiApiAvailable()) {
                val text = generateWithActualApi(model, prompt)
                if (text != null) {
                    AIProviderResult.Success(text)
                } else {
                    AIProviderResult.Error("Failed to generate content")
                }
            } else {
                AIProviderResult.Success("Gemini API not available. Response to: $prompt")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate content", e)
            AIProviderResult.Error("Generation failed: ${e.message}", e)
        }
    }

    override fun getCurrentModel(): AIModel? = currentModel

    override fun isReady(): Boolean {
        return isInitialized && generativeModel != null && currentModel != null
    }

    override suspend fun cleanup() {
        generativeModel = null
        currentModel = null
    }

    fun updateApiKey(newApiKey: String): Boolean {
        return if (newApiKey.isNotBlank()) {
            Log.d(TAG, "Updating Gemini API key")
            this.apiKey = newApiKey
            // Clear any loaded model so it can be reloaded with new key
            if (currentModel != null) {
                generativeModel = null
                currentModel = currentModel?.copy(isLoaded = false)
                Log.d(TAG, "Cleared loaded model - user needs to reload it with new API key")
            }
            // Ensure provider is initialized
            if (!isInitialized) {
                isInitialized = true
                Log.d(TAG, "Provider marked as initialized after API key update")
            }
            true
        } else {
            Log.w(TAG, "Attempted to update with blank API key")
            false
        }
    }
    
    /**
     * Check if API key is configured
     */
    fun hasApiKey(): Boolean {
        return !apiKey.isNullOrBlank()
    }

    // Actual Gemini API integration using reflection
    private fun createActualGenerativeModel(modelId: String, apiKey: String): Any? {
        return try {
            Log.d(TAG, "Creating GenerativeModel for $modelId")
            
            // Create GenerativeModel using reflection
            val generativeModelClass =
                Class.forName("com.google.ai.client.generativeai.GenerativeModel")
            Log.d(TAG, "Found GenerativeModel class")
            
            val builderClass =
                Class.forName("com.google.ai.client.generativeai.GenerativeModel\$Builder")
            Log.d(TAG, "Found GenerativeModel\$Builder class")

            // Create builder
            val builder =
                builderClass.getDeclaredConstructor(String::class.java, String::class.java)
                    .newInstance(modelId, apiKey)
            Log.d(TAG, "Created builder instance")

            // Configure generation settings
            try {
                val generationConfigClass =
                    Class.forName("com.google.ai.client.generativeai.type.GenerationConfig")
                val configBuilderClass =
                    Class.forName("com.google.ai.client.generativeai.type.GenerationConfig\$Builder")

                val configBuilder = configBuilderClass.getDeclaredConstructor().newInstance()

                // Set temperature, topP, etc.
                configBuilderClass.getMethod("temperature", Float::class.java)
                    .invoke(configBuilder, 0.7f)
                configBuilderClass.getMethod("topP", Float::class.java).invoke(configBuilder, 0.8f)
                configBuilderClass.getMethod("topK", Int::class.java).invoke(configBuilder, 40)
                configBuilderClass.getMethod("maxOutputTokens", Int::class.java)
                    .invoke(configBuilder, 8192)

                val config = configBuilderClass.getMethod("build").invoke(configBuilder)
                builderClass.getMethod("generationConfig", generationConfigClass)
                    .invoke(builder, config)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set generation config: ${e.message}")
            }

            // Build the model
            val model = builderClass.getMethod("build").invoke(builder)
            Log.d(TAG, "Successfully created GenerativeModel instance")
            model
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Gemini API classes not found. Please add the dependency: implementation(\"com.google.ai.client.generativeai:generativeai:0.7.0\")", e)
            null
        } catch (e: NoSuchMethodException) {
            Log.e(TAG, "Required Gemini API method not found. The SDK version may be incompatible.", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create GenerativeModel", e)
            e.printStackTrace()
            null
        }
    }

    private fun generateStreamWithActualApi(model: Any, prompt: String): Flow<String> {
        return try {
            Log.d(TAG, "generateStreamWithActualApi called with model type: ${model.javaClass.name}")
            Log.d(TAG, "Prompt: ${prompt.take(200)}...")
            
            // Use reflection to call generateContentStream
            val method = model.javaClass.getMethod("generateContentStream", String::class.java)
            Log.d(TAG, "Found generateContentStream method, invoking...")
            
            val flowResult = method.invoke(model, prompt)
            Log.d(TAG, "Method invoked, result type: ${flowResult?.javaClass?.name}")

            // Convert the result to our Flow<String>
            if (flowResult is Flow<*>) {
                Log.d(TAG, "Result is a Flow, mapping responses...")
                @Suppress("UNCHECKED_CAST")
                (flowResult as Flow<Any>).map { response ->
                    // Extract text from response using reflection
                    try {
                        Log.d(TAG, "Extracting text from response type: ${response.javaClass.name}")
                        val textProperty = response.javaClass.getMethod("getText")
                        val text = textProperty.invoke(response) as? String ?: ""
                        Log.d(TAG, "Extracted text length: ${text.length}")
                        text
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not extract text from response", e)
                        ""
                    }
                }
            } else {
                Log.e(TAG, "Invalid response type from Gemini API: ${flowResult?.javaClass?.name}")
                flow { emit("Error: Invalid response type from Gemini API. Expected Flow, got ${flowResult?.javaClass?.name}") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateStreamWithActualApi", e)
            e.printStackTrace()
            flow { emit("Error: ${e.message}\nStack trace: ${e.stackTraceToString()}") }
        }
    }

    private suspend fun generateWithActualApi(model: Any, prompt: String): String? {
        return try {
            // Use reflection to call generateContent
            val method = model.javaClass.getMethod("generateContent", String::class.java)
            val response = method.invoke(model, prompt)

            // Extract text from response
            val textMethod = response.javaClass.getMethod("getText")
            textMethod.invoke(response) as? String
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateWithActualApi", e)
            null
        }
    }
}