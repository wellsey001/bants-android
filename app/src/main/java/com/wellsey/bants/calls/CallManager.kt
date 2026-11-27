package com.wellsey.bants.calls

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.projection.MediaProjection
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
import org.webrtc.MediaStreamTrack
import org.webrtc.ScreenCapturerAndroid
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

/** Someone else on the call. */
data class CallPerson(val id: Long, val name: String, val avatar: String?)

data class CallUi(
    val callId: String?,
    val conversationId: Long,
    val peerId: Long,
    /** The other person, or the group's name for group calls. */
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
    val group: Boolean = false,
    /** Who started a group call (shown while it rings). */
    val callerName: String? = null,
    /** Everyone else in a group call right now. */
    val participants: List<CallPerson> = emptyList(),
    /** Background blur is on for your camera. */
    val blur: Boolean = false,
    /** Voice channels: you're sharing your screen ("live"). */
    val sharingScreen: Boolean = false,
    /** Set when this "call" is a server voice channel you've dropped into. */
    val voiceChannelId: Long? = null,
)

@Serializable
private data class IceServerDto(val urls: List<String>, val username: String? = null, val credential: String? = null)

@Serializable
private data class IceConfig(val iceServers: List<IceServerDto> = emptyList(), val relay: Boolean = false)

/**
 * Runs one call at a time: ringing, answering, the WebRTC connections, and the local
 * camera/microphone. Signalling goes over the Bants WebSocket.
 *
 * A 1:1 call has one connection. A group call has one connection to each other person
 * (a "mesh"); whoever joins last sends the offers, so two phones never both offer.
 */
object CallManager {
    val state = MutableStateFlow<CallUi?>(null)
    val localVideo = MutableStateFlow<VideoTrack?>(null)
    /** 1:1 calls: the other person's video. */
    val remoteVideo = MutableStateFlow<VideoTrack?>(null)
    /** Group calls: everyone's video, by user id. */
    val remoteVideos = MutableStateFlow<Map<Long, VideoTrack>>(emptyMap())
    /** Voice channels: screen shares coming in, by user id. */
    val remoteScreens = MutableStateFlow<Map<Long, VideoTrack>>(emptyMap())
    /** Voice channels: your own screen share, for the preview. */
    val localScreen = MutableStateFlow<VideoTrack?>(null)
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var screenSource: VideoSource? = null
    private var screenHelper: SurfaceTextureHelper? = null
    private var screenFps = 30

    private lateinit var appContext: Context
    val egl: EglBase by lazy { EglBase.create() }
    private var factory: PeerConnectionFactory? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var capturer: CameraVideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var iceCache: List<PeerConnection.IceServer>? = null
    private val scope get() = Bants.scope

    /** One WebRTC connection to one other person. */
    private class Peer(val userId: Long) {
        var pc: PeerConnection? = null
        val pendingCandidates = mutableListOf<IceCandidate>()
        val pendingSignals = mutableListOf<JsonObject>()
        var remoteSet = false
        var connected = false
        var disconnectJob: Job? = null
        var creating = false
        // Voice channels: fixed slots (mic, camera, screen, screen sound), same order as desktop.
        var camTx: RtpTransceiver? = null
        var screenTx: RtpTransceiver? = null
        var screenAudioTx: RtpTransceiver? = null
    }

    private val peers = java.util.concurrent.ConcurrentHashMap<Long, Peer>()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val inCall: Boolean get() = state.value != null && state.value?.status != CallStatus.Ended

    // ───────────── starting and answering ─────────────

    fun startCall(conversationId: Long, peerId: Long, peerName: String, peerAvatar: String?, video: Boolean, group: Boolean = false) {
        if (inCall) {
            Bants.notices.tryEmit("You're already on a call")
            return
        }
        state.value = CallUi(null, conversationId, peerId, peerName, peerAvatar, video, incoming = false, status = CallStatus.Calling, speaker = video || group, group = group)
        openCallScreen()
        CallService.start(appContext, video)
        setUpAudio(video || group)
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
    fun onIncoming(
        callId: String, conversationId: Long, callerId: Long, callerName: String, callerAvatar: String?, video: Boolean,
        group: Boolean = false, groupName: String? = null,
    ) {
        val current = state.value
        if (current != null && current.status != CallStatus.Ended) return // already busy; the server handles that
        state.value = if (group) {
            CallUi(callId, conversationId, 0, groupName ?: "Group call", null, video, incoming = true, status = CallStatus.Incoming, speaker = true, group = true, callerName = callerName)
        } else {
            CallUi(callId, conversationId, callerId, callerName, callerAvatar, video, incoming = true, status = CallStatus.Incoming, speaker = video)
        }
        Ringer.startRinging(appContext, video)
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
            group = data["group"] == "1",
            groupName = data["groupName"],
        )
        scope.launch {
            val stillRinging = runCatching { Bants.api.get<JsonObject>("/api/calls/$callId") }.isSuccess
            val s = state.value
            if (!stillRinging && s?.callId == callId && s.status == CallStatus.Incoming) end(null, notify = false)
        }
    }

    // ───────────── voice channels ─────────────

    /** Drop into a server voice channel. If you're already in one, you hop across. */
    fun joinVoice(channelId: Long, name: String) {
        val cur = state.value
        if (cur != null && cur.status != CallStatus.Ended) {
            if (cur.voiceChannelId == channelId) {
                showCallScreen()
                return
            }
            if (cur.voiceChannelId == null) {
                Bants.notices.tryEmit("Hang up your call first")
                return
            }
            // Leave the old channel, give the media a moment to shut down, then join the new one.
            hangUp()
            scope.launch {
                delay(700)
                joinVoice(channelId, name)
            }
            return
        }
        state.value = CallUi(
            null, 0, 0, name, null, video = false, incoming = false, status = CallStatus.Connecting,
            speaker = true, group = true, voiceChannelId = channelId, cameraOn = false,
        )
        CallService.start(appContext, false)
        setUpAudio(true)
        startLocalMedia(false)
        scope.launch {
            if (!Bants.connected.value) {
                Bants.realtime.reconnectNow()
                withTimeoutOrNull(10_000) { Bants.connected.first { it } }
            }
            val sent = Bants.realtime.send(buildJsonObject {
                put("type", "voice.join")
                put("channelId", channelId)
                put("clientId", "call")
            })
            if (!sent) end("You're offline", notify = false)
        }
    }

    /** Opens the full call screen (from the voice bar, or after joining). */
    fun showCallScreen() = openCallScreen()

    private fun sendVoiceState() {
        val s = state.value ?: return
        if (s.voiceChannelId == null || s.callId == null) return
        Bants.realtime.send(buildJsonObject {
            put("type", "voice.update")
            put("muted", s.muted)
            put("video", s.cameraOn && localVideo.value != null)
            put("screen", s.sharingScreen)
        })
    }

    // ───────────── camera and screen sharing in voice channels ─────────────
    // Every voice connection has fixed slots in this order: mic, camera, screen, screen sound.
    // Turning the camera or a screen share on just fills its slot, so nobody reconnects.

    private val camSizes = mapOf("360" to (640 to 360), "720" to (1280 to 720), "1080" to (1920 to 1080))
    private val camBitrates = mapOf("360" to 600_000, "720" to 1_500_000, "1080" to 3_000_000)
    private val screenBitrates = mapOf("720" to 2_000_000, "1080" to 3_500_000, "source" to 6_000_000)

    private fun voiceSlots(p: Peer, offering: Boolean) {
        val conn = p.pc ?: return
        val sendRecv = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
        if (offering) {
            if (p.camTx == null) p.camTx = conn.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, RtpTransceiver.RtpTransceiverInit(sendRecv))
            if (p.screenTx == null) p.screenTx = conn.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, RtpTransceiver.RtpTransceiverInit(sendRecv))
            if (p.screenAudioTx == null) p.screenAudioTx = conn.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, RtpTransceiver.RtpTransceiverInit(sendRecv))
        } else {
            // Answering: the slots came from their offer, in the same order.
            val all = conn.transceivers
            val vids = all.filter { it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO }
            val auds = all.filter { it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO }
            p.camTx = vids.getOrNull(0)
            p.screenTx = vids.getOrNull(1)
            p.screenAudioTx = auds.getOrNull(1)
            listOfNotNull(p.camTx, p.screenTx, p.screenAudioTx).forEach { it.setDirection(sendRecv) }
        }
        val cam = if (state.value?.cameraOn == true) localVideo.value else null
        runCatching { p.camTx?.sender?.setTrack(cam, false) }
        runCatching { p.screenTx?.sender?.setTrack(localScreen.value, false) }
    }

    /** Caps bitrate and frame rate so one stream can't swamp your upload. */
    private fun applyEncodings(p: Peer) {
        val sess = Bants.session
        fun cap(tx: RtpTransceiver?, bitrate: Int, fps: Int) {
            val sender = tx?.sender ?: return
            runCatching {
                val params = sender.parameters
                params.encodings.forEach {
                    it.maxBitrateBps = bitrate
                    it.maxFramerate = fps
                }
                sender.setParameters(params)
            }
        }
        cap(p.camTx, camBitrates[sess.callCamRes] ?: 1_500_000, sess.callCamFps)
        val scale = if (screenFps >= 60) 1.6 else if (screenFps <= 15) 0.6 else 1.0
        cap(p.screenTx, ((screenBitrates[sess.callScreenRes] ?: 3_500_000) * scale).toInt(), screenFps)
    }

    private fun fillCamSlot(track: VideoTrack?) {
        peers.values.forEach { p ->
            runCatching { p.camTx?.sender?.setTrack(track, false) }
            applyEncodings(p)
        }
        if (track != null) renegotiateAll()
    }

    private fun fillScreenSlot(track: VideoTrack?) {
        peers.values.forEach { p ->
            runCatching { p.screenTx?.sender?.setTrack(track, false) }
            applyEncodings(p)
        }
        if (track != null) renegotiateAll()
    }

    /**
     * Turning a camera or screen on: swapping the track in is usually enough, but some phones'
     * WebRTC won't start sending on a slot that was empty when the call connected, which left the
     * other person looking at a black tile. A fresh offer/answer gets the video flowing everywhere.
     */
    private fun renegotiateAll() {
        peers.values.forEach { p ->
            scope.launch {
                delay(300)
                val conn = p.pc ?: return@launch
                if (conn.signalingState() == PeerConnection.SignalingState.STABLE) runCatching { makeOffer(p.userId) }
            }
        }
    }

    /** Voice channels: camera on/off. Ask for the camera permission before calling this. */
    fun toggleVoiceCamera() {
        val s = state.value ?: return
        if (s.voiceChannelId == null) return
        if (s.cameraOn && localVideo.value != null) {
            fillCamSlot(null)
            stopCamera()
            state.value = state.value?.copy(cameraOn = false)
        } else {
            CallService.start(appContext, video = true, screen = s.sharingScreen)
            val (w, h) = camSizes[Bants.session.callCamRes] ?: (1280 to 720)
            if (!startCamera(w, h, Bants.session.callCamFps)) {
                Bants.notices.tryEmit("Couldn't open your camera")
                return
            }
            state.value = state.value?.copy(cameraOn = true)
            fillCamSlot(localVideo.value)
        }
        sendVoiceState()
    }

    /**
     * Voice channels: go live. [data] is the result of Android's "start recording or casting?" prompt.
     * The call service has to be running as a screen-capture service before capture starts (Android 14).
     */
    fun startScreenShare(data: Intent, res: String, fps: Int) {
        val s = state.value ?: return
        if (s.voiceChannelId == null) return
        Bants.session.callScreenRes = res
        Bants.session.callScreenFps = fps
        screenFps = fps
        CallService.start(appContext, video = s.cameraOn && localVideo.value != null, screen = true)
        scope.launch {
            withTimeoutOrNull(3_000) { CallService.screenReady.first { it } }
            try {
                stopScreenShareMedia()
                val f = ensureFactory()
                val m = appContext.resources.displayMetrics
                val longest = maxOf(m.widthPixels, m.heightPixels)
                val limit = when (res) { "720" -> 1280; "1080" -> 1920; else -> longest }
                val scale = minOf(1.0, limit.toDouble() / longest)
                val w = ((m.widthPixels * scale).toInt() / 2) * 2
                val h = ((m.heightPixels * scale).toInt() / 2) * 2
                val capturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
                    override fun onStop() {
                        scope.launch { stopScreenShare() }
                    }
                })
                val helper = SurfaceTextureHelper.create("BantsScreen", egl.eglBaseContext)
                val source = f.createVideoSource(true)
                capturer.initialize(helper, appContext, source.capturerObserver)
                capturer.startCapture(w, h, fps)
                screenCapturer = capturer
                screenHelper = helper
                screenSource = source
                val track = f.createVideoTrack("bants-screen", source)
                localScreen.value = track
                fillScreenSlot(track)
                state.value = state.value?.copy(sharingScreen = true)
                sendVoiceState()
            } catch (e: Exception) {
                stopScreenShareMedia()
                Bants.notices.tryEmit("Couldn't share your screen")
            }
        }
    }

    fun stopScreenShare() {
        if (localScreen.value == null && screenCapturer == null) return
        fillScreenSlot(null)
        stopScreenShareMedia()
        state.value = state.value?.copy(sharingScreen = false)
        sendVoiceState()
        val s = state.value ?: return
        CallService.start(appContext, video = s.cameraOn && localVideo.value != null, screen = false)
    }

    private fun stopScreenShareMedia() {
        runCatching { screenCapturer?.stopCapture() }
        runCatching { screenCapturer?.dispose() }
        screenCapturer = null
        localScreen.value?.let { runCatching { it.dispose() } }
        localScreen.value = null
        runCatching { screenSource?.dispose() }
        screenSource = null
        runCatching { screenHelper?.dispose() }
        screenHelper = null
    }

    private fun stopCamera() {
        runCatching { capturer?.stopCapture() }
        blurProcessor?.dispose()
        blurProcessor = null
        runCatching { capturer?.dispose() }
        capturer = null
        localVideo.value?.let { runCatching { it.dispose() } }
        localVideo.value = null
        runCatching { videoSource?.dispose() }
        videoSource = null
        runCatching { textureHelper?.dispose() }
        textureHelper = null
    }

    fun accept() {
        val s = state.value ?: return
        if (s.status != CallStatus.Incoming) return
        Ringer.stop()
        IncomingCallNotification.cancel(appContext)
        state.value = s.copy(status = CallStatus.Connecting)
        CallService.start(appContext, s.video)
        setUpAudio(s.video || s.group)
        startLocalMedia(s.video)
        scope.launch {
            // If we were woken by a push, wait for the live connection first.
            if (!Bants.connected.value) {
                Bants.realtime.reconnectNow()
                withTimeoutOrNull(10_000) { Bants.connected.first { it } }
            }
            // 1:1: get ready for the caller's offer. Group: we'll offer to everyone once we've joined.
            if (!s.group) createPeer(s.peerId)
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
        end(if (s.voiceChannelId != null) "Left voice" else if (s.answeredAt != null) "Call ended" else null, notify = false)
    }

    // ───────────── controls ─────────────

    fun toggleMute() {
        val s = state.value ?: return
        audioTrack?.setEnabled(s.muted)
        state.value = s.copy(muted = !s.muted)
        sendVoiceState()
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

    /** Background blur on or off mid-call (and remembered for next time). */
    fun toggleBlur() {
        val s = state.value ?: return
        val source = videoSource ?: return
        val on = !s.blur
        val level = if (on) Bants.session.callBlur.takeIf { it != "off" } ?: "light" else "off"
        Bants.session.callBlur = level
        if (on) {
            val p = BackgroundBlur(strong = level == "strong")
            source.setVideoProcessor(p)
            blurProcessor?.dispose()
            blurProcessor = p
            runCatching { capturer?.changeCaptureFormat(640, 480, 24) }
        } else {
            source.setVideoProcessor(null)
            blurProcessor?.dispose()
            blurProcessor = null
            runCatching { capturer?.changeCaptureFormat(1280, 720, 30) }
        }
        state.value = s.copy(blur = on)
    }

    fun flipCamera() {
        val s = state.value ?: return
        capturer?.switchCamera(null)
        state.value = s.copy(frontCamera = !s.frontCamera)
    }

    // ───────────── events from the server ─────────────

    private fun person(o: JsonObject?): CallPerson? {
        val id = o?.get("id")?.jsonPrimitive?.longOrNull ?: return null
        return CallPerson(id, o["displayName"]?.jsonPrimitive?.contentOrNull ?: "Someone", o["avatar"]?.jsonPrimitive?.contentOrNull)
    }

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
                    group = call["group"]?.jsonPrimitive?.booleanOrNull ?: false,
                    groupName = ev["groupName"]?.jsonPrimitive?.contentOrNull,
                )
            }
            "call.accepted" -> {
                if (s == null || s.callId != callId) return
                Ringer.stop()
                state.value = s.copy(status = CallStatus.Connecting)
                scope.launch {
                    createPeer(s.peerId)
                    makeOffer(s.peerId)
                }
            }
            "call.joined" -> {
                // Group: we're in. Connect to everyone already there (we send the offers).
                if (s == null || !s.group) return
                val others = (ev["participants"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { person(it as? JsonObject) }.orEmpty()
                val call = ev["call"]?.jsonObject
                val video = call?.get("video")?.jsonPrimitive?.booleanOrNull ?: s.video
                val voice = s.voiceChannelId != null
                state.value = s.copy(
                    callId = callId,
                    group = true,
                    video = video,
                    participants = others,
                    peerName = ev["groupName"]?.jsonPrimitive?.contentOrNull ?: s.peerName,
                    // A voice channel with nobody else in it is still "connected": you're just early.
                    status = when {
                        voice && others.isEmpty() -> CallStatus.Active
                        others.isEmpty() -> CallStatus.Ringing
                        else -> CallStatus.Connecting
                    },
                    answeredAt = if (voice) (s.answeredAt ?: System.currentTimeMillis()) else s.answeredAt,
                )
                if (voice && s.muted) sendVoiceState()
                if (others.isNotEmpty()) Ringer.stop()
                others.forEach { p ->
                    scope.launch {
                        createPeer(p.id)
                        makeOffer(p.id)
                    }
                }
            }
            "call.participant.joined" -> {
                if (s == null || s.callId != callId) return
                val p = person(ev["user"]?.jsonObject) ?: return
                Ringer.stop()
                state.value = s.copy(
                    participants = s.participants.filter { it.id != p.id } + p,
                    status = if (s.status == CallStatus.Active) CallStatus.Active else CallStatus.Connecting,
                )
                // They'll send us an offer; have a connection ready for it.
                scope.launch { createPeer(p.id) }
            }
            "call.participant.left" -> {
                if (s == null || s.callId != callId) return
                val id = ev["userId"]?.jsonPrimitive?.longOrNull ?: return
                closePeer(id)
                state.value = state.value?.let { cur ->
                    val left = cur.participants.filter { it.id != id }
                    // Alone in a voice channel is fine, not "reconnecting".
                    if (cur.voiceChannelId != null && left.isEmpty()) cur.copy(participants = left, reconnecting = false, status = CallStatus.Active)
                    else cur.copy(participants = left)
                }
            }
            "call.signal" -> {
                if (s == null || s.callId != callId) return
                val data = ev["data"]?.jsonObject ?: return
                val from = ev["from"]?.jsonPrimitive?.longOrNull ?: s.peerId
                val peer = peers[from]
                if (peer?.pc == null) {
                    // Not ready yet (or a group joiner we haven't heard about): queue it and get ready.
                    val p = peers.getOrPut(from) { Peer(from) }
                    p.pendingSignals += data
                    if (s.group) scope.launch { createPeer(from) }
                } else {
                    handleSignal(peer, data)
                }
            }
            "call.ended" -> {
                if (s == null || (s.callId != null && s.callId != callId)) return
                val reason = ev["reason"]?.jsonPrimitive?.contentOrNull
                end(endedText(reason, s), notify = false)
            }
        }
    }

    fun onStartError(message: String?) {
        val s = state.value
        if (s?.status == CallStatus.Calling || (s?.voiceChannelId != null && s.callId == null)) end(message ?: "Couldn't start the call", notify = false)
    }

    private fun endedText(reason: String?, s: CallUi) = when (reason) {
        "removed" -> "You were removed from the voice channel"
        "channel_deleted" -> "The voice channel was deleted"
        "empty" -> "Left voice"
        "declined" -> if (s.group) "Nobody picked up" else "${s.peerName} is busy"
        "busy" -> "${s.peerName} is on another call"
        "missed" -> if (s.incoming) null else "No answer"
        "answered_elsewhere" -> null
        "declined_elsewhere" -> null
        "cancelled" -> null
        else -> "Call ended"
    }

    // ───────────── WebRTC ─────────────

    private var factoryKey: String? = null
    private var initialized = false
    private var blurProcessor: BackgroundBlur? = null
    private val gate = NoiseGate()

    private fun ensureFactory(): PeerConnectionFactory {
        val sess = Bants.session
        // The phone's own echo/noise filters are set when the audio module is made, so a
        // settings change means a fresh one (only between calls, never mid-call).
        val key = "${sess.callNoise}|${sess.callEcho}"
        factory?.let { f ->
            if (factoryKey == key || peers.isNotEmpty()) return f
            runCatching { f.dispose() }
            factory = null
        }
        if (!initialized) {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions())
            initialized = true
        }
        val adm = JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(sess.callEcho)
            .setUseHardwareNoiseSuppressor(sess.callNoise != "off")
            // Strong: also silence the mic between words (keyboards, clicks, the telly).
            .setAudioRecordDataCallback { _, _, _, audioBuffer ->
                if (Bants.session.callNoise == "strong") gate.process(audioBuffer, audioBuffer.remaining())
            }
            .createAudioDeviceModule()
        factoryKey = key
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
        val sess = Bants.session
        val constraints = MediaConstraints().apply {
            fun set(k: String, v: Boolean) = mandatory.add(MediaConstraints.KeyValuePair(k, v.toString()))
            set("googEchoCancellation", sess.callEcho)
            set("googAutoGainControl", sess.callAutoGain)
            set("googNoiseSuppression", sess.callNoise != "off")
            set("googHighpassFilter", sess.callNoise != "off")
            set("googTypingNoiseDetection", sess.callNoise == "strong")
        }
        gate.reset()
        audioSource = f.createAudioSource(constraints)
        audioTrack = f.createAudioTrack("bants-audio", audioSource)
        if (!video) return
        startCamera(1280, 720, 30)
    }

    /** Opens the front camera into [localVideo], with background blur if it's switched on. */
    private fun startCamera(width: Int, height: Int, fps: Int): Boolean {
        val f = ensureFactory()
        val enumerator = Camera2Enumerator(appContext)
        val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: enumerator.deviceNames.firstOrNull() ?: return false
        val cam = enumerator.createCapturer(name, null) ?: return false
        val helper = SurfaceTextureHelper.create("BantsCamera", egl.eglBaseContext)
        val source = f.createVideoSource(false)
        cam.initialize(helper, appContext, source.capturerObserver)
        val blur = Bants.session.callBlur
        if (blur != "off") {
            blurProcessor = BackgroundBlur(strong = blur == "strong").also { source.setVideoProcessor(it) }
            // Blurring every frame is hard work, so use a smaller picture while it's on.
            runCatching { cam.startCapture(minOf(width, 640), minOf(height, 480), minOf(fps, 24)) }
        } else {
            runCatching { cam.startCapture(width, height, fps) }
        }
        state.value = state.value?.copy(blur = blur != "off", frontCamera = true)
        capturer = cam
        textureHelper = helper
        videoSource = source
        localVideo.value = f.createVideoTrack("bants-video", source)
        return true
    }

    private suspend fun iceServers(): List<PeerConnection.IceServer> {
        iceCache?.let { return it }
        val config = runCatching { Bants.api.get<IceConfig>("/api/calls/ice") }.getOrNull()
        val list = config?.iceServers ?: listOf(IceServerDto(listOf("stun:stun.l.google.com:19302")))
        return list.map { s ->
            val b = PeerConnection.IceServer.builder(s.urls)
            if (s.username != null) b.setUsername(s.username)
            if (s.credential != null) b.setPassword(s.credential)
            b.createIceServer()
        }.also { if (config != null) iceCache = it }
    }

    private suspend fun createPeer(userId: Long) {
        val p = peers.getOrPut(userId) { Peer(userId) }
        if (p.pc != null || p.creating) return
        p.creating = true
        val servers = iceServers()
        val config = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val conn = ensureFactory().createPeerConnection(config, observerFor(p))
        p.creating = false
        if (conn == null) {
            if (state.value?.group == true) closePeer(userId) else end("Couldn't start the call", notify = false)
            return
        }
        audioTrack?.let { conn.addTrack(it, listOf("bants")) }
        localVideo.value?.let { conn.addTrack(it, listOf("bants")) }
        p.pc = conn
        // Signals that arrived while we were setting up.
        val queued = p.pendingSignals.toList()
        p.pendingSignals.clear()
        queued.forEach { handleSignal(p, it) }
    }

    private fun constraints(): MediaConstraints {
        // Voice channels: no OfferToReceive* (it would flip the video slots to send-only).
        if (state.value?.voiceChannelId != null) return MediaConstraints()
        val video = state.value?.video == true
        return MediaConstraints().apply {
            mandatory += MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
            mandatory += MediaConstraints.KeyValuePair("OfferToReceiveVideo", video.toString())
        }
    }

    private suspend fun makeOffer(userId: Long) {
        val p = peers[userId] ?: return
        val conn = p.pc ?: return
        if (state.value?.voiceChannelId != null) voiceSlots(p, offering = true)
        val offer = conn.awaitCreate(true, constraints())
            ?: return if (state.value?.group == true) closePeer(userId) else end("Couldn't start the call", notify = false)
        conn.awaitSetLocal(offer)
        sendSignal(userId, buildJsonObject {
            put("type", "offer")
            put("sdp", offer.description)
        })
    }

    private fun handleSignal(p: Peer, data: JsonObject) {
        val conn = p.pc ?: return
        when (data["type"]?.jsonPrimitive?.contentOrNull) {
            "offer" -> scope.launch {
                val sdp = data["sdp"]?.jsonPrimitive?.contentOrNull ?: return@launch
                // Both sides offered at once: the one with the lower id wins, the other backs down.
                if (conn.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                    if (Bants.myId < p.userId) return@launch
                    conn.awaitSetLocal(SessionDescription(SessionDescription.Type.ROLLBACK, ""))
                }
                conn.awaitSetRemote(SessionDescription(SessionDescription.Type.OFFER, sdp))
                onRemoteDescription(p)
                val voice = state.value?.voiceChannelId != null
                if (voice) voiceSlots(p, offering = false)
                val answer = conn.awaitCreate(false, constraints()) ?: return@launch
                conn.awaitSetLocal(answer)
                sendSignal(p.userId, buildJsonObject {
                    put("type", "answer")
                    put("sdp", answer.description)
                })
                if (voice) applyEncodings(p)
            }
            "answer" -> scope.launch {
                val sdp = data["sdp"]?.jsonPrimitive?.contentOrNull ?: return@launch
                conn.awaitSetRemote(SessionDescription(SessionDescription.Type.ANSWER, sdp))
                onRemoteDescription(p)
                if (state.value?.voiceChannelId != null) applyEncodings(p)
            }
            "candidate" -> {
                val c = IceCandidate(
                    data["sdpMid"]?.jsonPrimitive?.contentOrNull,
                    data["sdpMLineIndex"]?.jsonPrimitive?.intOrNull ?: 0,
                    data["candidate"]?.jsonPrimitive?.contentOrNull ?: return,
                )
                if (p.remoteSet) conn.addIceCandidate(c) else p.pendingCandidates += c
            }
        }
    }

    private fun onRemoteDescription(p: Peer) {
        p.remoteSet = true
        val conn = p.pc ?: return
        p.pendingCandidates.forEach { conn.addIceCandidate(it) }
        p.pendingCandidates.clear()
    }

    private fun sendSignal(to: Long, data: JsonObject) {
        val s = state.value ?: return
        val id = s.callId ?: return
        Bants.realtime.send(buildJsonObject {
            put("type", "call.signal")
            put("callId", id)
            if (s.group) put("to", to)
            put("data", data)
        })
    }

    /** Recomputes "connected / reconnecting" from all the connections. */
    private fun refreshStatus() {
        val s = state.value ?: return
        if (s.status == CallStatus.Ended) return
        val anyConnected = peers.values.any { it.connected }
        state.value = when {
            anyConnected -> s.copy(status = CallStatus.Active, answeredAt = s.answeredAt ?: System.currentTimeMillis(), reconnecting = false)
            s.status == CallStatus.Active -> s.copy(reconnecting = true)
            else -> s
        }
    }

    private fun observerFor(p: Peer) = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            scope.launch {
                sendSignal(p.userId, buildJsonObject {
                    put("type", "candidate")
                    put("candidate", candidate.sdp)
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                })
            }
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
            scope.launch {
                val group = state.value?.group == true
                when (newState) {
                    PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> {
                        p.disconnectJob?.cancel()
                        p.connected = true
                        refreshStatus()
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        p.connected = false
                        refreshStatus()
                        p.disconnectJob?.cancel()
                        p.disconnectJob = scope.launch {
                            delay(15_000)
                            if (!p.connected) {
                                if (group) closePeer(p.userId) else hangUp()
                            }
                        }
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        p.connected = false
                        if (group) {
                            closePeer(p.userId)
                            refreshStatus()
                        } else {
                            hangUp()
                            state.value = state.value?.copy(endedText = "Couldn't connect the call")
                        }
                    }
                    else -> Unit
                }
            }
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            val track = transceiver.receiver.track()
            if (track is VideoTrack) scope.launch {
                if (state.value?.voiceChannelId != null) {
                    // Voice channels: second video slot = their screen, first = their camera.
                    val vids = p.pc?.transceivers?.filter { it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO }.orEmpty()
                    if (vids.indexOfFirst { it.mid == transceiver.mid } == 1) {
                        remoteScreens.value = remoteScreens.value + (p.userId to track)
                        return@launch
                    }
                }
                remoteVideos.value = remoteVideos.value + (p.userId to track)
                if (state.value?.group != true) remoteVideo.value = track
            }
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

    private fun closePeer(userId: Long) {
        val p = peers.remove(userId) ?: return
        p.disconnectJob?.cancel()
        remoteVideos.value = remoteVideos.value - userId
        remoteScreens.value = remoteScreens.value - userId
        runCatching { p.pc?.close() }
        p.pc = null
    }

    // ───────────── audio routing ─────────────

    @Suppress("DEPRECATION")
    private fun setUpAudio(speaker: Boolean) {
        val audio = appContext.getSystemService(AudioManager::class.java)
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        audio.isSpeakerphoneOn = speaker
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
        // Calls keep the connection open; once over, let it drop again if the app is in the background.
        if (!Bants.inForeground) Bants.onBackground()
    }

    private fun releaseMedia() {
        peers.keys.toList().forEach { closePeer(it) }
        peers.clear()
        iceCache = null
        stopScreenShareMedia()
        remoteScreens.value = emptyMap()
        runCatching { capturer?.stopCapture() }
        blurProcessor?.dispose()
        blurProcessor = null
        capturer?.dispose()
        capturer = null
        localVideo.value?.dispose()
        localVideo.value = null
        remoteVideo.value = null
        remoteVideos.value = emptyMap()
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
