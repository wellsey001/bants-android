package com.wellsey.bants

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
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
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub for a newer Bants release, downloads the APK and hands it to Android's installer.
 * Every build on main becomes a release, so "latest" is always the newest app.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/wellsey001/bants-android/releases/latest"
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

    data class Release(val version: String, val notes: String, val apkUrl: String)

    sealed interface Status {
        data object Idle : Status
        data class Downloading(val progress: Float?) : Status
        data object NeedsPermission : Status
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

    /** Downloads the update and opens the installer. Asks for the "install apps" permission first if needed. */
    suspend fun install(context: Context, release: Release) {
        if (status.value is Status.Downloading) return
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            status.value = Status.NeedsPermission
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        status.value = Status.Downloading(null)
        try {
            val file = withContext(Dispatchers.IO) { download(context, release.apkUrl) }
            // Make sure it really is Bants before handing it over.
            val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
            if (info?.packageName != context.packageName) throw IllegalStateException("That download wasn't a Bants app")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            status.value = Status.Idle
        } catch (e: Exception) {
            status.value = Status.Failed(e.message ?: "Download failed")
        }
    }

    private fun download(context: Context, url: String): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "Bants.apk")
        http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) throw IllegalStateException("Download failed (${res.code})")
            val body = res.body ?: throw IllegalStateException("Download failed")
            val total = body.contentLength().takeIf { it > 0 }
            var done = 0L
            var lastShown = -1
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total != null) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastShown) {
                                lastShown = pct
                                status.value = Status.Downloading(pct / 100f)
                            }
                        }
                    }
                }
            }
        }
        return out
    }
}
