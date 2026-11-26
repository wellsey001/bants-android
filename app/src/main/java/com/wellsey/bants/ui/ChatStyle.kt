package com.wellsey.bants.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellsey.bants.data.Bants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

/** How one chat looks on this phone: wallpaper pattern, its colour, and your bubble colour. */
data class ChatStyle(val pattern: String = "plain", val tint: Long = 0, val bubble: Long = 0) {
    companion object {
        val Patterns = listOf("plain" to "Plain", "dots" to "Dots", "zigzag" to "Zigzag", "confetti" to "Confetti", "grid" to "Grid", "photo" to "Photo")
        val Tints = listOf(0xFFFFE9A8, 0xFFFFD1E1, 0xFFC9F2DD, 0xFFCFE3FF, 0xFFE2D9FF, 0xFFFFDCC7, 0xFF1B1A22)
        val Bubbles = listOf(0xFF5B4BFF, 0xFFFF4D8D, 0xFF16A36F, 0xFF2D7FF9, 0xFFFF7A1A, 0xFF111111, 0xFFFFD23F)

        fun load(convId: Long): ChatStyle {
            val raw = Bants.session.chatStyle(convId) ?: return ChatStyle()
            val p = raw.split('|')
            return ChatStyle(p.getOrElse(0) { "plain" }, p.getOrNull(1)?.toLongOrNull() ?: 0, p.getOrNull(2)?.toLongOrNull() ?: 0)
        }

        fun save(convId: Long, s: ChatStyle) = Bants.session.setChatStyle(convId, "${s.pattern}|${s.tint}|${s.bubble}")

        fun photoFile(convId: Long) = File(Bants.appContext.filesDir, "wallpapers/chat_$convId.jpg")
    }
}

/** Your bubble colour in this chat, and the text colour that reads well on it. */
fun ChatStyle.myBubble(): Color = if (bubble != 0L) Color(bubble) else BantsPurple
fun ChatStyle.myInk(): Color = if (myBubble().luminance() > 0.5f) BantsInk else Color.White

/** Draws the chat's wallpaper behind everything. */
@Composable
fun ChatWallpaper(convId: Long, style: ChatStyle, modifier: Modifier = Modifier) {
    val fallback = MaterialTheme.colorScheme.background
    val base = if (style.tint != 0L) Color(style.tint) else fallback
    val dark = base.luminance() < 0.3f
    val mark = if (dark) Color.White.copy(alpha = 0.08f) else BantsInk.copy(alpha = 0.08f)
    Box(modifier.fillMaxSize().background(base)) {
        when (style.pattern) {
            "photo" -> {
                val file = ChatStyle.photoFile(convId)
                val bmp = remember(file.lastModified()) { if (file.exists()) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null }
                if (bmp != null) {
                    androidx.compose.foundation.Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    // Dim it a little so bubbles stay readable.
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
                }
            }
            "dots" -> Canvas(Modifier.fillMaxSize()) {
                val step = 26.dp.toPx()
                var row = 0
                var y = 0f
                while (y < size.height + step) {
                    var x = if (row % 2 == 0) 0f else step / 2
                    while (x < size.width + step) { drawCircle(mark, 3.5.dp.toPx(), Offset(x, y)); x += step }
                    y += step * 0.87f; row++
                }
            }
            "zigzag" -> Canvas(Modifier.fillMaxSize()) {
                val step = 22.dp.toPx()
                var y = 0f
                while (y < size.height + step) {
                    val path = Path().apply {
                        moveTo(0f, y)
                        var x = 0f
                        var up = true
                        while (x < size.width + step) { x += step / 2; lineTo(x, if (up) y - step / 3 else y); up = !up }
                    }
                    drawPath(path, mark, style = Stroke(2.5.dp.toPx()))
                    y += step * 1.4f
                }
            }
            "grid" -> Canvas(Modifier.fillMaxSize()) {
                val step = 28.dp.toPx()
                var x = 0f
                while (x < size.width) { drawLine(mark, Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx()); x += step }
                var y = 0f
                while (y < size.height) { drawLine(mark, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx()); y += step }
            }
            "confetti" -> Canvas(Modifier.fillMaxSize()) {
                val rnd = Random(convId)
                val colours = listOf(BantsPurple, BantsPink, BantsYellow, Color(0xFF16A36F), Color(0xFF2D7FF9))
                val count = (size.width * size.height / (60.dp.toPx() * 60.dp.toPx())).toInt()
                repeat(count) {
                    val c = colours[rnd.nextInt(colours.size)].copy(alpha = if (dark) 0.35f else 0.28f)
                    val at = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
                    rotate(rnd.nextFloat() * 360f, at) {
                        drawRoundRect(c, at, Size(10.dp.toPx(), 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                    }
                }
            }
        }
    }
}

/** Pick a wallpaper and your bubble colour for one chat. */
@Composable
fun ChatStyleScreen(convId: Long, nav: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val b = LocalBants.current
    var style by remember { mutableStateOf(ChatStyle.load(convId)) }
    var photoVersion by remember { mutableStateOf(0) }
    fun update(s: ChatStyle) { style = s; ChatStyle.save(convId, s) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { Bants.saveWallpaper(uri, ChatStyle.photoFile(convId)) } }
                .onSuccess { photoVersion++; update(style.copy(pattern = "photo")) }
                .onFailure { toast(context, it.message) }
        }
    }
    Scaffold(topBar = { ChunkyTopBar("Wallpaper & colours", nav) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Live preview.
            Box(Modifier.fillMaxWidth().height(200.dp).chunky(RoundedCornerShape(20.dp), b.card, b.outline, shadow = 4.dp).clip(RoundedCornerShape(20.dp))) {
                androidx.compose.runtime.key(photoVersion) { ChatWallpaper(convId, style) }
                Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.chunky(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp), b.theirBubble, b.outline, shadow = 3.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text("mate, new wallpaper?", color = b.onCard)
                    }
                    Box(Modifier.align(Alignment.End).chunky(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp), style.myBubble(), b.outline, shadow = 3.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text("obviously 😎", color = style.myInk())
                    }
                }
            }

            Label("WALLPAPER")
            ChatStyle.Patterns.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (key, name) ->
                        ChunkyChip(name, style.pattern == key) {
                            if (key == "photo") picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            else update(style.copy(pattern = key))
                        }
                    }
                }
            }
            if (style.pattern != "photo") {
                Label("BACKGROUND COLOUR")
                Swatches(listOf(0L) + ChatStyle.Tints, style.tint) { update(style.copy(tint = it)) }
            }
            Label("YOUR BUBBLES")
            Swatches(ChatStyle.Bubbles, if (style.bubble == 0L) ChatStyle.Bubbles.first() else style.bubble) { update(style.copy(bubble = it)) }
            Text("Only you see these. Your mates keep their own look.", fontSize = 13.sp, color = b.muted)
            ChunkyButton("Reset to normal", onClick = { update(ChatStyle()) }, fill = b.card, content = b.onCard, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = LocalBants.current.muted)
}

@Composable
private fun Swatches(colours: List<Long>, selected: Long, onPick: (Long) -> Unit) {
    val b = LocalBants.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colours.forEach { c ->
            val fill = if (c == 0L) MaterialTheme.colorScheme.background else Color(c)
            Box(
                Modifier
                    .size(36.dp)
                    .chunkyClickable(CircleShape, fill, b.outline, shadow = if (c == selected) 0.dp else 3.dp, border = if (c == selected) 4.dp else 2.5.dp) { onPick(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (c == 0L) Text("—", color = b.muted)
                else if (c == selected) Text("✓", color = if (fill.luminance() > 0.5f) BantsInk else Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
