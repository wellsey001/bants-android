package com.wellsey.bants.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.wellsey.bants.Notifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * App-wide state and actions. Screens read the StateFlows and call the functions;
 * live updates from the WebSocket are merged in here.
 */
object Bants {
    lateinit var appContext: Context
        private set
    lateinit var session: Session
        private set
    lateinit var api: Api
        private set
    lateinit var realtime: Realtime
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val loggedIn = MutableStateFlow(false)
    val me = MutableStateFlow<User?>(null)
    val conversations = MutableStateFlow<Map<Long, Conversation>>(emptyMap())
    val messages = MutableStateFlow<Map<Long, List<Message>>>(emptyMap())
    val hasMore = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val contacts = MutableStateFlow<List<User>>(emptyList())
    val requests = MutableStateFlow(Requests())
    val typing = MutableStateFlow<Map<Long, Set<Long>>>(emptyMap())
    val connected: StateFlow<Boolean> get() = realtime.connected

    @Volatile var activeConversation: Long? = null
    @Volatile var inForeground = false

    val myId: Long get() = me.value?.id ?: -1L

    private val typingTimers = mutableMapOf<Pair<Long, Long>, Job>()
    private var lastTypingSent = 0L
    private val loadingOlder = mutableSetOf<Long>()

    fun init(context: Context) {
        appContext = context.applicationContext
        session = Session(appContext)
        api = Api(session)
        realtime = Realtime(api, session, scope)
        me.value = session.user
        scope.launch { realtime.events.collect { handle(it) } }
        if (session.token != null) {
            loggedIn.value = true
            startSession()
        }
    }

    private fun startSession() {
        realtime.start()
        scope.launch { runCatching { refreshAll() } }
    }

    fun onForeground() {
        inForeground = true
        if (loggedIn.value) realtime.reconnectNow()
        activeConversation?.let { markRead(it) }
    }

    /** Runs an API call and signs out locally if the server says the session is gone. */
    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: ApiException) {
        if (e.status == 401 && loggedIn.value) signOutLocal()
        throw e
    }

    // ───────────── account ─────────────

    suspend fun serverInfo(url: String): ServerInfo {
        session.serverUrl = url
        return api.get<ServerInfo>("/api/server")
    }

    suspend fun login(username: String, password: String) {
        val r = api.post<AuthResponse>("/api/auth/login", buildJsonObject {
            put("username", username.trim())
            put("password", password)
            put("deviceName", deviceName())
        })
        onAuthed(r)
    }

    suspend fun register(username: String, password: String, displayName: String, inviteCode: String) {
        val r = api.post<AuthResponse>("/api/auth/register", buildJsonObject {
            put("username", username.trim())
            put("password", password)
            if (displayName.isNotBlank()) put("displayName", displayName.trim())
            if (inviteCode.isNotBlank()) put("inviteCode", inviteCode.trim())
            put("deviceName", deviceName())
        })
        onAuthed(r)
    }

    private fun onAuthed(r: AuthResponse) {
        session.token = r.token
        session.user = r.user
        me.value = r.user
        loggedIn.value = true
        startSession()
    }

    fun logout() {
        scope.launch {
            runCatching { api.post<JsonObject>("/api/auth/logout") }
            signOutLocal()
        }
    }

    fun signOutLocal() {
        realtime.stop()
        session.token = null
        session.user = null
        me.value = null
        conversations.value = emptyMap()
        messages.value = emptyMap()
        hasMore.value = emptyMap()
        contacts.value = emptyList()
        requests.value = Requests()
        typing.value = emptyMap()
        loggedIn.value = false
        Notifications.cancelAll(appContext)
    }

    private fun deviceName() = "${Build.MANUFACTURER} ${Build.MODEL}"

    suspend fun updateProfile(displayName: String, about: String) {
        val u = call {
            api.patch<User>("/api/me", buildJsonObject {
                put("displayName", displayName.trim())
                put("about", about.trim())
            })
        }
        setMe(u)
    }

    suspend fun uploadAvatar(uri: Uri) {
        val (bytes, mime) = withContext(Dispatchers.IO) { compressImage(uri, maxSide = 640) }
        val media = upload(bytes, mime, "avatar.jpg")
        val u = call { api.patch<User>("/api/me", buildJsonObject { put("avatar", media.id) }) }
        setMe(u)
    }

    suspend fun resetFriendCode() {
        setMe(call { api.post<User>("/api/me/friend-code/reset") })
    }

    private fun setMe(u: User) {
        me.value = u
        session.user = u
    }

    // ───────────── loading ─────────────

    suspend fun refreshAll() = coroutineScope {
        launch { refreshMe() }
        launch { refreshConversations() }
        launch { refreshContacts() }
        launch { refreshRequests() }
    }

    suspend fun refreshMe() = setMe(call { api.get<User>("/api/me") })

    suspend fun refreshConversations() {
        val list = call { api.get<List<Conversation>>("/api/conversations") }
        conversations.value = list.associateBy { it.id }
    }

    suspend fun refreshContacts() {
        contacts.value = call { api.get<List<User>>("/api/contacts") }
    }

    suspend fun refreshRequests() {
        requests.value = call { api.get<Requests>("/api/contacts/requests") }
    }

    private fun realMessages(list: List<Message>) = list.filter { !it.pending && !it.failed && it.id > 0 }

    /** Merges messages into a chat, replacing any pending copies with the same clientId. */
    private fun putMessages(convId: Long, incoming: List<Message>) {
        if (incoming.isEmpty() && messages.value.containsKey(convId)) return
        messages.update { all ->
            val clientIds = incoming.mapNotNull { it.clientId }.toSet()
            val byId = LinkedHashMap<Long, Message>()
            all[convId].orEmpty()
                .filterNot { (it.pending || it.failed) && it.clientId != null && it.clientId in clientIds }
                .forEach { byId[it.id] = it }
            incoming.forEach { byId[it.id] = it }
            val sorted = byId.values.sortedWith(
                compareBy<Message> { it.pending || it.failed }.thenBy { if (it.id < 0) -it.id else it.id },
            )
            all + (convId to sorted)
        }
    }

    private fun replaceLocal(convId: Long, m: Message) {
        messages.update { all ->
            val list = all[convId] ?: return@update all
            all + (convId to list.map { if (it.id == m.id) m else it })
        }
    }

    suspend fun loadMessages(convId: Long) {
        val lastReal = realMessages(messages.value[convId].orEmpty()).maxOfOrNull { it.id }
        if (lastReal == null) {
            val page = call { api.get<MessagesPage>("/api/conversations/$convId/messages?limit=50") }
            putMessages(convId, page.messages)
            hasMore.update { it + (convId to page.hasMore) }
        } else {
            var after = lastReal
            while (true) {
                val page = call { api.get<MessagesPage>("/api/conversations/$convId/messages?after=$after&limit=200") }
                putMessages(convId, page.messages)
                if (!page.hasMore || page.messages.isEmpty()) break
                after = page.messages.last().id
            }
        }
    }

    suspend fun loadOlder(convId: Long) {
        if (hasMore.value[convId] != true || !loadingOlder.add(convId)) return
        try {
            val first = realMessages(messages.value[convId].orEmpty()).minOfOrNull { it.id } ?: return
            val page = call { api.get<MessagesPage>("/api/conversations/$convId/messages?before=$first&limit=50") }
            putMessages(convId, page.messages)
            hasMore.update { it + (convId to page.hasMore) }
        } finally {
            loadingOlder.remove(convId)
        }
    }

    // ───────────── sending ─────────────

    private fun newPending(convId: Long, kind: String, body: String, localUri: String?) = Message(
        id = -System.nanoTime(),
        conversationId = convId,
        senderId = myId,
        clientId = UUID.randomUUID().toString(),
        kind = kind,
        body = body,
        createdAt = System.currentTimeMillis(),
        pending = true,
        localUri = localUri,
    )

    fun sendText(convId: Long, text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        val temp = newPending(convId, "text", body, null)
        putMessages(convId, listOf(temp))
        deliver(temp)
    }

    fun sendImage(convId: Long, uri: Uri) {
        val temp = newPending(convId, "image", "", uri.toString())
        putMessages(convId, listOf(temp))
        deliver(temp)
    }

    fun retry(m: Message) {
        val again = m.copy(pending = true, failed = false)
        replaceLocal(m.conversationId, again)
        deliver(again)
    }

    private fun deliver(temp: Message) {
        scope.launch {
            try {
                var mediaId: String? = null
                if (temp.localUri != null) {
                    val (bytes, mime) = withContext(Dispatchers.IO) { compressImage(Uri.parse(temp.localUri)) }
                    mediaId = upload(bytes, mime, "photo.jpg").id
                }
                val sent = call {
                    api.post<Message>("/api/conversations/${temp.conversationId}/messages", buildJsonObject {
                        put("clientId", temp.clientId)
                        if (temp.body.isNotEmpty()) put("body", temp.body)
                        if (mediaId != null) put("media", mediaId)
                    })
                }
                putMessages(temp.conversationId, listOf(sent))
                onNewMessage(sent, notify = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                replaceLocal(temp.conversationId, temp.copy(pending = false, failed = true))
            }
        }
    }

    suspend fun upload(bytes: ByteArray, mime: String, filename: String): MediaUpload = call {
        val text = api.raw("POST", "/api/media", bytes.toRequestBody(mime.toMediaType()), mapOf("X-Filename" to filename))
        api.json.decodeFromString(MediaUpload.serializer(), text)
    }

    suspend fun editMessage(m: Message, body: String) {
        val updated = call { api.patch<Message>("/api/messages/${m.id}", buildJsonObject { put("body", body.trim()) }) }
        putMessages(updated.conversationId, listOf(updated))
    }

    suspend fun deleteMessage(m: Message) {
        if (m.failed) {
            messages.update { all -> all + (m.conversationId to all[m.conversationId].orEmpty().filter { it.id != m.id }) }
            return
        }
        val updated = call { api.delete<Message>("/api/messages/${m.id}") }
        putMessages(updated.conversationId, listOf(updated))
    }

    fun markRead(convId: Long) {
        val lastId = realMessages(messages.value[convId].orEmpty()).maxOfOrNull { it.id } ?: return
        val conv = conversations.value[convId] ?: return
        val mine = conv.members.firstOrNull { it.user.id == myId }
        if (conv.unreadCount == 0 && (mine?.lastRead ?: 0) >= lastId) return
        conversations.update { all ->
            all + (convId to conv.copy(
                unreadCount = 0,
                members = conv.members.map {
                    if (it.user.id == myId) it.copy(lastRead = lastId, lastDelivered = maxOf(it.lastDelivered, lastId)) else it
                },
            ))
        }
        val sent = realtime.send(buildJsonObject {
            put("type", "receipt")
            put("conversationId", convId)
            put("read", lastId)
        })
        if (!sent) scope.launch {
            runCatching { api.post<JsonObject>("/api/conversations/$convId/receipts", buildJsonObject { put("read", lastId) }) }
        }
        Notifications.cancel(appContext, convId)
    }

    fun sendTyping(convId: Long) {
        val now = System.currentTimeMillis()
        if (now - lastTypingSent < 3_000) return
        lastTypingSent = now
        realtime.send(buildJsonObject {
            put("type", "typing")
            put("conversationId", convId)
            put("typing", true)
        })
    }

    // ───────────── contacts & chats ─────────────

    suspend fun addContact(username: String? = null, friendCode: String? = null): AddContactResult {
        val r = call {
            api.post<AddContactResult>("/api/contacts/requests", buildJsonObject {
                if (username != null) put("username", username.trim().removePrefix("@"))
                if (friendCode != null) put("friendCode", friendCode.trim())
            })
        }
        r.conversation?.let { c -> conversations.update { it + (c.id to c) } }
        runCatching { refreshContacts() }
        runCatching { refreshRequests() }
        return r
    }

    suspend fun acceptRequest(id: Long): AddContactResult {
        val r = call { api.post<AddContactResult>("/api/contacts/requests/$id/accept") }
        r.conversation?.let { c -> conversations.update { it + (c.id to c) } }
        runCatching { refreshContacts() }
        runCatching { refreshRequests() }
        return r
    }

    suspend fun declineRequest(id: Long) {
        call { api.post<JsonObject>("/api/contacts/requests/$id/decline") }
        runCatching { refreshRequests() }
    }

    suspend fun startDirect(userId: Long): Long {
        val c = call {
            api.post<Conversation>("/api/conversations", buildJsonObject {
                put("type", "direct")
                put("userId", userId)
            })
        }
        conversations.update { it + (c.id to c) }
        return c.id
    }

    suspend fun createGroup(name: String, memberIds: List<Long>): Long {
        val c = call {
            api.post<Conversation>("/api/conversations", buildJsonObject {
                put("type", "group")
                put("name", name.trim())
                put("memberIds", JsonArray(memberIds.map { JsonPrimitive(it) }))
            })
        }
        conversations.update { it + (c.id to c) }
        return c.id
    }

    suspend fun setMuted(convId: Long, muted: Boolean) {
        val c = call { api.patch<Conversation>("/api/conversations/$convId", buildJsonObject { put("muted", muted) }) }
        conversations.update { it + (c.id to c) }
    }

    suspend fun leaveGroup(convId: Long) {
        call { api.delete<JsonObject>("/api/conversations/$convId/members/$myId") }
        conversations.update { it - convId }
        messages.update { it - convId }
    }

    suspend fun block(userId: Long) {
        call { api.post<JsonObject>("/api/blocks/$userId") }
        runCatching { refreshContacts() }
    }

    // ───────────── live events ─────────────

    private fun handle(ev: JsonObject) {
        val type = ev.str("type") ?: return
        try {
            when (type) {
                "ready" -> scope.launch {
                    runCatching { refreshAll() }
                    messages.value.keys.forEach { id -> runCatching { loadMessages(id) } }
                }
                "message.new" -> {
                    val m = decode<Message>(ev["message"])
                    putMessages(m.conversationId, listOf(m))
                    m.senderId?.let { setTyping(m.conversationId, it, false) }
                    onNewMessage(m, notify = true)
                }
                "message.updated" -> {
                    val m = decode<Message>(ev["message"])
                    putMessages(m.conversationId, listOf(m))
                    conversations.update { all ->
                        val c = all[m.conversationId]
                        if (c != null && c.lastMessage?.id == m.id) all + (c.id to c.copy(lastMessage = m)) else all
                    }
                }
                "receipt" -> {
                    val convId = ev.long("conversationId")
                    val userId = ev.long("userId")
                    val read = ev.long("lastRead")
                    val delivered = ev.long("lastDelivered")
                    conversations.update { all ->
                        val c = all[convId] ?: return@update all
                        all + (convId to c.copy(members = c.members.map {
                            if (it.user.id == userId) it.copy(lastRead = read, lastDelivered = delivered) else it
                        }))
                    }
                }
                "typing" -> setTyping(ev.long("conversationId"), ev.long("userId"), ev.bool("typing") ?: true)
                "presence" -> {
                    val online = ev.bool("online") ?: false
                    val lastSeen = ev["lastSeen"]?.jsonPrimitive?.longOrNull
                    updateUser(ev.long("userId")) { it.copy(online = online, lastSeen = lastSeen ?: it.lastSeen) }
                }
                "user.updated" -> {
                    val u = decode<User>(ev["user"])
                    updateUser(u.id) { old -> u.copy(online = u.online ?: old.online, lastSeen = u.lastSeen ?: old.lastSeen, friendCode = old.friendCode) }
                }
                "conversation.updated" -> {
                    val c = decode<Conversation>(ev["conversation"])
                    conversations.update { it + (c.id to c) }
                }
                "conversation.removed" -> {
                    val id = ev.long("conversationId")
                    conversations.update { it - id }
                }
                "contact.request", "contact.request.removed" -> scope.launch { runCatching { refreshRequests() } }
                "contact.added" -> {
                    val c = decode<Conversation>(ev["conversation"])
                    conversations.update { it + (c.id to c) }
                    scope.launch {
                        runCatching { refreshContacts() }
                        runCatching { refreshRequests() }
                    }
                }
                "auth.lost" -> signOutLocal()
            }
        } catch (e: Exception) {
            // Ignore events we can't understand rather than crashing.
        }
    }

    private fun onNewMessage(m: Message, notify: Boolean) {
        val mine = m.senderId == myId
        val viewing = inForeground && activeConversation == m.conversationId
        val conv = conversations.value[m.conversationId]
        if (conv == null) {
            scope.launch { runCatching { refreshConversations() } }
        } else {
            conversations.update { all ->
                val c = all[m.conversationId] ?: return@update all
                val isNewer = (c.lastMessage?.id ?: 0) < m.id
                all + (c.id to c.copy(
                    lastMessage = if (isNewer || c.lastMessage?.id == m.id) m else c.lastMessage,
                    updatedAt = maxOf(c.updatedAt, m.createdAt),
                    unreadCount = if (!mine && !viewing && isNewer) c.unreadCount + 1 else c.unreadCount,
                ))
            }
        }
        if (mine || m.kind == "system") return
        realtime.send(buildJsonObject {
            put("type", "receipt")
            put("conversationId", m.conversationId)
            put("delivered", m.id)
        })
        if (viewing) {
            markRead(m.conversationId)
        } else if (notify && conv?.muted != true) {
            val c = conversations.value[m.conversationId]
            val sender = c?.members?.firstOrNull { it.user.id == m.senderId }?.user
            Notifications.showMessage(appContext, c, m, sender, myId)
        }
    }

    private fun setTyping(convId: Long, userId: Long, on: Boolean) {
        if (userId == myId) return
        val key = convId to userId
        typingTimers.remove(key)?.cancel()
        typing.update { all ->
            val set = all[convId].orEmpty().let { if (on) it + userId else it - userId }
            all + (convId to set)
        }
        if (on) typingTimers[key] = scope.launch {
            delay(6_000)
            setTyping(convId, userId, false)
        }
    }

    private fun updateUser(id: Long, change: (User) -> User) {
        contacts.update { list -> list.map { if (it.id == id) change(it) else it } }
        conversations.update { all ->
            all.mapValues { (_, c) ->
                if (c.members.none { it.user.id == id }) c
                else c.copy(members = c.members.map { if (it.user.id == id) it.copy(user = change(it.user)) else it })
            }
        }
        me.value?.let { if (it.id == id) setMe(change(it)) }
    }

    private inline fun <reified T> decode(el: JsonElement?): T =
        api.json.decodeFromJsonElement(kotlinx.serialization.serializer<T>(), el ?: JsonNull)

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.long(key: String) = this[key]?.jsonPrimitive?.longOrNull ?: 0L
    private fun JsonObject.bool(key: String) = this[key]?.jsonPrimitive?.booleanOrNull

    // ───────────── images ─────────────

    /** Shrinks a photo to a sensible size, fixes its rotation and re-encodes it as JPEG. */
    private fun compressImage(uri: Uri, maxSide: Int = 2048): Pair<ByteArray, String> {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        var bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw ApiException(0, "Couldn't read that image")

        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest > maxSide) {
            val scale = maxSide.toFloat() / longest
            bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        }
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f
        if (rotation != 0f) {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray() to "image/jpeg"
    }
}
