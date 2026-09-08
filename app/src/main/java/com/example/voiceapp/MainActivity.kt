package com.example.voiceapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.voiceapp.ui.BaseerScreen
import com.example.voiceapp.ui.theme.BaseerTheme

/**
 * BASEER — Assistive app POC.
 * Entry point: requests permissions, initializes Vosk, renders the Compose UI.
 */
class MainActivity : ComponentActivity() {

    private lateinit var voskManager: VoskManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voskManager = VoskManager(applicationContext)

        setContent {
            BaseerTheme {
                // Permission state
                var hasCameraPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                                == PackageManager.PERMISSION_GRANTED
                    )
                }
                var hasAudioPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_GRANTED
                    )
                }
                var voskReady by remember { mutableStateOf(false) }

                // Permission launcher
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
                    hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
                    Log.d("Baseer", "Permissions: camera=$hasCameraPermission, audio=$hasAudioPermission")
                }

                // Request permissions on first launch
                LaunchedEffect(Unit) {
                    val permsToRequest = mutableListOf<String>()
                    if (!hasCameraPermission) permsToRequest.add(Manifest.permission.CAMERA)
                    if (!hasAudioPermission) permsToRequest.add(Manifest.permission.RECORD_AUDIO)
                    if (permsToRequest.isNotEmpty()) {
                        permissionLauncher.launch(permsToRequest.toTypedArray())
                    }
                }

                // Initialize Vosk model
                LaunchedEffect(hasAudioPermission) {
                    if (hasAudioPermission && !voskManager.isModelReady) {
                        voskManager.initModel(
                            onReady = { voskReady = true },
                            onError = { Log.e("Baseer", "Vosk init failed: $it") }
                        )
                    }
                }

                // Main screen
                BaseerScreen(
                    voskManager = voskManager,
                    lifecycleOwner = this@MainActivity,
                    hasCameraPermission = hasCameraPermission,
                    hasAudioPermission = hasAudioPermission
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voskManager.destroy()
    }
}