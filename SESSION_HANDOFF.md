# Session Handoff Context

**To the next AI session:** Please read this file to understand the current state of the project.

## Project Goal
Building a native offline Android voice recognition application using Kotlin DSL and the Vosk engine (`vosk-model-small-en-us-0.15`).

## Current State & Accomplishments
- **Infrastructure:** The project is correctly configured with Gradle Wrapper 8.2 and Kotlin DSL (`build.gradle.kts`). Dependencies (`net.java.dev.jna:jna` and `com.alphacephei:vosk-android`) are resolving perfectly via JitPack/MavenCentral.
- **Permissions:** Dynamic runtime permissions for `RECORD_AUDIO` and `CAMERA` are handled upfront in `MainActivity.kt`, and declared in `AndroidManifest.xml` alongside `INTERNET` and `VIBRATE`.
- **Model Unpacking:** The Vosk model successfully unpacks from the `assets/model-en-us` directory. (A `uuid` file was manually added to satisfy the `StorageService`).
- **Intent Router:** Built an intent router in `MainActivity.kt` that strictly constraints the Vosk `Recognizer` to a JSON grammar array (`["currency", "read screen", "detect hazard", "find object", "help", "stop", "describe", "[unk]"]`). The router successfully parses the JSON and maps recognized keywords to actions.

## Pending Task: UI & Dynamic Vocabulary
The user requested the following features to be implemented next:
1. **Graphical UI:** Replace the headless background setup by creating a `res/layout/activity_main.xml` containing:
   - A `TextView` to display the recognized keyword directly on the screen.
   - An `EditText` to allow the user to input custom vocabulary words (e.g., `apple, banana`).
   - A `Button` to apply the new vocabulary.
2. **Main Thread UI Updates:** Use `runOnUiThread` inside `onResult` and `onPartialResult` so the on-screen `TextView` updates in real-time.
3. **Dynamic Reinitialization:** When the "Apply" button is clicked:
   - Stop the current `SpeechService`.
   - Read the words from the `EditText`.
   - Recreate the JSON grammar array constraint.
   - Reinitialize the `Recognizer` and restart the `SpeechService` using the new dynamically generated vocabulary.

**Note on Workspace:** The user just renamed the project folder to `voice-app-test`. You now have full terminal access in the new session.
