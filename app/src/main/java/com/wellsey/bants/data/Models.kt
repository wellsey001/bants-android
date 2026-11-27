package com.wellsey.bants.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class User(
    val id: Long,
    val username: String,
    val displayName: String,
    val about: String = "",
    val avatar: String? = null,
    val online: Boolean? = null,
    val lastSeen: Long? = null,
    val friendCode: String? = null,
    val isAdmin: Boolean = false,
    // Discord-style profile bits
    val banner: String? = null,
    val accentColour: Long? = null,
    val pronouns: String = "",
    val customStatus: String = "",
    val statusEmoji: String = "",
    // Flair (Nitro-style, free for everyone)
    val themeColours: List<Long>? = null,
    val decoration: String? = null,
    val profileEffect: String? = null,
    val nameStyle: NameStyle? = null,
    /** Your own sounds for joining and leaving voice channels (only on your own profile). */
    val joinSound: String? = null,
    val leaveSound: String? = null,
)

/** A name in a chosen font, optionally in one colour or a two-colour gradient. */
@Serializable
data class NameStyle(val font: String = "default", val colours: List<Long> = emptyList())

@Serializable
data class Message(
    val id: Long,
    val conversationId: Long,
    val senderId: Long? = null,
    val clientId: String? = null,
    val kind: String = "text",
    val body: String = "",
    val media: String? = null,
    val replyTo: Long? = null,
    val createdAt: Long = 0,
    val editedAt: Long? = null,
    val deleted: Boolean = false,
    val reactions: List<Reaction> = emptyList(),
    /** Custom emojis used in the text or reactions, so they can be drawn. */
    val emojis: List<CustomEmoji> = emptyList(),
    val mediaInfo: MediaInfo? = null,
    val meta: MessageMeta? = null,
    val expiresAt: Long? = null,
    // Local-only state for messages still on their way to the server.
    @Transient val pending: Boolean = false,
    @Transient val failed: Boolean = false,
    @Transient val localUri: String? = null,
)

@Serializable
data class MessageMeta(
    val mentions: List<Long> = emptyList(),
    val preview: LinkPreview? = null,
    val forwarded: Boolean = false,
    val poll: Poll? = null,
    val location: Location? = null,
)

@Serializable
data class Poll(val question: String, val multi: Boolean = false, val options: List<PollOption> = emptyList())

@Serializable
data class PollOption(val id: Int, val text: String, val voterIds: List<Long> = emptyList())

@Serializable
data class Location(
    val lat: Double,
    val lng: Double,
    val accuracy: Double? = null,
    val label: String? = null,
    val liveUntil: Long? = null,
    val updatedAt: Long = 0,
) {
    val isLive: Boolean get() = (liveUntil ?: 0) > System.currentTimeMillis()
}

@Serializable
data class MessageInfo(val messageId: Long, val sentAt: Long = 0, val members: List<MemberReceipt> = emptyList())

@Serializable
data class MemberReceipt(
    val userId: Long,
    val delivered: Boolean = false,
    val read: Boolean = false,
    val deliveredAt: Long? = null,
    val readAt: Long? = null,
)

@Serializable
data class ForwardResult(val messages: List<Message> = emptyList())

@Serializable
data class LinkPreview(
    val url: String,
    val title: String = "",
    val description: String? = null,
    val siteName: String? = null,
    val image: String? = null,
)

@Serializable
data class Sticker(val id: Long, val media: String, val createdAt: Long = 0)

@Serializable
data class Gif(
    val id: String,
    val title: String = "",
    val preview: String,
    val url: String,
    val width: Int = 200,
    val height: Int = 200,
)

@Serializable
data class GifPage(val gifs: List<Gif> = emptyList(), val nextOffset: Int = 0)

@Serializable
data class SearchResults(val results: List<Message> = emptyList())

@Serializable
data class Reaction(val emoji: String, val userIds: List<Long> = emptyList(), val superIds: List<Long> = emptyList())

@Serializable
data class MediaInfo(val mime: String = "", val size: Long = 0, val filename: String = "")

@Serializable
data class Member(
    val user: User,
    val role: String = "member",
    val lastRead: Long = 0,
    val lastDelivered: Long = 0,
)

@Serializable
data class Conversation(
    val id: Long,
    val type: String,
    val name: String? = null,
    val avatar: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val muted: Boolean = false,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val disappearAfter: Long = 0,
    val streak: Int = 0,
    val unreadCount: Int = 0,
    val lastMessage: Message? = null,
    val members: List<Member> = emptyList(),
)

@Serializable
data class ContactRequest(val id: Long, val createdAt: Long = 0, val user: User)

@Serializable
data class Requests(
    val incoming: List<ContactRequest> = emptyList(),
    val outgoing: List<ContactRequest> = emptyList(),
)

@Serializable
data class AuthResponse(val token: String, val user: User)

@Serializable
data class MessagesPage(val messages: List<Message> = emptyList(), val hasMore: Boolean = false, val removed: List<Long> = emptyList())

@Serializable
data class MediaUpload(val id: String, val mime: String = "", val size: Long = 0, val filename: String = "")

@Serializable
data class ServerInfo(
    val name: String = "",
    val registration: String = "open",
    val maxUploadBytes: Long = 0,
    val gifs: Boolean = false,
    /** bants-server supports servers (Discord-style communities). */
    val servers: Boolean = false,
)

@Serializable
data class AddContactResult(
    val status: String,
    val contact: User? = null,
    val conversation: Conversation? = null,
    val request: ContactRequest? = null,
)

/** A message waiting to be sent. Kept on disk so it survives the app closing. */
@Serializable
data class OutboxItem(
    val tempId: Long,
    val conversationId: Long,
    val clientId: String,
    val kind: String,
    val body: String = "",
    val file: String? = null,
    val mime: String? = null,
    val filename: String? = null,
    val mediaId: String? = null,
    val gifUrl: String? = null,
    val gifPreview: String? = null,
    val replyTo: Long? = null,
    val createdAt: Long,
    val failed: Boolean = false,
    val error: String? = null,
)

fun OutboxItem.toMessage(myId: Long) = Message(
    id = tempId,
    conversationId = conversationId,
    senderId = myId,
    clientId = clientId,
    kind = if (kind == "gif") "image" else kind,
    media = if (file == null) mediaId else null,
    body = body,
    replyTo = replyTo,
    createdAt = createdAt,
    mediaInfo = if (file != null) MediaInfo(mime ?: "", java.io.File(file).length(), filename ?: "") else null,
    pending = !failed,
    failed = failed,
    localUri = file?.let { "file://$it" } ?: gifPreview,
)

/** What's saved on the phone so chats open instantly and work offline. */
@Serializable
data class CacheSnapshot(
    val me: User? = null,
    val conversations: List<Conversation> = emptyList(),
    val contacts: List<User> = emptyList(),
    val requests: Requests = Requests(),
    val messages: Map<Long, List<Message>> = emptyMap(),
    val hasMore: Map<Long, Boolean> = emptyMap(),
)

// ───────────── helpers ─────────────

val Conversation.isGroup: Boolean get() = type == "group"

fun Conversation.peer(myId: Long): User? =
    if (isGroup) null else members.firstOrNull { it.user.id != myId }?.user

fun Conversation.title(myId: Long): String =
    if (isGroup) name ?: "Group" else peer(myId)?.displayName ?: "Chat"

fun Conversation.avatarMedia(myId: Long): String? = if (isGroup) avatar else peer(myId)?.avatar

fun Message.preview(): String = when {
    deleted -> "Message deleted"
    kind == "image" && mediaInfo?.mime != "image/gif" -> if (body.isNotBlank()) "📷 $body" else "📷 Photo"
    kind == "video" -> "🎥 Video"
    kind == "audio" -> "🎤 Voice note"
    kind == "file" -> "📎 " + (mediaInfo?.filename?.ifBlank { null } ?: "File")
    kind == "sticker" -> "Sticker"
    kind == "image" && mediaInfo?.mime == "image/gif" -> "GIF"
    kind == "poll" -> "📊 " + (meta?.poll?.question ?: body)
    kind == "location" -> if (meta?.location?.liveUntil != null) "📍 Live location" else "📍 Location"
    else -> body
}
