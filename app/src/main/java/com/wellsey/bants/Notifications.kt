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
    // Channel sounds can't change after they're made, so new sounds mean new channel ids.
    private const val CHANNEL = "messages_v2"
    private const val MENTIONS = "mentions"
    private const val CONTACTS = "contacts"

    private fun soundUri(context: Context, res: Int) =
        android.net.Uri.parse("android.resource://${context.packageName}/$res")

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val attrs = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        manager.deleteNotificationChannel("messages")
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New messages from your chats"
                setSound(soundUri(context, R.raw.bants_message), attrs)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(MENTIONS, "Mentions", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When someone @mentions you in a group, even if it's muted"
                setSound(soundUri(context, R.raw.bants_mention), attrs)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CONTACTS, "Contact requests", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "People adding you, and requests being accepted"
                setSound(soundUri(context, R.raw.bants_request), attrs)
            },
        )
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
            .build()
        NotificationManagerCompat.from(context).notify(m.conversationId.toInt(), notification)
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
                    else -> data["body"].orEmpty()
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
            .build()
        NotificationManagerCompat.from(context).notify(if (convId > 0) convId.toInt() else -1, notification)
    }

    fun cancel(context: Context, conversationId: Long) {
        NotificationManagerCompat.from(context).cancel(conversationId.toInt())
    }

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
