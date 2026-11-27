package com.wellsey.bants.calls

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.BlurOff
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.wellsey.bants.data.Servers
import com.wellsey.bants.ui.Avatar
import com.wellsey.bants.ui.BantsInk
import com.wellsey.bants.ui.BantsPink
import com.wellsey.bants.ui.BantsPurple
import com.wellsey.bants.ui.BantsTheme
import com.wellsey.bants.ui.BantsYellow
import com.wellsey.bants.ui.Bricolage
import com.wellsey.bants.ui.ChunkyIconButton
import com.wellsey.bants.ui.Pill
import com.wellsey.bants.ui.chunky
import com.wellsey.bants.ui.formatDuration
import kotlinx.coroutines.delay
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class CallActivity : ComponentActivity() {
    private var proximity: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val answerNow = intent?.action == ACTION_ANSWER
        setContent {
            BantsTheme {
                CallScreen(answerNow = answerNow, onDone = { finish() }, onProximity = ::setProximity)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_ANSWER && hasPermissions(CallManager.state.value?.video == true)) CallManager.accept()
    }

    private fun hasPermissions(video: Boolean): Boolean {
        val need = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (video) need += Manifest.permission.CAMERA
        return need.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    /** Screen goes dark when the phone is at your ear during a voice call. */
    private fun setProximity(on: Boolean) {
        val pm = getSystemService(PowerManager::class.java)
        if (on && proximity == null && pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "bants:call").apply { acquire(4 * 60 * 60 * 1000L) }
        } else if (!on) {
            proximity?.let { if (it.isHeld) it.release() }
            proximity = null
        }
    }

    override fun onDestroy() {
        setProximity(false)
        CallManager.inPip.value = false
        super.onDestroy()
    }

    /** Leave the app during a call and it carries on in a little floating window. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (skipPip) { skipPip = false; return }
        val c = CallManager.state.value ?: return
        if (c.status != CallStatus.Active || Build.VERSION.SDK_INT < 26) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching {
            val wide = CallManager.remoteScreens.value.isNotEmpty()
            enterPictureInPictureMode(
                android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(if (wide) android.util.Rational(16, 9) else android.util.Rational(9, 16))
                    .build(),
            )
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        CallManager.inPip.value = isInPictureInPictureMode
    }

    companion object {
        const val ACTION_ANSWER = "com.wellsey.bants.ANSWER_CALL"
        /** Set when we open the app ourselves (the chat button), so that doesn't shrink the call. */
        @Volatile var skipPip = false
    }
}

@Composable
private fun CallScreen(answerNow: Boolean, onDone: () -> Unit, onProximity: (Boolean) -> Unit) {
    val call by CallManager.state.collectAsState()
    val local by CallManager.localVideo.collectAsState()
    val remote by CallManager.remoteVideo.collectAsState()
    val remotes by CallManager.remoteVideos.collectAsState()
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) CallManager.accept()
        else {
            CallManager.decline()
            com.wellsey.bants.ui.toast(context, "Bants needs the microphone (and camera for video) to answer calls")
        }
    }
    fun answer() {
        val c = call ?: return
        val need = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (c.video) need += Manifest.permission.CAMERA
        if (need.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) CallManager.accept()
        else permissions.launch(need.toTypedArray())
    }

    LaunchedEffect(Unit) { if (answerNow) answer() }
    LaunchedEffect(call == null) { if (call == null) onDone() }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val c = call ?: return
    LaunchedEffect(c.video, c.speaker, c.status) {
        onProximity(!c.video && !c.speaker && c.status != CallStatus.Ended && c.status != CallStatus.Incoming)
    }
    BackHandler(enabled = c.status == CallStatus.Incoming) { /* decide first: answer or decline */ }

    if (c.voiceChannelId != null) {
        val status = when {
            c.endedText != null -> c.endedText
            c.reconnecting -> "Reconnecting…"
            c.status == CallStatus.Active -> "${c.participants.size + 1} in voice · " + formatDuration(now - (c.answeredAt ?: now))
            c.status == CallStatus.Ended -> "Left voice"
            else -> "Connecting…"
        }
        VoiceCallScreen(c, status) {
            // Jump to the voice channel's text chat in the main app; the call keeps going.
            Servers.openChannelRequest.value = c.voiceChannelId
            CallActivity.skipPip = true
            context.startActivity(
                android.content.Intent(context, com.wellsey.bants.MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
            onDone()
        }
        return
    }
    val pip by CallManager.inPip.collectAsState()
    if (pip) {
        Box(Modifier.fillMaxSize().background(if (remote != null && c.video) Color.Black else BantsPurple), contentAlignment = Alignment.Center) {
            val r = remote
            if (r != null && c.video) VideoView(r, mirror = false, modifier = Modifier.fillMaxSize())
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                com.wellsey.bants.ui.Avatar(c.peerName, c.peerAvatar, size = 56.dp)
                Text(formatDuration(now - (c.answeredAt ?: now)), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        return
    }
    val showGrid = c.group && c.participants.isNotEmpty() && c.status != CallStatus.Incoming && c.status != CallStatus.Ended
    val showRemote = !c.group && c.video && remote != null && c.status == CallStatus.Active
    Box(Modifier.fillMaxSize().background(if (showRemote || (showGrid && c.video)) Color.Black else BantsPurple)) {
        if (showRemote) VideoView(remote!!, mirror = false, modifier = Modifier.fillMaxSize())
        if (showGrid) {
            ParticipantGrid(
                c.participants, remotes, c.video,
                Modifier.fillMaxSize().statusBarsPadding().padding(top = 96.dp, bottom = 230.dp, start = 10.dp, end = 10.dp),
            )
        }

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!showRemote && !showGrid) {
                Spacer(Modifier.height(48.dp))
                Avatar(c.peerName, c.peerAvatar, size = 140.dp, group = c.group)
                Spacer(Modifier.height(18.dp))
            }
            Text(
                c.peerName,
                fontFamily = Bricolage,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (showRemote || showGrid) 22.sp else 34.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            val status = when {
                c.endedText != null -> c.endedText
                c.reconnecting -> "Reconnecting…"
                c.status == CallStatus.Calling -> "Calling…"
                c.status == CallStatus.Ringing -> if (c.group) "Ringing the group…" else "Ringing…"
                c.status == CallStatus.Incoming && c.group -> "${c.callerName ?: "Someone"} is calling the group"
                c.status == CallStatus.Incoming -> if (c.video) "Incoming video call" else "Incoming voice call"
                c.group && c.status == CallStatus.Active ->
                    "${c.participants.size + 1} on the call · " + formatDuration(now - (c.answeredAt ?: now))
                c.status == CallStatus.Connecting -> "Connecting…"
                c.status == CallStatus.Active -> formatDuration(now - (c.answeredAt ?: now))
                else -> "Call ended"
            }
            Spacer(Modifier.height(8.dp))
            Pill(status)
            Spacer(Modifier.weight(1f))

            if (c.status == CallStatus.Incoming) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BigButton(Icons.Default.CallEnd, "Decline", BantsPink, BantsInk) { CallManager.decline() }
                    BigButton(if (c.video) Icons.Default.Videocam else Icons.Default.Call, "Answer", Color(0xFF2ECC71), BantsInk) { answer() }
                }
            } else if (c.status != CallStatus.Ended) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ChunkyIconButton(
                        if (c.muted) Icons.Default.MicOff else Icons.Default.Mic,
                        if (c.muted) "Unmute" else "Mute",
                        { CallManager.toggleMute() },
                        fill = if (c.muted) BantsYellow else Color.White,
                        tint = BantsInk,
                        size = 58.dp,
                    )
                    ChunkyIconButton(
                        if (c.speaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                        if (c.speaker) "Speaker off" else "Speaker on",
                        { CallManager.toggleSpeaker() },
                        fill = if (c.speaker) BantsYellow else Color.White,
                        tint = BantsInk,
                        size = 58.dp,
                    )
                    if (c.video) {
                        ChunkyIconButton(
                            if (c.cameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            if (c.cameraOn) "Camera off" else "Camera on",
                            { CallManager.toggleCamera() },
                            fill = if (!c.cameraOn) BantsYellow else Color.White,
                            tint = BantsInk,
                            size = 58.dp,
                        )
                        ChunkyIconButton(Icons.Default.Cameraswitch, "Flip camera", { CallManager.flipCamera() }, fill = Color.White, tint = BantsInk, size = 58.dp)
                        ChunkyIconButton(
                            if (c.blur) Icons.Default.BlurOn else Icons.Default.BlurOff,
                            if (c.blur) "Background blur off" else "Blur background",
                            { CallManager.toggleBlur() },
                            fill = if (c.blur) BantsYellow else Color.White,
                            tint = BantsInk,
                            size = 58.dp,
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
                BigButton(Icons.Default.CallEnd, "Hang up", BantsPink, BantsInk) { CallManager.hangUp() }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (c.video && local != null && c.cameraOn && c.status != CallStatus.Ended) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .size(width = 112.dp, height = 160.dp)
                    .chunky(RoundedCornerShape(16.dp), Color.Black, BantsInk, shadow = 3.dp),
            ) {
                VideoView(local!!, mirror = c.frontCamera, overlay = true, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun BigButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, fill: Color, tint: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ChunkyIconButton(icon, label, onClick, fill = fill, tint = tint, size = 74.dp, shadow = 4.dp)
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun VideoTile(track: VideoTrack, mirror: Boolean, fit: Boolean, modifier: Modifier) = VideoView(track, mirror, modifier, fit = fit)

@Composable
private fun VideoView(track: VideoTrack, mirror: Boolean, modifier: Modifier, overlay: Boolean = false, fit: Boolean = false) {
    val context = LocalContext.current
    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(CallManager.egl.eglBaseContext, null)
            setScalingType(if (fit) RendererCommon.ScalingType.SCALE_ASPECT_FIT else RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setEnableHardwareScaler(true)
            if (overlay) setZOrderMediaOverlay(true)
        }
    }
    DisposableEffect(track) {
        track.addSink(renderer)
        onDispose { runCatching { track.removeSink(renderer) } }
    }
    DisposableEffect(Unit) { onDispose { renderer.release() } }
    AndroidView(factory = { renderer }, update = { it.setMirror(mirror) }, modifier = modifier)
}


/** Everyone else in a group call: their video, or their face if there's no video. */
@Composable
private fun ParticipantGrid(people: List<CallPerson>, videos: Map<Long, VideoTrack>, video: Boolean, modifier: Modifier) {
    val columns = if (people.size <= 1) 1 else 2
    val rows = people.chunked(columns)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .chunky(RoundedCornerShape(18.dp), if (video) Color.Black else Color.White.copy(alpha = 0.12f), BantsInk, shadow = 0.dp, border = 2.5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val track = videos[p.id]
                        if (video && track != null) {
                            VideoView(track, mirror = false, modifier = Modifier.fillMaxSize().padding(2.5.dp))
                        } else {
                            Avatar(p.name, p.avatar, size = if (people.size <= 2) 110.dp else 76.dp)
                        }
                        Text(
                            p.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                        )
                    }
                }
                if (row.size < columns) Spacer(Modifier.weight(1f))
            }
        }
    }
}
