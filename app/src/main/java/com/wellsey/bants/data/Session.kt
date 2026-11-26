package com.wellsey.bants.data

import android.content.Context

/** Stores the server address, sign-in token and the signed-in user between launches. */
class Session(context: Context) {
    private val prefs = context.getSharedPreferences("bants", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString("server", null) ?: DEFAULT_SERVER
        set(value) = prefs.edit().putString("server", normalise(value)).apply()

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit().putString("token", value).apply()

    var soundsOn: Boolean
        get() = prefs.getBoolean("sounds", true)
        set(value) = prefs.edit().putBoolean("sounds", value).apply()

    var hapticsOn: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(value) = prefs.edit().putBoolean("haptics", value).apply()

    fun soundStyle(kind: String): String? = prefs.getString("sound_$kind", null)

    fun setSoundStyle(kind: String, style: String) = prefs.edit().putString("sound_$kind", style).apply()

    var user: User?
        get() = prefs.getString("user", null)?.let {
            runCatching { BantsJson.decodeFromString(User.serializer(), it) }.getOrNull()
        }
        set(value) = prefs.edit()
            .putString("user", value?.let { BantsJson.encodeToString(User.serializer(), it) })
            .apply()

    companion object {
        const val DEFAULT_SERVER = "https://bants.kanji-avalanche.xyz"

        fun normalise(input: String): String {
            var s = input.trim().trimEnd('/')
            if (s.isEmpty()) return DEFAULT_SERVER
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
            return s
        }
    }
}
