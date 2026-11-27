package com.wellsey.bants.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wellsey.bants.data.BantsServer
import com.wellsey.bants.data.Bants
import com.wellsey.bants.data.InvitePreview
import com.wellsey.bants.data.ServerRole
import com.wellsey.bants.data.Servers
import kotlinx.coroutines.launch

/** A role's icon, shown after someone's name. */
@Composable
fun RoleIconBadge(role: ServerRole?, size: Int = 15) {
    role ?: return
    Spacer(Modifier.width(4.dp))
    if (role.iconMedia != null) {
        AsyncImage(model = mediaUrl(role.iconMedia), contentDescription = role.name, contentScale = ContentScale.Fit, modifier = Modifier.size(size.dp).clip(RoundedCornerShape(3.dp)))
    } else if (role.iconEmoji != null) {
        Text(role.iconEmoji, fontSize = (size - 2).sp)
    }
}

/** Server settings: icon, banner, invite background (GIFs move) and the vanity link. */
@Composable
fun ServerLooksSection(s: BantsServer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val b = LocalBants.current
    var icon by remember(s.id) { mutableStateOf(s.icon) }
    var banner by remember(s.id) { mutableStateOf(s.banner) }
    var inviteBg by remember(s.id) { mutableStateOf(s.inviteBackground) }
    var vanity by remember(s.id) { mutableStateOf(s.vanity.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { Bants.uploadImage(uri, if (target == "icon") 512 else 1600) }
                .onSuccess { when (target) { "icon" -> icon = it; "banner" -> banner = it; else -> inviteBg = it } }
                .onFailure { toast(context, it.message) }
            busy = false
        }
    }
    fun choose(t: String) { target = t; pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("How it looks", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Text("GIFs work too, so your icon, banner and invite background can move.", fontSize = 13.sp, color = b.muted)
        PicRow("Server icon", icon, { choose("icon") }) { icon = null }
        PicRow("Banner", banner, { choose("banner") }) { banner = null }
        PicRow("Invite background", inviteBg, { choose("invite") }) { inviteBg = null }
        OutlinedTextField(
            value = vanity, onValueChange = { v -> vanity = v.lowercase().filter { it.isLetterOrDigit() || it == '-' }.take(32) },
            label = { Text("Server link (never runs out)") }, placeholder = { Text("my-server") }, singleLine = true,
            shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        val changed = icon != s.icon || banner != s.banner || inviteBg != s.inviteBackground || vanity != s.vanity.orEmpty()
        ChunkyButton(if (busy) "Uploading…" else "Save looks", onClick = {
            scope.launch {
                runCatching { Servers.updateLooks(s.id, icon, banner, inviteBg, vanity) }
                    .onSuccess { toast(context, "Saved") }.onFailure { toast(context, it.message) }
            }
        }, enabled = changed && !busy, busy = busy, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PicRow(label: String, media: String?, onPick: () -> Unit, onRemove: () -> Unit) {
    val b = LocalBants.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).height(56.dp).chunky(RoundedCornerShape(12.dp), b.card, b.outline, shadow = 0.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (media != null) AsyncImage(model = mediaUrl(media), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text("None", color = b.muted, fontSize = 13.sp)
            }
            ChunkyChip("Change", false, onPick)
            if (media != null) ChunkyChip("Remove", false, onRemove)
        }
    }
}

/** A banner across the top of a server's channel list. */
@Composable
fun ServerBanner(s: BantsServer) {
    val banner = s.banner ?: return
    val b = LocalBants.current
    AsyncImage(
        model = mediaUrl(banner), contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 12.dp, vertical = 6.dp)
            .chunky(RoundedCornerShape(14.dp), b.card, b.outline, shadow = 0.dp),
    )
}

/** The join preview: the invite background (or banner) behind the server's name. */
@Composable
fun InviteCard(p: InvitePreview) {
    val b = LocalBants.current
    val bg = p.server.inviteBackground ?: p.server.banner
    Box(
        Modifier.fillMaxWidth().heightIn(min = 120.dp).chunky(RoundedCornerShape(16.dp), BantsPurple, b.outline, shadow = 0.dp)
            .background(Brush.linearGradient(listOf(BantsPurple, BantsPink))),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (bg != null) AsyncImage(model = mediaUrl(bg), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(p.server.name, p.server.icon, size = 48.dp, group = true)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(p.server.name, color = Color.White, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text("🟢 ${p.onlineCount} online · ${p.memberCount} members", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
            }
        }
    }
}

/** Role edit: an emoji or a little picture that shows next to names. */
@Composable
fun RoleIconPicker(emoji: String, media: String?, onEmoji: (String) -> Unit, onMedia: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val b = LocalBants.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { runCatching { Bants.uploadImage(uri, 128) }.onSuccess { onMedia(it) }.onFailure { toast(context, it.message) } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Role icon (shows next to names)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(44.dp).chunky(RoundedCornerShape(10.dp), b.card, b.outline, shadow = 0.dp), contentAlignment = Alignment.Center) {
                if (media != null) AsyncImage(model = mediaUrl(media), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(28.dp))
                else Text(emoji.ifBlank { "—" }, fontSize = 20.sp)
            }
            OutlinedTextField(
                value = emoji, onValueChange = { onEmoji(it.take(16)); onMedia(null) }, placeholder = { Text("🛡️") }, singleLine = true,
                shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.width(90.dp),
            )
            ChunkyChip("Picture", media != null) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            ChunkyChip("None", false) { onEmoji(""); onMedia(null) }
        }
    }
}

/** Settings: app theme and launcher icon. */
@Composable
fun AppLooksSection() {
    val context = LocalContext.current
    val b = LocalBants.current
    var icon by remember { mutableStateOf(Looks.icon(context)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("App theme", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Looks.THEMES.forEach { t ->
                val on = Looks.palette.id == t.id
                Column(
                    Modifier.chunkyClickable(RoundedCornerShape(12.dp), b.card, if (on) BantsPurple else b.outline, shadow = if (on) 0.dp else 3.dp, border = if (on) 3.dp else 2.5.dp) { Looks.setTheme(context, t.id) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(Modifier.clip(RoundedCornerShape(6.dp))) {
                        listOf(t.cream, t.purple, t.yellow).forEach { Box(Modifier.size(16.dp, 22.dp).background(it)) }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(t.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text("App icon", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Looks.ICONS.forEach { i ->
                val on = icon == i.id
                Column(
                    Modifier.chunkyClickable(RoundedCornerShape(12.dp), b.card, if (on) BantsPurple else b.outline, shadow = if (on) 0.dp else 3.dp, border = if (on) 3.dp else 2.5.dp) {
                        if (!on) { Looks.setIcon(context, i.id); icon = i.id; toast(context, "Icon changed. Your home screen may take a moment to catch up.") }
                    }.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(i.bg).border(2.dp, BantsInk, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Text("B", color = i.b, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(i.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text("If your home screen shortcut disappears after changing the icon, add Bants back from the app drawer.", fontSize = 12.sp, color = b.muted)
    }
}
