package com.example.voiceapp.ui

import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.voiceapp.AppMode
import com.example.voiceapp.BoundingBoxBounds
import kotlinx.coroutines.delay

/**
 * Plays mode demo media (video loop OR static image) and manages:
 * - Single-shot detection trigger (Color, Currency, OCR, Add Object, Find Object)
 * - Progressive distance-based alerts with growing bounding box (Hazard Detection)
 *
 * @param tts TextToSpeech engine reference for progressive modes that need to queue
 *            multiple announcements internally.
 */
@Composable
fun DemoVideoPlayer(
    mode: AppMode,
    tts: TextToSpeech?,
    onDetectionTriggered: (AppMode) -> Unit,
    onStatusUpdate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val isImage = remember(mode) {
        val path = mode.demoVideoFile.lowercase()
        path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".webp")
    }

    // Single-shot detection state
    var showBoundingBox by remember { mutableStateOf(false) }
    var detectionTriggered by remember { mutableStateOf(false) }

    // Progressive (hazard) detection state
    var currentAlertIndex by remember { mutableIntStateOf(-1) }
    var currentBounds by remember { mutableStateOf(mode.boundingBox) }
    var currentLabel by remember { mutableStateOf(mode.detectionLabel) }

    // Hazard accent color (red/orange warning)
    val hazardColor = Color(0xFFFF4444)

    Box(modifier = modifier.fillMaxSize()) {
        if (isImage) {
            // Static image path
            val bitmap = remember(mode) {
                try {
                    val stream = context.assets.open(mode.demoVideoFile)
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                } catch (e: Exception) {
                    Log.e("Baseer", "Failed to load image asset: ${mode.demoVideoFile}", e)
                    null
                }
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Demo Feed Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            LaunchedEffect(mode) {
                delay(mode.detectionDelayMs)
                if (!detectionTriggered) {
                    detectionTriggered = true
                    showBoundingBox = true
                    onDetectionTriggered(mode)
                }
            }
        } else {
            // Video path
            val videoUri = remember(mode) {
                val rawResId = context.resources.getIdentifier(mode.rawResName, "raw", context.packageName)
                if (rawResId != 0) {
                    Uri.parse("android.resource://${context.packageName}/$rawResId")
                } else {
                    val assetPath = try {
                        context.assets.open(mode.demoVideoFile).close()
                        mode.demoVideoFile
                    } catch (e: Exception) {
                        if (mode.demoVideoFile.contains("register_object")) {
                            "demo_videos/register watch.mp4"
                        } else if (mode.demoVideoFile.contains("find_object")) {
                            "demo_videos/find object.mp4"
                        } else {
                            mode.demoVideoFile
                        }
                    }
                    Uri.parse("asset:///$assetPath")
                }
            }

            val exoPlayer = remember(mode) {
                ExoPlayer.Builder(context).build().apply {
                    val mediaItem = MediaItem.fromUri(videoUri)
                    setMediaItem(mediaItem)
                    repeatMode = Player.REPEAT_MODE_OFF
                    prepare()
                    playWhenReady = true
                }
            }

            val toneGenerator = remember {
                ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            }
            DisposableEffect(Unit) {
                onDispose { toneGenerator.release() }
            }

            // Monitoring loop: handles single-shot, scanning beeps, proximity beeps, and progressive detection
            LaunchedEffect(exoPlayer, mode) {
                if (mode.isProgressiveMode) {
                    // --- Progressive distance alerts (Hazard mode) ---
                    val alerts = mode.distanceAlerts
                    var firedCount = 0

                    while (true) {
                        delay(100)
                        val pos = exoPlayer.currentPosition

                        if (firedCount < alerts.size && pos >= alerts[firedCount].timestampMs) {
                            val alert = alerts[firedCount]
                            firedCount++
                            currentAlertIndex = firedCount - 1
                            showBoundingBox = true
                            currentBounds = alert.boundingBox
                            currentLabel = alert.label

                            // Update status banner
                            onStatusUpdate(alert.ttsPhrase)

                            // Speak the distance alert via TTS with QUEUE_FLUSH so voice and text stay 100% in sync
                            tts?.let {
                                if (alert.ttsPhrase.contains("Caution", ignoreCase = true)) {
                                    // Hurry / urgent voice for caution alert
                                    it.setSpeechRate(1.45f)
                                    it.setPitch(1.15f)
                                } else {
                                    it.setSpeechRate(1.25f)
                                    it.setPitch(1.0f)
                                }
                                val params = Bundle()
                                it.speak(alert.ttsPhrase, TextToSpeech.QUEUE_FLUSH, params, "hazard_alert_$firedCount")
                            }

                            Log.d("Baseer", "Hazard alert $firedCount: ${alert.ttsPhrase} at ${pos}ms")
                        }
                    }
                } else if (mode.enableScanningBeeps) {
                    // --- Registration mode with periodic scanning beeps ---
                    // Single middle bounding box that shrinks as the watch moves further away
                    showBoundingBox = true
                    currentLabel = ""
                    var lastBeepTime = 0L
                    val beepIntervalMs = 1500L

                    while (true) {
                        delay(50)
                        val pos = exoPlayer.currentPosition

                        // Progressively shrink box as watch moves further away
                        val progress = (pos.toFloat() / mode.detectionDelayMs).coerceIn(0f, 1f)
                        val halfW = 0.26f - (0.13f * progress)
                        val halfH = 0.22f - (0.11f * progress)
                        val centerX = 0.50f
                        val centerY = 0.48f

                        currentBounds = BoundingBoxBounds(
                            left = centerX - halfW,
                            top = centerY - halfH,
                            right = centerX + halfW,
                            bottom = centerY + halfH
                        )

                        if (!detectionTriggered) {
                            if (pos >= mode.detectionDelayMs) {
                                detectionTriggered = true
                                exoPlayer.pause()
                                Log.d("Baseer", "Registration complete at ${pos}ms for: ${mode.displayName}")
                                onDetectionTriggered(mode)
                            } else if (pos - lastBeepTime >= beepIntervalMs) {
                                lastBeepTime = pos
                                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
                            }
                        }
                    }
                } else if (mode.enableProximityBeeps) {
                    // --- Find Object mode: bounding box expands, proximity beeps accelerate, ending with sprinkle chime ---
                    showBoundingBox = true
                    currentLabel = ""
                    var lastBeepTime = 0L

                    while (true) {
                        delay(40)
                        val pos = exoPlayer.currentPosition

                        // Progressively expand box as camera nears the watch
                        val progress = (pos.toFloat() / mode.detectionDelayMs).coerceIn(0f, 1f)
                        val halfW = 0.12f + (0.16f * progress)
                        val halfH = 0.10f + (0.14f * progress)
                        val centerX = 0.50f
                        val centerY = 0.48f

                        currentBounds = BoundingBoxBounds(
                            left = centerX - halfW,
                            top = centerY - halfH,
                            right = centerX + halfW,
                            bottom = centerY + halfH
                        )

                        if (!detectionTriggered) {
                            if (pos >= mode.detectionDelayMs) {
                                detectionTriggered = true
                                Log.d("Baseer", "6th second reached — sparkling and announcing detection, video continues")
                                com.example.voiceapp.audio.SoundEffects.playSprinkleChime()
                                onDetectionTriggered(mode)
                            } else {
                                // Geiger-counter style: interval shrinks from 1000ms down to 140ms
                                val intervalMs = (1000L - (850L * progress).toLong()).coerceAtLeast(140L)
                                if (pos - lastBeepTime >= intervalMs) {
                                    lastBeepTime = pos
                                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 75)
                                }
                            }
                        }
                    }
                } else {
                    // --- Standard single-shot detection ---
                    while (true) {
                        delay(100)
                        val pos = exoPlayer.currentPosition

                        if (!detectionTriggered && pos >= mode.detectionDelayMs) {
                            detectionTriggered = true
                            showBoundingBox = true
                            Log.d("Baseer", "Detection at ${pos}ms for: ${mode.displayName}")
                            onDetectionTriggered(mode)
                        }
                    }
                }
            }

            DisposableEffect(mode) {
                onDispose { exoPlayer.release() }
            }

            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Bounding Box Overlay
        BoundingBoxOverlayContainer(
            isVisible = showBoundingBox,
            bounds = currentBounds,
            label = currentLabel,
            modifier = Modifier.fillMaxSize(),
            accentColor = if (mode.isProgressiveMode) hazardColor else Color(0xFF00FF66)
        )
    }
}
