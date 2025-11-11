# Quick Gemini API Setup Guide

This guide shows how to quickly set up and use the Gemini API integration in your TechnoTrak app.

## ✨ What's New

Your app now supports **two AI providers**:

- **RunAnywhere SDK** (local models) - existing functionality
- **Google Gemini API** (cloud models) - **NEW!**

You can easily switch between them and use the same natural language commands with both.

## 🚀 Quick Setup (2 minutes)

### Step 1: Get Your Free Gemini API Key

1. Go to [Google AI Studio](https://ai.google.dev/)
2. Sign in with your Google account
3. Click "Get API Key" → "Create API key in new project"
4. Copy the key (starts with `AIza...`)

### Step 2: Add API Key to Your Project

Open `gradle.properties` and add your key:

```properties
# Replace with your actual API key
GEMINI_API_KEY=AIzaSyDhYourActualApiKeyHere
```

### Step 3: Run and Test

1. **Build and run** your app
2. Go to **AI Settings** screen
3. You should see both providers:
    - ✅ RunAnywhere (Local) - Available
    - ✅ Google Gemini (Cloud) - Available
4. **Select Gemini** and choose a model (e.g., "Gemini 1.5 Flash")
5. Go to **AI Chat** and test with: `"Create a mobile app project"`

## 🎯 Key Benefits

### RunAnywhere (Local)

- ✅ **Privacy**: Everything runs on-device
- ✅ **Offline**: Works without internet
- ✅ **No costs**: No API charges
- ❌ **Slower**: Depends on device performance
- ❌ **Large downloads**: Models are 2-8GB

### Gemini (Cloud)

- ✅ **Fast**: Instant responses
- ✅ **No downloads**: Ready immediately
- ✅ **Always updated**: Latest model capabilities
- ✅ **Free tier**: Generous daily limits
- ❌ **Requires internet**: Online only
- ❌ **API costs**: After free tier (very reasonable)

## 🔧 Usage Examples

The natural language interface works identically with both providers:

```kotlin
// All these commands work with both RunAnywhere and Gemini:
viewModel.processNaturalLanguageCommand("Create a mobile app project")
viewModel.processNaturalLanguageCommand("Add UI design task to mobile project")
viewModel.processNaturalLanguageCommand("Update project description to include user authentication")
viewModel.processNaturalLanguageCommand("Delete the old test project")
```

## 🔄 Switching Providers

### Via UI (Recommended)

1. Open **AI Settings**
2. Select your preferred provider (RunAnywhere or Gemini)
3. Choose/load a model
4. Start using AI commands

### Programmatically

```kotlin
// Switch to Gemini
aiProjectAssistantViewModel.switchProvider(AIProviderType.GEMINI)

// Switch to RunAnywhere  
aiProjectAssistantViewModel.switchProvider(AIProviderType.RUN_ANYWHERE)
```

## 🛠️ Advanced Configuration

### Runtime API Key Update

If you prefer not to add the key to gradle.properties:

1. Leave `GEMINI_API_KEY=` empty in gradle.properties
2. Open the app → AI Settings → Select Gemini
3. Click the ⚙️ settings icon next to Gemini
4. Enter your API key in the dialog

### Multiple Environments

```properties
# gradle.properties
# Development
GEMINI_API_KEY_DEV=AIzaSyDevelopmentKey

# Production  
GEMINI_API_KEY_PROD=AIzaSyProductionKey

# Use appropriate key based on build variant
GEMINI_API_KEY=${GEMINI_API_KEY_DEV}
```

## 🐛 Troubleshooting

### "Provider not initialized"

- **Cause**: API key not set or invalid
- **Fix**: Check your API key in gradle.properties or app settings

### "Model not ready"

- **Cause**: No model loaded
- **Fix**: Go to AI Settings → Select provider → Load a model

### "Network error"

- **Cause**: Internet connection issues (Gemini only)
- **Fix**: Check internet connection or switch to RunAnywhere

### Models not showing up

- **Cause**: Build sync issues
- **Fix**: Clean and rebuild: `./gradlew clean build`

## 📊 Performance Comparison

| Feature | RunAnywhere | Gemini |
|---------|-------------|---------|
| **First Response** | 10-30s (model loading) | <2s |
| **Subsequent Responses** | 5-15s | 1-3s |
| **Model Size** | 2-8GB | 0MB |
| **Internet Required** | No | Yes |
| **Monthly Cost** | $0 | $0-20 (typical usage) |

## 🔒 Security Notes

- API keys are stored securely using Android's EncryptedSharedPreferences
- Keys are never logged in release builds
- You can revoke/rotate keys anytime in Google AI Studio
- Local RunAnywhere models never send data externally

## 🎉 What's Next?

Try these advanced commands:

1. **Complex Project Creation**:
   ```
   "Create an e-commerce Android app project with tasks for user authentication, product catalog, shopping cart, payment integration, and order tracking"
   ```

2. **Project Management**:
   ```
   "Update the mobile app project to include social media integration and assign the UI tasks to designer@company.com"
   ```

3. **Bulk Operations**:
   ```
   "Create projects for website redesign, mobile app, and API backend, each with their respective development tasks"
   ```

The AI will intelligently parse your requests and execute the appropriate project management
actions!

---

**Need help?** Check the full [AI Provider Integration Guide](./AI_PROVIDER_INTEGRATION_GUIDE.md)
for detailed technical information.