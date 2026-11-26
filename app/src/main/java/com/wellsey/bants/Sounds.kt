package com.wellsey.bants

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

/** Bants' own little noises for when the app is open. Made with sounds/make_sounds.py. */
object Sounds {
    enum class Kind(val res: Int, val volume: Float) {
        Message(R.raw.bants_message, 0.8f),
        Incoming(R.raw.bants_incoming, 0.6f),
        Sent(R.raw.bants_sent, 0.45f),
        Mention(R.raw.bants_mention, 0.8f),
        Request(R.raw.bants_request, 0.8f),
    }

    private lateinit var pool: SoundPool
    private lateinit var audio: AudioManager
    private val ids = mutableMapOf<Kind, Int>()
    private val ready = mutableSetOf<Int>()
    private var lastPlayed = 0L

    fun init(context: Context) {
        audio = context.getSystemService(AudioManager::class.java)
        pool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready += id }
        Kind.values().forEach { ids[it] = pool.load(context, it.res, 1) }
    }

    /** Plays a sound unless sounds are off or the phone is on silent or vibrate. */
    fun play(kind: Kind, enabled: Boolean) {
        if (!enabled || !::pool.isInitialized) return
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val now = System.currentTimeMillis()
        if (now - lastPlayed < 120) return // don't machine-gun when lots arrive at once
        lastPlayed = now
        val id = ids[kind] ?: return
        if (id in ready) pool.play(id, kind.volume, kind.volume, 1, 0, 1f)
    }
}
