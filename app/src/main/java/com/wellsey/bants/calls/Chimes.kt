package com.wellsey.bants.calls

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.wellsey.bants.R
import com.wellsey.bants.data.Bants

/**
 * Join and leave chimes for calls and voice channels (like Discord's): two soft notes going up
 * when someone joins, down when they leave. Made with sounds/make_voice_sounds.py.
 * They play through the call's audio, so you hear them in your earpiece or headphones.
 */
object Chimes {
    private var pool: SoundPool? = null
    private var join = 0
    private var leave = 0

    fun init(context: Context) {
        if (pool != null) return
        val p = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        join = p.load(context, R.raw.voice_join, 1)
        leave = p.load(context, R.raw.voice_leave, 1)
        pool = p
    }

    fun join() = play(join)
    fun leave() = play(leave)

    private fun play(id: Int) {
        if (!Bants.session.voiceChimes || id == 0) return
        pool?.play(id, 0.55f, 0.55f, 1, 0, 1f)
    }
}
