package com.wellsey.bants.data

import android.content.Context

/** Stores the server address, sign-in token and the signed-in user between launches. */
class Session(context: Context) {
    private val prefs = context.getSharedPreferences("bants", Context.MODE_PRIVATE)

    var serverUrl: String
        // normalise() also upgrades any old http:// address to https://.
        get() = normalise(prefs.getString("server", null) ?: DEFAULT_SERVER)
        set(value) = prefs.edit().putString("server", normalise(value)).apply()

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit().putString("token", value).apply()

    var soundsOn: Boolean
        get() = prefs.getBoolean("sounds", true)
        set(value) = prefs.edit().putBoolean("sounds", value).apply()

    var photoQuality: String
        get() = prefs.getString("photoQuality", "auto") ?: "auto"
        set(value) = prefs.edit().putString("photoQuality", value).apply()

    var videoQuality: String
        get() = prefs.getString("videoQuality", "auto") ?: "auto"
        set(value) = prefs.edit().putString("videoQuality", value).apply()

    /** Bants' own call ringtones, or false for the phone's ringtone. */
    var bantsRingtone: Boolean
        get() = prefs.getBoolean("bantsRingtone", true)
        set(value) = prefs.edit().putBoolean("bantsRingtone", value).apply()

    /** Emojis you've reacted with lately, newest first. They fill the quick-reaction row. */
    var recentReactions: List<String>
        get() = prefs.getString("recentReactions", null)?.split('\u0001')?.filter { it.isNotBlank() }.orEmpty()
        set(value) = prefs.edit().putString("recentReactions", value.take(12).joinToString("\u0001")).apply()

    fun chatStyle(convId: Long): String? = prefs.getString("chatStyle_$convId", null)
    fun setChatStyle(convId: Long, value: String) = prefs.edit().putString("chatStyle_$convId", value).apply()

    var appLock: Boolean
        get() = prefs.getBoolean("appLock", false)
        set(value) = prefs.edit().putBoolean("appLock", value).apply()

    /** How long Bants can be in the background before it locks again. */
    var appLockAfterSeconds: Int
        get() = prefs.getInt("appLockAfter", 0)
        set(value) = prefs.edit().putInt("appLockAfter", value).apply()

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

        /**
         * Tidies a server address and insists on HTTPS, so messages and passwords are always
         * encrypted on their way. Plain http:// is only allowed for servers on your home
         * network (handy for testing), never over the internet.
         */
        fun normalise(input: String): String {
            var s = input.trim().trimEnd('/')
            if (s.isEmpty()) return DEFAULT_SERVER
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
            if (s.startsWith("http://") && !isLocal(s.removePrefix("http://"))) s = "https://" + s.removePrefix("http://")
            return s
        }

        private fun isLocal(hostAndRest: String): Boolean {
            val host = hostAndRest.substringBefore('/').substringBefore(':').lowercase()
            if (host == "localhost" || host.endsWith(".local") || host.endsWith(".lan")) return true
            val p = host.split('.').mapNotNull { it.toIntOrNull() }
            if (p.size != 4) return false
            return p[0] == 10 || p[0] == 127 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)
        }
    }
}
