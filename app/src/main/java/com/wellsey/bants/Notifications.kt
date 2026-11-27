package com.wellsey.bants

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wellsey.bants.data.Conversation
import com.wellsey.bants.data.Message
import com.wellsey.bants.data.User
import com.wellsey.bants.data.isGroup
import com.wellsey.bants.data.preview
import com.wellsey.bants.data.title

object Notifications {
    // A channel's sound can't change once it exists, so each sound choice gets its own channel id.
    private fun channelId(kind: Sounds.Kind) = "${kind.key}_${Sounds.styleFor(kind).key}"
    private val CHANNEL get() = channelId(Sounds.Kind.Message)
    private val MENTIONS get() = channelId(Sounds.Kind.Mention)
    private val CONTACTS get() = channelId(Sounds.Kind.Request)

    fun createChannel(context: Context) = syncChannels(context)

    /** Makes sure the notification channels use the sounds picked on the Sounds screen. */
    fun syncChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val attrs = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        fun make(kind: Sounds.Kind, name: String, about: String, importance: Int) {
            val sound = android.net.Uri.parse(
                "android.resource://${context.packageName}/raw/${Sounds.resName(kind, Sounds.styleFor(kind))}",
            )
            manager.createNotificationChannel(
                NotificationChannel(channelId(kind), name, importance).apply {
                    description = about
                    setSound(sound, attrs)
                },
            )
        }
        make(Sounds.Kind.Message, "Messages", "New messages from your chats", NotificationManager.IMPORTANCE_HIGH)
        make(Sounds.Kind.Mention, "Mentions", "When someone @mentions you in a group, even if it's muted", NotificationManager.IMPORTANCE_HIGH)
        make(Sounds.Kind.Request, "Contact requests", "People adding you, and requests being accepted", NotificationManager.IMPORTANCE_DEFAULT)
        val keep = setOf(CHANNEL, MENTIONS, CONTACTS)
        val oldSoundChannel = { id: String ->
            id in setOf("messages", "messages_v2", "mentions", "contacts") ||
                Sounds.Kind.values().any { id.startsWith(it.key + "_") }
        }
        manager.notificationChannels
            .filter { it.id !in keep && oldSoundChannel(it.id) }
            .forEach { manager.deleteNotificationChannel(it.id) }
    }

    @SuppressLint("MissingPermission")
    fun showMessage(context: Context, conv: Conversation?, m: Message, sender: User?, myId: Long) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val title = conv?.title(myId) ?: sender?.displayName ?: "Bants"
        val text = if (conv?.isGroup == true) "${sender?.displayName ?: "Someone"}: ${m.preview()}" else m.preview()

        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_CONVERSATION, m.conversationId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            m.conversationId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val channel = if (myId in m.meta?.mentions.orEmpty()) MENTIONS else CHANNEL
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF5B4BFF.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .addExtras(android.os.Bundle().apply { putLong(EXTRA_MESSAGE, m.id) })
            .build()
        NotificationManagerCompat.from(context).notify(m.conversationId.toInt(), notification)
    }

    /** A message in a server channel. Grouped per channel, like Discord. */
    @SuppressLint("MissingPermission")
    fun showChannelMessage(context: Context, info: com.wellsey.bants.data.Servers.NotifyInfo) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val m = info.message
        val who = m.author?.let { it.nickname?.takeIf { n -> n.isNotBlank() } ?: it.displayName } ?: "Someone"
        val where = if (info.channel.parentName != null) "${info.channel.parentName} › ${info.channel.name}" else "#${info.channel.name}"
        val preview = when {
            m.poll != null -> "📊 ${m.poll.question}"
            m.attachments.any { it.mime.startsWith("audio/") } -> "🎤 Voice message"
            m.attachments.any { it.mime.startsWith("image/") } -> "📷 Photo" + (m.body.takeIf { it.isNotBlank() }?.let { " · ${com.wellsey.bants.data.Emojis.plain(it)}" } ?: "")
            else -> com.wellsey.bants.data.Emojis.plain(m.body).replace(Regex("<@&?\\d+>"), "@someone").replace(Regex("<#\\d+>"), "#channel")
        }
        val text = "$who: $preview"
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_CHANNEL, info.channel.id)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 500_000 + info.channel.id.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, if (info.mentioned) MENTIONS else CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF5B4BFF.toInt())
            .setContentTitle("${info.server.name} · $where")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(500_000 + info.channel.id.toInt(), notification)
    }

    /** Builds a notification from a Firebase push (used when the app isn't running). */
    @SuppressLint("MissingPermission")
    fun showPush(context: Context, data: Map<String, String>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val title: String
        val text: String
        val convId: Long
        when (data["type"]) {
            "message" -> {
                convId = data["conversationId"]?.toLongOrNull() ?: return
                val sender = data["senderName"].orEmpty().ifBlank { "Someone" }
                val preview = when (data["kind"]) {
                    "image" -> "📷 Photo" + (data["body"]?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
                    "video" -> "🎥 Video"
                    "audio" -> "🎤 Voice note"
                    "file" -> "📎 File"
                    else -> data["body"].orEmpty().ifBlank { "New message" }
                }
                if (data["conversationType"] == "group") {
                    title = data["conversationName"].orEmpty().ifBlank { "Group" }
                    text = "$sender: $preview"
                } else {
                    title = sender
                    text = preview
                }
            }
            "contact_request" -> {
                convId = 0
                title = data["fromName"].orEmpty().ifBlank { "Someone" }
                text = "wants to add you on Bants"
            }
            "contact_accepted" -> {
                convId = data["conversationId"]?.toLongOrNull() ?: 0
                title = data["name"].orEmpty().ifBlank { "Someone" }
                text = "accepted your request. Say hi!"
            }
            "event" -> {
                // Server events: 15 minutes to go, and starting now.
                convId = 0
                title = data["title"].orEmpty().ifBlank { "Event" }
                text = data["body"].orEmpty()
            }
            else -> return
        }

        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (convId > 0) intent.putExtra(MainActivity.EXTRA_CONVERSATION, convId)
        val pending = PendingIntent.getActivity(
            context,
            convId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val channel = when {
            data["type"] != "message" -> CONTACTS
            data["mentioned"] == "1" -> MENTIONS
            else -> CHANNEL
        }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF5B4BFF.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .apply { data["messageId"]?.toLongOrNull()?.let { id -> addExtras(android.os.Bundle().apply { putLong(EXTRA_MESSAGE, id) }) } }
            .build()
        val id = if (convId > 0) convId.toInt() else data["eventId"]?.toIntOrNull()?.let { 900_000 + it } ?: -1
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    @SuppressLint("MissingPermission")
    fun showMissedCall(context: Context, data: Map<String, String>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val convId = data["conversationId"]?.toLongOrNull() ?: return
        val name = data["callerName"].orEmpty().ifBlank { "Someone" }
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_CONVERSATION, convId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, convId.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF5B4BFF.toInt())
            .setContentTitle("Missed ${if (data["video"] == "1") "video" else "voice"} call")
            .setContentText("$name tried to call you")
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(convId.toInt(), n)
    }

    private const val EXTRA_MESSAGE = "bants.messageId"

    /** Takes down a chat's notification if it's showing one of these (now unsent) messages. */
    fun cancelIfShowing(context: Context, conversationId: Long, messageIds: Collection<Long>) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val showing = runCatching { manager.activeNotifications }.getOrNull() ?: return
        showing.filter { it.id == conversationId.toInt() }
            .filter { it.notification.extras.getLong(EXTRA_MESSAGE, -1L) in messageIds }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    fun cancel(context: Context, conversationId: Long) {
        NotificationManagerCompat.from(context).cancel(conversationId.toInt())
    }

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
