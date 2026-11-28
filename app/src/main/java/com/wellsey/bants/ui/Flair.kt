@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wellsey.bants.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellsey.bants.data.NameStyle
import kotlin.random.Random

/** Nitro-style flair, free for everyone. */
object Flair {
    val decorations = listOf("rainbow" to "🌈 Rainbow", "fire" to "🔥 Fire", "hearts" to "💖 Hearts", "sparkle" to "✨ Sparkle", "neon" to "💡 Neon",
        "gold" to "🏆 Gold", "snow" to "❄️ Snow", "halo" to "😇 Halo", "crown" to "👑 Crown", "cat" to "🐱 Cat ears")
    val effects = listOf("confetti" to "🎉 Confetti", "hearts" to "💕 Hearts", "snow" to "❄️ Snow", "sparkles" to "✨ Sparkles",
        "bubbles" to "🫧 Bubbles", "fireworks" to "🎆 Fireworks", "stars" to "⭐ Stars")
    val fonts = listOf("default" to "Normal", "bricolage" to "Chunky", "mono" to "Mono", "serif" to "Serif", "script" to "Script", "rounded" to "Rounded")

    fun colour(n: Long) = Color(0xFF000000 or n)

    fun font(key: String?): Pair<FontFamily?, FontStyle?> = when (key) {
        "bricolage" -> Bricolage to null
        "mono" -> FontFamily.Monospace to null
        "serif" -> FontFamily.Serif to FontStyle.Italic
        "script" -> FontFamily.Cursive to null
        "rounded" -> FontFamily.SansSerif to null
        else -> null to null
    }

    fun themeBrush(theme: List<Long>?): Brush? =
        if (theme != null && theme.size == 2) Brush.linearGradient(listOf(colour(theme[0]), colour(theme[1]))) else null
}

/** An avatar wearing its decoration: a spinning ring, a glow, or something sat on top. */
@Composable
fun DecoratedAvatar(name: String, media: String?, size: Dp, decoration: String?, online: Boolean = false) {
    if (decoration == null) { Avatar(name, media, size = size, online = online); return }
    val spin = rememberInfiniteTransition(label = "deco")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "angle")
    val pulse by spin.animateFloat(0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val ring = size * 0.09f
    Box(contentAlignment = Alignment.Center) {
        val ringBrush: Brush? = when (decoration) {
            "rainbow" -> Brush.sweepGradient(listOf(Color(0xFFFF4D4D), Color(0xFFFFD23F), Color(0xFF2ECC71), Color(0xFF2D7FF9), Color(0xFF9B51E0), Color(0xFFFF4D8D), Color(0xFFFF4D4D)))
            "gold" -> Brush.sweepGradient(listOf(Color(0xFFB8860B), Color(0xFFFFD700), Color(0xFFFFF6B0), Color(0xFFFFD700), Color(0xFFB8860B)))
            "hearts" -> Brush.sweepGradient(listOf(Color(0xFFFF4D8D), Color(0xFFFFB3D1), Color(0xFFFF4D8D)))
            "sparkle" -> Brush.sweepGradient(listOf(Color.White, Color(0xFFFFD23F), Color.White, BantsPurple, Color.White))
            "snow" -> Brush.sweepGradient(listOf(Color(0xFFE6F7FF), Color(0xFF9FD8FF), Color(0xFFE6F7FF)))
            "neon" -> Brush.sweepGradient(listOf(Color(0xFF00F0FF), Color(0xFFFF00E5), Color(0xFF00F0FF)))
            "fire" -> Brush.sweepGradient(listOf(Color(0xFFFFD23F), Color(0xFFFF7A1A), Color(0xFFE5484D), Color(0xFFFF7A1A), Color(0xFFFFD23F)))
            else -> null
        }
        if (ringBrush != null) {
            Box(
                Modifier.size(size + ring * 2).rotate(if (decoration == "neon" || decoration == "fire") 0f else angle)
                    .alpha(if (decoration == "neon" || decoration == "fire") pulse else 1f)
                    .border(ring, ringBrush, CircleShape),
            )
        }
        Avatar(name, media, size = size, online = online)
        val top = when (decoration) { "crown" -> "👑"; "halo" -> "😇"; "cat" -> "🐱"; else -> null }
        if (top != null) Text(top, fontSize = (size.value * 0.36f).sp, modifier = Modifier.align(Alignment.TopCenter).offset(y = -size * 0.22f))
        val float = when (decoration) { "hearts" -> "💖"; "snow" -> "❄️"; "fire" -> "🔥"; else -> null }
        if (float != null) Text(float, fontSize = (size.value * 0.28f).sp, modifier = Modifier.align(Alignment.TopEnd).offset(x = size * 0.04f, y = -size * 0.04f - (pulse * 3).dp))
    }
}

/** A name in its chosen font and colours. Falls back to [fallback] (the role colour). */
@Composable
fun StyledName(
    text: String,
    style: NameStyle?,
    fallback: Color,
    fontSize: TextUnit = 16.sp,
    modifier: Modifier = Modifier,
    defaultFamily: FontFamily? = Bricolage,
) {
    val (family, fontStyle) = Flair.font(style?.font)
    val colours = style?.colours.orEmpty()
    val base = TextStyle(fontSize = fontSize, fontWeight = FontWeight.ExtraBold, fontFamily = family ?: defaultFamily, fontStyle = fontStyle)
    val st = when (colours.size) {
        2 -> base.copy(brush = Brush.linearGradient(listOf(Flair.colour(colours[0]), Flair.colour(colours[1]))))
        1 -> base.copy(color = Flair.colour(colours[0]))
        else -> base.copy(color = fallback)
    }
    Text(text, style = st, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** A profile effect: things fall (or float up) over the card for a few seconds. */
@Composable
fun ProfileEffect(effect: String?, key: Any?, modifier: Modifier = Modifier) {
    if (effect == null) return
    val glyphs = when (effect) {
        "confetti" -> listOf("🎉", "🎊", "✨", "🟣", "🟡")
        "hearts" -> listOf("💖", "💕", "💗")
        "snow" -> listOf("❄️", "❅", "❆")
        "sparkles" -> listOf("✨", "⭐", "💫")
        "bubbles" -> listOf("🫧", "○")
        "fireworks" -> listOf("🎆", "🎇", "✨")
        else -> listOf("⭐", "🌟", "✦")
    }
    val rise = effect == "bubbles" || effect == "fireworks"
    val progress = remember(key, effect) { Animatable(0f) }
    LaunchedEffect(key, effect) { progress.snapTo(0f); progress.animateTo(1f, tween(3200, easing = LinearEasing)) }
    if (progress.value >= 1f) return
    val pieces = remember(key, effect) { List(22) { Triple(Random.nextFloat(), Random.nextFloat() * 0.35f, 12 + Random.nextInt(14)) } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val h = maxHeight
        val w = maxWidth
        pieces.forEachIndexed { i, (x, delay, sz) ->
            val t = ((progress.value - delay) / 0.65f).coerceIn(0f, 1f)
            if (t <= 0f || t >= 1f) return@forEachIndexed
            val y = if (rise) h * (1f - t) else h * t - 20.dp
            Text(glyphs[i % glyphs.size], fontSize = sz.sp, modifier = Modifier.offset(x = w * x, y = y).graphicsLayer { alpha = 1f - t * t; rotationZ = t * 220f })
        }
    }
}

/** The flair bits of the profile editor. */
@Composable
fun FlairEditor(
    theme: List<Long>?, onTheme: (List<Long>?) -> Unit,
    decoration: String?, onDecoration: (String?) -> Unit,
    effect: String?, onEffect: (String?) -> Unit,
    nameStyle: NameStyle?, onNameStyle: (NameStyle?) -> Unit,
) {
    val b = LocalBants.current
    val palette = listOf(0x5B4BFFL, 0xFF4D8DL, 0x16A36FL, 0x2D7FF9L, 0xFF7A1AL, 0x9B51E0L, 0xE5484DL, 0xFFD23FL, 0x111111L, 0x00F0FFL)
    @Composable
    fun ColourRow(selected: Long?, onPick: (Long) -> Unit) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            palette.forEach { c ->
                Box(Modifier.size(30.dp).chunkyClickable(CircleShape, Flair.colour(c), b.outline, shadow = 0.dp, border = if (selected == c) 4.dp else 2.dp) { onPick(c) })
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("✨ FLAIR", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = b.muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Profile theme (gradient card)", fontWeight = FontWeight.Bold, color = b.onCard, modifier = Modifier.weight(1f))
            Switch(theme != null, { onTheme(if (it) listOf(0x5B4BFFL, 0xFF4D8DL) else null) })
        }
        if (theme != null) {
            Text("Top colour", fontSize = 12.sp, color = b.muted); ColourRow(theme[0]) { onTheme(listOf(it, theme[1])) }
            Text("Bottom colour", fontSize = 12.sp, color = b.muted); ColourRow(theme[1]) { onTheme(listOf(theme[0], it)) }
        }
        Text("Avatar decoration", fontWeight = FontWeight.Bold, color = b.onCard)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChunkyChip("None", decoration == null) { onDecoration(null) }
            Flair.decorations.forEach { (k, l) -> ChunkyChip(l, decoration == k) { onDecoration(k) } }
        }
        Text("Profile effect (plays when someone opens your card)", fontWeight = FontWeight.Bold, color = b.onCard)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChunkyChip("None", effect == null) { onEffect(null) }
            Flair.effects.forEach { (k, l) -> ChunkyChip(l, effect == k) { onEffect(k) } }
        }
        Text("Name style", fontWeight = FontWeight.Bold, color = b.onCard)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Flair.fonts.forEach { (k, l) -> ChunkyChip(l, (nameStyle?.font ?: "default") == k) { onNameStyle((nameStyle ?: NameStyle()).copy(font = k)) } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gradient name", color = b.onCard, modifier = Modifier.weight(1f))
            Switch(nameStyle?.colours?.size == 2, { onNameStyle((nameStyle ?: NameStyle()).copy(colours = if (it) listOf(0xFFD23FL, 0xFF4D8DL) else emptyList())) })
        }
        val nc = nameStyle?.colours.orEmpty()
        if (nc.size == 2) {
            Text("From", fontSize = 12.sp, color = b.muted); ColourRow(nc[0]) { onNameStyle(nameStyle!!.copy(colours = listOf(it, nc[1]))) }
            Text("To", fontSize = 12.sp, color = b.muted); ColourRow(nc[1]) { onNameStyle(nameStyle!!.copy(colours = listOf(nc[0], it))) }
        }
        Text("Animated avatars and banners work too: just pick a GIF.", fontSize = 12.sp, color = b.muted)
    }
}

/** A quick burst when a super reaction lands. */
@Composable
fun SuperBurst(emoji: String, key: Any) {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { p.snapTo(0f); p.animateTo(1f, tween(800)) }
    if (p.value >= 1f) return
    Box(contentAlignment = Alignment.Center) {
        repeat(10) { i ->
            val a = Math.toRadians(i * 36.0)
            val d = 34f * p.value
            Box(Modifier.offset(x = (kotlin.math.cos(a) * d).dp, y = (kotlin.math.sin(a) * d).dp).graphicsLayer { alpha = 1f - p.value; scaleX = 0.6f + p.value; scaleY = 0.6f + p.value }) {
                EmojiGlyph(emoji, size = 16.dp, fontSize = 13.sp)
            }
        }
    }
}
