# Gemini API Key Configuration Guide

This guide shows you **where and how** to add your Gemini API key so the app can use Google Gemini models.

## 🔑 Two Ways to Add Your API Key

### **Option 1: Add to `gradle.properties` (Recommended for Development)**

1. **Location**: Open the file `gradle.properties` in the root of your project
2. **Find this line** (around line 27-28):
   ```properties
   # GEMINI_API_KEY=your_api_key_here
   GEMINI_API_KEY=AIzaSyCGCqf7advVgnc_f-Y9gEhaUD5WInqyxyo
   ```
3. **Replace** the value after `=` with your actual API key:
   ```properties
   GEMINI_API_KEY=AIzaSyYourActualApiKeyHere
   ```
4. **Sync Gradle**: In Android Studio, click "Sync Now" or run `./gradlew build`
5. **Rebuild**: Clean and rebuild your project

**Note**: The API key in `gradle.properties` is already set. If it's not working, try Option 2.

---

### **Option 2: Configure via App UI (Recommended for Production)**

1. **Get your API key**:

   - Visit [Google AI Studio](https://ai.google.dev/)
   - Sign in with your Google account
   - Click "Get API Key" → "Create API key in new project"
   - Copy your key (starts with `AIza...`)

2. **Configure in the app**:

   - Open your app
   - Go to **AI Settings** screen
   - Find **"Google Gemini (Cloud)"** option
   - Click the **settings icon** (⚙️) next to it
   - Enter your API key in the dialog
   - Click **"Save"**

3. **Verify**:
   - The status should change from "Not configured" to "Available"
   - Select Gemini provider
   - Choose a model (e.g., "Gemini 1.5 Flash")
   - Load the model

---

## ✅ How to Verify It's Working

1. **Check AI Settings**:

   - Open AI Settings screen
   - You should see: **"Google Gemini (Cloud) - Available"** (not "Not configured")

2. **Load a Model**:

   - Select Gemini provider
   - Choose a model (e.g., "Gemini 1.5 Flash")
   - Click "Load Model"
   - You should see: "Model loaded! Ready to assist."

3. **Test in AI Chat**:
   - Go to AI Chat/Assistant screen
   - Type: `"Create a mobile app project"`
   - You should get a proper response from Gemini (not just the prompt text)

---

## 🔍 Current Configuration Status

**Your `gradle.properties` file already has an API key set:**

```properties
GEMINI_API_KEY=AIzaSyCGCqf7advVgnc_f-Y9gEhaUD5WInqyxyo
```

**If it's still showing "Not configured":**

1. The API key might be invalid or expired
2. Try using Option 2 (configure via UI) with a fresh API key
3. Check Logcat for error messages (filter by "GeminiProvider")

---

## 🛠️ Troubleshooting

### Issue: "Gemini API key not configured"

**Solution**:

- Make sure the API key in `gradle.properties` is not commented out (no `#` at the start)
- Or configure it via the UI (Option 2)

### Issue: "Gemini API dependency not available"

**Solution**:

- The Gemini SDK dependency is missing
- Check `app/build.gradle.kts` - it should have:
  ```kotlin
  implementation("com.google.ai.client.generativeai:generativeai:0.7.0")
  ```
- Sync Gradle and rebuild

### Issue: "Model not loaded" or "No model ready"

**Solution**:

1. Make sure API key is configured (check AI Settings)
2. Select Gemini provider
3. Choose a model from the list
4. Click "Load Model"
5. Wait for "Model loaded! Ready to assist." message

---

## 📝 File Locations Summary

- **`gradle.properties`** (root directory) - For build-time API key
- **AI Settings Screen** (in app) - For runtime API key configuration
- **DataStore** (internal) - Where UI-configured API keys are stored

---

## 🎯 Quick Checklist

- [ ] Get API key from [ai.google.dev](https://ai.google.dev/)
- [ ] Add to `gradle.properties` OR configure via UI
- [ ] Sync Gradle (if using `gradle.properties`)
- [ ] Rebuild project
- [ ] Open AI Settings → Verify "Available" status
- [ ] Select Gemini → Load a model
- [ ] Test in AI Chat

---

**Need help?** Check Logcat logs with filter "GeminiProvider" or "AIProviderManager" for detailed error messages.
