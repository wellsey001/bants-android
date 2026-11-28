package com.wellsey.bants.ui

import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState

import androidx.compose.foundation.horizontalScroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellsey.bants.data.Bants

/** Settings → Voice & video: noise reduction, echo, mic volume and background blur for calls. */
@Composable
fun CallSettingsScreen(nav: Navigator) {
    val b = LocalBants.current
    val session = Bants.session
    var noise by remember { mutableStateOf(session.callNoise) }
    var echo by remember { mutableStateOf(session.callEcho) }
    var autoGain by remember { mutableStateOf(session.callAutoGain) }
    var blur by remember { mutableStateOf(session.callBlur) }
    var camRes by remember { mutableStateOf(session.callCamRes) }
    var camFps by remember { mutableStateOf(session.callCamFps) }
    var codec by remember { mutableStateOf(session.callCodec) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pickBg = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            // Keep a copy (shrunk to 1280px) in the app's own storage.
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val src = context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) } ?: error("Couldn't read that picture")
                    val sc = minOf(1f, 1280f / maxOf(src.width, src.height))
                    val bmp = android.graphics.Bitmap.createScaledBitmap(src, (src.width * sc).toInt().coerceAtLeast(1), (src.height * sc).toInt().coerceAtLeast(1), true)
                    val f = java.io.File(context.filesDir, "call-background.jpg")
                    f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
                    f.absolutePath
                }
            }.onSuccess { session.callBgImage = it; blur = "image"; session.callBlur = "image" }.onFailure { toast(context, it.message) }
        }
    }
    val me by Bants.me.collectAsState()
    var soundKey by remember { mutableStateOf<String?>(null) }
    val pickSound = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        val key = soundKey
        if (uri != null && key != null) scope.launch {
            runCatching { com.wellsey.bants.data.VoiceExtras.setOwnSound(context, key, uri) }.onSuccess { toast(context, "Saved") }.onFailure { toast(context, it.message) }
        }
    }
    var saver by remember { mutableStateOf(session.callDataSaver) }

    Scaffold(topBar = { ChunkyTopBar("Voice & video", nav) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Heading("BACKGROUND NOISE REDUCTION")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("off" to "Off", "standard" to "Standard", "strong" to "Strong").forEach { (k, label) ->
                    ChunkyChip(label, noise == k) { noise = k; session.callNoise = k }
                }
            }
            Text(
                when (noise) {
                    "off" -> "Everything your mic hears goes through. Best for playing music down the phone."
                    "strong" -> "Also cuts out keyboards, clicks and the telly between sentences. Only your voice gets through when you're talking."
                    else -> "Your phone's noise filter plus Bants' own. Removes hum, fans, wind and hiss."
                },
                style = MaterialTheme.typography.bodySmall, color = b.onCard,
            )

            Heading("YOUR MIC")
            Toggle("Echo cancellation", "Stops people hearing themselves through your loudspeaker.", echo) { echo = it; session.callEcho = it }
            Toggle("Automatic mic volume", "Evens out your volume whether you whisper or shout.", autoGain) { autoGain = it; session.callAutoGain = it }

            Heading("BACKGROUND BLUR")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("off" to "Off", "light" to "Light", "strong" to "Strong").forEach { (k, label) ->
                    ChunkyChip(label, blur == k) { blur = k; session.callBlur = k }
                }
                ChunkyChip("🖼 Picture", blur == "image") { if (session.callBgImage != null) { blur = "image"; session.callBlur = "image" } else pickBg.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            }
            if (blur == "image") ChunkyChip("Change picture", false) { pickBg.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            Text(
                when (blur) {
                    "off" -> "People see your camera as it is."
                    "strong" -> "Your background is blurred so much it's just colours. Nobody's seeing your washing."
                    "image" -> "Your own picture behind you, like a virtual background."
                    else -> "Your background is softly blurred so you stand out."
                },
                style = MaterialTheme.typography.bodySmall, color = b.onCard,
            )
            Text(
                "Blur is worked out on your phone, so it uses more battery and older phones may send a slightly jerkier picture. You can also turn it on and off during a call.",
                style = MaterialTheme.typography.bodySmall, color = b.muted,
            )

            Heading("CAMERA IN VOICE CHANNELS")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("360" to "360p", "720" to "720p", "1080" to "1080p").forEach { (k, label) ->
                    ChunkyChip(label, camRes == k) { camRes = k; session.callCamRes = k }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30).forEach { f -> ChunkyChip("$f fps", camFps == f) { camFps = f; session.callCamFps = f } }
            }
            Text(
                "Higher looks sharper but uses more data, and everyone in the channel gets their own copy. Screen share quality is picked each time you go live.",
                style = MaterialTheme.typography.bodySmall, color = b.muted,
            )

            Heading("VIDEO FORMAT")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("auto" to "Auto (VP8)", "vp9" to "VP9", "h264" to "H.264", "av1" to "AV1").forEach { (k, label) ->
                    ChunkyChip(label, codec == k) { codec = k; session.callCodec = k }
                }
            }
            Text(
                when (codec) {
                    "vp9" -> "Sharper for the same data, but works your phone harder and uses more battery."
                    "h264" -> "Easiest on the battery because your phone's video chip does the work. Some phones don't get on with each other's H.264, so if someone's picture goes black, switch back to Auto."
                    "av1" -> "Best picture for the data, on phones that can send it. Phones that can't fall back to VP8 by themselves."
                    else -> "The safe choice: every phone and the desktop app can always show it."
                },
                style = MaterialTheme.typography.bodySmall, color = b.onCard,
            )
            Text("Both sides have to support a format to use it, otherwise the call quietly uses VP8.", style = MaterialTheme.typography.bodySmall, color = b.muted)

            Heading("JOIN AND LEAVE")
            var chimes by remember { mutableStateOf(Bants.session.voiceChimes) }
            Toggle("Join and leave chimes", "Play a chime when people join or leave a call or voice channel", chimes) {
                chimes = it
                Bants.session.voiceChimes = it
            }

            Heading("YOUR VOICE CHANNEL SOUNDS")
            listOf("joinSound" to "When you join", "leaveSound" to "When you leave").forEach { (key, label) ->
                val current = if (key == "joinSound") me?.joinSound else me?.leaveSound
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$label: ${if (current != null) "your sound" else "none"}", color = b.onCard, modifier = Modifier.weight(1f))
                    if (current != null) ChunkyChip("▶", false) { scope.launch { com.wellsey.bants.data.VoiceExtras.play(current, 0.8f) } }
                    ChunkyChip(if (current != null) "Change" else "Pick", false) { soundKey = key; pickSound.launch("audio/*") }
                    if (current != null) ChunkyChip("Remove", false) { scope.launch { runCatching { com.wellsey.bants.data.VoiceExtras.setOwnSound(context, key, null) } } }
                }
            }
            Text("Everyone in the voice channel hears it. Keep it short: a few seconds, under 1 MB.", style = MaterialTheme.typography.bodySmall, color = b.muted)

            Heading("DATA")
            Toggle("Data saver on mobile data", "Sends a lighter picture when you're not on Wi-Fi. Voice is unaffected.", saver) { saver = it; session.callDataSaver = it }

            ChunkyCard(Modifier.fillMaxWidth(), shadow = 0.dp) {
                Text(
                    "Noise, mic and video format changes apply from your next call or voice channel. Blur changes straight away. Long-press someone in a voice channel to change how loud they are for you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = b.muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = LocalBants.current.muted, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = LocalBants.current.onCard)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = LocalBants.current.muted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
