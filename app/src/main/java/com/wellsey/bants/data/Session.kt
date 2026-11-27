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

    /** Calls: background noise reduction, "off", "standard" or "strong". */
    var callNoise: String
        get() = prefs.getString("callNoise", "standard") ?: "standard"
        set(value) = prefs.edit().putString("callNoise", value).apply()

    var callEcho: Boolean
        get() = prefs.getBoolean("callEcho", true)
        set(value) = prefs.edit().putBoolean("callEcho", value).apply()

    var callAutoGain: Boolean
        get() = prefs.getBoolean("callAutoGain", true)
        set(value) = prefs.edit().putBoolean("callAutoGain", value).apply()

    /** Voice channels: camera quality ("360", "720", "1080") and frame rate. */
    var callCamRes: String
        get() = prefs.getString("callCamRes", "720") ?: "720"
        set(value) = prefs.edit().putString("callCamRes", value).apply()

    var callCamFps: Int
        get() = prefs.getInt("callCamFps", 30)
        set(value) = prefs.edit().putInt("callCamFps", value).apply()

    /** Voice channels: last screen share quality picked ("720", "1080", "source") and frame rate. */
    var callScreenRes: String
        get() = prefs.getString("callScreenRes", "1080") ?: "1080"
        set(value) = prefs.edit().putString("callScreenRes", value).apply()

    var callScreenFps: Int
        get() = prefs.getInt("callScreenFps", 30)
        set(value) = prefs.edit().putInt("callScreenFps", value).apply()

    /** Video calls: blur behind you, "off", "light" or "strong". */
    var callBlur: String
        get() = prefs.getString("callBlur", "off") ?: "off"
        set(value) = prefs.edit().putString("callBlur", value).apply()

    /** The picture behind you when the camera background is "image" (a file in the app's storage). */
    var callBgImage: String?
        get() = prefs.getString("callBgImage", null)
        set(value) = prefs.edit().putString("callBgImage", value).apply()

    /** Which video format calls ask for first: "auto" (VP8), "vp9", "h264" or "av1". */
    var callCodec: String
        get() = prefs.getString("callCodec", "auto") ?: "auto"
        set(value) = prefs.edit().putString("callCodec", value).apply()

    /** Send lighter video when on mobile data. */
    var callDataSaver: Boolean
        get() = prefs.getBoolean("callDataSaver", true)
        set(value) = prefs.edit().putBoolean("callDataSaver", value).apply()

    /** How loud someone is for you in calls (0–2, 1 = normal). */
    fun userVolume(userId: Long): Float = prefs.getFloat("vol_$userId", 1f)
    fun userMuted(userId: Long): Boolean = prefs.getBoolean("mutedFor_$userId", false)
    fun setUserVolume(userId: Long, volume: Float, muted: Boolean) =
        prefs.edit().putFloat("vol_$userId", volume).putBoolean("mutedFor_$userId", muted).apply()

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
