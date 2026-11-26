package com.wellsey.bants.calls

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wellsey.bants.R

/** The full-screen "Dave is calling" alert when Bants isn't open. */
object IncomingCallNotification {
    private const val CHANNEL = "incoming_calls"
    private const val ID = 4202

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rings when someone calls you on Bants"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 900, 600, 900)
            },
        )
    }

    @SuppressLint("MissingPermission")
    fun show(context: Context, call: CallUi) {
        createChannel(context)
        // The ringtone comes from Ringer, so the notification itself stays quiet when we're running.
        val full = PendingIntent.getActivity(
            context, 10, Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val answer = PendingIntent.getActivity(
            context, 11,
            Intent(context, CallActivity::class.java).setAction(CallActivity.ACTION_ANSWER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val decline = PendingIntent.getBroadcast(
            context, 12, Intent(context, CallActionReceiver::class.java).setAction(CallActionReceiver.ACTION_DECLINE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF5B4BFF.toInt())
            .setContentTitle(call.peerName)
            .setContentText(if (call.video) "Incoming video call" else "Incoming voice call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setTimeoutAfter(45_000)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, "Decline", decline)
            .addAction(0, "Answer", answer)
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        NotificationManagerCompat.from(context).notify(ID, n)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID)
    }

    @SuppressLint("MissingPermission")
    fun showMissed(context: Context, data: Map<String, String>) {
        com.wellsey.bants.Notifications.showMissedCall(context, data)
    }
}

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DECLINE) CallManager.decline()
    }

    companion object {
        const val ACTION_DECLINE = "com.wellsey.bants.DECLINE_CALL"
    }
}
