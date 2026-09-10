package com.example.voiceapp.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import com.example.voiceapp.AppMode
import com.example.voiceapp.VoskManager
import java.util.Locale

/** Possible screen states */
private enum class ScreenState {
    IDLE,             // Camera preview, waiting for input
    LISTENING,        // Vosk active after valid mic press
    MODE_ACTIVATED,   // "<Mode> mode activated" spoken
    FIND_OBJECT_MENU, // "What do you want to find?" menu open
    PLACE_RECALL,     // Place Memory recall TTS playing before video
    PLAYING_VIDEO,    // Demo video continuous feed playing
    SHOWING_RESULT,   // Static result view if video missing
    NAMING_OBJECT,    // Asking user: "What would you like to name this object?"
    LISTENING_NAME,   // Free-form Vosk listening for object name
    OBJECT_SAVED      // Confirmation spoken and displayed
}

/**
 * Main screen orchestrator for the Dar-e-Rah (BASEER / Aura) POC.
 * Manages mode switching, video feeds, bounding boxes, TTS, and Place Memory recall.
 */
@Composable
fun BaseerScreen(
    voskManager: VoskManager,
    lifecycleOwner: LifecycleOwner,
    hasCameraPermission: Boolean,
    hasAudioPermission: Boolean
) {
    val context = LocalContext.current

    // --- State ---
    var screenState by remember { mutableStateOf(ScreenState.IDLE) }
    var activeMode by remember { mutableStateOf<AppMode?>(null) }
    var statusText by remember { mutableStateOf("Tap a mode or use voice") }
    var registeredObjectName by remember { mutableStateOf<String?>(null) }

    // --- TTS ---
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.US
                ttsReady = true
                Log.d("Baseer", "TTS initialized successfully")
            }
        }
        engine
    }

    // --- Beep tone for voice recognition activation ---
    val toneGenerator = remember {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
    }
    DisposableEffect(Unit) {
        onDispose { toneGenerator.release() }
    }

    // Forward declaration of free-form naming listener function
    fun startFreeFormListeningForObjectName() {
        if (!hasAudioPermission || !voskManager.isModelReady) {
            val fallbackName = "Watch"
            registeredObjectName = fallbackName
            screenState = ScreenState.OBJECT_SAVED
            val confirmation = "Object saved as $fallbackName in storage."
            statusText = "✅ Saved as: $fallbackName"
            speakTts(tts, confirmation, "object_name_saved")
            return
        }

        screenState = ScreenState.LISTENING_NAME
        statusText = "Listening for object name..."
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)

        voskManager.startFreeFormListening(timeoutMs = 5000L) { result ->
            val finalName = if (!result.isNullOrBlank()) {
                result.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            } else {
                "Watch"
            }
            registeredObjectName = finalName
            screenState = ScreenState.OBJECT_SAVED
            val confirmation = "Object saved as $finalName in storage."
            statusText = "✅ Saved as: $finalName"
            speakTts(tts, confirmation, "object_name_saved")
        }
    }

    // Handle Find Object target selection
    fun onFindTargetSelected(target: String = "Watch") {
        Log.d("Baseer", "Find target selected: $target")
        voskManager.stopListening()
        screenState = ScreenState.PLACE_RECALL
        val memoryText = "Your watch was found at lounge table last at 4:30. Searching now."
        statusText = memoryText
        speakTts(tts, memoryText, "place_memory")
    }

    fun startListeningForFindTarget() {
        if (!hasAudioPermission || !voskManager.isModelReady) return
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        statusText = "Listening... Say 'Watch'"
        voskManager.startFreeFormListening(timeoutMs = 6000L) { result ->
            if (screenState == ScreenState.FIND_OBJECT_MENU) {
                Log.d("Baseer", "Target voice result: $result")
                onFindTargetSelected(result ?: "Watch")
            }
        }
    }

    // TTS completion listener
    DisposableEffect(tts) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                Log.d("Baseer", "TTS finished utterance: $utteranceId")
                when {
                    utteranceId == "ask_find_target" -> {
                        startListeningForFindTarget()
                    }
                    utteranceId == "mode_activated" -> {
                        val mode = activeMode ?: return
                        if (mode.hasPlaceMemory) {
                            screenState = ScreenState.PLACE_RECALL
                            statusText = "Recalling place memory..."
                            speakTts(tts, mode.placeMemoryTts!!, "place_memory")
                        } else if (videoExists(context, mode)) {
                            screenState = ScreenState.PLAYING_VIDEO
                            statusText = if (mode == AppMode.ADD_OBJECT) "Registering object... Please hold steady" else "Simulating live camera feed..."
                        } else {
                            screenState = ScreenState.SHOWING_RESULT
                            statusText = mode.detectionTts
                            speakTts(tts, mode.detectionTts, "detection_result")
                        }
                    }
                    utteranceId == "place_memory" -> {
                        val mode = activeMode ?: return
                        if (videoExists(context, mode)) {
                            screenState = ScreenState.PLAYING_VIDEO
                            statusText = "Searching for watch..."
                        } else {
                            screenState = ScreenState.SHOWING_RESULT
                            statusText = mode.detectionTts
                            speakTts(tts, mode.detectionTts, "detection_result")
                        }
                    }
                    utteranceId == "detection_result" -> {
                        val mode = activeMode ?: return
                        // If Add Object mode, follow up by asking user to name the object
                        if (mode == AppMode.ADD_OBJECT) {
                            screenState = ScreenState.NAMING_OBJECT
                            val question = "What would you like to name this object?"
                            statusText = question
                            speakTts(tts, question, "ask_object_name")
                        }
                    }
                    utteranceId == "ask_object_name" -> {
                        // Spoken question finished -> now activate Vosk listening
                        startFreeFormListeningForObjectName()
                    }
                    utteranceId == "object_name_saved" -> {
                        val name = registeredObjectName ?: "Watch"
                        statusText = "✅ Saved as: $name"
                    }
                    // Ignore hazard_alert_* utterances — they are fire-and-forget
                    utteranceId?.startsWith("hazard_alert_") == true -> {
                        // Progressive alerts — no state transition needed
                    }
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e("Baseer", "TTS error on utterance: $utteranceId")
            }
        })
        onDispose { tts.shutdown() }
    }

    // --- Core function: activateMode ---
    fun activateMode(mode: AppMode) {
        Log.d("Baseer", "Activating mode: ${mode.displayName}")
        voskManager.stopListening()
        activeMode = mode
        registeredObjectName = null

        if (mode == AppMode.FIND_OBJECT) {
            screenState = ScreenState.FIND_OBJECT_MENU
            val prompt = "What do you want to find?"
            statusText = prompt
            speakTts(tts, prompt, "ask_find_target")
        } else {
            screenState = ScreenState.MODE_ACTIVATED
            val announcement = "${mode.displayName} mode activated"
            statusText = announcement
            speakTts(tts, announcement, "mode_activated")
        }
    }

    // --- Voice activation handler ---
    fun onVoicePress() {
        if (screenState == ScreenState.LISTENING || screenState == ScreenState.LISTENING_NAME) return
        if (!hasAudioPermission || !voskManager.isModelReady) {
            statusText = "Voice model not ready"
            return
        }

        // If in find object menu, tapping mic starts listening for target
        if (screenState == ScreenState.FIND_OBJECT_MENU) {
            startListeningForFindTarget()
            return
        }

        // If in naming flow, tapping mic prompts naming listening again
        if (activeMode == AppMode.ADD_OBJECT && (screenState == ScreenState.NAMING_OBJECT || screenState == ScreenState.OBJECT_SAVED)) {
            startFreeFormListeningForObjectName()
            return
        }

        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        screenState = ScreenState.LISTENING
        statusText = "Listening..."

        voskManager.startListening { result ->
            if (result != null) {
                val mode = AppMode.fromVoiceCommand(result)
                if (mode != null) {
                    activateMode(mode)
                } else {
                    Log.d("Baseer", "Unrecognized command: $result")
                    screenState = ScreenState.IDLE
                    statusText = "Unknown command. Try again."
                }
            } else {
                screenState = ScreenState.IDLE
                statusText = "No command heard. Try again."
            }
        }
    }

    // --- UI Layout ---
    Scaffold(
        bottomBar = {
            BottomModeBar(
                activeMode = activeMode,
                onModeSelected = { mode -> activateMode(mode) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main content
            Crossfade(
                targetState = screenState,
                animationSpec = tween(300),
                label = "screen_state_crossfade"
            ) { state ->
                when (state) {
                    ScreenState.PLAYING_VIDEO,
                    ScreenState.NAMING_OBJECT,
                    ScreenState.LISTENING_NAME,
                    ScreenState.OBJECT_SAVED -> {
                        val mode = activeMode
                        if (mode != null) {
                            DemoVideoPlayer(
                                mode = mode,
                                tts = tts,
                                onDetectionTriggered = { triggeredMode ->
                                    statusText = triggeredMode.detectionTts
                                    speakTts(tts, triggeredMode.detectionTts, "detection_result")
                                },
                                onStatusUpdate = { text ->
                                    statusText = text
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    else -> {
                        if (hasCameraPermission) {
                            CameraPreview(
                                lifecycleOwner = lifecycleOwner,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Camera permission required",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }

            // Floating Voice Mic Button (top-left)
            val isListening = screenState == ScreenState.LISTENING || screenState == ScreenState.LISTENING_NAME || screenState == ScreenState.FIND_OBJECT_MENU
            FloatingActionButton(
                onClick = { onVoicePress() },
                containerColor = if (isListening)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.primary,
                contentColor = if (isListening)
                    Color.White
                else
                    MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .size(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Voice Command",
                    modifier = Modifier.size(26.dp)
                )
            }

            // Interactive Find Object Menu Modal
            if (screenState == ScreenState.FIND_OBJECT_MENU) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.70f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.90f)
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "What do you want to find?",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Say 'Watch' or tap below",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                tonalElevation = 4.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onFindTargetSelected("Watch") }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Search,
                                            contentDescription = "Watch",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = "Watch",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Lounge table • Last seen at 4:30",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Status banner overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(16.dp)
            ) {
                Text(
                    text = statusText,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Speak text via TextToSpeech with an utterance ID */
private fun speakTts(tts: TextToSpeech?, text: String, utteranceId: String) {
    tts ?: return
    tts.setSpeechRate(1.0f)
    tts.setPitch(1.0f)
    val params = Bundle()
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
}

/** Check if video/image exists in res/raw or assets */
private fun videoExists(context: Context, mode: AppMode): Boolean {
    val rawResId = context.resources.getIdentifier(mode.rawResName, "raw", context.packageName)
    if (rawResId != 0) return true
    return try {
        context.assets.open(mode.demoVideoFile).close()
        true
    } catch (e: Exception) {
        try {
            if (mode.demoVideoFile.contains("register_object")) {
                context.assets.open("demo_videos/register watch.mp4").close()
                true
            } else if (mode.demoVideoFile.contains("find_object")) {
                context.assets.open("demo_videos/find object.mp4").close()
                true
            } else {
                false
            }
        } catch (e2: Exception) {
            false
        }
    }
}
