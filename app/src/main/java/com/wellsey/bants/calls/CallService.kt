package com.wellsey.bants.calls

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.wellsey.bants.R

/** Keeps the microphone (and camera) running when you leave the call screen. */
class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HANG_UP) {
            CallManager.hangUp()
            return START_NOT_STICKY
        }
        val video = intent?.getBooleanExtra(EXTRA_VIDEO, false) == true
        val screen = intent?.getBooleanExtra(EXTRA_SCREEN, false) == true
        val voice = CallManager.state.value?.voiceChannelId != null
        val name = CallManager.state.value?.peerName ?: "Bants"
        createChannel(this)
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val hangUp = PendingIntent.getService(
            this, 2, Intent(this, CallService::class.java).setAction(ACTION_HANG_UP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF5B4BFF.toInt())
            .setContentTitle(if (voice) "In voice: $name" else if (video) "Video call with $name" else "Voice call with $name")
            .setContentText(if (screen) "You're sharing your screen" else "Tap to go back to the call")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(open)
            .addAction(0, "Hang up", hangUp)
            .build()
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (video && Build.VERSION.SDK_INT >= 30) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        // Screen sharing: Android 14 only lets capture start once the service says it's capturing the screen.
        if (screen && Build.VERSION.SDK_INT >= 29) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, if (Build.VERSION.SDK_INT >= 29) type else 0)
            screenReady.value = screen
        } catch (e: Exception) {
            screenReady.value = false
            if (!screen) stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "ongoing_call"
        private const val NOTIFICATION_ID = 4201
        private const val ACTION_HANG_UP = "com.wellsey.bants.HANG_UP"
        private const val EXTRA_VIDEO = "video"
        private const val EXTRA_SCREEN = "screen"

        /** True once the service is running as a screen-capture service. */
        val screenReady = kotlinx.coroutines.flow.MutableStateFlow(false)

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Ongoing call", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while you're on a call"
                    setSound(null, null)
                },
            )
        }

        fun start(context: Context, video: Boolean, screen: Boolean = false) {
            if (screen) screenReady.value = false
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, CallService::class.java).putExtra(EXTRA_VIDEO, video).putExtra(EXTRA_SCREEN, screen),
                )
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallService::class.java))
        }
    }
}
