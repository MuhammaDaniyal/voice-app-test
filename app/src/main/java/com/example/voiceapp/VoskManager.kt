package com.example.voiceapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/**
 * Manages Vosk model lifecycle and on-demand speech recognition.
 * Voice is NOT always-on — it activates for a short window on each valid press.
 */
class VoskManager(private val context: Context) {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    var isModelReady: Boolean = false
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    companion object {
        private const val TAG = "Baseer"
        private const val LISTEN_TIMEOUT_MS = 4000L
    }

    /** Unpack the Vosk model from assets. Call once on app start. */
    fun initModel(onReady: () -> Unit, onError: (String) -> Unit) {
        Log.d(TAG, "Unpacking Vosk model...")
        StorageService.unpack(context, "model-en-us", "model",
            { m ->
                model = m
                isModelReady = true
                Log.d(TAG, "Vosk model ready")
                onReady()
            },
            { exception ->
                Log.e(TAG, "Failed to unpack model: ${exception.message}")
                onError(exception.message ?: "Unknown error")
            }
        )
    }

    /**
     * Start listening for a single command.
     * Automatically stops after receiving one valid result or after LISTEN_TIMEOUT_MS.
     * @param onResult called with the recognized command string, or null if timeout/error.
     */
    fun startListening(onResult: (String?) -> Unit) {
        if (!isModelReady || model == null) {
            Log.e(TAG, "Model not ready, cannot listen")
            onResult(null)
            return
        }

        stopListening() // cleanup any previous session

        val grammar = AppMode.grammarJson()
        Log.d(TAG, "Starting recognition with grammar: $grammar")

        val recognizer = Recognizer(model, 16000.0f, grammar)
        var resultDelivered = false

        fun deliverResult(text: String?) {
            if (resultDelivered) return
            resultDelivered = true
            cancelTimeout()
            stopListening()
            mainHandler.post { onResult(text) }
        }

        val listener = object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                // Ignore partials for on-demand mode — we wait for final
            }

            override fun onResult(hypothesis: String?) {
                parseAndDeliver(hypothesis) { deliverResult(it) }
            }

            override fun onFinalResult(hypothesis: String?) {
                parseAndDeliver(hypothesis) { deliverResult(it) }
                // If nothing valid was found, deliver null
                if (!resultDelivered) deliverResult(null)
            }

            override fun onError(exception: Exception?) {
                Log.e(TAG, "Vosk error: ${exception?.message}")
                deliverResult(null)
            }

            override fun onTimeout() {
                Log.d(TAG, "Vosk timeout")
                deliverResult(null)
            }
        }

        speechService = SpeechService(recognizer, 16000.0f)
        speechService?.startListening(listener)

        // Auto-stop after timeout
        timeoutRunnable = Runnable { deliverResult(null) }
        mainHandler.postDelayed(timeoutRunnable!!, LISTEN_TIMEOUT_MS)
    }

    /**
     * Start listening for free-form speech without grammar restriction.
     * Used for capturing user input like naming an object during registration.
     * Automatically stops after receiving one result or after [timeoutMs].
     * @param timeoutMs Timeout in milliseconds (default 5000ms).
     * @param onResult called with recognized text string, or null if timeout/error.
     */
    fun startFreeFormListening(timeoutMs: Long = 5000L, onResult: (String?) -> Unit) {
        if (!isModelReady || model == null) {
            Log.e(TAG, "Model not ready, cannot listen free-form")
            onResult(null)
            return
        }

        stopListening() // cleanup any previous session

        Log.d(TAG, "Starting free-form recognition (no grammar constraint)...")

        val recognizer = Recognizer(model, 16000.0f)
        var resultDelivered = false

        fun deliverResult(text: String?) {
            if (resultDelivered) return
            resultDelivered = true
            cancelTimeout()
            stopListening()
            mainHandler.post { onResult(text) }
        }

        val listener = object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {}

            override fun onResult(hypothesis: String?) {
                parseAndDeliver(hypothesis) { deliverResult(it) }
            }

            override fun onFinalResult(hypothesis: String?) {
                parseAndDeliver(hypothesis) { deliverResult(it) }
                if (!resultDelivered) deliverResult(null)
            }

            override fun onError(exception: Exception?) {
                Log.e(TAG, "Vosk free-form error: ${exception?.message}")
                deliverResult(null)
            }

            override fun onTimeout() {
                Log.d(TAG, "Vosk free-form timeout")
                deliverResult(null)
            }
        }

        speechService = SpeechService(recognizer, 16000.0f)
        speechService?.startListening(listener)

        timeoutRunnable = Runnable { deliverResult(null) }
        mainHandler.postDelayed(timeoutRunnable!!, timeoutMs)
    }

    /** Stop any active listening session */
    fun stopListening() {
        cancelTimeout()
        val service = speechService
        speechService = null
        try {
            service?.stop()
            service?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping speech service: ${e.message}")
        }
    }

    /** Release all resources */
    fun destroy() {
        stopListening()
        model?.close()
        model = null
        isModelReady = false
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun parseAndDeliver(hypothesis: String?, deliver: (String) -> Unit) {
        if (hypothesis == null) return
        try {
            val json = JSONObject(hypothesis)
            val text = json.optString("text", "")
            if (text.isNotBlank() && text != "[unk]") {
                Log.d(TAG, "Recognized command: '$text'")
                deliver(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse error: ${e.message}")
        }
    }
}
