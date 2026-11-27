package com.wellsey.bants.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import com.wellsey.bants.calls.CallManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Voice channel extras: the soundboard, join/leave sounds, and stage channels' mic rules. */
object VoiceExtras {
    private val api get() = Bants.api
    val sounds = MutableStateFlow<Map<Long, List<ServerSound>>>(emptyMap())
    val favourites = MutableStateFlow<List<ServerSound>>(emptyList())

    fun init() {
        Bants.scope.launch { Bants.realtime.events.collect { handle(it) } }
        // On a stage, your mic only works while you're a speaker.
        Bants.scope.launch {
            combine(CallManager.state, Servers.servers) { s, _ ->
                val id = s?.voiceChannelId ?: return@combine false
                val ch = Servers.channel(id) ?: return@combine false
                ch.stage && ch.voiceMembers.firstOrNull { it.id == Bants.myId }?.speaker != true
            }.collect { CallManager.stageAudience = it }
        }
    }

    private fun handle(ev: JsonObject) {
        when (ev["type"]?.jsonPrimitive?.contentOrNull) {
            "voice.sound" -> {
                val channelId = ev["channelId"]?.jsonPrimitive?.longOrNull ?: return
                if (CallManager.state.value?.voiceChannelId != channelId) return
                val s = ev["sound"]?.jsonObject ?: return
                val media = s["media"]?.jsonPrimitive?.contentOrNull ?: return
                val volume = s["volume"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 1f
                Bants.scope.launch { play(media, volume) }
                val id = s["id"]?.jsonPrimitive?.longOrNull ?: 0
                if (id > 0) {
                    val uid = ev["userId"]?.jsonPrimitive?.longOrNull
                    val ch = Servers.channel(channelId)
                    val who = Servers.members.value[ch?.serverId]?.firstOrNull { it.user.id == uid }?.shownName() ?: "Someone"
                    Bants.notices.tryEmit("${s["emoji"]?.jsonPrimitive?.contentOrNull ?: "🔊"} $who played ${s["name"]?.jsonPrimitive?.contentOrNull ?: "a sound"}")
                }
            }
            "server.sounds" -> {
                val sid = ev["serverId"]?.jsonPrimitive?.longOrNull ?: return
                sounds.update { it - sid }
            }
        }
    }

    /** Plays a sound locally (downloaded once, then cached). */
    suspend fun play(media: String, volume: Float = 1f) {
        val file = runCatching { Bants.mediaFile(media, "sound-$media") }.getOrNull() ?: return
        withContext(Dispatchers.Main) {
            runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    setDataSource(file.absolutePath)
                    setVolume(volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f))
                    setOnCompletionListener { it.release() }
                    prepare()
                    start()
                }
            }
        }
    }

    suspend fun load(serverId: Long): List<ServerSound> {
        val list = api.get<List<ServerSound>>("/api/servers/$serverId/sounds")
        sounds.update { it + (serverId to list) }
        favourites.value = runCatching { api.get<List<ServerSound>>("/api/me/sounds") }.getOrDefault(favourites.value)
        return list
    }

    suspend fun setFavourite(s: ServerSound, on: Boolean) {
        api.raw("PUT", "/api/sounds/${s.id}/favourite", api.body(buildJsonObject { put("favourite", on) }))
        load(s.serverId)
    }

    suspend fun add(context: Context, serverId: Long, uri: Uri, name: String, emoji: String) {
        val (bytes, mime, filename) = readAudio(context, uri)
        val id = Bants.uploadBytes(bytes, mime, filename)
        api.post<ServerSound>("/api/servers/$serverId/sounds", buildJsonObject {
            put("media", id)
            put("name", name.ifBlank { filename.substringBeforeLast('.') }.take(32))
            put("emoji", emoji.ifBlank { "🔊" })
        })
        load(serverId)
    }

    suspend fun delete(s: ServerSound) {
        api.delete<JsonObject>("/api/sounds/${s.id}")
        load(s.serverId)
    }

    /** Your join or leave sound: "joinSound" / "leaveSound", or null to remove it. */
    suspend fun setOwnSound(context: Context, key: String, uri: Uri?) {
        val id = uri?.let { val (b, m, f) = readAudio(context, it); Bants.uploadBytes(b, m, f) }
        Bants.updateProfileFields(buildJsonObject { if (id == null) put(key, kotlinx.serialization.json.JsonNull) else put(key, id) })
    }

    private suspend fun readAudio(context: Context, uri: Uri): Triple<ByteArray, String, String> = withContext(Dispatchers.IO) {
        val r = context.contentResolver
        val mime = r.getType(uri)?.takeIf { it.startsWith("audio/") } ?: "audio/mpeg"
        val name = r.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "sound"
        val bytes = r.openInputStream(uri)?.use { it.readBytes() } ?: throw ApiException(0, "Couldn't read that sound")
        if (bytes.size > 1024 * 1024) throw ApiException(0, "Keep sounds under 1 MB (a few seconds)")
        Triple(bytes, mime, name)
    }
}
