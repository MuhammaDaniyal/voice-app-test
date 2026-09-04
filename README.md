# Voice App Test

A basic native Android application built with Kotlin DSL that uses the Vosk offline speech recognition engine.

## Features
- **Offline Speech Recognition:** Uses Vosk for completely offline, on-device transcription.
- **Dynamic Permissions:** Handles `RECORD_AUDIO` permissions at runtime.
- **Kotlin DSL:** Built with modern Android Gradle Plugin and `build.gradle.kts`.

## Models Used
This project uses the **[Vosk Small English Model (`vosk-model-small-en-us-0.15`)](https://alphacephei.com/vosk/models)**.

**Why this model?**
- **Lightweight:** The acoustic and language models combined are roughly ~40MB, making it small enough to bundle directly within the Android APK's `assets/` folder.
- **Low Resource Consumption:** Once loaded into the phone's memory, it consumes only about 50-80MB of RAM. This makes it extremely efficient for mobile devices, preserving battery life and leaving resources free for other tasks.
- **Offline Privacy:** It operates 100% locally on-device without requiring an internet connection, ensuring user privacy and zero-latency inference.

---

## How to Run the Code

Before running, ensure you have an Android device connected via USB with **USB Debugging** enabled. 

### On Ubuntu / Linux
1. Open your terminal in the root directory of the project.
2. Build and install the app using the Gradle wrapper:
   ```bash
   ./gradlew installDebug
   ```
3. Once installed, open the app on your phone and grant the microphone permission when prompted.
4. To view the real-time transcriptions, run `adb logcat` and filter for Vosk output:
   ```bash
   adb logcat -s VoiceRouter VoskError
   ```
   *(Note: If `adb` is not in your global path, use the full path, e.g., `~/Android/Sdk/platform-tools/adb logcat ...`)*

### On Windows
1. Open Command Prompt (CMD) or PowerShell in the root directory of the project.
2. Build and install the app using the Windows Gradle wrapper:
   ```cmd
   gradlew.bat installDebug
   ```
3. Once installed, open the app on your phone and grant the microphone permission when prompted.
4. To view the real-time transcriptions, use `adb`:
   ```cmd
   adb logcat -s Vosk VoskPartial VoskResult VoskError
   ```
   *(Note: If `adb` is not recognized, you must add your Android SDK `platform-tools` directory—usually `C:\Users\%USERNAME%\AppData\Local\Android\Sdk\platform-tools`—to your Windows System Environment Variables `Path`).*

---

## Notes
- The Vosk model is unpacked from the `assets/model-en-us` folder on first launch into the app's cache directory.
- The model requires a `uuid` file in the assets folder to track version control during unpacking.
