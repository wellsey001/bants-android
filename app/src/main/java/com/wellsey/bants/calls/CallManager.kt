package com.wellsey.bants.calls

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.wellsey.bants.data.Bants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume

enum class CallStatus { Calling, Ringing, Incoming, Connecting, Active, Ended }

data class CallUi(
    val callId: String?,
    val conversationId: Long,
    val peerId: Long,
    val peerName: String,
    val peerAvatar: String?,
    val video: Boolean,
    val incoming: Boolean,
    val status: CallStatus,
    val answeredAt: Long? = null,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val cameraOn: Boolean = true,
    val frontCamera: Boolean = true,
    val endedText: String? = null,
    val reconnecting: Boolean = false,
)

@Serializable
private data class IceServerDto(val urls: List<String>, val username: String? = null, val credential: String? = null)

@Serializable
private data class IceConfig(val iceServers: List<IceServerDto> = emptyList(), val relay: Boolean = false)

/**
 * Runs one call at a time: ringing, answering, the WebRTC connection, and the local
 * camera/microphone. Signalling goes over the Bants WebSocket.
 */
object CallManager {
    val state = MutableStateFlow<CallUi?>(null)
    val localVideo = MutableStateFlow<VideoTrack?>(null)
    val remoteVideo = MutableStateFlow<VideoTrack?>(null)

    private lateinit var appContext: Context
    val egl: EglBase by lazy { EglBase.create() }
    private var factory: PeerConnectionFactory? = null
    private var pc: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var capturer: CameraVideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private val pendingSignals = mutableListOf<JsonObject>()
    private var remoteDescriptionSet = false
    private var disconnectJob: Job? = null
    private val scope get() = Bants.scope

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val inCall: Boolean get() = state.value != null && state.value?.status != CallStatus.Ended

    // ───────────── starting and answering ─────────────

    fun startCall(conversationId: Long, peerId: Long, peerName: String, peerAvatar: String?, video: Boolean) {
        if (inCall) {
            Bants.notices.tryEmit("You're already on a call")
            return
        }
        state.value = CallUi(null, conversationId, peerId, peerName, peerAvatar, video, incoming = false, status = CallStatus.Calling, speaker = video)
        openCallScreen()
        CallService.start(appContext, video)
        setUpAudio(video)
        startLocalMedia(video)
        val sent = Bants.realtime.send(buildJsonObject {
            put("type", "call.start")
            put("conversationId", conversationId)
            put("video", video)
            put("clientId", "call")
        })
        if (!sent) end("Couldn't connect. Check your signal and try again.", notify = false)
    }

    /** A call arrived over the live connection or a push. */
    fun onIncoming(callId: String, conversationId: Long, callerId: Long, callerName: String, callerAvatar: String?, video: Boolean) {
        val current = state.value
        if (current != null && current.status != CallStatus.Ended) return // already busy; the server handles that
        state.value = CallUi(callId, conversationId, callerId, callerName, callerAvatar, video, incoming = true, status = CallStatus.Incoming, speaker = video)
        Ringer.startRinging(appContext)
        if (Bants.inForeground) openCallScreen() else IncomingCallNotification.show(appContext, state.value!!)
    }

    /** A call push woke us up: ring, then check the call is still going. */
    fun onIncomingFromPush(data: Map<String, String>) {
        val callId = data["callId"] ?: return
        val started = data["startedAt"]?.toLongOrNull() ?: 0L
        if (started > 0 && System.currentTimeMillis() - started > 40_000) return
        onIncoming(
            callId = callId,
            conversationId = data["conversationId"]?.toLongOrNull() ?: return,
            callerId = data["callerId"]?.toLongOrNull() ?: return,
            callerName = data["callerName"].orEmpty().ifBlank { "Someone" },
            callerAvatar = null,
            video = data["video"] == "1",
        )
        scope.launch {
            val stillRinging = runCatching { Bants.api.get<JsonObject>("/api/calls/$callId") }.isSuccess
            val s = state.value
            if (!stillRinging && s?.callId == callId && s.status == CallStatus.Incoming) end(null, notify = false)
        }
    }

    fun accept() {
        val s = state.value ?: return
        if (s.status != CallStatus.Incoming) return
        Ringer.stop()
        IncomingCallNotification.cancel(appContext)
        state.value = s.copy(status = CallStatus.Connecting)
        CallService.start(appContext, s.video)
        setUpAudio(s.video)
        startLocalMedia(s.video)
        scope.launch {
            // If we were woken by a push, wait for the live connection first.
            if (!Bants.connected.value) {
                Bants.realtime.reconnectNow()
                withTimeoutOrNull(10_000) { Bants.connected.first { it } }
            }
            createPeer()
            val sent = Bants.realtime.send(buildJsonObject {
                put("type", "call.accept")
                put("callId", s.callId)
            })
            if (!sent) end("Couldn't connect the call", notify = false)
        }
    }

    fun decline() {
        val s = state.value ?: return
        Bants.realtime.send(buildJsonObject {
            put("type", "call.decline")
            put("callId", s.callId)
        })
        end(null, notify = false)
    }

    fun hangUp() {
        val s = state.value ?: return
        if (s.callId != null) {
            Bants.realtime.send(buildJsonObject {
                put("type", "call.end")
                put("callId", s.callId)
            })
        }
        end(if (s.answeredAt != null) "Call ended" else null, notify = false)
    }

    // ───────────── controls ─────────────

    fun toggleMute() {
        val s = state.value ?: return
        audioTrack?.setEnabled(s.muted)
        state.value = s.copy(muted = !s.muted)
    }

    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        val s = state.value ?: return
        val audio = appContext.getSystemService(AudioManager::class.java)
        audio.isSpeakerphoneOn = !s.speaker
        state.value = s.copy(speaker = !s.speaker)
    }

    fun toggleCamera() {
        val s = state.value ?: return
        if (!s.video) return
        val on = !s.cameraOn
        localVideo.value?.setEnabled(on)
        if (on) runCatching { capturer?.startCapture(1280, 720, 30) } else runCatching { capturer?.stopCapture() }
        state.value = s.copy(cameraOn = on)
    }

    fun flipCamera() {
        val s = state.value ?: return
        capturer?.switchCamera(null)
        state.value = s.copy(frontCamera = !s.frontCamera)
    }

    // ───────────── events from the server ─────────────

    fun onEvent(type: String, ev: JsonObject) {
        val s = state.value
        val callId = ev["callId"]?.jsonPrimitive?.contentOrNull
            ?: (ev["call"] as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
        when (type) {
            "call.started" -> {
                if (s == null || s.status != CallStatus.Calling) return
                state.value = s.copy(callId = callId, status = CallStatus.Ringing)
                Ringer.startRingback()
            }
            "call.incoming" -> {
                val call = ev["call"]?.jsonObject ?: return
                val caller = ev["caller"]?.jsonObject
                onIncoming(
                    callId = callId ?: return,
                    conversationId = call["conversationId"]?.jsonPrimitive?.longOrNull ?: return,
                    callerId = call["callerId"]?.jsonPrimitive?.longOrNull ?: return,
                    callerName = caller?.get("displayName")?.jsonPrimitive?.contentOrNull ?: "Someone",
                    callerAvatar = caller?.get("avatar")?.jsonPrimitive?.contentOrNull,
                    video = call["video"]?.jsonPrimitive?.booleanOrNull ?: false,
                )
            }
            "call.accepted" -> {
                if (s == null || s.callId != callId) return
                Ringer.stop()
                state.value = s.copy(status = CallStatus.Connecting)
                scope.launch {
                    createPeer()
                    makeOffer()
                }
            }
            "call.joined" -> Unit
            "call.signal" -> {
                if (s == null || s.callId != callId) return
                val data = ev["data"]?.jsonObject ?: return
                if (pc == null) pendingSignals += data else handleSignal(data)
            }
            "call.ended" -> {
                if (s == null || (s.callId != null && s.callId != callId)) return
                val reason = ev["reason"]?.jsonPrimitive?.contentOrNull
                end(endedText(reason, s), notify = false)
            }
        }
    }

    fun onStartError(message: String?) {
        if (state.value?.status == CallStatus.Calling) end(message ?: "Couldn't start the call", notify = false)
    }

    private fun endedText(reason: String?, s: CallUi) = when (reason) {
        "declined" -> "${s.peerName} is busy"
        "busy" -> "${s.peerName} is on another call"
        "missed" -> if (s.incoming) null else "No answer"
        "answered_elsewhere" -> null
        "declined_elsewhere" -> null
        "cancelled" -> null
        else -> "Call ended"
    }

    // ───────────── WebRTC ─────────────

    private fun ensureFactory(): PeerConnectionFactory {
        factory?.let { return it }
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions())
        val adm = JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        val f = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
        factory = f
        return f
    }

    private fun startLocalMedia(video: Boolean) {
        val f = ensureFactory()
        audioSource = f.createAudioSource(MediaConstraints())
        audioTrack = f.createAudioTrack("bants-audio", audioSource)
        if (!video) return
        val enumerator = Camera2Enumerator(appContext)
        val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: enumerator.deviceNames.firstOrNull() ?: return
        val cam = enumerator.createCapturer(name, null) ?: return
        val helper = SurfaceTextureHelper.create("BantsCamera", egl.eglBaseContext)
        val source = f.createVideoSource(false)
        cam.initialize(helper, appContext, source.capturerObserver)
        runCatching { cam.startCapture(1280, 720, 30) }
        capturer = cam
        textureHelper = helper
        videoSource = source
        localVideo.value = f.createVideoTrack("bants-video", source)
    }

    private suspend fun iceServers(): List<PeerConnection.IceServer> {
        val config = runCatching { Bants.api.get<IceConfig>("/api/calls/ice") }.getOrNull()
        val list = config?.iceServers ?: listOf(IceServerDto(listOf("stun:stun.l.google.com:19302")))
        return list.map { s ->
            val b = PeerConnection.IceServer.builder(s.urls)
            if (s.username != null) b.setUsername(s.username)
            if (s.credential != null) b.setPassword(s.credential)
            b.createIceServer()
        }
    }

    private suspend fun createPeer() {
        if (pc != null) return
        val servers = iceServers()
        val config = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val peer = ensureFactory().createPeerConnection(config, observer) ?: run {
            end("Couldn't start the call", notify = false)
            return
        }
        audioTrack?.let { peer.addTrack(it, listOf("bants")) }
        localVideo.value?.let { peer.addTrack(it, listOf("bants")) }
        pc = peer
        // Signals that arrived while we were setting up.
        val queued = pendingSignals.toList()
        pendingSignals.clear()
        queued.forEach { handleSignal(it) }
    }

    private fun constraints(): MediaConstraints {
        val video = state.value?.video == true
        return MediaConstraints().apply {
            mandatory += MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
            mandatory += MediaConstraints.KeyValuePair("OfferToReceiveVideo", video.toString())
        }
    }

    private suspend fun makeOffer() {
        val peer = pc ?: return
        val offer = peer.awaitCreate(true, constraints()) ?: return end("Couldn't start the call", notify = false)
        peer.awaitSetLocal(offer)
        sendSignal(buildJsonObject {
            put("type", "offer")
            put("sdp", offer.description)
        })
    }

    private fun handleSignal(data: JsonObject) {
        val peer = pc ?: return
        when (data["type"]?.jsonPrimitive?.contentOrNull) {
            "offer" -> scope.launch {
                val sdp = data["sdp"]?.jsonPrimitive?.contentOrNull ?: return@launch
                peer.awaitSetRemote(SessionDescription(SessionDescription.Type.OFFER, sdp))
                onRemoteDescription()
                val answer = peer.awaitCreate(false, constraints()) ?: return@launch
                peer.awaitSetLocal(answer)
                sendSignal(buildJsonObject {
                    put("type", "answer")
                    put("sdp", answer.description)
                })
            }
            "answer" -> scope.launch {
                val sdp = data["sdp"]?.jsonPrimitive?.contentOrNull ?: return@launch
                peer.awaitSetRemote(SessionDescription(SessionDescription.Type.ANSWER, sdp))
                onRemoteDescription()
            }
            "candidate" -> {
                val c = IceCandidate(
                    data["sdpMid"]?.jsonPrimitive?.contentOrNull,
                    data["sdpMLineIndex"]?.jsonPrimitive?.intOrNull ?: 0,
                    data["candidate"]?.jsonPrimitive?.contentOrNull ?: return,
                )
                if (remoteDescriptionSet) peer.addIceCandidate(c) else pendingCandidates += c
            }
        }
    }

    private fun onRemoteDescription() {
        remoteDescriptionSet = true
        val peer = pc ?: return
        pendingCandidates.forEach { peer.addIceCandidate(it) }
        pendingCandidates.clear()
    }

    private fun sendSignal(data: JsonObject) {
        val id = state.value?.callId ?: return
        Bants.realtime.send(buildJsonObject {
            put("type", "call.signal")
            put("callId", id)
            put("data", data)
        })
    }

    private val observer = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            scope.launch {
                sendSignal(buildJsonObject {
                    put("type", "candidate")
                    put("candidate", candidate.sdp)
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                })
            }
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
            scope.launch {
                val s = state.value ?: return@launch
                when (newState) {
                    PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> {
                        disconnectJob?.cancel()
                        state.value = s.copy(status = CallStatus.Active, answeredAt = s.answeredAt ?: System.currentTimeMillis(), reconnecting = false)
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        state.value = s.copy(reconnecting = true)
                        disconnectJob?.cancel()
                        disconnectJob = scope.launch {
                            delay(15_000)
                            if (state.value?.reconnecting == true) hangUp()
                        }
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        hangUp()
                        state.value = state.value?.copy(endedText = "Couldn't connect the call")
                    }
                    else -> Unit
                }
            }
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            val track = transceiver.receiver.track()
            if (track is VideoTrack) scope.launch { remoteVideo.value = track }
        }

        override fun onSignalingChange(p0: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionReceivingChange(p0: Boolean) = Unit
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>) = Unit
        override fun onAddStream(p0: MediaStream) = Unit
        override fun onRemoveStream(p0: MediaStream) = Unit
        override fun onDataChannel(p0: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
    }

    // ───────────── audio routing ─────────────

    @Suppress("DEPRECATION")
    private fun setUpAudio(video: Boolean) {
        val audio = appContext.getSystemService(AudioManager::class.java)
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        audio.isSpeakerphoneOn = video
    }

    @Suppress("DEPRECATION")
    private fun resetAudio() {
        val audio = appContext.getSystemService(AudioManager::class.java)
        audio.isSpeakerphoneOn = false
        audio.mode = AudioManager.MODE_NORMAL
    }

    // ───────────── tidy up ─────────────

    private fun end(text: String?, notify: Boolean) {
        Ringer.stop()
        IncomingCallNotification.cancel(appContext)
        disconnectJob?.cancel()
        val s = state.value
        scope.launch(Dispatchers.Default) { releaseMedia() }
        resetAudio()
        CallService.stop(appContext)
        if (s == null) return
        state.value = s.copy(status = CallStatus.Ended, endedText = text)
        scope.launch {
            delay(if (text != null) 1_600 else 300)
            if (state.value?.status == CallStatus.Ended) state.value = null
        }
    }

    private fun releaseMedia() {
        runCatching { pc?.close() }
        pc = null
        remoteDescriptionSet = false
        pendingCandidates.clear()
        pendingSignals.clear()
        runCatching { capturer?.stopCapture() }
        capturer?.dispose()
        capturer = null
        localVideo.value?.dispose()
        localVideo.value = null
        remoteVideo.value = null
        videoSource?.dispose()
        videoSource = null
        textureHelper?.dispose()
        textureHelper = null
        audioTrack?.dispose()
        audioTrack = null
        audioSource?.dispose()
        audioSource = null
    }

    private fun openCallScreen() {
        appContext.startActivity(
            Intent(appContext, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

// ───────────── coroutine helpers for WebRTC's callback API ─────────────

private suspend fun PeerConnection.awaitCreate(offer: Boolean, constraints: MediaConstraints): SessionDescription? =
    suspendCancellableCoroutine { cont ->
        val obs = object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) = cont.resume(sdp)
            override fun onCreateFailure(error: String?) = cont.resume(null)
            override fun onSetSuccess() = Unit
            override fun onSetFailure(error: String?) = Unit
        }
        if (offer) createOffer(obs, constraints) else createAnswer(obs, constraints)
    }

private suspend fun PeerConnection.awaitSetLocal(sdp: SessionDescription) = awaitSet { setLocalDescription(it, sdp) }

private suspend fun PeerConnection.awaitSetRemote(sdp: SessionDescription) = awaitSet { setRemoteDescription(it, sdp) }

private suspend fun awaitSet(block: (SdpObserver) -> Unit): Boolean = withContext(Dispatchers.Default) {
    suspendCancellableCoroutine { cont ->
        block(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) = Unit
            override fun onCreateFailure(error: String?) = Unit
            override fun onSetSuccess() = cont.resume(true)
            override fun onSetFailure(error: String?) = cont.resume(false)
        })
    }
}
