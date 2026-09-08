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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    PLACE_RECALL,     // Place Memory recall TTS playing before video
    PLAYING_VIDEO,    // Demo video continuous feed playing
    SHOWING_RESULT    // Static result view if video missing
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

    // TTS completion listener
    DisposableEffect(tts) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                Log.d("Baseer", "TTS finished utterance: $utteranceId")
                when {
                    utteranceId == "mode_activated" -> {
                        val mode = activeMode ?: return
                        // If this mode has Place Memory, speak it first before video
                        if (mode.hasPlaceMemory) {
                            screenState = ScreenState.PLACE_RECALL
                            statusText = "Recalling place memory..."
                            speakTts(tts, mode.placeMemoryTts!!, "place_memory")
                        } else if (videoExists(context, mode)) {
                            screenState = ScreenState.PLAYING_VIDEO
                            statusText = "Simulating live camera feed..."
                        } else {
                            screenState = ScreenState.SHOWING_RESULT
                            statusText = mode.detectionTts
                            speakTts(tts, mode.detectionTts, "detection_result")
                        }
                    }
                    utteranceId == "place_memory" -> {
                        // Place memory recall finished → now start the video
                        val mode = activeMode ?: return
                        if (videoExists(context, mode)) {
                            screenState = ScreenState.PLAYING_VIDEO
                            statusText = "Searching..."
                        } else {
                            screenState = ScreenState.SHOWING_RESULT
                            statusText = mode.detectionTts
                            speakTts(tts, mode.detectionTts, "detection_result")
                        }
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

    // --- Beep tone for voice recognition activation ---
    val toneGenerator = remember {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
    }
    DisposableEffect(Unit) {
        onDispose { toneGenerator.release() }
    }

    // --- Core function: activateMode ---
    fun activateMode(mode: AppMode) {
        Log.d("Baseer", "Activating mode: ${mode.displayName}")
        activeMode = mode
        screenState = ScreenState.MODE_ACTIVATED
        val announcement = "${mode.displayName} mode activated"
        statusText = announcement
        speakTts(tts, announcement, "mode_activated")
    }

    // --- Voice activation handler ---
    fun onVoicePress() {
        if (screenState == ScreenState.LISTENING) return
        if (!hasAudioPermission || !voskManager.isModelReady) {
            statusText = "Voice model not ready"
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
                    ScreenState.PLAYING_VIDEO -> {
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
            FloatingActionButton(
                onClick = { onVoicePress() },
                containerColor = if (screenState == ScreenState.LISTENING)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.primary,
                contentColor = if (screenState == ScreenState.LISTENING)
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
        false
    }
}
