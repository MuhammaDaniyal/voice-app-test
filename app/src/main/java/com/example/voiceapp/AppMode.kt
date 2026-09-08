package com.example.voiceapp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Bounding box bounds represented as normalized screen proportions (0.0f to 1.0f).
 */
data class BoundingBoxBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/**
 * For modes with progressive distance-based TTS alerts (e.g. Hazard Detection),
 * each step defines a timestamp, a TTS phrase, a label, and a growing bounding box.
 */
data class DistanceAlert(
    val timestampMs: Long,
    val ttsPhrase: String,
    val label: String,
    val boundingBox: BoundingBoxBounds
)

/**
 * The user-selectable modes in the Dar-e-Rah (BASEER / Aura) app.
 * All detection metadata is centralized here for easy scalability.
 *
 * Modes that use progressive distance alerts (like HAZARD) populate [distanceAlerts].
 * Modes with a "place memory" recall announce [placeMemoryTts] before showing the video.
 */
enum class AppMode(
    val displayName: String,
    val voiceCommand: String,
    val icon: ImageVector,
    val rawResName: String,
    val demoVideoFile: String,
    val detectionLabel: String,
    val detectionTts: String,
    val detectionDelayMs: Long,
    val boundingBox: BoundingBoxBounds,
    /** Progressive distance alerts — only used by modes like HAZARD */
    val distanceAlerts: List<DistanceAlert> = emptyList(),
    /** Place Memory recall TTS — spoken BEFORE switching to video (e.g. Find Object) */
    val placeMemoryTts: String? = null
) {
    COLOR(
        displayName = "Color",
        voiceCommand = "color",
        icon = Icons.Filled.Palette,
        rawResName = "color",
        demoVideoFile = "demo_videos/color.mp4",
        detectionLabel = "Blue Color",
        detectionTts = "Blue color detected",
        detectionDelayMs = 2000L,
        boundingBox = BoundingBoxBounds(left = 0.25f, top = 0.28f, right = 0.75f, bottom = 0.68f)
    ),
    CURRENCY(
        displayName = "Currency",
        voiceCommand = "currency",
        icon = Icons.Filled.AttachMoney,
        rawResName = "currency",
        demoVideoFile = "demo_videos/currency.jpeg",
        detectionLabel = "500 Rupees",
        detectionTts = "500 Rupees detected",
        detectionDelayMs = 2000L,
        boundingBox = BoundingBoxBounds(left = 0.18f, top = 0.32f, right = 0.82f, bottom = 0.64f)
    ),
    OCR(
        displayName = "OCR",
        voiceCommand = "ocr",
        icon = Icons.Filled.Description,
        rawResName = "ocr",
        demoVideoFile = "demo_videos/ocr.jpeg",
        detectionLabel = "Detected Quote",
        detectionTts = "If you don't like the road you are walking, start paving another one",
        detectionDelayMs = 2000L,
        boundingBox = BoundingBoxBounds(left = 0.12f, top = 0.28f, right = 0.88f, bottom = 0.68f)
    ),
    HAZARD(
        displayName = "Hazard",
        voiceCommand = "detect hazard",
        icon = Icons.Filled.Warning,
        rawResName = "hazard",
        demoVideoFile = "demo_videos/hazard.mp4",
        detectionLabel = "Table",
        detectionTts = "Caution! Table directly in front of you",
        detectionDelayMs = 2000L,
        boundingBox = BoundingBoxBounds(left = 0.30f, top = 0.35f, right = 0.70f, bottom = 0.65f),
        distanceAlerts = listOf(
            DistanceAlert(
                timestampMs = 1500L,
                ttsPhrase = "Table detected, 2 meters ahead",
                label = "Table — 2m",
                boundingBox = BoundingBoxBounds(left = 0.38f, top = 0.40f, right = 0.62f, bottom = 0.58f)
            ),
            DistanceAlert(
                timestampMs = 3500L,
                ttsPhrase = "Table, 1 meter ahead",
                label = "Table — 1m",
                boundingBox = BoundingBoxBounds(left = 0.32f, top = 0.35f, right = 0.68f, bottom = 0.63f)
            ),
            DistanceAlert(
                timestampMs = 5500L,
                ttsPhrase = "Table, half meter ahead",
                label = "Table — 0.5m",
                boundingBox = BoundingBoxBounds(left = 0.25f, top = 0.28f, right = 0.75f, bottom = 0.70f)
            ),
            DistanceAlert(
                timestampMs = 7000L,
                ttsPhrase = "Caution! Table directly in front of you!",
                label = "⚠ TABLE — STOP",
                boundingBox = BoundingBoxBounds(left = 0.15f, top = 0.20f, right = 0.85f, bottom = 0.78f)
            )
        )
    ),
    ADD_OBJECT(
        displayName = "Add Object",
        voiceCommand = "add object",
        icon = Icons.Filled.CameraAlt,
        rawResName = "register_watch",
        demoVideoFile = "demo_videos/register watch.mp4",
        detectionLabel = "Watch Registered",
        detectionTts = "Watch registered successfully",
        detectionDelayMs = 2000L,
        boundingBox = BoundingBoxBounds(left = 0.28f, top = 0.25f, right = 0.72f, bottom = 0.72f)
    ),
    FIND_OBJECT(
        displayName = "Find Object",
        voiceCommand = "find object",
        icon = Icons.Filled.Search,
        rawResName = "find_object",
        demoVideoFile = "demo_videos/find object.mp4",
        detectionLabel = "Watch Found",
        detectionTts = "Watch found, it is on the table, 2 feet ahead",
        detectionDelayMs = 2000L,
        boundingBox = BoundingBoxBounds(left = 0.20f, top = 0.35f, right = 0.80f, bottom = 0.70f),
        placeMemoryTts = "Recalling place memory. Your watch was last seen on the lounge table. Searching now."
    );

    /** Whether this mode uses progressive distance-based alerts instead of a single detection */
    val isProgressiveMode: Boolean get() = distanceAlerts.isNotEmpty()

    /** Whether this mode has a place memory recall announcement */
    val hasPlaceMemory: Boolean get() = placeMemoryTts != null

    companion object {
        /** Build the Vosk grammar JSON array from all mode voice commands */
        fun grammarJson(): String {
            val commands = entries.map { "\"${it.voiceCommand}\"" }.joinToString(", ")
            return "[$commands, \"[unk]\"]"
        }

        /** Find a mode by its voice command string */
        fun fromVoiceCommand(command: String): AppMode? {
            return entries.find { it.voiceCommand == command }
        }
    }
}
