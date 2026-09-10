#!/bin/bash

# Fast and reliable deployment script for Dar-e-Rah (Voice App)

echo "🚀 Starting fast build and deployment..."

# Find adb
if command -v adb >/dev/null 2>&1; then
    ADB="adb"
elif [ -f "$HOME/Android/Sdk/platform-tools/adb" ]; then
    ADB="$HOME/Android/Sdk/platform-tools/adb"
else
    echo "❌ adb command not found!"
    exit 1
fi

# 1. Wake up connected phone screen so Xiaomi/MIUI won't block install
$ADB shell input keyevent KEYCODE_WAKEUP 2>/dev/null

# 2. Build APK incrementally with Gradle cache
echo "📦 Building APK (incremental)..."
./gradlew assembleDebug --offline --build-cache -q

if [ $? -ne 0 ]; then
    echo "❌ Build failed! Check Gradle compilation errors above."
    exit 1
fi

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "❌ APK file not found at $APK_PATH"
    exit 1
fi

# 3. Stream install directly via adb (reinstall, keep data, allow downgrade)
echo "📲 Installing to connected device..."
INSTALL_OUTPUT=$($ADB install -r -d "$APK_PATH" 2>&1)
INSTALL_STATUS=$?

if [ $INSTALL_STATUS -eq 0 ] && echo "$INSTALL_OUTPUT" | grep -q "Success"; then
    echo "=========================================================="
    echo "✅ Successfully installed on your device in seconds!"
    echo "=========================================================="
    
    echo "▶️ Launching the app..."
    $ADB shell am start -n com.example.voiceapp/.MainActivity >/dev/null 2>&1
    
    # If user passed --log flag, stream logcat
    if [ "$1" == "--log" ] || [ "$1" == "-l" ]; then
        echo "📋 Streaming app logs (Ctrl+C to stop)..."
        $ADB logcat -c
        $ADB logcat -s Baseer VoiceRouter
    fi
else
    echo "=========================================================="
    echo "❌ INSTALLATION FAILED!"
    echo "$INSTALL_OUTPUT"
    echo ""
    echo "HOW TO FIX IT:"
    echo "1. Unlock your phone screen (make sure it's not on lock screen)."
    echo "2. Go to Settings -> Developer Options:"
    echo "   - Enable 'Install via USB'"
    echo "   - Turn off 'Verify apps over USB' (if present)"
    echo "3. Run again: ./deploy.sh"
    echo "=========================================================="
    exit 1
fi
