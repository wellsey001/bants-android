package com.wellsey.bants.data

import android.content.Context
import com.wellsey.bants.Sounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.serializer
import java.util.UUID

/** Which half of the app you're looking at. The switch on the home screen flips this. */
enum class HomeMode { Chats, Servers }

/**
 * State and actions for servers (Discord-style communities). Works like [Bants] does for chats:
 * screens read the StateFlows and call the functions, and live events from the WebSocket are merged in.
 */
object Servers {
    private lateinit var prefs: android.content.SharedPreferences
    private val api get() = Bants.api
    private val scope get() = Bants.scope

    /** True once the server says it supports servers (older bants-server versions don't). */
    val available = MutableStateFlow(false)
    val mode = MutableStateFlow(HomeMode.Chats)

    val servers = MutableStateFlow<Map<Long, BantsServer>>(emptyMap())
    val messages = MutableStateFlow<Map<Long, List<ChannelMessage>>>(emptyMap())
    val hasMore = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val members = MutableStateFlow<Map<Long, List<ServerMember>>>(emptyMap())
    /** Threads we've seen, by their own id. */
    val threads = MutableStateFlow<Map<Long, ServerChannel>>(emptyMap())
    /** channelId -> (userId -> when their typing indicator runs out) */
    val typing = MutableStateFlow<Map<Long, Map<Long, Long>>>(emptyMap())

    /** Set from the call screen's chat button: the app opens this channel. */
    val openChannelRequest = MutableStateFlow<Long?>(null)

    /** The channel on screen right now, so its messages are marked read as they arrive. */
    @Volatile var activeChannel: Long? = null

    private var lastTypingSent = 0L

    fun init(context: Context) {
        prefs = context.getSharedPreferences("bants_servers", Context.MODE_PRIVATE)
        mode.value = if (prefs.getString("mode", null) == "servers") HomeMode.Servers else HomeMode.Chats
        available.value = prefs.getBoolean("available", false)
        scope.launch { Bants.realtime.events.collect { handle(it) } }
        scope.launch {
            Bants.loggedIn.collect { on ->
                if (on) runCatching { refresh() } else clear()
            }
        }
        // Typing indicators fade out on their own.
        scope.launch {
            while (true) {
                delay(2_000)
                val now = System.currentTimeMillis()
                if (typing.value.values.any { m -> m.values.any { it < now } }) {
                    typing.update { all -> all.mapValues { (_, m) -> m.filterValues { it >= now } }.filterValues { it.isNotEmpty() } }
                }
            }
        }
    }

    fun setMode(m: HomeMode) {
        mode.value = m
        prefs.edit().putString("mode", if (m == HomeMode.Servers) "servers" else "chats").apply()
    }

    private fun clear() {
        servers.value = emptyMap()
        messages.value = emptyMap()
        hasMore.value = emptyMap()
        members.value = emptyMap()
        threads.value = emptyMap()
        typing.value = emptyMap()
    }

    // ───────────── loading ─────────────

    suspend fun refresh() {
        val info = api.get<ServerInfo>("/api/server")
        available.value = info.servers
        prefs.edit().putBoolean("available", info.servers).apply()
        if (!info.servers) {
            if (mode.value == HomeMode.Servers) setMode(HomeMode.Chats)
            return
        }
        val list = api.get<List<BantsServer>>("/api/servers")
        servers.value = list.associateBy { it.id }
    }

    suspend fun refreshServer(id: Long) = upsert(api.get<BantsServer>("/api/servers/$id"))

    private fun upsert(s: BantsServer) = servers.update { it + (s.id to s) }

    /** Your servers in your own order. */
    fun ordered(all: Map<Long, BantsServer>): List<BantsServer> =
        all.values.sortedWith(compareBy<BantsServer> { it.me?.position ?: 0 }.thenBy { it.id })

    fun channel(id: Long): ServerChannel? =
        servers.value.values.firstNotNullOfOrNull { s -> s.channels.firstOrNull { it.id == id } } ?: threads.value[id]

    fun serverOf(channel: ServerChannel): BantsServer? = servers.value[channel.serverId]

    suspend fun loadMessages(channelId: Long) {
        val list = api.get<List<ChannelMessage>>("/api/channels/$channelId/messages?limit=50")
        messages.update { all ->
            val pending = all[channelId].orEmpty().filter { it.pending || it.failed }
            all + (channelId to merge(list, pending))
        }
        hasMore.update { it + (channelId to (list.size >= 50)) }
    }

    suspend fun loadOlder(channelId: Long) {
        val first = messages.value[channelId].orEmpty().firstOrNull { !it.pending && !it.failed } ?: return
        val list = api.get<List<ChannelMessage>>("/api/channels/$channelId/messages?limit=50&before=${first.id}")
        putMessages(channelId, list)
        hasMore.update { it + (channelId to (list.size >= 50)) }
    }

    suspend fun loadMembers(serverId: Long) {
        val list = api.get<List<ServerMember>>("/api/servers/$serverId/members")
        members.update { it + (serverId to list) }
    }

    suspend fun loadThreads(channelId: Long, archived: Boolean = false): List<ServerChannel> {
        val list = api.get<List<ServerChannel>>("/api/channels/$channelId/threads?archived=$archived")
        threads.update { all -> all + list.associateBy { it.id } }
        return list
    }

    private fun merge(existing: List<ChannelMessage>, incoming: List<ChannelMessage>): List<ChannelMessage> {
        val byId = LinkedHashMap<Long, ChannelMessage>()
        val clientIds = incoming.mapNotNull { m -> m.clientId.takeIf { !m.pending && !m.failed } }.toSet()
        for (m in existing) if (!(m.pending || m.failed) || m.clientId !in clientIds) byId[m.id] = m
        for (m in incoming) {
            if (m.pending || m.failed) { byId[m.id] = m; continue }
            // A real message replaces its pending copy.
            byId.entries.removeAll { (_, old) -> (old.pending || old.failed) && old.clientId != null && old.clientId == m.clientId }
            byId[m.id] = m
        }
        val (local, real) = byId.values.partition { it.pending || it.failed }
        return real.sortedBy { it.id } + local.sortedBy { it.createdAt }
    }

    private fun putMessages(channelId: Long, incoming: List<ChannelMessage>) {
        messages.update { all -> all + (channelId to merge(all[channelId].orEmpty(), incoming)) }
    }

    private fun updateChannel(channelId: Long, change: (ServerChannel) -> ServerChannel) {
        threads.update { all -> all[channelId]?.let { all + (channelId to change(it)) } ?: all }
        servers.update { all ->
            val s = all.values.firstOrNull { sv -> sv.channels.any { it.id == channelId } } ?: return@update all
            val channels = s.channels.map { if (it.id == channelId) change(it) else it }
            all + (s.id to s.copy(
                channels = channels,
                unread = channels.any { it.unread },
                mentionCount = channels.sumOf { it.mentionCount },
            ))
        }
    }

    // ───────────── sending ─────────────

    /** Turns "@username" into a proper mention for anyone in the server. */
    private fun withMentions(serverId: Long, text: String): String {
        val people = members.value[serverId].orEmpty().associateBy { it.user.username.lowercase() }
        return Regex("(^|\\s)@([A-Za-z0-9_.]{3,24})").replace(text) { m ->
            val who = people[m.groupValues[2].lowercase()]
            if (who != null) "${m.groupValues[1]}<@${who.user.id}>" else m.value
        }
    }

    fun send(channelId: Long, text: String, replyTo: Long? = null) {
        val ch = channel(channelId) ?: return
        val body = withMentions(ch.serverId, text.trim())
        if (body.isEmpty()) return
        val me = Bants.me.value ?: return
        val clientId = UUID.randomUUID().toString()
        val local = ChannelMessage(
            id = -System.nanoTime(),
            channelId = channelId,
            serverId = ch.serverId,
            clientId = clientId,
            author = ChannelAuthor(me.id, me.username, me.displayName, me.avatar, servers.value[ch.serverId]?.me?.nickname),
            body = body,
            replyTo = replyTo,
            createdAt = System.currentTimeMillis(),
            pending = true,
        )
        putMessages(channelId, listOf(local))
        scope.launch { deliver(local) }
    }

    private suspend fun deliver(m: ChannelMessage) {
        try {
            val sent = api.post<ChannelMessage>("/api/channels/${m.channelId}/messages", buildJsonObject {
                put("body", m.body)
                put("clientId", m.clientId)
                m.replyTo?.let { put("replyTo", it) }
            })
            putMessages(m.channelId, listOf(sent))
            if (Bants.inForeground) Sounds.play(Sounds.Kind.Sent, Bants.session.soundsOn)
        } catch (e: ApiException) {
            messages.update { all ->
                all + (m.channelId to all[m.channelId].orEmpty().map { if (it.id == m.id) it.copy(pending = false, failed = true) else it })
            }
            Bants.notices.tryEmit(e.message ?: "Couldn't send that")
        }
    }

    fun retry(m: ChannelMessage) {
        val again = m.copy(pending = true, failed = false)
        putMessages(m.channelId, listOf(again))
        scope.launch { deliver(again) }
    }

    fun discard(m: ChannelMessage) {
        messages.update { all -> all + (m.channelId to all[m.channelId].orEmpty().filter { it.id != m.id }) }
    }

    fun sendTyping(channelId: Long) {
        val now = System.currentTimeMillis()
        if (now - lastTypingSent < 5_000) return
        lastTypingSent = now
        Bants.realtime.send(buildJsonObject {
            put("type", "channel.typing")
            put("channelId", channelId)
        })
    }

    fun markRead(channelId: Long) {
        val ch = channel(channelId) ?: return
        if (!ch.unread && ch.mentionCount == 0) return
        updateChannel(channelId) { it.copy(unread = false, mentionCount = 0) }
        val viaSocket = Bants.realtime.send(buildJsonObject {
            put("type", "channel.read")
            put("channelId", channelId)
        })
        if (!viaSocket) scope.launch { runCatching { api.post<JsonObject>("/api/channels/$channelId/read") } }
    }

    suspend fun markServerRead(serverId: Long) = upsert(api.post<BantsServer>("/api/servers/$serverId/read"))

    suspend fun edit(m: ChannelMessage, body: String) {
        val updated = api.patch<ChannelMessage>("/api/channel-messages/${m.id}", buildJsonObject { put("body", withMentions(m.serverId ?: 0, body.trim())) })
        putMessages(m.channelId, listOf(updated))
    }

    suspend fun delete(m: ChannelMessage) {
        api.delete<JsonObject>("/api/channel-messages/${m.id}")
        removeMessage(m.channelId, m.id)
    }

    private fun removeMessage(channelId: Long, id: Long) {
        messages.update { all -> all[channelId]?.let { list -> all + (channelId to list.filter { it.id != id }) } ?: all }
    }

    suspend fun toggleReaction(m: ChannelMessage, emoji: String) {
        val mine = m.reactions.firstOrNull { it.emoji == emoji }?.userIds?.contains(Bants.myId) == true
        val path = "/api/channel-messages/${m.id}/reactions/${java.net.URLEncoder.encode(emoji, "UTF-8").replace("+", "%20")}"
        if (mine) api.delete<JsonObject>(path) else api.raw("PUT", path, api.body(JsonObject(emptyMap())))
        applyReaction(m.channelId, m.id, Bants.myId, emoji, !mine)
        if (!mine) Bants.session.recentReactions = (listOf(emoji) + Bants.session.recentReactions.filter { it != emoji })
    }

    private fun applyReaction(channelId: Long, messageId: Long, userId: Long, emoji: String, added: Boolean) {
        messages.update { all ->
            val list = all[channelId] ?: return@update all
            all + (channelId to list.map { m ->
                if (m.id != messageId) return@map m
                val others = m.reactions.filter { it.emoji != emoji }
                val current = m.reactions.firstOrNull { it.emoji == emoji }?.userIds.orEmpty()
                val users = if (added) (current + userId).distinct() else current - userId
                val reactions = if (users.isEmpty()) others else {
                    val idx = m.reactions.indexOfFirst { it.emoji == emoji }
                    val r = ChannelReaction(emoji, users)
                    if (idx >= 0) m.reactions.map { if (it.emoji == emoji) r else it } else others + r
                }
                m.copy(reactions = reactions)
            })
        }
    }

    suspend fun setPinned(m: ChannelMessage, pinned: Boolean) {
        val path = "/api/channel-messages/${m.id}/pin"
        if (pinned) api.raw("PUT", path, api.body(JsonObject(emptyMap()))) else api.delete<JsonObject>(path)
    }

    suspend fun pins(channelId: Long): List<ChannelMessage> = api.get("/api/channels/$channelId/pins")

    // ───────────── servers ─────────────

    suspend fun create(name: String): BantsServer {
        val s = api.post<BantsServer>("/api/servers", buildJsonObject { put("name", name.trim()) })
        upsert(s)
        return s
    }

    /** Accepts a bare code or a whole invite link. */
    fun inviteCode(input: String): String = input.trim().trimEnd('/').substringAfterLast('/').substringAfterLast('=')

    suspend fun previewInvite(input: String): InvitePreview = api.get("/api/invites/${inviteCode(input)}")

    suspend fun join(input: String): BantsServer {
        val s = api.post<BantsServer>("/api/invites/${inviteCode(input)}")
        upsert(s)
        return s
    }

    suspend fun createInvite(serverId: Long, maxUses: Int = 0, hours: Int = 168): CreatedInvite =
        api.post("/api/servers/$serverId/invites", buildJsonObject {
            put("maxUses", maxUses)
            put("expiresInHours", hours)
        })

    fun inviteLink(code: String) = "${Bants.session.serverUrl}/invite/$code"

    suspend fun update(serverId: Long, name: String, description: String) =
        upsert(api.patch<BantsServer>("/api/servers/$serverId", buildJsonObject {
            put("name", name.trim())
            put("description", description.trim())
        }))

    suspend fun setMuted(serverId: Long, muted: Boolean) =
        upsert(api.patch<BantsServer>("/api/servers/$serverId/me", buildJsonObject { put("muted", muted) }))

    suspend fun leave(serverId: Long) {
        api.post<JsonObject>("/api/servers/$serverId/leave")
        forget(serverId)
    }

    suspend fun deleteServer(serverId: Long) {
        api.delete<JsonObject>("/api/servers/$serverId")
        forget(serverId)
    }

    private fun forget(serverId: Long) {
        val ids = servers.value[serverId]?.channels?.map { it.id }.orEmpty().toSet()
        servers.update { it - serverId }
        messages.update { it - ids }
        members.update { it - serverId }
        threads.update { all -> all.filterValues { it.serverId != serverId } }
    }

    // ───────────── channels ─────────────

    suspend fun createChannel(serverId: Long, name: String, type: String, parentId: Long?, private: Boolean): ServerChannel {
        val c = api.post<ServerChannel>("/api/servers/$serverId/channels", buildJsonObject {
            put("name", name.trim())
            put("type", type)
            if (parentId != null) put("parentId", parentId)
            put("private", private)
        })
        runCatching { refreshServer(serverId) }
        return c
    }

    suspend fun editChannel(channelId: Long, name: String, topic: String, slowmode: Int) {
        val c = api.patch<ServerChannel>("/api/channels/$channelId", buildJsonObject {
            put("name", name.trim())
            put("topic", topic.trim())
            put("slowmode", slowmode)
        })
        runCatching { refreshServer(c.serverId) }
    }

    suspend fun deleteChannel(channelId: Long) {
        val serverId = channel(channelId)?.serverId
        api.delete<JsonObject>("/api/channels/$channelId")
        messages.update { it - channelId }
        threads.update { it - channelId }
        if (serverId != null) runCatching { refreshServer(serverId) }
    }

    suspend fun startThread(channelId: Long, name: String, messageId: Long?): ServerChannel {
        val t = api.post<ServerChannel>("/api/channels/$channelId/threads", buildJsonObject {
            put("name", name.trim())
            if (messageId != null) put("messageId", messageId)
        })
        threads.update { it + (t.id to t) }
        return t
    }

    suspend fun setThreadState(threadId: Long, archived: Boolean? = null, locked: Boolean? = null) {
        val t = api.patch<ServerChannel>("/api/channels/$threadId", buildJsonObject {
            archived?.let { put("archived", it) }
            locked?.let { put("locked", it) }
        })
        threads.update { it + (t.id to t) }
    }

    // ───────────── members and roles ─────────────

    private fun putMember(serverId: Long, m: ServerMember) {
        members.update { all ->
            val list = all[serverId] ?: return@update all
            all + (serverId to (list.filter { it.user.id != m.user.id } + m).sortedBy { it.joinedAt })
        }
    }

    suspend fun setRoles(serverId: Long, userId: Long, roleIds: List<Long>) {
        putMember(serverId, api.patch("/api/servers/$serverId/members/$userId", buildJsonObject {
            putJsonArray("roleIds") { roleIds.forEach { add(JsonPrimitive(it)) } }
        }))
    }

    suspend fun setNickname(serverId: Long, userId: Long, nickname: String?) {
        putMember(serverId, api.patch("/api/servers/$serverId/members/$userId", buildJsonObject {
            if (nickname.isNullOrBlank()) put("nickname", JsonNull) else put("nickname", nickname.trim())
        }))
    }

    /** minutes = 0 ends a timeout. */
    suspend fun timeout(serverId: Long, userId: Long, minutes: Int) {
        putMember(serverId, api.patch("/api/servers/$serverId/members/$userId", buildJsonObject {
            if (minutes <= 0) put("timeoutUntil", JsonNull) else put("timeoutUntil", System.currentTimeMillis() + minutes * 60_000L)
        }))
    }

    suspend fun kick(serverId: Long, userId: Long) {
        api.delete<JsonObject>("/api/servers/$serverId/members/$userId")
        members.update { all -> all[serverId]?.let { all + (serverId to it.filter { m -> m.user.id != userId }) } ?: all }
    }

    suspend fun ban(serverId: Long, userId: Long, reason: String, wipeHours: Int) {
        api.post<JsonObject>("/api/servers/$serverId/bans/$userId", buildJsonObject {
            put("reason", reason)
            put("deleteMessageHours", wipeHours)
        })
        members.update { all -> all[serverId]?.let { all + (serverId to it.filter { m -> m.user.id != userId }) } ?: all }
    }

    suspend fun createRole(serverId: Long, name: String): ServerRole {
        val r = api.post<ServerRole>("/api/servers/$serverId/roles", buildJsonObject { put("name", name) })
        runCatching { refreshServer(serverId) }
        return r
    }

    suspend fun updateRole(serverId: Long, roleId: Long, name: String?, colour: Long?, permissions: Long, hoist: Boolean, mentionable: Boolean) {
        api.patch<ServerRole>("/api/roles/$roleId", buildJsonObject {
            if (name != null) put("name", name.trim())
            if (colour == null) put("colour", JsonNull) else put("colour", colour)
            put("permissions", permissions)
            put("hoist", hoist)
            put("mentionable", mentionable)
        })
        runCatching { refreshServer(serverId) }
    }

    suspend fun deleteRole(serverId: Long, roleId: Long) {
        api.delete<JsonObject>("/api/roles/$roleId")
        runCatching { refreshServer(serverId) }
    }

    // ───────────── profiles ─────────────

    suspend fun profile(serverId: Long, userId: Long): ProfileCardData =
        api.get("/api/servers/$serverId/members/$userId/profile")

    /** Your profile in one server. Pass null/empty to fall back to your main profile. */
    suspend fun updateServerProfile(serverId: Long, nickname: String?, includeNickname: Boolean, avatar: String?, banner: String?, bio: String, pronouns: String) {
        upsert(api.patch<BantsServer>("/api/servers/$serverId/me", buildJsonObject {
            if (includeNickname) { if (nickname.isNullOrBlank()) put("nickname", JsonNull) else put("nickname", nickname.trim()) }
            if (avatar == null) put("avatar", JsonNull) else put("avatar", avatar)
            if (banner == null) put("banner", JsonNull) else put("banner", banner)
            put("bio", bio.trim())
            put("pronouns", pronouns.trim())
        }))
        runCatching { loadMembers(serverId) }
    }

    /** The colour of someone's most senior coloured role, for their name. */
    fun nameColour(serverId: Long, userId: Long): Long? {
        val s = servers.value[serverId] ?: return null
        val roleIds = members.value[serverId]?.firstOrNull { it.user.id == userId }?.roleIds ?: return null
        return s.roles.filter { it.id in roleIds && it.colour != null }.maxByOrNull { it.position }?.colour
    }

    // ───────────── live events ─────────────

    private fun handle(ev: JsonObject) {
        val type = ev.str("type") ?: return
        try {
            when (type) {
                "ready" -> scope.launch {
                    runCatching { refresh() }
                    activeChannel?.let { id -> runCatching { loadMessages(id) } }
                }
                "server.add", "server.update" -> upsert(decode(ev["server"]))
                "server.remove" -> forget(ev.long("serverId"))
                "server.member.add" -> {
                    val sid = ev.long("serverId")
                    val m = decode<ServerMember>(ev["member"])
                    servers.update { all -> all[sid]?.let { all + (sid to it.copy(memberCount = it.memberCount + 1)) } ?: all }
                    members.update { all -> all[sid]?.let { list -> all + (sid to (list.filter { it.user.id != m.user.id } + m)) } ?: all }
                }
                "server.member.update" -> putMember(ev.long("serverId"), decode(ev["member"]))
                "server.member.remove" -> {
                    val sid = ev.long("serverId")
                    val uid = ev.long("userId")
                    servers.update { all -> all[sid]?.let { all + (sid to it.copy(memberCount = (it.memberCount - 1).coerceAtLeast(0))) } ?: all }
                    members.update { all -> all[sid]?.let { list -> all + (sid to list.filter { it.user.id != uid }) } ?: all }
                }
                "user.updated" -> {
                    // Someone changed their main profile: refresh it in every member list.
                    val o = ev["user"] as? JsonObject ?: return
                    val id = o["id"]?.jsonPrimitive?.longOrNull ?: return
                    members.update { all ->
                        all.mapValues { (_, list) ->
                            list.map { m ->
                                if (m.user.id != id) m else m.copy(user = m.user.copy(
                                    displayName = o["displayName"]?.jsonPrimitive?.contentOrNull ?: m.user.displayName,
                                    avatar = if (o.containsKey("avatar")) o["avatar"]?.jsonPrimitive?.contentOrNull else m.user.avatar,
                                    about = o["about"]?.jsonPrimitive?.contentOrNull ?: m.user.about,
                                    banner = if (o.containsKey("banner")) o["banner"]?.jsonPrimitive?.contentOrNull else m.user.banner,
                                    accentColour = if (o.containsKey("accentColour")) o["accentColour"]?.jsonPrimitive?.longOrNull else m.user.accentColour,
                                    pronouns = o["pronouns"]?.jsonPrimitive?.contentOrNull ?: m.user.pronouns,
                                    customStatus = o["customStatus"]?.jsonPrimitive?.contentOrNull ?: m.user.customStatus,
                                    statusEmoji = o["statusEmoji"]?.jsonPrimitive?.contentOrNull ?: m.user.statusEmoji,
                                ))
                            }
                        }
                    }
                }
                "server.presence" -> {
                    val uid = ev.long("userId")
                    val online = ev.bool("online") ?: false
                    members.update { all -> all.mapValues { (_, list) -> list.map { if (it.user.id == uid) it.copy(online = online) else it } } }
                }
                "channel.message" -> {
                    val m = decode<ChannelMessage>(ev["message"])
                    putMessages(m.channelId, listOf(m))
                    m.author?.let { a -> typing.update { all -> all[m.channelId]?.let { t -> all + (m.channelId to (t - a.id)) } ?: all } }
                    val mine = m.author?.id == Bants.myId
                    val viewing = Bants.inForeground && activeChannel == m.channelId
                    val mentioned = ev.bool("mentioned") == true
                    if (mine || viewing) {
                        updateChannel(m.channelId) { it.copy(lastMessageId = m.id) }
                        if (viewing && !mine) markReadQuietly(m.channelId)
                    } else {
                        updateChannel(m.channelId) {
                            it.copy(lastMessageId = m.id, unread = true, mentionCount = it.mentionCount + if (mentioned) 1 else 0)
                        }
                        val muted = servers.value[m.serverId ?: 0]?.me?.muted == true
                        if (mentioned && Bants.inForeground && !muted) Sounds.play(Sounds.Kind.Mention, Bants.session.soundsOn)
                    }
                }
                "channel.message.update" -> {
                    val m = decode<ChannelMessage>(ev["message"])
                    if (messages.value.containsKey(m.channelId)) putMessages(m.channelId, listOf(m))
                }
                "channel.message.delete" -> removeMessage(ev.long("channelId"), ev.long("messageId"))
                "channel.reaction" -> {
                    val uid = ev.long("userId")
                    if (uid != Bants.myId) applyReaction(ev.long("channelId"), ev.long("messageId"), uid, ev.str("emoji") ?: return, ev.bool("added") ?: true)
                }
                "channel.typing" -> {
                    val cid = ev.long("channelId")
                    val uid = ev.long("userId")
                    typing.update { all -> all + (cid to (all[cid].orEmpty() + (uid to System.currentTimeMillis() + 8_000))) }
                }
                "channel.read" -> {
                    val cid = ev.long("channelId")
                    val upto = ev.long("lastRead")
                    updateChannel(cid) { if (upto >= it.lastMessageId) it.copy(unread = false, mentionCount = 0) else it }
                }
                "channel.thread.create", "channel.thread.update" -> {
                    val t = decode<ServerChannel>(ev["thread"])
                    threads.update { it + (t.id to t) }
                }
                "voice.state" -> {
                    val cid = ev.long("channelId")
                    val list = (ev["members"] as? JsonArray)?.map { decode<VoiceMember>(it) }.orEmpty()
                    updateChannel(cid) { it.copy(voiceMembers = list) }
                }
                "channel.thread.delete" -> {
                    val id = ev.long("threadId")
                    threads.update { it - id }
                    messages.update { it - id }
                }
            }
        } catch (e: Exception) {
            // Ignore events we can't understand rather than crashing.
        }
    }

    /** The socket already told the server via channel.read; just keep the local badge clear. */
    private fun markReadQuietly(channelId: Long) {
        Bants.realtime.send(buildJsonObject {
            put("type", "channel.read")
            put("channelId", channelId)
        })
    }

    private inline fun <reified T> decode(el: JsonElement?): T =
        api.json.decodeFromJsonElement(serializer<T>(), el ?: JsonNull)

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.long(key: String) = this[key]?.jsonPrimitive?.longOrNull ?: 0L
    private fun JsonObject.bool(key: String) = this[key]?.jsonPrimitive?.booleanOrNull
}
