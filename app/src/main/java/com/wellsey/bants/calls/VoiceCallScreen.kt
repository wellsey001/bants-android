package com.wellsey.bants.calls

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.BlurOff
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.wellsey.bants.data.Bants
import com.wellsey.bants.data.Servers
import com.wellsey.bants.data.VoiceMember
import com.wellsey.bants.ui.Avatar
import com.wellsey.bants.ui.BantsInk
import com.wellsey.bants.ui.BantsPink
import com.wellsey.bants.ui.BantsYellow
import com.wellsey.bants.ui.Bricolage
import com.wellsey.bants.ui.ChunkyChip
import com.wellsey.bants.ui.ChunkyIconButton
import com.wellsey.bants.ui.Pill
import com.wellsey.bants.ui.chunky
import com.wellsey.bants.ui.toast
import org.webrtc.VideoTrack

/** One thing on the voice stage: a person (camera or face) or a screen share. */
private data class StageTile(val key: String, val name: String, val avatar: String?, val track: VideoTrack?, val screen: Boolean, val mine: Boolean, val muted: Boolean)

/**
 * The call screen for voice channels: screen shares big at the top (tap to switch between them),
 * everyone's camera or face underneath, and the controls: mute, speaker, camera, screen share, chat, leave.
 */
@Composable
fun VoiceCallScreen(c: CallUi, status: String, onOpenChat: () -> Unit) {
    val context = LocalContext.current
    val local by CallManager.localVideo.collectAsState()
    val myScreen by CallManager.localScreen.collectAsState()
    val cams by CallManager.remoteVideos.collectAsState()
    val screens by CallManager.remoteScreens.collectAsState()
    val servers by Servers.servers.collectAsState()
    val members by Servers.members.collectAsState()
    val channel = c.voiceChannelId?.let { id -> servers.values.firstNotNullOfOrNull { s -> s.channels.firstOrNull { it.id == id } } }
    val flags: Map<Long, VoiceMember> = channel?.voiceMembers.orEmpty().associateBy { it.id }
    val me = Bants.me.value
    var focus by remember { mutableStateOf<String?>(null) }
    var askingQuality by remember { mutableStateOf(false) }
    var pendingQuality by remember { mutableStateOf("1080" to 30) }

    val projection = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == Activity.RESULT_OK && data != null) CallManager.startScreenShare(data, pendingQuality.first, pendingQuality.second)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) CallManager.toggleVoiceCamera() else toast(context, "Bants needs the camera for that")
    }

    // Build the tiles.
    val screenTiles = buildList {
        myScreen?.let { add(StageTile("me-screen", "Your screen", null, it, screen = true, mine = true, muted = false)) }
        c.participants.forEach { p ->
            val t = screens[p.id]
            if (flags[p.id]?.screen == true && t != null) add(StageTile("s${p.id}", "${p.name}'s screen", p.avatar, t, screen = true, mine = false, muted = false))
        }
    }
    val people = buildList {
        add(StageTile("me", me?.displayName ?: "You", me?.avatar, if (c.cameraOn) local else null, screen = false, mine = true, muted = c.muted))
        c.participants.forEach { p ->
            val f = flags[p.id]
            add(StageTile("u${p.id}", p.name, p.avatar, if (f?.video == true) cams[p.id] else null, screen = false, mine = false, muted = f?.muted == true))
        }
    }
    val big = screenTiles.firstOrNull { it.key == focus } ?: screenTiles.firstOrNull { !it.mine } ?: screenTiles.firstOrNull()

    Column(Modifier.fillMaxSize().background(Color(0xFF111015)).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.peerName, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Pill(status)
            }
            ChunkyIconButton(Icons.AutoMirrored.Filled.Chat, "Open the voice channel's chat", onOpenChat, fill = Color.White, tint = BantsInk, size = 46.dp)
        }

        // Screen shares
        if (big != null) {
            Box(
                Modifier.fillMaxWidth().weight(1.3f).padding(horizontal = 10.dp)
                    .chunky(RoundedCornerShape(16.dp), Color.Black, BantsInk, shadow = 0.dp, border = 2.dp),
            ) {
                VideoTile(big.track!!, mirror = false, fit = true, modifier = Modifier.fillMaxSize().padding(2.dp))
                TileLabel(big, Modifier.align(Alignment.BottomStart))
            }
            if (screenTiles.size > 1) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    screenTiles.forEach { t -> ChunkyChip(t.name, t.key == big.key) { focus = t.key } }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // People
        val columns = if (people.size <= 1) 1 else 2
        Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            people.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { t ->
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .chunky(RoundedCornerShape(16.dp), if (t.track != null) Color.Black else Color.White.copy(alpha = 0.10f), BantsInk, shadow = 0.dp, border = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (t.track != null) VideoTile(t.track, mirror = t.mine && c.frontCamera, fit = false, modifier = Modifier.fillMaxSize().padding(2.dp))
                            else Avatar(t.name, t.avatar, size = if (people.size <= 2) 96.dp else 64.dp)
                            TileLabel(t, Modifier.align(Alignment.BottomStart))
                        }
                    }
                    if (row.size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }

        // Controls
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChunkyIconButton(if (c.muted) Icons.Default.MicOff else Icons.Default.Mic, if (c.muted) "Unmute" else "Mute", { CallManager.toggleMute() },
                fill = if (c.muted) BantsYellow else Color.White, tint = BantsInk, size = 52.dp)
            ChunkyIconButton(if (c.speaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown, if (c.speaker) "Speaker off" else "Speaker on", { CallManager.toggleSpeaker() },
                fill = if (c.speaker) BantsYellow else Color.White, tint = BantsInk, size = 52.dp)
            ChunkyIconButton(if (c.cameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff, if (c.cameraOn) "Camera off" else "Camera on", {
                if (c.cameraOn || has(context, Manifest.permission.CAMERA)) CallManager.toggleVoiceCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
            }, fill = if (c.cameraOn) BantsYellow else Color.White, tint = BantsInk, size = 52.dp)
            if (c.cameraOn) {
                ChunkyIconButton(Icons.Default.Cameraswitch, "Flip camera", { CallManager.flipCamera() }, fill = Color.White, tint = BantsInk, size = 52.dp)
                ChunkyIconButton(if (c.blur) Icons.Default.BlurOn else Icons.Default.BlurOff, if (c.blur) "Background blur off" else "Blur background", { CallManager.toggleBlur() },
                    fill = if (c.blur) BantsYellow else Color.White, tint = BantsInk, size = 52.dp)
            }
            ChunkyIconButton(if (c.sharingScreen) Icons.Default.StopScreenShare else Icons.Default.ScreenShare, if (c.sharingScreen) "Stop sharing" else "Share your screen", {
                if (c.sharingScreen) CallManager.stopScreenShare() else askingQuality = true
            }, fill = if (c.sharingScreen) BantsPink else Color.White, tint = BantsInk, size = 52.dp)
            ChunkyIconButton(Icons.Default.CallEnd, "Leave voice", { CallManager.hangUp() }, fill = BantsPink, tint = BantsInk, size = 52.dp)
        }
    }

    if (askingQuality) ScreenQualityDialog(
        others = c.participants.size,
        onDismiss = { askingQuality = false },
    ) { res, fps ->
        askingQuality = false
        pendingQuality = res to fps
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection.launch(mpm.createScreenCaptureIntent())
    }
}

private fun has(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
private fun TileLabel(t: StageTile, modifier: Modifier) {
    Row(
        modifier.padding(8.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (t.screen) {
            Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.background(Color(0xFFE5484D), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(t.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (t.muted) Text("  🔇", fontSize = 12.sp)
    }
}

/** Discord-style: pick resolution and frame rate before going live. */
@Composable
private fun ScreenQualityDialog(others: Int, onDismiss: () -> Unit, onGo: (String, Int) -> Unit) {
    var res by remember { mutableStateOf(Bants.session.callScreenRes) }
    var fps by remember { mutableStateOf(Bants.session.callScreenFps) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share your screen", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Resolution", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("720" to "720p", "1080" to "1080p", "source" to "Source").forEach { (k, l) -> ChunkyChip(l, res == k) { res = k } }
                }
                Text("Frame rate", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 60).forEach { f -> ChunkyChip("$f fps", fps == f) { fps = f } }
                }
                Text(
                    (if (fps >= 60) "Smooth for games and videos. " else if (fps <= 15) "Best for slides and documents. " else "Good for most things. ") +
                        (if (others > 1) "You're sending to $others people, so higher settings need good signal." else "Uses more battery and data than voice."),
                    fontSize = 13.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onGo(res, fps) }) { Text("Go live", fontWeight = FontWeight.ExtraBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
