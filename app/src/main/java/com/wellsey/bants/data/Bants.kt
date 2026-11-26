package com.wellsey.bants.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import androidx.exifinterface.media.ExifInterface
import com.wellsey.bants.Notifications
import com.wellsey.bants.Sounds
import com.wellsey.bants.calls.CallManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.merge
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
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * App-wide state and actions. Screens read the StateFlows and call the functions;
 * live updates from the WebSocket are merged in here. Chats are saved on the phone,
 * and unsent messages wait in an outbox until there's a connection.
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
    private lateinit var store: Store

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val loggedIn = MutableStateFlow(false)
    val me = MutableStateFlow<User?>(null)
    val conversations = MutableStateFlow<Map<Long, Conversation>>(emptyMap())
    val messages = MutableStateFlow<Map<Long, List<Message>>>(emptyMap())
    val hasMore = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val contacts = MutableStateFlow<List<User>>(emptyList())
    val requests = MutableStateFlow(Requests())
    val typing = MutableStateFlow<Map<Long, Set<Long>>>(emptyMap())
    val outbox = MutableStateFlow<List<OutboxItem>>(emptyList())
    val stickers = MutableStateFlow<List<Sticker>>(emptyList())
    val gifsAvailable = MutableStateFlow(false)

    /** How far each outgoing photo/video/file has uploaded (0–1), by client id. */
    val uploadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val connected: StateFlow<Boolean> get() = realtime.connected

    /** Short messages for the user (shown as toasts). */
    val notices = MutableSharedFlow<String>(extraBufferCapacity = 8)

    @Volatile var activeConversation: Long? = null
    @Volatile var inForeground = false
    var maxUploadBytes = 50L * 1024 * 1024
        private set

    val myId: Long get() = me.value?.id ?: -1L

    private val typingTimers = mutableMapOf<Pair<Long, Long>, Job>()
    private var lastTypingSent = 0L
    private val loadingOlder = mutableSetOf<Long>()
    private var saveJob: Job? = null
    private var outboxJob: Job? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        session = Session(appContext)
        api = Api(session)
        realtime = Realtime(api, session, scope)
        store = Store(appContext)
        me.value = session.user

        if (session.token != null) {
            store.loadCache()?.let { c ->
                if (c.me != null) me.value = c.me
                conversations.value = c.conversations.associateBy { it.id }
                contacts.value = c.contacts
                requests.value = c.requests
                messages.value = c.messages
                hasMore.value = c.hasMore
            }
            outbox.value = store.loadOutbox().map { it.copy(failed = it.failed) }
        }

        scope.launch { realtime.events.collect { handle(it) } }
        scope.launch { realtime.connected.collect { if (it) kickOutbox() } }
        scope.launch {
            merge(me, conversations, contacts, requests, messages).collect { if (loggedIn.value) scheduleSave() }
        }
        scope.launch {
            outbox.collect { items ->
                if (loggedIn.value) withContext(Dispatchers.IO) { runCatching { store.saveOutbox(items) } }
            }
        }

        if (session.token != null) {
            loggedIn.value = true
            startSession()
        }
        scope.launch {
            while (true) {
                delay(30_000)
                pruneExpired()
            }
        }
    }

    /** Drops disappearing messages whose time is up, even if the server hasn't told us yet. */
    private fun pruneExpired() {
        val now = System.currentTimeMillis()
        if (messages.value.values.none { list -> list.any { (it.expiresAt ?: Long.MAX_VALUE) <= now } }) return
        messages.update { all -> all.mapValues { (_, list) -> list.filter { (it.expiresAt ?: Long.MAX_VALUE) > now } } }
    }

    private fun startSession() {
        realtime.start()
        scope.launch { runCatching { refreshAll() } }
        kickOutbox()
        setUpPush()
    }

    // ───────────── push notifications ─────────────

    /** True when the app was built with a Firebase config (app/google-services.json). */
    val pushAvailable: Boolean
        get() = runCatching { FirebaseApp.getApps(appContext).isNotEmpty() }.getOrDefault(false)

    private fun setUpPush() {
        if (!pushAvailable) return
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> registerPushToken(token) }
        }
    }

    /** Tells the server where to send pushes for this phone. */
    fun registerPushToken(token: String) {
        if (session.token == null) return
        scope.launch {
            runCatching { api.post<JsonObject>("/api/me/push-token", buildJsonObject { put("token", token) }) }
        }
    }

    /** A push arrived while the app was in the background: fetch it so the chat is ready. */
    fun onPushMessage() {
        if (!loggedIn.value) return
        realtime.reconnectNow()
        if (!inForeground) pauseSoon()
    }

    private var pauseJob: kotlinx.coroutines.Job? = null

    /** Shortly after leaving the app, drop the live connection so pushes take over. */
    fun onBackground() {
        inForeground = false
        pauseSoon()
    }

    private fun pauseSoon() {
        pauseJob?.cancel()
        pauseJob = scope.launch {
            kotlinx.coroutines.delay(15_000)
            // Stay connected during a call: the call needs the connection for signalling.
            if (!inForeground && !com.wellsey.bants.calls.CallManager.inCall) realtime.pause()
        }
    }

    fun onForeground() {
        pauseJob?.cancel()
        inForeground = true
        scope.launch { runCatching { com.wellsey.bants.Updater.check(appContext) } }
        if (loggedIn.value) {
            realtime.reconnectNow()
            kickOutbox()
        }
        activeConversation?.let { markRead(it) }
    }

    /** Runs an API call and signs out locally if the server says the session is gone. */
    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: ApiException) {
        if (e.status == 401 && loggedIn.value) signOutLocal()
        throw e
    }

    // ───────────── saving on the phone ─────────────

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1_500)
            val snap = snapshot()
            withContext(Dispatchers.IO) { runCatching { store.saveCache(snap) } }
        }
    }

    private fun snapshot(): CacheSnapshot {
        val keep = 150
        val more = hasMore.value.toMutableMap()
        val trimmed = messages.value.mapValues { (id, list) ->
            if (list.size > keep) {
                more[id] = true
                list.takeLast(keep)
            } else list
        }
        return CacheSnapshot(me.value, conversations.value.values.toList(), contacts.value, requests.value, trimmed, more)
    }

    // ───────────── account ─────────────

    suspend fun serverInfo(url: String): ServerInfo {
        session.serverUrl = url
        val info = api.get<ServerInfo>("/api/server")
        if (info.maxUploadBytes > 0) maxUploadBytes = info.maxUploadBytes
        gifsAvailable.value = info.gifs
        return info
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
        loggedIn.value = false
        saveJob?.cancel()
        outboxJob?.cancel()
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
        outbox.value = emptyList()
        stickers.value = emptyList()
        store.clear()
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
        val media = uploadPhoto(uri)
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
        launch { runCatching { serverInfo(session.serverUrl) } }
        launch { runCatching { loadStickers() } }
    }

    suspend fun refreshMe() = setMe(call { api.get<User>("/api/me") })

    suspend fun refreshConversations() {
        val list = call { api.get<List<Conversation>>("/api/conversations") }
        conversations.value = list.associateBy { it.id }
        // Forget saved messages for chats we're no longer in.
        val ids = list.map { it.id }.toSet()
        if (messages.value.keys.any { it !in ids }) messages.update { all -> all.filterKeys { it in ids } }
    }

    suspend fun refreshConversation(convId: Long) {
        val c = call { api.get<Conversation>("/api/conversations/$convId") }
        conversations.update { it + (c.id to c) }
    }

    suspend fun refreshContacts() {
        contacts.value = call { api.get<List<User>>("/api/contacts") }
    }

    suspend fun refreshRequests() {
        requests.value = call { api.get<Requests>("/api/contacts/requests") }
    }

    /** Merges messages from the server into a chat and clears any outbox copies of them. */
    private fun putMessages(convId: Long, incoming: List<Message>) {
        if (incoming.isEmpty()) {
            if (!messages.value.containsKey(convId)) messages.update { it + (convId to emptyList()) }
            return
        }
        messages.update { all ->
            val byId = LinkedHashMap<Long, Message>()
            all[convId].orEmpty().forEach { byId[it.id] = it }
            incoming.forEach { byId[it.id] = it }
            all + (convId to byId.values.sortedBy { it.id })
        }
        val sentIds = incoming.mapNotNull { it.clientId }.toSet()
        if (outbox.value.any { it.clientId in sentIds }) {
            val done = outbox.value.filter { it.clientId in sentIds }
            outbox.update { list -> list.filterNot { it.clientId in sentIds } }
            done.forEach { item -> item.file?.let { File(it).delete() } }
        }
    }

    suspend fun loadMessages(convId: Long) {
        val lastReal = messages.value[convId]?.maxOfOrNull { it.id }
        if (lastReal == null) {
            val page = call { api.get<MessagesPage>("/api/conversations/$convId/messages?limit=50") }
            putMessages(convId, page.messages)
            hasMore.update { it + (convId to page.hasMore) }
        } else {
            var after = lastReal
            val knownFrom = messages.value[convId]?.filter { it.id > 0 }?.minOfOrNull { it.id } ?: lastReal
            var first = true
            while (true) {
                val extra = if (first) "&knownFrom=$knownFrom" else ""
                val page = call { api.get<MessagesPage>("/api/conversations/$convId/messages?after=$after&limit=200$extra") }
                first = false
                removeMessages(convId, page.removed.toSet())
                putMessages(convId, page.messages)
                if (!page.hasMore || page.messages.isEmpty()) break
                after = page.messages.last().id
            }
        }
    }

    suspend fun loadOlder(convId: Long) {
        if (hasMore.value[convId] != true || !loadingOlder.add(convId)) return
        try {
            val first = messages.value[convId]?.minOfOrNull { it.id } ?: return
            val page = call { api.get<MessagesPage>("/api/conversations/$convId/messages?before=$first&limit=50") }
            putMessages(convId, page.messages)
            hasMore.update { it + (convId to page.hasMore) }
        } finally {
            loadingOlder.remove(convId)
        }
    }

    // ───────────── sending (via the outbox) ─────────────

    private fun newItem(convId: Long, kind: String, replyTo: Long?) = OutboxItem(
        tempId = -System.nanoTime(),
        conversationId = convId,
        clientId = UUID.randomUUID().toString(),
        kind = kind,
        replyTo = replyTo,
        createdAt = System.currentTimeMillis(),
    )

    fun sendText(convId: Long, text: String, replyTo: Long? = null) {
        val body = text.trim()
        if (body.isEmpty()) return
        enqueue(newItem(convId, "text", replyTo).copy(body = body))
    }

    /** Photos are shrunk first; videos and other files are sent as they are. */
    fun sendMedia(convId: Long, uri: Uri, replyTo: Long? = null) {
        scope.launch {
            try {
                val item = withContext(Dispatchers.IO) { prepareMedia(convId, uri, replyTo) }
                if (item != null) enqueue(item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notices.tryEmit(e.message ?: "Couldn't attach that")
            }
        }
    }

    fun sendVoice(convId: Long, file: File, replyTo: Long? = null) {
        enqueue(newItem(convId, "audio", replyTo).copy(file = file.absolutePath, mime = "audio/mp4", filename = "Voice note.m4a"))
    }

    fun sendGif(convId: Long, gif: Gif, replyTo: Long? = null, caption: String = "") {
        enqueue(newItem(convId, "gif", replyTo).copy(gifUrl = gif.url, gifPreview = gif.preview, body = caption.trim()))
    }

    fun sendSticker(convId: Long, mediaId: String, replyTo: Long? = null) {
        enqueue(newItem(convId, "sticker", replyTo).copy(mediaId = mediaId))
    }

    fun newOutboxFile(extension: String): File = File(store.outboxDir, "${UUID.randomUUID()}.$extension")

    private suspend fun prepareMedia(convId: Long, uri: Uri, replyTo: Long?): OutboxItem? {
        val resolver = appContext.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        var name = "file"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        val isPhoto = mime.startsWith("image/") && mime != "image/gif"
        val isVideo = mime.startsWith("video/")
        val photoQ = com.wellsey.bants.media.MediaQuality.resolve(appContext, com.wellsey.bants.media.Quality.of(session.photoQuality))
        val videoQ = com.wellsey.bants.media.MediaQuality.resolve(appContext, com.wellsey.bants.media.Quality.of(session.videoQuality))
        val photoSide = if (isPhoto) com.wellsey.bants.media.MediaQuality.photoMaxSide(photoQ) else null
        val videoSide = if (isVideo) com.wellsey.bants.media.MediaQuality.videoShortSide(videoQ) else null
        if (photoSide == null && videoSide == null && size > maxUploadBytes) {
            notices.tryEmit("That's too big to send (max ${maxUploadBytes / (1024 * 1024)} MB)")
            return null
        }
        val kind = when {
            mime.startsWith("image/") -> "image"
            mime.startsWith("video/") -> "video"
            mime.startsWith("audio/") -> "audio"
            else -> "file"
        }
        val base = newItem(convId, kind, replyTo)
        if (photoSide != null) {
            val (bytes, jpeg) = compressImage(uri, maxSide = photoSide, quality = if (photoSide > 2048) 92 else 85)
            val f = newOutboxFile("jpg").apply { writeBytes(bytes) }
            return base.copy(file = f.absolutePath, mime = jpeg, filename = name.substringBeforeLast('.') + ".jpg")
        }
        if (videoSide != null) {
            notices.tryEmit("Getting your video ready…")
            val target = newOutboxFile("mp4")
            val shrunk = runCatching {
                com.wellsey.bants.media.MediaQuality.shrinkVideo(appContext, uri, videoSide, target)
            }.getOrNull()
            if (shrunk == null) target.delete()
            if (shrunk != null && shrunk.length() > 0 && (size <= 0 || shrunk.length() < size)) {
                if (shrunk.length() > maxUploadBytes) {
                    shrunk.delete()
                    notices.tryEmit("That's too big to send (max ${maxUploadBytes / (1024 * 1024)} MB)")
                    return null
                }
                return base.copy(file = shrunk.absolutePath, mime = "video/mp4", filename = name.substringBeforeLast('.') + ".mp4")
            }
            shrunk?.delete() // already small enough, or shrinking failed: send the original
        }
        return run {
            val ext = name.substringAfterLast('.', "bin").take(8)
            val f = newOutboxFile(ext)
            resolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
                ?: throw ApiException(0, "Couldn't read that file")
            if (f.length() > maxUploadBytes) {
                f.delete()
                notices.tryEmit("That's too big to send (max ${maxUploadBytes / (1024 * 1024)} MB)")
                return null
            }
            base.copy(file = f.absolutePath, mime = mime, filename = name)
        }
    }

    private fun enqueue(item: OutboxItem) {
        outbox.update { it + item }
        kickOutbox()
    }

    fun retry(m: Message) {
        outbox.update { list -> list.map { if (it.clientId == m.clientId) it.copy(failed = false, error = null) else it } }
        kickOutbox()
    }

    fun discard(m: Message) {
        val item = outbox.value.firstOrNull { it.clientId == m.clientId } ?: return
        outbox.update { list -> list.filterNot { it.clientId == m.clientId } }
        item.file?.let { File(it).delete() }
    }

    /** Sends whatever is waiting, one at a time, oldest first. Stops if we go offline. */
    fun kickOutbox() {
        if (!loggedIn.value || outboxJob?.isActive == true) return
        outboxJob = scope.launch {
            while (true) {
                val item = outbox.value.firstOrNull { !it.failed } ?: break
                if (!sendOne(item)) break
            }
        }
    }

    /** Returns false if we're offline and should wait for a connection. */
    private suspend fun sendOne(item: OutboxItem): Boolean {
        var current = item
        try {
            if (current.gifUrl != null) {
                val sent = call {
                    api.post<Message>("/api/conversations/${current.conversationId}/gif", buildJsonObject {
                        put("clientId", current.clientId)
                        put("url", current.gifUrl)
                        if (current.body.isNotBlank()) put("body", current.body)
                        current.replyTo?.let { put("replyTo", it) }
                    })
                }
                putMessages(sent.conversationId, listOf(sent))
                onNewMessage(sent, notify = false)
                if (inForeground) Sounds.play(Sounds.Kind.Sent, session.soundsOn)
                return true
            }
            if (current.file != null && current.mediaId == null) {
                val f = File(current.file!!)
                if (!f.exists()) throw ApiException(400, "The file isn't there any more")
                val clientId = current.clientId
                val body = ProgressBody(f.asRequestBody((current.mime ?: "application/octet-stream").toMediaType())) { p ->
                    uploadProgress.update { it + (clientId to p) }
                }
                val media = try {
                    upload(body, current.filename ?: f.name)
                } finally {
                    uploadProgress.update { it - clientId }
                }
                current = current.copy(mediaId = media.id)
                val updated = current
                outbox.update { list -> list.map { if (it.clientId == updated.clientId) updated else it } }
            }
            val sent = call {
                api.post<Message>("/api/conversations/${current.conversationId}/messages", buildJsonObject {
                    put("clientId", current.clientId)
                    if (current.body.isNotEmpty()) put("body", current.body)
                    current.mediaId?.let { put("media", it) }
                    if (current.kind == "sticker") put("kind", "sticker")
                    current.replyTo?.let { put("replyTo", it) }
                })
            }
            putMessages(sent.conversationId, listOf(sent))
            onNewMessage(sent, notify = false)
            if (inForeground) Sounds.play(Sounds.Kind.Sent, session.soundsOn)
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            if (e.status == 0 || e.status >= 500) return false
            markFailed(current, e.message)
            return true
        } catch (e: Exception) {
            markFailed(current, e.message)
            return true
        }
    }

    private fun markFailed(item: OutboxItem, error: String?) {
        outbox.update { list -> list.map { if (it.clientId == item.clientId) item.copy(failed = true, error = error) else it } }
    }

    private suspend fun upload(body: RequestBody, filename: String): MediaUpload = call {
        val text = api.raw("POST", "/api/media", body, mapOf("X-Filename" to Uri.encode(filename)))
        api.json.decodeFromString(MediaUpload.serializer(), text)
    }

    private suspend fun uploadPhoto(uri: Uri): MediaUpload {
        val (bytes, mime) = withContext(Dispatchers.IO) { compressImage(uri, maxSide = 640) }
        return upload(bytes.toRequestBody(mime.toMediaType()), "photo.jpg")
    }

    /** Downloads (once) and returns a local copy of a voice note, video or file. */
    suspend fun mediaFile(id: String, filename: String?): File = withContext(Dispatchers.IO) {
        val safe = (filename?.takeIf { it.isNotBlank() } ?: id).replace(Regex("[^A-Za-z0-9._ ()-]"), "_")
        val dir = File(appContext.cacheDir, "media/$id").apply { mkdirs() }
        val target = File(dir, safe)
        if (target.exists() && target.length() > 0) return@withContext target
        val request = Request.Builder().url("${session.serverUrl}/api/media/$id").build()
        val response = try {
            api.authedHttp.newCall(request).execute()
        } catch (e: IOException) {
            throw ApiException(0, "Can't reach the server")
        }
        response.use { res ->
            if (!res.isSuccessful) throw ApiException(res.code, "Couldn't download that")
            val tmp = File(dir, "$safe.part")
            res.body?.byteStream()?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(target)
        }
        target
    }

    suspend fun editMessage(m: Message, body: String) {
        val updated = call { api.patch<Message>("/api/messages/${m.id}", buildJsonObject { put("body", body.trim()) }) }
        putMessages(updated.conversationId, listOf(updated))
    }

    /** Unsend: takes the message off everyone's phone. Disappears here straight away. */
    suspend fun deleteMessage(m: Message) {
        if (m.pending || m.failed) {
            discard(m)
            return
        }
        removeMessages(m.conversationId, setOf(m.id))
        try {
            call { api.delete<JsonObject>("/api/messages/${m.id}") }
        } catch (e: Exception) {
            putMessages(m.conversationId, listOf(m)) // put it back
            throw e
        }
    }

    /** Drops unsent (or expired) messages from the cache, the chat list and any notification. */
    fun removeMessages(convId: Long, ids: Set<Long>) {
        if (ids.isEmpty()) return
        messages.update { all ->
            val list = all[convId] ?: return@update all
            all + (convId to list.filter { it.id !in ids })
        }
        if (::appContext.isInitialized) Notifications.cancelIfShowing(appContext, convId, ids)
        if (conversations.value[convId]?.lastMessage?.id?.let { it in ids } == true) {
            scope.launch { runCatching { refreshConversation(convId) } }
        }
    }

    /** Toggles your reaction. Shows straight away and corrects itself if the server disagrees. */
    suspend fun react(m: Message, emoji: String) {
        val mine = m.reactions.firstOrNull { myId in it.userIds }?.emoji
        val without = m.reactions.map { it.copy(userIds = it.userIds - myId) }.filter { it.userIds.isNotEmpty() }
        val updated = when {
            mine == emoji -> without
            without.any { it.emoji == emoji } -> without.map { if (it.emoji == emoji) it.copy(userIds = it.userIds + myId) else it }
            else -> without + Reaction(emoji, listOf(myId))
        }
        putMessages(m.conversationId, listOf(m.copy(reactions = updated)))
        try {
            val fromServer = call { api.post<Message>("/api/messages/${m.id}/react", buildJsonObject { put("emoji", emoji) }) }
            putMessages(m.conversationId, listOf(fromServer))
        } catch (e: Exception) {
            putMessages(m.conversationId, listOf(m))
            throw e
        }
    }

    fun markRead(convId: Long) {
        val lastId = messages.value[convId]?.maxOfOrNull { it.id } ?: return
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

    // ───────────── contacts ─────────────

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

    suspend fun block(userId: Long) {
        call { api.post<JsonObject>("/api/blocks/$userId") }
        runCatching { refreshContacts() }
    }

    // ───────────── chats & groups ─────────────

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

    private fun upsert(c: Conversation) = conversations.update { it + (c.id to c) }

    suspend fun setMuted(convId: Long, muted: Boolean) =
        upsert(call { api.patch<Conversation>("/api/conversations/$convId", buildJsonObject { put("muted", muted) }) })

    suspend fun renameGroup(convId: Long, name: String) =
        upsert(call { api.patch<Conversation>("/api/conversations/$convId", buildJsonObject { put("name", name.trim()) }) })

    suspend fun setGroupPhoto(convId: Long, uri: Uri) {
        val media = uploadPhoto(uri)
        upsert(call { api.patch<Conversation>("/api/conversations/$convId", buildJsonObject { put("avatar", media.id) }) })
    }

    suspend fun addMembers(convId: Long, userIds: List<Long>) = upsert(
        call {
            api.post<Conversation>("/api/conversations/$convId/members", buildJsonObject {
                put("userIds", JsonArray(userIds.map { JsonPrimitive(it) }))
            })
        },
    )

    suspend fun removeMember(convId: Long, userId: Long) {
        call { api.delete<JsonObject>("/api/conversations/$convId/members/$userId") }
        runCatching { refreshConversation(convId) }
    }

    suspend fun setAdmin(convId: Long, userId: Long, admin: Boolean) = upsert(
        call { api.post<Conversation>("/api/conversations/$convId/admins/$userId", buildJsonObject { put("admin", admin) }) },
    )

    suspend fun leaveGroup(convId: Long) {
        call { api.delete<JsonObject>("/api/conversations/$convId/members/$myId") }
        conversations.update { it - convId }
        messages.update { it - convId }
    }

    // ───────────── pins, archive, disappearing ─────────────

    suspend fun setPinned(convId: Long, pinned: Boolean) =
        upsert(call { api.patch<Conversation>("/api/conversations/$convId", buildJsonObject { put("pinned", pinned) }) })

    suspend fun setArchived(convId: Long, archived: Boolean) =
        upsert(call { api.patch<Conversation>("/api/conversations/$convId", buildJsonObject { put("archived", archived) }) })

    suspend fun setDisappearing(convId: Long, seconds: Long) =
        upsert(call { api.patch<Conversation>("/api/conversations/$convId", buildJsonObject { put("disappearAfter", seconds) }) })

    // ───────────── search ─────────────

    suspend fun search(text: String): List<Message> =
        call { api.get<SearchResults>("/api/search?q=${Uri.encode(text.trim())}") }.results

    /** Loads older pages until a message is on screen (for jumping to a search result). */
    suspend fun loadUntil(convId: Long, messageId: Long): Boolean {
        if (messages.value[convId] == null) loadMessages(convId)
        repeat(40) {
            if (messages.value[convId].orEmpty().any { it.id == messageId }) return true
            if (hasMore.value[convId] != true) return false
            loadOlder(convId)
        }
        return messages.value[convId].orEmpty().any { it.id == messageId }
    }

    // ───────────── GIFs & stickers ─────────────

    suspend fun findGifs(text: String, offset: Int): GifPage =
        call { api.get<GifPage>("/api/gifs?q=${Uri.encode(text.trim())}&offset=$offset") }

    suspend fun loadStickers() {
        stickers.value = call { api.get<List<Sticker>>("/api/stickers") }
    }

    /** Turns a photo into a square sticker and adds it to your collection. */
    suspend fun makeSticker(uri: Uri) {
        val bytes = withContext(Dispatchers.IO) { squareSticker(uri) }
        val media = upload(bytes.toRequestBody("image/webp".toMediaType()), "sticker.webp")
        saveSticker(media.id)
    }

    suspend fun saveSticker(mediaId: String) {
        val s = call { api.post<Sticker>("/api/stickers", buildJsonObject { put("media", mediaId) }) }
        stickers.update { list -> listOf(s) + list.filter { it.id != s.id } }
    }

    suspend fun deleteSticker(id: Long) {
        call { api.delete<JsonObject>("/api/stickers/$id") }
        stickers.update { list -> list.filter { it.id != id } }
    }

    private fun squareSticker(uri: Uri): ByteArray {
        val (jpeg, _) = compressImage(uri, maxSide = 1024)
        val src = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: throw ApiException(0, "Couldn't read that image")
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, 512, 512, true)
        val out = ByteArrayOutputStream()
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        scaled.compress(format, 88, out)
        return out.toByteArray()
    }

    // ───────────── live events ─────────────

    private fun handle(ev: JsonObject) {
        val type = ev.str("type") ?: return
        try {
            when (type) {
                "ready" -> scope.launch {
                    runCatching { refreshAll() }
                    messages.value.keys.forEach { id -> runCatching { loadMessages(id) } }
                    kickOutbox()
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
                "message.removed" -> {
                    val convId = ev.long("conversationId")
                    val ids = (ev["ids"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.longOrNull }?.toSet().orEmpty()
                    removeMessages(convId, ids)
                }
                "conversation.updated" -> upsert(decode<Conversation>(ev["conversation"]))
                "conversation.removed" -> {
                    val id = ev.long("conversationId")
                    conversations.update { it - id }
                }
                "contact.request" -> {
                    if (inForeground) Sounds.play(Sounds.Kind.Request, session.soundsOn)
                    scope.launch { runCatching { refreshRequests() } }
                }
                "contact.request.removed" -> scope.launch { runCatching { refreshRequests() } }
                "contact.added" -> {
                    upsert(decode<Conversation>(ev["conversation"]))
                    scope.launch {
                        runCatching { refreshContacts() }
                        runCatching { refreshRequests() }
                    }
                }
                "call.started", "call.incoming", "call.accepted", "call.joined", "call.signal", "call.ended" ->
                    CallManager.onEvent(type, ev)
                "error" -> if (ev.str("clientId") == "call") CallManager.onStartError(ev.str("error"))
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
                    archived = if (!mine && isNewer) false else c.archived,
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
        val mentioned = myId in m.meta?.mentions.orEmpty()
        if (viewing) {
            markRead(m.conversationId)
            Sounds.play(Sounds.Kind.Incoming, session.soundsOn)
        } else if (notify && (conv?.muted != true || mentioned)) {
            if (inForeground) {
                // App is open on another screen: a Bants noise instead of a system notification.
                Sounds.play(if (mentioned) Sounds.Kind.Mention else Sounds.Kind.Message, session.soundsOn)
            } else {
                val c = conversations.value[m.conversationId]
                val sender = c?.members?.firstOrNull { it.user.id == m.senderId }?.user
                Notifications.showMessage(appContext, c, m, sender, myId)
            }
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
        api.json.decodeFromJsonElement(serializer<T>(), el ?: JsonNull)

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.long(key: String) = this[key]?.jsonPrimitive?.longOrNull ?: 0L
    private fun JsonObject.bool(key: String) = this[key]?.jsonPrimitive?.booleanOrNull

    // ───────────── images ─────────────

    /** Shrinks a photo to a sensible size, fixes its rotation and re-encodes it as JPEG. */
    private fun compressImage(uri: Uri, maxSide: Int = 2048, quality: Int = 85): Pair<ByteArray, String> {
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
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray() to "image/jpeg"
    }
}
