package com.wellsey.bants

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub for a newer Bants release and opens its download link.
 * Every build on main becomes a release, so "latest" is always the newest app.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/wellsey001/bants-android/releases/latest"
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

    data class Release(val version: String, val notes: String, val apkUrl: String)

    sealed interface Status {
        data object Idle : Status
        data class Failed(val message: String) : Status
    }

    /** A newer version, once found. */
    val available = MutableStateFlow<Release?>(null)
    val status = MutableStateFlow<Status>(Status.Idle)

    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    }

    fun currentVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** "1.0.19" → [1, 0, 19], for comparing. */
    private fun parts(v: String) = v.trimStart('v', 'V').split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    private fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * Looks for a newer release. Without [force] it only asks GitHub every few hours.
     * Returns the newer release, or null if you're up to date.
     */
    suspend fun check(context: Context, force: Boolean = false): Release? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("updater", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("lastCheck", 0) < CHECK_EVERY_MS) return@withContext available.value
        val request = Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").build()
        val json = http.newCall(request).execute().use { res ->
            if (!res.isSuccessful) throw IllegalStateException("Couldn't check for updates (${res.code})")
            Json.parseToJsonElement(res.body!!.string()).jsonObject
        }
        prefs.edit().putLong("lastCheck", now).apply()
        val tag = json["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val apk = json["assets"]?.jsonArray?.map { it.jsonObject }
            ?.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true }
            ?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
        val release = if (apk != null && isNewer(tag, currentVersion(context))) {
            Release(tag.trimStart('v'), json["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(), apk)
        } else {
            null
        }
        available.value = release
        release
    }

    /**
     * Opens the new APK's download link in the browser. The browser downloads it and Android's
     * installer takes over when you tap it. Bants itself never installs apps, which keeps
     * Play Protect happy.
     */
    fun install(context: Context, release: Release) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            status.value = Status.Idle
        } catch (e: Exception) {
            status.value = Status.Failed("Couldn't open your browser")
        }
    }
}
