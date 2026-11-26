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
    private const val CHANNEL = "messages"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "New messages from your chats"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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

        val notification = NotificationCompat.Builder(context, CHANNEL)
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

    fun cancel(context: Context, conversationId: Long) {
        NotificationManagerCompat.from(context).cancel(conversationId.toInt())
    }

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
