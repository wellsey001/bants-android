package com.wellsey.bants.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream

@Serializable
data class CustomEmoji(
    val id: Long,
    val name: String,
    val media: String,
    val animated: Boolean = false,
    val userId: Long? = null,
    /** Set for emojis that belong to a server rather than a person. */
    val serverId: Long? = null,
) {
    val token: String get() = "<${if (animated) "a" else ""}:$name:$id>"
}

@Serializable
private data class EmojiList(val emojis: List<CustomEmoji> = emptyList(), val max: Int = 50)

/**
 * Custom emojis: your own uploads (up to 50, still or animated) that work in any server.
 * In a message they're written <:name:id> or <a:name:id>. Ones we can't find show as :name:.
 */
object Emojis {
    private lateinit var prefs: android.content.SharedPreferences
    private val api get() = Bants.api

    val mine = MutableStateFlow<List<CustomEmoji>>(emptyList())
    val max = MutableStateFlow(50)
    /** Every custom emoji we've seen (yours and other people's), so messages can draw them. */
    val known = MutableStateFlow<Map<Long, CustomEmoji>>(emptyMap())

    val TOKEN = Regex("<(a?):([A-Za-z0-9_]{2,32}):(\\d{1,12})>")
    private val ONE = Regex("^<(a?):([A-Za-z0-9_]{2,32}):(\\d{1,12})>$")

    fun init(context: Context) {
        prefs = context.getSharedPreferences("bants_emojis", Context.MODE_PRIVATE)
        Bants.scope.launch { Bants.realtime.events.collect { handle(it) } }
        Bants.scope.launch {
            Bants.loggedIn.collect { on ->
                if (on) runCatching { load() } else { mine.value = emptyList(); known.value = emptyMap() }
            }
        }
    }

    suspend fun load() {
        val r = api.get<EmojiList>("/api/emojis")
        max.value = r.max
        setMine(r.emojis)
    }

    private fun setMine(list: List<CustomEmoji>) {
        mine.value = list.sortedBy { it.name.lowercase() }
        remember(list)
    }

    fun remember(list: List<CustomEmoji>) {
        if (list.isEmpty()) return
        known.update { it + list.associateBy { e -> e.id } }
    }

    private fun handle(ev: JsonObject) {
        when (ev["type"]?.jsonPrimitive?.contentOrNull) {
            "emojis.updated" -> runCatching {
                setMine(api.json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(CustomEmoji.serializer()), ev["emojis"] as JsonArray))
            }
            "channel.reaction" -> (ev["emojis"] as? JsonArray)?.let { arr ->
                runCatching { remember(api.json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(CustomEmoji.serializer()), arr)) }
            }
        }
    }

    /** The pieces of a <:name:id> token, or null for a normal emoji. */
    fun parse(text: String): Triple<Long, String, Boolean>? {
        val m = ONE.find(text) ?: return null
        return Triple(m.groupValues[3].toLong(), m.groupValues[2], m.groupValues[1] == "a")
    }

    fun plain(text: String) = TOKEN.replace(text) { ":${it.groupValues[2]}:" }

    /** Typing :name: for one of yours (or this server's) turns it into the real thing when you send. */
    fun expand(text: String, serverId: Long? = null): String {
        val server = serverId?.let { Servers.servers.value[it]?.emojis }.orEmpty()
        val list = mine.value + server
        if (list.isEmpty() || ':' !in text) return text
        val byName = list.associateBy { it.name.lowercase() }
        // Leave tokens that are already there alone.
        return Regex("(<a?)?:([A-Za-z0-9_]{2,32}):(\\d{1,12}>)?").replace(text) { m ->
            if (m.groupValues[1].isNotEmpty() || m.groupValues[3].isNotEmpty()) m.value
            else byName[m.groupValues[2].lowercase()]?.token ?: m.value
        }
    }

    /** True when a message is just custom emojis (and maybe normal ones), so they're drawn big. */
    fun onlyEmojis(text: String): Boolean {
        val count = TOKEN.findAll(text).count()
        if (count == 0 || count > 27) return false
        val rest = TOKEN.replace(text, "").filterNot { it.isWhitespace() }
        return rest.codePoints().allMatch { cp -> Character.getType(cp) == Character.OTHER_SYMBOL.toInt() || cp == 0x200D || cp == 0xFE0F || cp in 0x1F3FB..0x1F3FF }
    }

    // ───────────── recent picks (kept apart from the chat ones) ─────────────

    var recent: List<String>
        get() = prefs.getString("recent", "")!!.split('\n').filter { it.isNotBlank() }
        set(v) = prefs.edit().putString("recent", v.distinct().take(12).joinToString("\n")).apply()

    fun used(token: String) { recent = listOf(token) + recent.filter { it != token } }

    // ───────────── managing yours ─────────────

    private fun nameFor(filename: String): String {
        var base = filename.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_]"), "_").replace(Regex("_+"), "_").trim('_').take(28)
        if (base.length < 2) base = "emoji"
        val taken = mine.value.map { it.name.lowercase() }.toSet()
        var name = base
        var i = 2
        while (name.lowercase() in taken) name = "$base${i++}"
        return name
    }

    /** Shrinks a still to a 128px PNG (GIFs go up as they are, so they keep moving) and uploads it. Returns (media id, a name). */
    suspend fun prepareAndUpload(context: Context, uri: Uri): Pair<String, String> {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/png"
        val filename = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "emoji"
        val (bytes, type) = withContext(Dispatchers.IO) { prepare(context, uri, mime, filename) }
        val id = Bants.uploadBytes(bytes, type, if (type == "image/gif") "emoji.gif" else "emoji.png")
        var base = filename.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_]"), "_").replace(Regex("_+"), "_").trim('_').take(28)
        if (base.length < 2) base = "emoji"
        return id to base
    }

    private fun prepare(context: Context, uri: Uri, mime: String, filename: String): Pair<ByteArray, String> {
        val resolver = context.contentResolver
        return if (mime == "image/gif") {
            val raw = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw ApiException(0, "Couldn't read that GIF")
            if (raw.size > 1024 * 1024) throw ApiException(0, "$filename is over 1 MB. Try a smaller GIF")
            raw to "image/gif"
        } else {
            val src = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: throw ApiException(0, "Couldn't read that picture")
            val scale = minOf(1f, 128f / maxOf(src.width, src.height))
            val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true) else src
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray() to "image/png"
        }
    }

    /** Adds pictures as emojis. Stills get shrunk to 128px PNGs; GIFs go up as they are so they keep moving. */
    suspend fun add(context: Context, uris: List<Uri>): Int {
        var added = 0
        for (uri in uris) {
            if (mine.value.size >= max.value) throw ApiException(0, "You've got ${max.value} emojis. Delete one to add another")
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "image/png"
            val filename = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "emoji"
            val (bytes, type) = withContext(Dispatchers.IO) {
                if (mime == "image/gif") {
                    val raw = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw ApiException(0, "Couldn't read that GIF")
                    if (raw.size > 1024 * 1024) throw ApiException(0, "$filename is over 1 MB. Try a smaller GIF")
                    raw to "image/gif"
                } else {
                    val src = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: throw ApiException(0, "Couldn't read that picture")
                    val scale = minOf(1f, 128f / maxOf(src.width, src.height))
                    val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true) else src
                    val out = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.toByteArray() to "image/png"
                }
            }
            val mediaId = Bants.uploadBytes(bytes, type, if (type == "image/gif") "emoji.gif" else "emoji.png")
            val e = api.post<CustomEmoji>("/api/emojis", buildJsonObject {
                put("name", nameFor(filename))
                put("media", mediaId)
            })
            if (mine.value.none { it.id == e.id }) setMine(mine.value + e)
            added++
        }
        return added
    }

    suspend fun rename(e: CustomEmoji, name: String) {
        val u = api.patch<CustomEmoji>("/api/emojis/${e.id}", buildJsonObject { put("name", name.trim()) })
        setMine(mine.value.map { if (it.id == u.id) u else it })
    }

    suspend fun delete(e: CustomEmoji) {
        api.delete<JsonObject>("/api/emojis/${e.id}")
        setMine(mine.value.filter { it.id != e.id })
    }
}
