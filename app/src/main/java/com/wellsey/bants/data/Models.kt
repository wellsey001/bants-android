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
)

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
    // Local-only state for messages still on their way to the server.
    @Transient val pending: Boolean = false,
    @Transient val failed: Boolean = false,
    @Transient val localUri: String? = null,
)

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
data class MessagesPage(val messages: List<Message> = emptyList(), val hasMore: Boolean = false)

@Serializable
data class MediaUpload(val id: String, val mime: String = "", val size: Long = 0, val filename: String = "")

@Serializable
data class ServerInfo(val name: String = "", val registration: String = "open", val maxUploadBytes: Long = 0)

@Serializable
data class AddContactResult(
    val status: String,
    val contact: User? = null,
    val conversation: Conversation? = null,
    val request: ContactRequest? = null,
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
    kind == "image" -> if (body.isNotBlank()) "📷 $body" else "📷 Photo"
    kind == "video" -> "🎥 Video"
    kind == "audio" -> "🎤 Voice note"
    kind == "file" -> "📎 File"
    else -> body
}
