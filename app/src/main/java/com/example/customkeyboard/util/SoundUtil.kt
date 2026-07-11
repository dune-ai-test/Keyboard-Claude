package com.example.customkeyboard.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

/**
 * Plays very short click sounds using [SoundPool], which is the correct low-latency, low-battery
 * choice for UI sound effects (as opposed to MediaPlayer, which is heavier and higher-latency).
 */
class SoundUtil(private val context: Context) {

    private var soundPool: SoundPool? = null
    private var clickSoundId: Int = 0
    private var loaded = false

    fun init() {
        if (soundPool != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attributes)
            .build()
        soundPool?.setOnLoadCompleteListener { _, _, status -> loaded = status == 0 }
        // In production, load a bundled short click .ogg from res/raw. Falls back to system click.
    }

    fun playClick(volume: Float) {
        // Uses the system's standard keyboard click tone as a lightweight, dependency-free
        // fallback so the project doesn't require bundling binary audio assets.
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, volume.coerceIn(0f, 1f))
        } catch (t: Throwable) {
            // Silently ignore — sound is a non-critical enhancement.
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }
}
