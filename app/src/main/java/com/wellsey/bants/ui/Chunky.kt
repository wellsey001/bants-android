package com.wellsey.bants.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The house style: a fill, a thick outline and a hard offset shadow.
 * Clips the content to the shape, so ripples stay inside.
 */
fun Modifier.chunky(
    shape: Shape,
    fill: Color,
    outline: Color,
    shadow: Dp = 4.dp,
    border: Dp = 2.5.dp,
    press: (() -> Float)? = null,
): Modifier = this
    .drawBehind {
        if (shadow > 0.dp) {
            val o = shape.createOutline(size, layoutDirection, this)
            // While pressed the face slides onto its shadow, so the shadow's offset shrinks to match.
            val d = shadow.toPx() * (1f - (press?.invoke() ?: 0f))
            if (d > 0.5f) translate(d, d) { drawOutline(o, outline) }
        }
    }
    .clip(shape)
    .background(fill, shape)
    .border(border, outline, shape)

@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = BantsPurple,
    content: Color = Color.White,
    enabled: Boolean = true,
    busy: Boolean = false,
    icon: ImageVector? = null,
) {
    val b = LocalBants.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .defaultMinSize(minHeight = 52.dp)
            .chunkyClickable(shape, if (enabled) fill else b.card, b.outline, shadow = if (enabled) 4.dp else 0.dp, enabled = enabled && !busy, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val ink = if (enabled) content else b.muted
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp, color = ink)
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = ink, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        }
    }
}

@Composable
fun ChunkyIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = LocalBants.current.card,
    tint: Color = LocalBants.current.onCard,
    size: Dp = 44.dp,
    shadow: Dp = 0.dp,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .chunkyClickable(RoundedCornerShape(14.dp), fill, LocalBants.current.outline, shadow = shadow, enabled = enabled, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun ChunkyChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val b = LocalBants.current
    Box(
        Modifier
            .chunkyClickable(RoundedCornerShape(50), if (selected) BantsYellow else b.card, b.outline, shadow = 0.dp, border = 2.dp, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (selected) BantsInk else b.onCard)
    }
}

@Composable
fun ChunkyCard(
    modifier: Modifier = Modifier,
    fill: Color = LocalBants.current.card,
    shadow: Dp = 4.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier.chunky(RoundedCornerShape(20.dp), fill, LocalBants.current.outline, shadow = shadow)) { content() }
}

/** The tilted yellow "bants" sticker. */
@Composable
fun Sticker(text: String, size: TextUnit = 40.sp, modifier: Modifier = Modifier) {
    Text(
        text,
        fontFamily = Bricolage,
        fontWeight = FontWeight.ExtraBold,
        fontSize = size,
        letterSpacing = (-1).sp,
        color = BantsInk,
        modifier = modifier
            .rotate(-2f)
            .background(BantsYellow, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp),
    )
}

/** Small pill label, like the "TODAY" divider. */
@Composable
fun Pill(text: String, fill: Color = BantsYellow, modifier: Modifier = Modifier) {
    Box(
        modifier
            .chunky(RoundedCornerShape(50), fill, LocalBants.current.outline, shadow = 0.dp, border = 2.dp)
            .padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = BantsInk)
    }
}

@Composable
fun ChunkyTopBar(
    title: String,
    nav: Navigator?,
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.background,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(fill)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (nav != null) BackButton(nav)
        Text(
            title,
            fontFamily = Bricolage,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun chunkyFieldColors(): TextFieldColors {
    val b = LocalBants.current
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = b.outline,
        unfocusedBorderColor = b.outline,
        focusedContainerColor = b.card,
        unfocusedContainerColor = b.card,
        focusedLabelColor = b.onCard,
        cursorColor = BantsPurple,
    )
}

val FieldShape = RoundedCornerShape(14.dp)

@Composable
fun EmptyNote(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Sticker(title, size = 26.sp)
        Spacer(Modifier.size(14.dp))
        Text(body, color = LocalBants.current.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
