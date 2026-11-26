package com.wellsey.bants.calls

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.wellsey.bants.data.Bants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The phone's ringtone for incoming calls, and a "ring ring" while you're calling someone. */
object Ringer {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var ringback: Job? = null

    fun startRinging(context: Context) {
        stop()
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= 28) isLooping = true
                play()
            }
        }
        if (audio.ringerMode != AudioManager.RINGER_MODE_SILENT) {
            val v = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 900), 0))
            vibrator = v
        }
    }

    fun startRingback() {
        ringback?.cancel()
        ringback = Bants.scope.launch {
            val tone = runCatching { ToneGenerator(AudioManager.STREAM_VOICE_CALL, 60) }.getOrNull() ?: return@launch
            try {
                while (true) {
                    tone.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1500)
                    delay(4_000)
                }
            } finally {
                tone.release()
            }
        }
    }

    fun stop() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
        ringback?.cancel()
        ringback = null
    }
}
