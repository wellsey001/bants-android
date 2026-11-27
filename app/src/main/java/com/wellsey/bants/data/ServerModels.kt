package com.wellsey.bants.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

// Servers: Discord-style communities. These mirror what bants-server sends from src/servers.js.

@Serializable
data class ServerRole(
    val id: Long,
    val name: String,
    val colour: Long? = null,
    val position: Int = 0,
    val permissions: Long = 0,
    val hoist: Boolean = false,
    val mentionable: Boolean = false,
    val isDefault: Boolean = false,
)

@Serializable
data class ServerChannel(
    val id: Long,
    val serverId: Long,
    val type: String = "text", // text, voice, announcement, category, thread
    val parentId: Long? = null,
    val name: String = "",
    val topic: String = "",
    val position: Int = 0,
    val slowmode: Int = 0,
    val nsfw: Boolean = false,
    val syncPerms: Boolean = true,
    val lastMessageId: Long = 0,
    val unread: Boolean = false,
    val mentionCount: Int = 0,
    val permissions: Long = 0,
    val userLimit: Int? = null,
    val ownerId: Long? = null,
    val archived: Boolean = false,
    val locked: Boolean = false,
    val starterMessageId: Long? = null,
    /** Voice channels: who's in there right now. */
    val voiceMembers: List<VoiceMember> = emptyList(),
    /** Forum posts (threads): how many messages, the first one, and when it started. */
    val messageCount: Int = 0,
    val firstMessage: ChannelMessage? = null,
    /** Your notifications here: "all", "mentions", "none", or null for the server's default. */
    val notify: String? = null,
    /** Voice: how good voices sound in here (kbps), and whether it's a stage. */
    val bitrate: Int = 64,
    val stage: Boolean = false,
    val createdAt: Long = 0,
)

@Serializable
data class VoiceMember(val id: Long, val muted: Boolean = false, val deafened: Boolean = false, val video: Boolean = false, val screen: Boolean = false, val speaker: Boolean = false, val hand: Boolean = false)

@Serializable
data class ServerSound(val id: Long, val serverId: Long = 0, val name: String, val emoji: String = "🔊", val media: String, val volume: Float = 1f, val favourite: Boolean = false)

@Serializable
data class ServerMe(
    val nickname: String? = null,
    val muted: Boolean = false,
    val position: Int = 0,
    val timeoutUntil: Long? = null,
    val roleIds: List<Long> = emptyList(),
)

@Serializable
data class BantsServer(
    val id: Long,
    val name: String,
    val description: String = "",
    val icon: String? = null,
    val banner: String? = null,
    val ownerId: Long = 0,
    val systemChannelId: Long? = null,
    val createdAt: Long = 0,
    val memberCount: Int = 0,
    val permissions: Long = 0,
    val roles: List<ServerRole> = emptyList(),
    val channels: List<ServerChannel> = emptyList(),
    val me: ServerMe? = null,
    val unread: Boolean = false,
    val mentionCount: Int = 0,
    /** Emojis that belong to the server: everyone in it can use them. */
    val emojis: List<CustomEmoji> = emptyList(),
    val rules: String = "",
    val welcome: String = "",
    val requireRules: Boolean = false,
    val rulesAccepted: Boolean = true,
)

@Serializable
data class ChannelAuthor(
    val id: Long,
    val username: String,
    val displayName: String,
    val avatar: String? = null,
    val nickname: String? = null,
    /** Posted by a webhook (Unraid, a game server…), not a person. */
    val bot: Boolean = false,
    val decoration: String? = null,
    val nameStyle: NameStyle? = null,
)

@Serializable
data class ChannelPoll(
    val question: String,
    val options: List<String>,
    val multi: Boolean = false,
    val endsAt: Long? = null,
    val votes: List<List<Long>> = emptyList(),
)

@Serializable
data class ServerEvent(
    val id: Long,
    val serverId: Long,
    val channelId: Long? = null,
    val title: String,
    val description: String = "",
    val location: String = "",
    val startsAt: Long,
    val endsAt: Long? = null,
    val createdBy: Long? = null,
    val going: List<Long> = emptyList(),
    val interested: List<Long> = emptyList(),
    val myRsvp: String? = null,
)

@Serializable
data class Webhook(val id: Long, val channelId: Long, val name: String, val url: String, val createdAt: Long = 0)

@Serializable
data class ChannelAttachment(
    val id: String,
    val mime: String = "",
    val size: Long = 0,
    val filename: String = "",
    val spoiler: Boolean = false,
)

@Serializable
data class ChannelReaction(val emoji: String, val userIds: List<Long> = emptyList(), val superIds: List<Long> = emptyList())

@Serializable
data class ChannelMessage(
    val id: Long,
    val channelId: Long,
    val serverId: Long? = null,
    val clientId: String? = null,
    val kind: String = "default", // default, system, join, thread_created, pin
    val author: ChannelAuthor? = null,
    val body: String = "",
    val replyTo: Long? = null,
    val createdAt: Long = 0,
    val editedAt: Long? = null,
    val deleted: Boolean = false,
    val pinned: Boolean = false,
    val mentionsEveryone: Boolean = false,
    val reactions: List<ChannelReaction> = emptyList(),
    val attachments: List<ChannelAttachment> = emptyList(),
    /** Custom emojis used in the text or reactions, so anyone can draw them. */
    val emojis: List<CustomEmoji> = emptyList(),
    val poll: ChannelPoll? = null,
    val meta: kotlinx.serialization.json.JsonObject? = null,
    // Local-only state for messages still on their way to the server.
    @Transient val pending: Boolean = false,
    @Transient val failed: Boolean = false,
)

@Serializable
data class MemberUser(
    val id: Long,
    val username: String,
    val displayName: String,
    val avatar: String? = null,
    val about: String = "",
    val banner: String? = null,
    val accentColour: Long? = null,
    val pronouns: String = "",
    val customStatus: String = "",
    val statusEmoji: String = "",
    val themeColours: List<Long>? = null,
    val decoration: String? = null,
    val profileEffect: String? = null,
    val nameStyle: NameStyle? = null,
)

/** Someone's profile in one server. Empty bits fall back to their main profile. */
@Serializable
data class ServerProfile(
    val avatar: String? = null,
    val banner: String? = null,
    val bio: String = "",
    val pronouns: String = "",
)

@Serializable
data class ServerMember(
    val user: MemberUser,
    val serverProfile: ServerProfile = ServerProfile(),
    val nickname: String? = null,
    val roleIds: List<Long> = emptyList(),
    val joinedAt: Long = 0,
    val timeoutUntil: Long? = null,
    val online: Boolean = false,
)

@Serializable
data class InviteServer(val id: Long, val name: String, val icon: String? = null, val description: String = "")

@Serializable
data class InvitePreview(val code: String, val server: InviteServer, val memberCount: Int = 0, val onlineCount: Int = 0)

@Serializable
data class CreatedInvite(val code: String, val serverId: Long, val maxUses: Int = 0, val uses: Int = 0, val expiresAt: Long? = null)

/** Permission bits, the same as bants-server's src/permissions.js. */
object Perm {
    const val VIEW_CHANNEL = 1L shl 0
    const val SEND_MESSAGES = 1L shl 1
    const val READ_HISTORY = 1L shl 2
    const val ATTACH_FILES = 1L shl 3
    const val EMBED_LINKS = 1L shl 4
    const val ADD_REACTIONS = 1L shl 5
    const val USE_EXTERNAL_EMOJI = 1L shl 6
    const val MENTION_EVERYONE = 1L shl 7
    const val MANAGE_MESSAGES = 1L shl 8
    const val CREATE_THREADS = 1L shl 9
    const val SEND_IN_THREADS = 1L shl 10
    const val MANAGE_THREADS = 1L shl 11
    const val CONNECT = 1L shl 12
    const val SPEAK = 1L shl 13
    const val VIDEO = 1L shl 14
    const val MUTE_MEMBERS = 1L shl 15
    const val DEAFEN_MEMBERS = 1L shl 16
    const val MOVE_MEMBERS = 1L shl 17
    const val CREATE_INVITE = 1L shl 18
    const val CHANGE_NICKNAME = 1L shl 19
    const val MANAGE_NICKNAMES = 1L shl 20
    const val MANAGE_EMOJIS = 1L shl 21
    const val MANAGE_CHANNELS = 1L shl 22
    const val MANAGE_ROLES = 1L shl 23
    const val KICK_MEMBERS = 1L shl 24
    const val BAN_MEMBERS = 1L shl 25
    const val TIMEOUT_MEMBERS = 1L shl 26
    const val VIEW_AUDIT_LOG = 1L shl 27
    const val MANAGE_SERVER = 1L shl 28
    const val ADMINISTRATOR = 1L shl 29

    /** Labels for the role editor, grouped the way people think about them. */
    val groups: List<Pair<String, List<Pair<Long, String>>>> = listOf(
        "General" to listOf(
            VIEW_CHANNEL to "See channels",
            CREATE_INVITE to "Create invites",
            CHANGE_NICKNAME to "Change own nickname",
        ),
        "Messages" to listOf(
            SEND_MESSAGES to "Send messages",
            READ_HISTORY to "Read message history",
            ATTACH_FILES to "Attach files",
            EMBED_LINKS to "Link previews",
            ADD_REACTIONS to "Add reactions",
            MENTION_EVERYONE to "Ping @everyone and any role",
            CREATE_THREADS to "Start threads",
            SEND_IN_THREADS to "Talk in threads",
        ),
        "Voice" to listOf(
            CONNECT to "Join voice channels",
            SPEAK to "Speak",
            VIDEO to "Camera and screen share",
        ),
        "Moderation" to listOf(
            MANAGE_MESSAGES to "Delete and pin messages",
            MANAGE_THREADS to "Manage threads",
            MANAGE_NICKNAMES to "Change other people's nicknames",
            TIMEOUT_MEMBERS to "Time people out",
            KICK_MEMBERS to "Kick people",
            BAN_MEMBERS to "Ban people",
            MUTE_MEMBERS to "Mute people in voice",
            MOVE_MEMBERS to "Move people between voice channels",
            VIEW_AUDIT_LOG to "See the audit log",
        ),
        "Admin" to listOf(
            MANAGE_CHANNELS to "Manage channels",
            MANAGE_ROLES to "Manage roles",
            MANAGE_SERVER to "Manage server",
            ADMINISTRATOR to "Administrator (can do everything)",
        ),
    )
}

fun Long.has(bit: Long): Boolean = (this and bit) == bit

fun ChannelAuthor.shownName(): String = nickname?.takeIf { it.isNotBlank() } ?: displayName
fun ServerMember.shownName(): String = nickname?.takeIf { it.isNotBlank() } ?: user.displayName

/** Text, announcement and thread channels hold messages; categories and voice channels don't. */
val ServerChannel.isTextLike: Boolean get() = type == "text" || type == "announcement" || type == "thread"


@Serializable
data class MutualServer(val id: Long, val name: String, val icon: String? = null)

/** Everything for someone's profile card in a server. */
@Serializable
data class ProfileCardData(
    val user: MemberUser,
    val serverProfile: ServerProfile = ServerProfile(),
    val nickname: String? = null,
    val roleIds: List<Long> = emptyList(),
    val joinedAt: Long = 0,
    val timeoutUntil: Long? = null,
    val online: Boolean = false,
    val memberSince: Long = 0,
    val mutualServers: List<MutualServer> = emptyList(),
    val contact: String = "none", // self, contact, requested, incoming, none
    val canMessage: Boolean = false,
) {
    fun asMember() = ServerMember(user, serverProfile, nickname, roleIds, joinedAt, timeoutUntil, online)
}

/** What a profile card shows, with the server profile winning where it's filled in. */
data class ShownProfile(
    val name: String,
    val displayName: String,
    val username: String,
    val avatar: String?,
    val banner: String?,
    val accent: Long?,
    val pronouns: String,
    val bio: String,
    val status: String,
    val statusEmoji: String,
    val online: Boolean,
    val theme: List<Long>? = null,
    val decoration: String? = null,
    val effect: String? = null,
    val nameStyle: NameStyle? = null,
)

fun ServerMember.shown() = ShownProfile(
    name = nickname?.takeIf { it.isNotBlank() } ?: user.displayName,
    displayName = user.displayName,
    username = user.username,
    avatar = serverProfile.avatar ?: user.avatar,
    banner = serverProfile.banner ?: user.banner,
    accent = user.accentColour,
    pronouns = serverProfile.pronouns.ifBlank { user.pronouns },
    bio = serverProfile.bio.ifBlank { user.about },
    status = user.customStatus,
    statusEmoji = user.statusEmoji,
    online = online,
    theme = user.themeColours,
    decoration = user.decoration,
    effect = user.profileEffect,
    nameStyle = user.nameStyle,
)

/** Someone's picture in a server: their server avatar if they've set one. */
val ServerMember.shownAvatar: String? get() = serverProfile.avatar ?: user.avatar

/** A sticker sent into a channel (drawn big, with no frame). */
val ChannelMessage.isSticker: Boolean get() = meta?.get("sticker")?.toString() == "true"
