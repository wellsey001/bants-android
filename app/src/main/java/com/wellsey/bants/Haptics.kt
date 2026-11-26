package com.wellsey.bants

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.wellsey.bants.data.Bants

/**
 * Little buzzes for taps. Uses the vibrator directly rather than the system "touch feedback"
 * setting, which many phones (ColorOS included) turn off by default.
 * Stays still when the user turns it off in Sounds & vibration, or when the phone is on silent.
 */
object Haptics {
    private var vibrator: Vibrator? = null
    private var audio: AudioManager? = null

    fun init(context: Context) {
        val app = context.applicationContext
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private fun allowed(): Boolean =
        Bants.session.hapticsOn &&
            vibrator?.hasVibrator() == true &&
            audio?.ringerMode != AudioManager.RINGER_MODE_SILENT

    /** A short tick for a button press. */
    fun tap() {
        if (!allowed()) return
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        else v.vibrate(VibrationEffect.createOneShot(8, 80))
    }

    /** A firmer thunk for a long press (message menu, chat options). */
    fun longPress() {
        if (!allowed()) return
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        else v.vibrate(VibrationEffect.createOneShot(20, 180))
    }
}
