package com.example.voiceapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.app.Activity
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

class MainActivity : Activity(), RecognitionListener {

    private var speechService: SpeechService? = null
    private var voskModel: Model? = null
    private val PERMISSIONS_REQUEST_CODE = 1
    
    private lateinit var recognizedTextView: TextView
    private lateinit var vocabularyEditText: EditText
    private lateinit var applyVocabularyButton: Button

    // Requesting both AUDIO and CAMERA upfront
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        recognizedTextView = findViewById(R.id.recognizedTextView)
        vocabularyEditText = findViewById(R.id.vocabularyEditText)
        applyVocabularyButton = findViewById(R.id.applyVocabularyButton)
        
        applyVocabularyButton.setOnClickListener {
            applyCustomVocabulary()
        }
        
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE)
        } else {
            initModel()
        }
    }

    private fun applyCustomVocabulary() {
        val inputText = vocabularyEditText.text.toString()
        if (inputText.isBlank() || voskModel == null) return
        
        val words = inputText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val jsonArray = JSONArray()
        for (word in words) {
            jsonArray.put(word)
        }
        jsonArray.put("[unk]")
        
        val grammarJson = jsonArray.toString()
        Log.d("VoiceRouter", "Applying new vocabulary: $grammarJson")
        
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        
        val recognizer = Recognizer(voskModel, 16000.0f, grammarJson)
        speechService = SpeechService(recognizer, 16000.0f)
        speechService?.startListening(this)
        
        recognizedTextView.text = "Vocabulary applied! Say something..."
    }

    private fun hasPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            // Check if ALL requested permissions were granted
            if (hasPermissions()) {
                initModel()
            } else {
                Log.e("VoiceRouter", "Some permissions were denied by the user. The app requires them to function.")
            }
        }
    }

    private fun initModel() {
        StorageService.unpack(this, "model-en-us", "model",
            { model -> 
                voskModel = model
                setupRecognizer(model) 
            },
            { exception -> Log.e("VoiceRouter", "Failed to unpack model: ${exception.message}") }
        )
    }

    private fun setupRecognizer(model: Model) {
        // 1. Vosk Grammar Configuration
        val grammarJson = "[\"currency\", \"read screen\", \"detect hazard\", \"find object\", \"help\", \"stop\", \"describe\", \"[unk]\"]"
        
        // Pass the grammar constraint to the Recognizer
        val recognizer = Recognizer(model, 16000.0f, grammarJson)
        
        speechService = SpeechService(recognizer, 16000.0f)
        speechService?.startListening(this)
    }

    override fun onPartialResult(hypothesis: String?) {
        if (hypothesis != null) {
            try {
                val jsonObject = JSONObject(hypothesis)
                if (jsonObject.has("partial")) {
                    val partial = jsonObject.getString("partial")
                    // Filter out empty partials and [unk]
                    if (partial.isNotBlank() && partial != "[unk]") {
                        Log.d("VoiceRouter", "Partial Recognized Keyword: $partial")
                        runOnUiThread {
                            recognizedTextView.text = partial
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("VoiceRouter", "JSON Parse Error on Partial: ${e.message}")
            }
        }
    }

    override fun onResult(hypothesis: String?) {
        if (hypothesis != null) {
            try {
                val jsonObject = JSONObject(hypothesis)
                if (jsonObject.has("text")) {
                    val text = jsonObject.getString("text")
                    if (text.isNotBlank() && text != "[unk]") {
                        runOnUiThread {
                            recognizedTextView.text = text
                        }
                    }
                    routeIntent(text)
                }
            } catch (e: Exception) {
                Log.e("VoiceRouter", "JSON Parse Error on Result: ${e.message}")
            }
        }
    }

    // 2. Intent Router
    private fun routeIntent(text: String) {
        // Filter out empty string results and [unk] gracefully without false positives
        if (text.isBlank() || text == "[unk]") return
        
        Log.d("VoiceRouter", "========================================")
        Log.d("VoiceRouter", "Recognized Keyword: '$text'")

        when (text) {
            "currency" -> Log.d("VoiceRouter", "Routed Mode Action: Triggering Currency Mode")
            "read screen" -> Log.d("VoiceRouter", "Routed Mode Action: Triggering Read Screen Mode")
            "detect hazard" -> Log.d("VoiceRouter", "Routed Mode Action: Triggering Hazard Detection Mode")
            "find object" -> Log.d("VoiceRouter", "Routed Mode Action: Triggering Find Object Mode")
            "help" -> Log.d("VoiceRouter", "Routed Mode Action: Triggering Help Mode")
            "stop" -> Log.d("VoiceRouter", "Routed Mode Action: Triggering Stop Mode")
            "describe" -> Log.d("VoiceRouter", "Routed Mode Action: Triggering Describe Mode")
            else -> Log.d("VoiceRouter", "Routed Mode Action: Unknown command ($text)")
        }
        Log.d("VoiceRouter", "========================================")
    }

    override fun onFinalResult(hypothesis: String?) {
        // Treat final result the same as a normal result
        onResult(hypothesis)
    }

    override fun onError(exception: Exception?) {
        Log.e("VoiceRouter", "Vosk Error: ${exception?.message}")
    }
    
    override fun onTimeout() {}
}