package com.wellsey.bants

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.wellsey.bants.data.Bants

/** Receives pushes from the Bants server via Firebase, even when the app is closed. */
class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Bants.registerPushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["type"]) {
            "call" -> {
                com.wellsey.bants.calls.CallManager.onIncomingFromPush(data)
                Bants.onPushMessage()
                return
            }
            "unsend" -> {
                val convId = data["conversationId"]?.toLongOrNull() ?: return
                val id = data["messageId"]?.toLongOrNull() ?: return
                Notifications.cancelIfShowing(applicationContext, convId, listOf(id))
                Bants.removeMessages(convId, setOf(id))
                return
            }
            "missed_call" -> {
                if (!Bants.inForeground) Notifications.showMissedCall(applicationContext, data)
                return
            }
        }
        // If the app is open and connected, the live connection has already handled it.
        if (Bants.inForeground && Bants.connected.value) return
        val convId = data["conversationId"]?.toLongOrNull()
        val mentioned = data["mentioned"] == "1"
        if (convId != null && Bants.conversations.value[convId]?.muted == true && !mentioned) return
        Notifications.showPush(applicationContext, data)
        if (data["type"] == "message") Bants.onPushMessage()
    }
}
