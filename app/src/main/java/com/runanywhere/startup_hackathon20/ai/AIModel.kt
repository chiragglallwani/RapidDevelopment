package com.runanywhere.startup_hackathon20.ai

data class AIModel(
    val id: String,
    val name: String,
    val description: String? = null,
    val providerType: AIProviderType,
    var isDownloaded: Boolean = false,
    var isLoaded: Boolean = false
)
