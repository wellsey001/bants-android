package com.wellsey.bants.location

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.wellsey.bants.R
import com.wellsey.bants.data.Bants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps a live location message up to date while it's being shared, then stops on its own.
 * Shows an ongoing notification with a Stop button the whole time.
 */
class LiveLocationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var messageId = 0L
    private var until = 0L
    private var timer: Job? = null
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val id = messageId
            scope.launch { runCatching { Bants.updateLiveLocation(id, loc.latitude, loc.longitude, loc.accuracy.toDouble()) } }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSharing(tellServer = true)
            return START_NOT_STICKY
        }
        val newId = intent?.getLongExtra(EXTRA_MESSAGE, 0L) ?: 0L
        if (newId == 0L) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Only one live share at a time: a new one ends the old.
        if (messageId != 0L && messageId != newId) {
            val old = messageId
            scope.launch { runCatching { Bants.stopLiveLocation(old) } }
        }
        messageId = newId
        until = intent?.getLongExtra(EXTRA_UNTIL, 0L) ?: 0L
        createChannel(this)
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        client.removeLocationUpdates(callback)
        runCatching {
            client.requestLocationUpdates(
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 20_000L).setMinUpdateDistanceMeters(15f).build(),
                callback,
                Looper.getMainLooper(),
            )
        }
        timer?.cancel()
        timer = scope.launch {
            delay((until - System.currentTimeMillis()).coerceAtLeast(0))
            stopSharing(tellServer = false)
        }
        return START_NOT_STICKY
    }

    private fun stopSharing(tellServer: Boolean) {
        val id = messageId
        if (tellServer && id != 0L) scope.launch { runCatching { Bants.stopLiveLocation(id) } }
        messageId = 0L
        client.removeLocationUpdates(callback)
        active = 0L
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        // Give the "stop" request a moment to go out before the scope is torn down.
        scope.launch { delay(3_000); scope.cancel() }
        super.onDestroy()
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Sharing your live location")
        .setContentText("Until " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(until)))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .addAction(
            0, "Stop sharing",
            PendingIntent.getService(
                this, 1, Intent(this, LiveLocationService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    companion object {
        private const val CHANNEL = "live_location"
        private const val NOTIFICATION_ID = 4301
        private const val ACTION_STOP = "com.wellsey.bants.STOP_LIVE_LOCATION"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_UNTIL = "until"

        /** The message currently being kept live, or 0. */
        @Volatile var active = 0L
            private set

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Live location", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while you're sharing your live location"
                    setSound(null, null)
                },
            )
        }

        fun start(context: Context, messageId: Long, until: Long) {
            active = messageId
            ContextCompat.startForegroundService(
                context,
                Intent(context, LiveLocationService::class.java).putExtra(EXTRA_MESSAGE, messageId).putExtra(EXTRA_UNTIL, until),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, LiveLocationService::class.java).setAction(ACTION_STOP))
        }
    }
}
