@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.wellsey.bants.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.wellsey.bants.data.Bants
import com.wellsey.bants.data.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// ───────────── helpers ─────────────

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.UK, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

private fun Message.localFile(): File? = localUri?.removePrefix("file://")?.let { File(it) }?.takeIf { it.exists() }

fun openFile(context: Context, file: File, mime: String?) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mime?.takeIf { it.isNotBlank() } ?: "*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (e: ActivityNotFoundException) {
        toast(context, "No app on your phone can open this file")
    }
}

// ───────────── voice recording ─────────────

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    fun start(): Boolean {
        val f = Bants.newOutboxFile("m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = f
            startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            r.release()
            f.delete()
            false
        }
    }

    /** Stops and returns the file, or null if it was too short to keep. */
    fun stop(): File? {
        val r = recorder ?: return null
        recorder = null
        val f = file
        val length = System.currentTimeMillis() - startedAt
        return try {
            r.stop()
            r.release()
            if (length < 800) {
                f?.delete()
                null
            } else f
        } catch (e: Exception) {
            r.release()
            f?.delete()
            null
        }
    }

    fun cancel() {
        val r = recorder ?: return
        recorder = null
        runCatching { r.stop() }
        r.release()
        file?.delete()
    }
}

// ───────────── voice note playback ─────────────

object AudioPlayback {
    val playingKey = MutableStateFlow<String?>(null)
    val progress = MutableStateFlow(0f)
    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    fun toggle(key: String, file: File) {
        if (playingKey.value == key) {
            stop()
            return
        }
        stop()
        val p = try {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
            }
        } catch (e: Exception) {
            Bants.notices.tryEmit("Can't play that voice note")
            return
        }
        p.setOnCompletionListener { stop() }
        p.start()
        player = p
        playingKey.value = key
        ticker = Bants.scope.launch {
            while (true) {
                val current = player ?: break
                if (current.duration > 0) progress.value = current.currentPosition.toFloat() / current.duration
                delay(100)
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        playingKey.value = null
        progress.value = 0f
    }
}

@Composable
fun AudioBubble(m: Message, ink: Color, accent: Color) {
    val key = m.clientId ?: m.id.toString()
    val playing by AudioPlayback.playingKey.collectAsState()
    val progress by AudioPlayback.progress.collectAsState()
    var file by remember(m.media, m.localUri) { mutableStateOf(m.localFile()) }
    var duration by remember { mutableLongStateOf(0L) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(m.media) {
        val id = m.media
        if (file == null && id != null) {
            loading = true
            file = runCatching { Bants.mediaFile(id, m.mediaInfo?.filename) }.getOrNull()
            loading = false
        }
    }
    LaunchedEffect(file) {
        val f = file ?: return@LaunchedEffect
        duration = withContext(Dispatchers.IO) {
            runCatching {
                val r = MediaMetadataRetriever()
                r.setDataSource(f.absolutePath)
                val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                r.release()
                d
            }.getOrDefault(0L)
        }
    }
    val isPlaying = playing == key

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(230.dp)) {
        IconButton(onClick = { file?.let { AudioPlayback.toggle(key, it) } }, enabled = file != null) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = ink)
            } else {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play voice note",
                    tint = ink,
                )
            }
        }
        Column(Modifier.weight(1f).padding(end = 4.dp)) {
            LinearProgressIndicator(
                progress = { if (isPlaying) progress else 0f },
                color = accent,
                trackColor = ink.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            Text(
                formatDuration(if (isPlaying) (progress * duration).toLong() else duration),
                fontSize = 12.sp,
                color = ink.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// ───────────── video ─────────────

@Composable
fun VideoTile(m: Message, ink: Color, onOpen: () -> Unit, onLongPress: () -> Unit = {}) {
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier
            .padding(top = 2.dp, bottom = 4.dp)
            .size(width = 240.dp, height = 150.dp)
            .pressScale(press, 0.04f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF17171C))
            .combinedClickable(interactionSource = press, indication = LocalIndication.current, onLongClick = onLongPress, onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play video", tint = Color(0xFF17171C), modifier = Modifier.size(32.dp))
        }
        m.mediaInfo?.size?.takeIf { it > 0 }?.let {
            Text(
                "Video · ${formatSize(it)}",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
        }
    }
}

@Composable
fun VideoDialog(m: Message, onDismiss: () -> Unit) {
    var file by remember { mutableStateOf(m.localFile()) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(m.media) {
        val id = m.media
        if (file == null && id != null) {
            file = runCatching { Bants.mediaFile(id, m.mediaInfo?.filename) }.getOrNull()
            if (file == null) failed = true
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            val f = file
            when {
                f != null -> AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val controls = MediaController(ctx)
                            controls.setAnchorView(this)
                            setMediaController(controls)
                            setVideoPath(f.absolutePath)
                            setOnPreparedListener { start() }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                failed -> Text("Couldn't load the video", color = Color.White)
                else -> CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

// ───────────── files ─────────────

@Composable
fun FileTile(m: Message, ink: Color, onLongPress: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    val info = m.mediaInfo
    val name = info?.filename?.takeIf { it.isNotBlank() } ?: "File"

    val press = remember { MutableInteractionSource() }
    Row(
        Modifier
            .widthIn(min = 200.dp, max = 260.dp)
            .padding(top = 2.dp, bottom = 4.dp)
            .pressScale(press, 0.04f)
            .clip(RoundedCornerShape(10.dp))
            .background(ink.copy(alpha = 0.1f))
            .combinedClickable(interactionSource = press, indication = LocalIndication.current, enabled = !loading, onLongClick = onLongPress) {
                scope.launch {
                    loading = true
                    try {
                        val f = m.localFile() ?: Bants.mediaFile(m.media ?: return@launch, info?.filename)
                        openFile(context, f, info?.mime)
                    } catch (e: Exception) {
                        toast(context, e.message)
                    } finally {
                        loading = false
                    }
                }
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = ink)
        else Icon(Icons.Default.AttachFile, contentDescription = null, tint = ink, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(name, color = ink, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val ext = name.substringAfterLast('.', "").uppercase()
            Text(
                listOfNotNull(info?.size?.takeIf { it > 0 }?.let { formatSize(it) }, ext.takeIf { it.isNotEmpty() && it.length <= 5 }).joinToString(" · "),
                color = ink.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
        }
    }
}
