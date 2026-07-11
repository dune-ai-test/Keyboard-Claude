package com.example.customkeyboard.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Thin wrapper around the system vibrator that is battery-conscious: it reuses a single
 * [Vibrator] instance and issues very short, low-amplitude pulses (tens of milliseconds).
 */
class HapticUtil(context: Context) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (t: Throwable) {
        null
    }

    fun keyPress(durationMs: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator() || durationMs <= 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(durationMs.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(durationMs.toLong())
        }
    }
}
