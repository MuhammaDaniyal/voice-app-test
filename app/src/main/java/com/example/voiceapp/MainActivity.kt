package com.example.voiceapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.app.Activity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

class MainActivity : Activity(), RecognitionListener {

    private var speechService: SpeechService? = null
    private val RECORD_AUDIO_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_REQUEST_CODE)
        } else {
            initModel()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initModel()
            } else {
                Log.e("Vosk", "Record Audio permission denied")
            }
        }
    }

    private fun initModel() {
        StorageService.unpack(this, "model-en-us", "model",
            { model -> setupRecognizer(model) },
            { exception -> Log.e("Vosk", "Failed to unpack model: ${exception.message}") }
        )
    }

    private fun setupRecognizer(model: Model) {
        val recognizer = Recognizer(model, 16000.0f)
        speechService = SpeechService(recognizer, 16000.0f)
        speechService?.startListening(this)
    }

    override fun onPartialResult(hypothesis: String?) {
        Log.d("VoskPartial", hypothesis ?: "")
    }

    override fun onResult(hypothesis: String?) {
        Log.d("VoskResult", hypothesis ?: "")
    }

    override fun onFinalResult(hypothesis: String?) {}
    override fun onError(exception: Exception?) {
        Log.e("VoskError", exception?.message ?: "")
    }
    override fun onTimeout() {}
}