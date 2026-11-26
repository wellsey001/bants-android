package com.wellsey.bants.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wellsey.bants.data.Bants
import com.wellsey.bants.data.User
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun mediaUrl(id: String) = "${Bants.session.serverUrl}/api/media/$id"

fun toast(context: Context, message: String?) {
    Toast.makeText(context, message ?: "Something went wrong", Toast.LENGTH_SHORT).show()
}

private val avatarColours = listOf(
    Color(0xFF5B4BFF), Color(0xFFFF4D8D), Color(0xFF00A88F), Color(0xFFFF8A00),
    Color(0xFF2D8CFF), Color(0xFF9B51E0), Color(0xFFE0457B), Color(0xFF3D9A50),
)

fun colourFor(name: String): Color = avatarColours[(name.hashCode() and 0x7fffffff) % avatarColours.size]

@Composable
fun Avatar(name: String, media: String?, size: Dp = 48.dp, online: Boolean = false, group: Boolean = false, outlined: Boolean = true) {
    val ring = if (outlined) Modifier.border(if (size >= 40.dp) 2.5.dp else 2.dp, LocalBants.current.outline, CircleShape) else Modifier
    Box(Modifier.size(size)) {
        if (media != null) {
            AsyncImage(
                model = mediaUrl(media),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape).then(ring),
            )
        } else {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).background(colourFor(name)).then(ring),
                contentAlignment = Alignment.Center,
            ) {
                if (group) {
                    Icon(Icons.Default.Group, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
                } else {
                    Text(
                        name.trim().take(1).uppercase().ifEmpty { "?" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.42f).sp,
                    )
                }
            }
        }
        if (online) {
            Box(
                Modifier
                    .size(size * 0.28f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(OnlineGreen)
                    .border(2.dp, LocalBants.current.outline, CircleShape),
            )
        }
    }
}

@Composable
fun BackButton(nav: Navigator) {
    ChunkyIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", { nav.back() })
}

@Composable
fun UserRow(
    user: User,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(user.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) },
        supportingContent = {
            Text(
                supporting ?: user.about.ifBlank { "@${user.username}" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = { Avatar(user.displayName, user.avatar, size = 44.dp, online = user.online == true) },
        trailingContent = trailing,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
fun ActionRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else null,
        leadingContent = {
            Box(
                Modifier.size(44.dp).chunky(RoundedCornerShape(14.dp), BantsPurple, LocalBants.current.outline, shadow = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = LocalBants.current.muted,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
fun FriendCodeCard(code: String?) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    ChunkyCard(Modifier.fillMaxWidth(), fill = BantsYellow) {
        Column(Modifier.padding(20.dp)) {
            Text("YOUR FRIEND CODE", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.sp, color = BantsInk)
            Text(
                code ?: "…",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
                color = BantsInk,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Text("Anyone with this code can add you straight away.", fontSize = 14.sp, color = BantsInk)
            Spacer(Modifier.size(14.dp))
            Row {
                ChunkyButton(
                    "Copy",
                    onClick = {
                        if (code != null) {
                            clipboard.setText(AnnotatedString(code))
                            toast(context, "Copied")
                        }
                    },
                    fill = Color.White,
                    content = BantsInk,
                    icon = Icons.Default.ContentCopy,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                ChunkyButton(
                    "Share",
                    onClick = {
                        if (code != null) {
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, "Add me on Bants! My friend code is $code")
                            context.startActivity(Intent.createChooser(send, "Share your friend code"))
                        }
                    },
                    icon = Icons.Default.Share,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ───────────── time labels ─────────────

private val clock = DateTimeFormatter.ofPattern("HH:mm")

private fun local(ts: Long) = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())

fun clockTime(ts: Long): String = local(ts).format(clock)

fun sameDay(a: Long, b: Long) = local(a).toLocalDate() == local(b).toLocalDate()

fun shortTime(ts: Long): String {
    val dt = local(ts)
    val day = dt.toLocalDate()
    val today = LocalDate.now()
    return when {
        day == today -> dt.format(clock)
        day == today.minusDays(1) -> "Yesterday"
        day.isAfter(today.minusDays(7)) -> dt.format(DateTimeFormatter.ofPattern("EEE"))
        else -> dt.format(DateTimeFormatter.ofPattern("dd/MM/yy"))
    }
}

fun dayLabel(ts: Long): String {
    val day = local(ts).toLocalDate()
    val today = LocalDate.now()
    return when {
        day == today -> "Today"
        day == today.minusDays(1) -> "Yesterday"
        day.year == today.year -> day.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))
        else -> day.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
    }
}

fun lastSeenLabel(user: User): String {
    if (user.online == true) return "online"
    val ts = user.lastSeen ?: return ""
    val dt = local(ts)
    val day = dt.toLocalDate()
    val today = LocalDate.now()
    return when {
        day == today -> "last seen today at ${dt.format(clock)}"
        day == today.minusDays(1) -> "last seen yesterday at ${dt.format(clock)}"
        else -> "last seen ${dt.format(DateTimeFormatter.ofPattern("d MMM"))}"
    }
}
