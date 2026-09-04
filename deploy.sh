#!/bin/bash

echo "Starting build and deployment process..."

# Build and install the app using gradle
./gradlew installDebug

# Check the exit status of the gradle command
if [ $? -eq 0 ]; then
    echo "=========================================================="
    echo "✅ Successfully installed the app on the connected device!"
    echo "=========================================================="
    
    # Try to launch the app automatically using the local Android SDK adb
    ADB_PATH="$HOME/Android/Sdk/platform-tools/adb"
    if [ -f "$ADB_PATH" ]; then
        echo "Launching the app..."
        $ADB_PATH shell am start -n com.example.voiceapp/.MainActivity
        echo "Starting logcat..."
        $ADB_PATH logcat -c # clear old logs
        $ADB_PATH logcat -s VoiceRouter VoskError
    else
        echo "Could not find adb at $ADB_PATH to auto-launch the app."
        echo "Please open the app manually on your phone."
    fi
else
    echo "=========================================================="
    echo "❌ INSTALLATION FAILED!"
    echo ""
    echo "Since you didn't see a prompt on your screen, this usually means"
    echo "your phone's system (like MIUI on Xiaomi/Redmi) blocked it automatically."
    echo ""
    echo "HOW TO FIX IT:"
    echo "1. Go to your phone's Settings -> Developer Options."
    echo "2. Find and turn ON 'Install via USB'."
    echo "3. (Optional) Turn ON 'USB Debugging (Security settings)' if it's there."
    echo "4. Run this script again: ./deploy.sh"
    echo "=========================================================="
fi
