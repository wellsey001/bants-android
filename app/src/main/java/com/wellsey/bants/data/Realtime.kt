package com.wellsey.bants.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.math.min

/** Keeps a WebSocket open to the server and reconnects with backoff when it drops. */
class Realtime(
    private val api: Api,
    private val session: Session,
    private val scope: CoroutineScope,
) {
    val events = MutableSharedFlow<JsonObject>(extraBufferCapacity = 256)
    val connected = MutableStateFlow(false)

    private var socket: WebSocket? = null
    @Volatile private var wanted = false
    private var attempt = 0
    private var reconnectJob: Job? = null

    fun start() {
        wanted = true
        if (socket == null) open()
    }

    fun stop() {
        wanted = false
        reconnectJob?.cancel()
        socket?.close(1000, null)
        socket = null
        connected.value = false
    }

    /** Reconnect straight away (e.g. when the app comes back to the foreground). */
    fun reconnectNow() {
        if (!wanted || socket != null) return
        reconnectJob?.cancel()
        attempt = 0
        open()
    }

    fun send(obj: JsonObject): Boolean = socket?.takeIf { connected.value }?.send(obj.toString()) ?: false

    @Synchronized
    private fun open() {
        val token = session.token ?: return
        val request = try {
            Request.Builder()
                .url(session.serverUrl + "/ws")
                .header("Authorization", "Bearer $token")
                .build()
        } catch (e: IllegalArgumentException) {
            return
        }
        socket = api.http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                attempt = 0
                connected.value = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val obj = runCatching { api.json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                events.tryEmit(obj)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                dropped(webSocket, code)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                dropped(webSocket, response?.code ?: 0)
            }
        })
    }

    private fun dropped(ws: WebSocket, code: Int) {
        synchronized(this) {
            if (socket !== ws) return
            socket = null
        }
        connected.value = false
        if (code == 401 || code == 4001) {
            wanted = false
            events.tryEmit(JsonObject(mapOf("type" to JsonPrimitive("auth.lost"))))
            return
        }
        if (!wanted) return
        val wait = min(30_000L, 1_000L * (1L shl min(attempt, 5)))
        attempt++
        reconnectJob = scope.launch {
            delay(wait)
            if (wanted && socket == null) open()
        }
    }
}
