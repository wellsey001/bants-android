package com.wellsey.bants

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.wellsey.bants.data.Bants

/**
 * Bants' own noises. Each type has four styles to pick from on the Sounds screen.
 * All made with sounds/make_sounds.py.
 */
object Sounds {
    enum class Kind(val key: String, val label: String, val about: String, val volume: Float, val default: Style) {
        Message("message", "New message", "When a message arrives", 0.8f, Style.Arcade),
        Incoming("incoming", "In the chat", "A message arrives in the chat you're looking at", 0.6f, Style.Crisp),
        Sent("sent", "Sent", "Your message reaches the server", 0.45f, Style.Crisp),
        Mention("mention", "Mentions", "Someone @mentions you", 0.8f, Style.ToyBox),
        Request("request", "Contact requests", "Someone adds you", 0.8f, Style.Crisp),
    }

    enum class Style(val key: String, val label: String) {
        Arcade("arcade", "Arcade"),
        Bubbly("bubbly", "Bubbly"),
        ToyBox("toybox", "Toy box"),
        Crisp("crisp", "Crisp"),
    }

    fun resName(kind: Kind, style: Style) = "sound_${kind.key}_${style.key}"

    fun styleFor(kind: Kind): Style {
        val saved = Bants.session.soundStyle(kind.key)
        return Style.values().firstOrNull { it.key == saved } ?: kind.default
    }

    fun setStyle(context: Context, kind: Kind, style: Style) {
        Bants.session.setSoundStyle(kind.key, style.key)
        Notifications.syncChannels(context)
    }

    private lateinit var pool: SoundPool
    private lateinit var audio: AudioManager
    private val ids = mutableMapOf<String, Int>()
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
        for (kind in Kind.values()) for (style in Style.values()) {
            val res = context.resources.getIdentifier(resName(kind, style), "raw", context.packageName)
            if (res != 0) ids[resName(kind, style)] = pool.load(context, res, 1)
        }
    }

    /** Plays the chosen sound for an event, unless sounds are off or the phone is on silent. */
    fun play(kind: Kind, enabled: Boolean) {
        if (!enabled || !::pool.isInitialized) return
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val now = System.currentTimeMillis()
        if (now - lastPlayed < 120) return
        lastPlayed = now
        playRaw(kind, styleFor(kind))
    }

    /** Plays a sound on the Sounds screen, even on silent, so you can hear what you're picking. */
    fun preview(kind: Kind, style: Style) {
        if (::pool.isInitialized) playRaw(kind, style)
    }

    private fun playRaw(kind: Kind, style: Style) {
        val id = ids[resName(kind, style)] ?: return
        if (id in ready) pool.play(id, kind.volume, kind.volume, 1, 0, 1f)
    }
}
