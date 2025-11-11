# Fix Gemini SDK "Builder class not found" Error

## Quick Fix Steps

### Step 1: Sync Gradle
```bash
./gradlew --refresh-dependencies
```
Or in Android Studio: **File → Sync Project with Gradle Files**

### Step 2: Clean Build
```bash
./gradlew clean
```
Or in Android Studio: **Build → Clean Project**

### Step 3: Rebuild
```bash
./gradlew :app:assembleDebug
```
Or in Android Studio: **Build → Rebuild Project**

### Step 4: Uninstall Old App
- Uninstall the app from your device/emulator completely
- This ensures a fresh install with all dependencies

### Step 5: Install Fresh Build
- Run the app again from Android Studio
- Or install the new APK

## Verify It's Fixed

1. Open the app
2. Go to **AI Settings**
3. Select **Google Gemini (Cloud)**
4. Click **Load** on any model (e.g., "Gemini 1.5 Flash")
5. Should see: "Model loaded! Ready to assist."

## If Still Not Working

### Check Logcat
Filter by tag: `GeminiProvider`

Look for:
- "Found GenerativeModel class" ✅
- "Found Builder class" ✅
- "Created builder instance" ✅

If you see "ClassNotFoundException", the SDK classes aren't in the APK.

### Verify Dependency
The dependency is already in `app/build.gradle.kts`:
```kotlin
implementation("com.google.ai.client.generativeai:generativeai:0.7.0")
```

### Try Different SDK Version
If 0.7.0 doesn't work, try updating to the latest version:
```kotlin
implementation("com.google.ai.client.generativeai:generativeai:0.8.0")
```
Then sync and rebuild.

## Common Causes

1. **Stale build** - Most common. Clean rebuild fixes it.
2. **Dependency not synced** - Gradle sync needed.
3. **Old APK installed** - Uninstall and reinstall.
4. **Version incompatibility** - Try updating SDK version.

## The Code Now

The code has been updated to:
- Try multiple ways to find the Builder class
- Check inner classes automatically
- Try direct constructor if Builder isn't found
- Provide detailed error messages

Check Logcat for "GeminiProvider" logs to see exactly what's happening.

