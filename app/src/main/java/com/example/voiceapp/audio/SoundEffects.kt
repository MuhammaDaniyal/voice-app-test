package com.example.voiceapp.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

object SoundEffects {

    /**
     * Plays a sparkling, magical chime / sprinkle sound effect.
     * Uses native AudioTrack with cascading harmonic sine waves (E6 to E8).
     */
    suspend fun playSprinkleChime() = withContext(Dispatchers.Default) {
        try {
            val sampleRate = 44100
            val duration = 1.0f
            val numSamples = (sampleRate * duration).toInt()

            val chimes = listOf(
                Triple(0.00f, 1318.51f, 0.45f), // E6
                Triple(0.07f, 1661.22f, 0.45f), // G#6
                Triple(0.14f, 1975.53f, 0.45f), // B6
                Triple(0.21f, 2637.02f, 0.50f), // E7
                Triple(0.28f, 3322.44f, 0.50f), // G#7
                Triple(0.35f, 3951.07f, 0.40f), // B7
                Triple(0.42f, 5274.04f, 0.35f)  // E8
            )

            val rawData = FloatArray(numSamples)

            for ((startT, freq, amp) in chimes) {
                val startIdx = (startT * sampleRate).toInt()
                val decay = 6.0f
                for (i in startIdx until numSamples) {
                    val t = (i - startIdx).toFloat() / sampleRate
                    val envelope = exp(-decay * t)
                    val wave = (sin(2.0 * Math.PI * freq * t) + 0.25 * sin(4.0 * Math.PI * freq * t)).toFloat() * envelope * amp
                    rawData[i] += wave
                }
            }

            var maxAmp = 0.0001f
            for (v in rawData) {
                val a = abs(v)
                if (a > maxAmp) maxAmp = a
            }

            val pcm = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                pcm[i] = ((rawData[i] / maxAmp) * 28000).toInt().toShort()
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(numSamples * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcm, 0, numSamples)
            track.play()

            delay(1000)
            track.stop()
            track.release()
        } catch (e: Exception) {
            Log.e("Baseer", "Error playing sprinkle chime: ${e.message}")
        }
    }
}
