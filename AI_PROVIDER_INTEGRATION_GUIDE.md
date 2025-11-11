# AI Provider Integration Guide

This document explains how to use and configure multiple AI providers in the TechnoTrak application.
The app now supports both RunAnywhere SDK (local models) and Google Gemini API (cloud models), with
easy switching between them.

## Overview

The AI provider system allows you to:

- Switch between local (RunAnywhere) and cloud (Gemini) AI providers
- Manage API keys securely
- Download and manage local models
- Use the same natural language interface with any provider
- Seamlessly switch providers without losing functionality

## Supported Providers

### 1. RunAnywhere SDK (Local Models)

- **Type**: Local inference
- **Models**: Various open-source models (LLaMA-based)
- **Pros**: Privacy, offline usage, no API costs
- **Cons**: Requires model downloads, device performance dependent

### 2. Google Gemini API (Cloud Models)

- **Type**: Cloud API
- **Models**: Gemini 1.5 Flash, Gemini 1.5 Pro, Gemini 1.0 Pro
- **Pros**: High performance, no local storage needed, always up-to-date
- **Cons**: Requires internet, API costs (has free tier)

## Setup Instructions

### For Gemini API

1. **Get API Key**:
    - Visit [Google AI Studio](https://ai.google.dev/)
    - Sign up/sign in with your Google account
    - Create a new API key
    - Copy your API key (starts with `AIza...`)

2. **Configure in Project**:

   **Option A: Build-time Configuration**
   ```properties
   # In gradle.properties file
   GEMINI_API_KEY=AIzaSyDhYourActualApiKeyHere
   ```

   **Option B: Runtime Configuration**
    - Open the app
    - Go to AI Settings
    - Select Gemini provider
    - Click the settings icon
    - Enter your API key

3. **Verify Setup**:
    - Open AI Settings
    - You should see "Google Gemini (Cloud)" as available
    - Select it and choose a model

### For RunAnywhere SDK

RunAnywhere is configured automatically. Just:

1. Go to AI Settings
2. Select "RunAnywhere (Local)"
3. Download a model if needed
4. Load the model

## Usage Guide

### Switching Providers

1. **Via Settings Screen**:
   ```kotlin
   // Navigate to AI Settings
   // Select desired provider (RunAnywhere or Gemini)
   // The change is applied immediately
   ```

2. **Programmatically**:
   ```kotlin
   // In your ViewModel or component
   aiProjectAssistantViewModel.switchProvider(AIProviderType.GEMINI)
   // or
   aiProjectAssistantViewModel.switchProvider(AIProviderType.RUN_ANYWHERE)
   ```

### Managing Models

**For RunAnywhere Models**:

```kotlin
// Download a model
aiProjectAssistantViewModel.downloadModel("model-id")

// Load a model  
aiProjectAssistantViewModel.loadModel("model-id")
```

**For Gemini Models**:

```kotlin
// Models are available immediately once API key is configured
aiProjectAssistantViewModel.loadModel("gemini-1.5-flash")
```

### Using AI Commands

The natural language interface works the same regardless of provider:

```kotlin
// All these work with both providers
viewModel.processNaturalLanguageCommand("Create a mobile app project")
viewModel.processNaturalLanguageCommand("Add UI design task to mobile project")  
viewModel.processNaturalLanguageCommand("Update project description")
```

## Architecture

### Core Components

1. **AIProvider Interface**
   ```kotlin
   interface AIProvider {
       suspend fun initialize(): AIProviderResult<Unit>
       suspend fun generateStream(prompt: String): Flow<String>
       suspend fun loadModel(modelId: String): AIProviderResult<Unit>
       // ... other methods
   }
   ```

2. **AIProviderManager**
    - Manages multiple providers
    - Handles provider switching
    - Stores preferences
    - Manages API keys

3. **Concrete Providers**
    - `RunAnywhereProvider`: Wraps RunAnywhere SDK
    - `GeminiProvider`: Implements Gemini API

### Data Flow

```
User Command → AIProviderManager → Active Provider → AI Model → Response
```

## Configuration Options

### Provider Preferences

Stored in DataStore with automatic persistence:

- Selected provider type
- Gemini API key
- Model preferences

### Security

- API keys stored securely using EncryptedSharedPreferences
- Keys never logged or exposed in debug builds
- Option to configure at build time or runtime

## API Reference

### AIProviderType

```kotlin
enum class AIProviderType {
    RUN_ANYWHERE,
    GEMINI
}
```

### AIModel

```kotlin
data class AIModel(
    val id: String,
    val name: String, 
    val description: String,
    val isDownloaded: Boolean,
    val isLoaded: Boolean,
    val providerType: AIProviderType
)
```

### AIProjectAssistantViewModel New Methods

```kotlin
// Provider Management
fun switchProvider(providerType: AIProviderType)
fun updateGeminiApiKey(apiKey: String)

// State Access  
val currentProviderType: StateFlow<AIProviderType?>
val availableProviders: StateFlow<List<Pair<AIProviderType, Boolean>>>
val geminiApiKey: StateFlow<String>
```

## Error Handling

### Common Issues

1. **"Provider not initialized"**
    - Solution: Check API key configuration for Gemini
    - Solution: Ensure RunAnywhere SDK is properly set up

2. **"Model not ready"**
    - Solution: Load a model first via AI Settings
    - Solution: Check internet connection for Gemini

3. **"API key not configured"**
    - Solution: Add API key in gradle.properties or app settings

### Error Recovery

The system automatically:

- Falls back to available providers
- Persists provider preferences
- Gracefully handles network issues
- Provides clear error messages

## Performance Considerations

### RunAnywhere (Local)

- Model loading: 5-30 seconds depending on model size
- Inference speed: Varies by device (faster on newer devices)
- Memory usage: 2-8GB depending on model

### Gemini (Cloud)

- Model loading: Instant (cloud-based)
- Inference speed: Depends on network latency
- Memory usage: Minimal local usage

## Migration Guide

### From Previous Implementation

If upgrading from the old RunAnywhere-only implementation:

1. **Update ViewModel Constructor**:
   ```kotlin
   // Old
   AIProjectAssistantViewModel(projectRepo, taskRepo)
   
   // New  
   AIProjectAssistantViewModel(context, projectRepo, taskRepo)
   ```

2. **Update Dependency Injection**:
   ```kotlin
   // Provide context to ViewModel
   viewModel { AIProjectAssistantViewModel(androidContext(), get(), get()) }
   ```

3. **No Changes Required**:
    - All existing natural language commands work the same
    - UI components remain compatible
    - Command processing logic unchanged

## Troubleshooting

### Debug Logging

Enable detailed logging:

```kotlin
// In your Application class
if (BuildConfig.DEBUG) {
    // AI provider logs are automatically enabled in debug builds
}
```

### Provider Status Check

```kotlin
// Check provider availability
val providers = aiProviderManager.getAvailableProviders()
providers.forEach { (type, available) ->
    Log.d("AI", "$type: ${if (available) "Ready" else "Not configured"}")
}
```

### Model Status Check

```kotlin
// Verify model is ready
val isReady = aiProviderManager.isReady()
val currentModel = aiProviderManager.getCurrentModel()
Log.d("AI", "Ready: $isReady, Model: ${currentModel?.name}")
```

## Best Practices

### For Development

- Use Gemini for rapid prototyping (no model downloads)
- Use RunAnywhere for privacy-sensitive development
- Test with both providers to ensure compatibility

### For Production

- Provide both options to users
- Cache provider preferences
- Handle network failures gracefully
- Monitor API usage costs

### For Performance

- Keep one provider loaded at a time
- Pre-load preferred models
- Cache AI responses when appropriate
- Use appropriate model sizes for device capabilities

## Example Integration

### Basic Setup

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val viewModel = viewModel<AIProjectAssistantViewModel>()
            
            // AI Provider is automatically initialized
            AISettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { /* handle navigation */ }
            )
        }
    }
}
```

### Advanced Usage

```kotlin
class ProjectManagementScreen : ComponentActivity() {
    private val viewModel by viewModel<AIProjectAssistantViewModel>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Observe provider changes
        lifecycleScope.launch {
            viewModel.currentProviderType.collect { provider ->
                Log.d("AI", "Active provider: $provider")
            }
        }
        
        // Use AI commands
        viewModel.processNaturalLanguageCommand("Create a new mobile project with authentication tasks")
    }
}
```

This integration provides a flexible, extensible AI system that can easily accommodate additional
providers in the future while maintaining a consistent user experience.