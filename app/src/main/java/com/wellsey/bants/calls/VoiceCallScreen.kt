package com.wellsey.bants.calls

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
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
    // Everything on the stage: screen shares first, then people. Like Discord, tap any tile to focus it.
    val tiles = screenTiles + people
    // Someone else going live gets focused automatically, until you pick something else.
    var autoFocused by remember { mutableStateOf<String?>(null) }
    val liveKey = screenTiles.firstOrNull { !it.mine }?.key
    LaunchedEffect(liveKey) { if (liveKey != null && liveKey != autoFocused) { focus = liveKey; autoFocused = liveKey } }
    // If whoever was focused leaves or stops sharing, go back to the grid.
    val focused = tiles.firstOrNull { it.key == focus }
    var more by remember { mutableStateOf(false) }

    @Composable
    fun Tile(t: StageTile, modifier: Modifier, small: Boolean = false) {
        key(t.key) {
            Box(
                modifier
                    .chunky(RoundedCornerShape(if (small) 12.dp else 16.dp), if (t.track != null) Color.Black else Color(0xFF2A2833), if (t.key == focus && small) BantsYellow else BantsInk, shadow = 0.dp, border = 2.dp)
                    .clickable { focus = if (focus == t.key) null else t.key },
                contentAlignment = Alignment.Center,
            ) {
                if (t.track != null) {
                    // Cameras fill their tile (cropped, like Discord); screens are shown whole.
                    VideoTile(t.track, mirror = t.mine && !t.screen && c.frontCamera, fit = t.screen, modifier = Modifier.fillMaxSize().padding(2.dp))
                } else {
                    Avatar(t.name, t.avatar, size = if (small) 40.dp else if (tiles.size <= 2) 96.dp else 64.dp)
                }
                if (small) {
                    Text(t.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(5.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
                } else {
                    TileLabel(t, Modifier.align(Alignment.BottomStart))
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF111015)).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.peerName, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Pill(status)
            }
            ChunkyIconButton(Icons.AutoMirrored.Filled.Chat, "Open the voice channel's chat", onOpenChat, fill = Color.White, tint = BantsInk, size = 46.dp)
        }

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp)) {
            val portrait = maxHeight > maxWidth
            val gap = 8.dp
            when {
                // One tile big, everyone else in a strip underneath (or down the side in landscape).
                focused != null && tiles.size > 1 -> {
                    val rest = tiles.filter { it.key != focused.key }
                    if (portrait) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                            Tile(focused, Modifier.fillMaxWidth().weight(1f))
                            Row(Modifier.fillMaxWidth().height(118.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                rest.forEach { Tile(it, Modifier.width(96.dp).fillMaxHeight(), small = true) }
                            }
                        }
                    } else {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            Tile(focused, Modifier.weight(1f).fillMaxHeight())
                            Column(Modifier.width(150.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(gap)) {
                                rest.forEach { Tile(it, Modifier.fillMaxWidth().height(96.dp), small = true) }
                            }
                        }
                    }
                }
                tiles.size == 1 -> Tile(tiles[0], Modifier.fillMaxSize())
                // Two people: one above the other in portrait, side by side in landscape.
                tiles.size == 2 -> if (portrait) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                        tiles.forEach { Tile(it, Modifier.fillMaxWidth().weight(1f)) }
                    }
                } else {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        tiles.forEach { Tile(it, Modifier.weight(1f).fillMaxHeight()) }
                    }
                }
                // Up to four (six in landscape): an even grid that fills the space.
                tiles.size <= (if (portrait) 4 else 6) -> {
                    val cols = if (portrait) 2 else 3
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                        tiles.chunked(cols).forEach { row ->
                            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                row.forEach { Tile(it, Modifier.weight(1f).fillMaxHeight()) }
                                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                // Loads of people: a scrolling grid of fixed-shape tiles.
                else -> {
                    val cols = if (portrait) 2 else 4
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(gap)) {
                        tiles.chunked(cols).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                row.forEach { Tile(it, Modifier.weight(1f).aspectRatio(if (portrait) 0.8f else 16f / 9f)) }
                                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }

        // Controls: the everyday ones on the bar, camera extras under ⋯ so nothing falls off the edge.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val sz = 50.dp
            ChunkyIconButton(if (c.muted) Icons.Default.MicOff else Icons.Default.Mic, if (c.muted) "Unmute" else "Mute", { CallManager.toggleMute() },
                fill = if (c.muted) BantsYellow else Color.White, tint = BantsInk, size = sz)
            ChunkyIconButton(if (c.cameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff, if (c.cameraOn) "Camera off" else "Camera on", {
                if (c.cameraOn || has(context, Manifest.permission.CAMERA)) CallManager.toggleVoiceCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
            }, fill = if (c.cameraOn) BantsYellow else Color.White, tint = BantsInk, size = sz)
            ChunkyIconButton(if (c.sharingScreen) Icons.Default.StopScreenShare else Icons.Default.ScreenShare, if (c.sharingScreen) "Stop sharing" else "Share your screen", {
                if (c.sharingScreen) CallManager.stopScreenShare() else askingQuality = true
            }, fill = if (c.sharingScreen) BantsPink else Color.White, tint = BantsInk, size = sz)
            ChunkyIconButton(if (c.speaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown, if (c.speaker) "Speaker off" else "Speaker on", { CallManager.toggleSpeaker() },
                fill = if (c.speaker) BantsYellow else Color.White, tint = BantsInk, size = sz)
            Box {
                ChunkyIconButton(Icons.Default.MoreHoriz, "More", { more = true }, fill = Color.White, tint = BantsInk, size = sz)
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(
                        text = { Text("Flip camera") }, leadingIcon = { Icon(Icons.Default.Cameraswitch, null) }, enabled = c.cameraOn,
                        onClick = { more = false; CallManager.flipCamera() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (c.blur) "Background blur off" else "Blur background") }, leadingIcon = { Icon(if (c.blur) Icons.Default.BlurOff else Icons.Default.BlurOn, null) }, enabled = c.cameraOn,
                        onClick = { more = false; CallManager.toggleBlur() },
                    )
                    if (focus != null) DropdownMenuItem(
                        text = { Text("Show everyone") }, leadingIcon = { Icon(Icons.Default.GridView, null) },
                        onClick = { more = false; focus = null },
                    )
                }
            }
            ChunkyIconButton(Icons.Default.CallEnd, "Leave voice", { CallManager.hangUp() }, fill = BantsPink, tint = BantsInk, size = sz)
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
