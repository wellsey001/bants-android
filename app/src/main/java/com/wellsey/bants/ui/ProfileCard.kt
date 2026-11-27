@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wellsey.bants.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wellsey.bants.data.Bants
import com.wellsey.bants.data.BantsServer
import com.wellsey.bants.data.MutualServer
import com.wellsey.bants.data.Perm
import com.wellsey.bants.data.ProfileCardData
import com.wellsey.bants.data.ServerRole
import com.wellsey.bants.data.Servers
import com.wellsey.bants.data.ShownProfile
import com.wellsey.bants.data.has
import com.wellsey.bants.data.shown
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val accents = listOf(0x5B4BFFL, 0xFF4D8DL, 0x16A36FL, 0x2D7FF9L, 0xFF7A1AL, 0x9B51E0L, 0xE5484DL, 0xFFD23FL, 0x111111L)
private fun day(ts: Long) = SimpleDateFormat("d MMM yyyy", Locale.UK).format(Date(ts))

/** The card itself. Also the live preview in the profile editors. */
@Composable
fun ProfileCardView(
    p: ShownProfile,
    modifier: Modifier = Modifier,
    roles: List<ServerRole> = emptyList(),
    since: Long = 0,
    joined: Long = 0,
    mutual: List<MutualServer> = emptyList(),
    actions: (@Composable () -> Unit)? = null,
) {
    val b = LocalBants.current
    val theme = Flair.themeBrush(p.theme)
    Box(modifier.then(if (theme != null) Modifier.background(theme) else Modifier)) {
    Column {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            Box(
                Modifier.fillMaxWidth().height(110.dp)
                    .background(p.accent?.let { Color(0xFF000000 or it) } ?: BantsPurple),
            ) {
                if (p.banner != null) AsyncImage(model = mediaUrl(p.banner), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(
                Modifier.padding(start = 16.dp).offset(y = 62.dp).size(88.dp)
                    .background(b.card, CircleShape).padding(5.dp),
            ) {
                DecoratedAvatar(p.displayName, p.avatar, size = 78.dp, decoration = p.decoration)
                if (p.online) {
                    Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(18.dp).background(OnlineGreen, CircleShape).border(3.dp, b.card, CircleShape))
                }
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StyledName(p.name, p.nameStyle, fallback = if (theme != null) Color.White else b.onCard, fontSize = 22.sp)
            Text("@${p.username}" + if (p.pronouns.isNotBlank()) " · ${p.pronouns}" else "", fontSize = 14.sp, color = b.muted)
            if (p.status.isNotBlank() || p.statusEmoji.isNotBlank()) {
                Text(
                    listOf(p.statusEmoji, p.status).filter { it.isNotBlank() }.joinToString(" "),
                    fontSize = 14.sp, color = b.onCard,
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth().background(b.outline.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            if (p.bio.isNotBlank()) {
                CardHeading("ABOUT ME")
                Text(p.bio, fontSize = 14.sp, color = b.onCard)
            }
            if (roles.isNotEmpty()) {
                CardHeading("ROLES")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    roles.forEach { r ->
                        Row(
                            Modifier.border(2.dp, b.outline, RoundedCornerShape(50)).padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(11.dp).background(r.colour?.let { Color(0xFF000000 or it) } ?: b.muted, CircleShape))
                            Spacer(Modifier.width(5.dp))
                            Text(r.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = b.onCard)
                        }
                    }
                }
            }
            if (since > 0) {
                CardHeading("MEMBER SINCE")
                Text("💬 ${day(since)}" + if (joined > 0) "  ·  🏠 ${day(joined)}" else "", fontSize = 14.sp, color = b.onCard)
            }
            if (mutual.isNotEmpty()) {
                CardHeading("MUTUAL SERVERS — ${mutual.size}")
                mutual.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Avatar(m.name, m.icon, size = 24.dp, group = true)
                        Spacer(Modifier.width(8.dp))
                        Text(m.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = b.onCard)
                    }
                }
            }
            if (actions != null) {
                Spacer(Modifier.height(10.dp))
                actions()
            }
        }
    }
    // The profile effect plays over the whole card when it opens (and when it changes in the editor).
    ProfileEffect(p.effect, p.effect, Modifier.matchParentSize())
    }
}

@Composable
private fun CardHeading(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, color = LocalBants.current.muted, modifier = Modifier.padding(top = 10.dp))
}

/** Tap someone's name or picture in a server: their card slides up. */
@Composable
fun ProfileSheet(serverId: Long, userId: Long, nav: Navigator, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val servers by Servers.servers.collectAsState()
    val s = servers[serverId]
    var data by remember { mutableStateOf<ProfileCardData?>(null) }
    var requested by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    LaunchedEffect(serverId, userId) {
        runCatching { Servers.profile(serverId, userId) }.onSuccess { data = it }.onFailure { toast(context, it.message); onDismiss() }
    }
    val b = LocalBants.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = b.card, dragHandle = null) {
        val d = data
        if (d == null || s == null) {
            Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) { Text("Loading…", color = b.muted) }
            return@ModalBottomSheet
        }
        val roles = s.roles.filter { it.id in d.roleIds && !it.isDefault }.sortedByDescending { it.position }
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            ProfileCardView(d.asMember().shown(), roles = roles, since = d.memberSince, joined = d.joinedAt, mutual = d.mutualServers) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (d.contact == "self") {
                        ChunkyButton("Edit server profile", onClick = { onDismiss(); nav.go(Screen.ServerProfile(serverId)) }, modifier = Modifier.fillMaxWidth())
                        ChunkyButton("Edit main profile", onClick = { onDismiss(); nav.go(Screen.Profile) }, fill = b.card, content = b.onCard, modifier = Modifier.fillMaxWidth())
                    } else {
                        if (d.canMessage) {
                            ChunkyButton("Message", onClick = {
                                scope.launch {
                                    runCatching { Bants.startDirect(userId) }
                                        .onSuccess { id -> onDismiss(); nav.go(Screen.Chat(id)) }
                                        .onFailure { toast(context, it.message) }
                                }
                            }, modifier = Modifier.fillMaxWidth())
                        }
                        when {
                            d.contact == "contact" -> Text("✓ In your contacts", color = b.muted, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                            d.contact == "requested" || requested -> Text("Contact request sent", color = b.muted, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                            d.contact == "incoming" -> Text("They asked to be contacts. See Contacts.", color = b.muted, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                            else -> ChunkyButton("Add contact", onClick = {
                                scope.launch {
                                    runCatching { Bants.addContact(username = d.user.username) }
                                        .onSuccess { requested = true; toast(context, "Request sent") }
                                        .onFailure { toast(context, it.message) }
                                }
                            }, fill = b.card, content = b.onCard, modifier = Modifier.fillMaxWidth())
                        }
                        val p = s.permissions
                        if (listOf(Perm.MANAGE_ROLES, Perm.KICK_MEMBERS, Perm.BAN_MEMBERS, Perm.TIMEOUT_MEMBERS, Perm.MANAGE_NICKNAMES).any { p.has(it) }) {
                            ChunkyButton("Manage", onClick = { managing = true }, fill = BantsYellow, content = BantsInk, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
    if (managing) {
        val d = data
        if (d != null && s != null) MemberDialog(s, d.asMember()) { managing = false; onDismiss() }
    }
}

/** Your profile in one server: nickname, avatar, banner, bio, pronouns. Empty bits use your main profile. */
@Composable
fun ServerProfileScreen(serverId: Long, nav: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val servers by Servers.servers.collectAsState()
    val allMembers by Servers.members.collectAsState()
    val me by Bants.me.collectAsState()
    val s: BantsServer = servers[serverId] ?: return
    val mine = allMembers[serverId]?.firstOrNull { it.user.id == me?.id }
    LaunchedEffect(serverId) { if (Servers.members.value[serverId] == null) runCatching { Servers.loadMembers(serverId) } }
    var nickname by remember(mine?.nickname) { mutableStateOf(mine?.nickname.orEmpty()) }
    var pronouns by remember(mine) { mutableStateOf(mine?.serverProfile?.pronouns.orEmpty()) }
    var bio by remember(mine) { mutableStateOf(mine?.serverProfile?.bio.orEmpty()) }
    var avatar by remember(mine) { mutableStateOf(mine?.serverProfile?.avatar) }
    var banner by remember(mine) { mutableStateOf(mine?.serverProfile?.banner) }
    var saving by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    val canNick = s.permissions.has(Perm.CHANGE_NICKNAME) || s.permissions.has(Perm.MANAGE_NICKNAMES) || s.ownerId == me?.id
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { uploading = true; runCatching { Bants.uploadImage(uri, 640) }.onSuccess { avatar = it }.onFailure { toast(context, it.message) }; uploading = false }
    }
    val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { uploading = true; runCatching { Bants.uploadImage(uri, 1600) }.onSuccess { banner = it }.onFailure { toast(context, it.message) }; uploading = false }
    }
    val b = LocalBants.current
    val u = me
    Scaffold(topBar = { ChunkyTopBar("Profile in ${s.name}", nav) }) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Only people in this server see these. Anything left empty uses your main profile.", fontSize = 13.sp, color = b.muted)
            if (u != null) {
                ProfileCardView(
                    ShownProfile(
                        name = nickname.ifBlank { u.displayName }, displayName = u.displayName, username = u.username,
                        avatar = avatar ?: u.avatar, banner = banner ?: u.banner, accent = u.accentColour,
                        pronouns = pronouns.ifBlank { u.pronouns }, bio = bio.ifBlank { u.about },
                        status = u.customStatus, statusEmoji = u.statusEmoji, online = true,
                    ),
                    modifier = Modifier.fillMaxWidth().chunky(RoundedCornerShape(18.dp), b.card, b.outline, shadow = 3.dp).clip(RoundedCornerShape(18.dp)),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChunkyChip(if (avatar != null) "Change avatar" else "Server avatar", avatar != null) { pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                if (avatar != null) ChunkyChip("Use main", false) { avatar = null }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChunkyChip(if (banner != null) "Change banner" else "Server banner", banner != null) { pickBanner.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                if (banner != null) ChunkyChip("Use main", false) { banner = null }
            }
            if (uploading) Text("Uploading…", color = b.muted, fontSize = 13.sp)
            if (canNick) {
                OutlinedTextField(value = nickname, onValueChange = { nickname = it.take(32) }, label = { Text("Nickname") }, placeholder = { Text(u?.displayName ?: "") },
                    singleLine = true, shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(value = pronouns, onValueChange = { pronouns = it.take(40) }, label = { Text("Pronouns") }, placeholder = { Text(u?.pronouns?.ifBlank { null } ?: "he/him, she/her, they/them…") },
                singleLine = true, shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = bio, onValueChange = { bio = it.take(190) }, label = { Text("Server bio") }, placeholder = { Text(u?.about?.ifBlank { null } ?: "Say something for this server") },
                minLines = 3, shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.fillMaxWidth())
            ChunkyButton("Save", onClick = {
                scope.launch {
                    saving = true
                    runCatching { Servers.updateServerProfile(serverId, nickname, canNick, avatar, banner, bio, pronouns) }
                        .onSuccess { toast(context, "Server profile saved"); nav.back() }
                        .onFailure { toast(context, it.message) }
                    saving = false
                }
            }, busy = saving, enabled = !uploading, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The main-profile extras: banner, colour, pronouns, status. Shown on the Profile screen. */
@Composable
fun ProfileExtras() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val me by Bants.me.collectAsState()
    val u = me ?: return
    var pronouns by remember(u.pronouns) { mutableStateOf(u.pronouns) }
    var status by remember(u.customStatus) { mutableStateOf(u.customStatus) }
    var emoji by remember(u.statusEmoji) { mutableStateOf(u.statusEmoji) }
    var saving by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    val b = LocalBants.current
    val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            uploading = true
            runCatching {
                val id = Bants.uploadImage(uri, 1600)
                Bants.updateProfileFields(kotlinx.serialization.json.buildJsonObject { put("banner", kotlinx.serialization.json.JsonPrimitive(id)) })
            }.onFailure { toast(context, it.message) }
            uploading = false
        }
    }
    fun save(fields: kotlinx.serialization.json.JsonObject) {
        scope.launch {
            saving = true
            runCatching { Bants.updateProfileFields(fields) }.onFailure { toast(context, it.message) }
            saving = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("PROFILE CARD", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = b.muted)
        ProfileCardView(
            ShownProfile(u.displayName, u.displayName, u.username, u.avatar, u.banner, u.accentColour, pronouns, u.about, status, emoji, online = true,
                theme = u.themeColours, decoration = u.decoration, effect = u.profileEffect, nameStyle = u.nameStyle),
            modifier = Modifier.fillMaxWidth().chunky(RoundedCornerShape(18.dp), b.card, b.outline, shadow = 3.dp).clip(RoundedCornerShape(18.dp)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChunkyChip(if (u.banner != null) "Change banner" else "Add a banner", u.banner != null) { pickBanner.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            if (u.banner != null) ChunkyChip("Remove", false) { save(kotlinx.serialization.json.buildJsonObject { put("banner", kotlinx.serialization.json.JsonNull) }) }
        }
        if (uploading) Text("Uploading…", color = b.muted, fontSize = 13.sp)
        Text("Profile colour (shows when there's no banner)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = b.onCard)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(34.dp).chunkyClickable(CircleShape, b.card, b.outline, shadow = 0.dp, border = if (u.accentColour == null) 4.dp else 2.dp) {
                    save(kotlinx.serialization.json.buildJsonObject { put("accentColour", kotlinx.serialization.json.JsonNull) })
                },
                contentAlignment = Alignment.Center,
            ) { Text("–", fontWeight = FontWeight.ExtraBold) }
            accents.forEach { c ->
                Box(Modifier.size(34.dp).chunkyClickable(CircleShape, Color(0xFF000000 or c), b.outline, shadow = 0.dp, border = if (u.accentColour == c) 4.dp else 2.dp) {
                    save(kotlinx.serialization.json.buildJsonObject { put("accentColour", kotlinx.serialization.json.JsonPrimitive(c)) })
                })
            }
        }
        // Flair saves as soon as you pick something, so the card above updates straight away.
        FlairEditor(
            theme = u.themeColours,
            onTheme = { t -> save(kotlinx.serialization.json.buildJsonObject { put("themeColours", if (t == null) kotlinx.serialization.json.JsonNull else kotlinx.serialization.json.JsonArray(t.map { kotlinx.serialization.json.JsonPrimitive(it) })) }) },
            decoration = u.decoration,
            onDecoration = { d -> save(kotlinx.serialization.json.buildJsonObject { put("decoration", d?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull) }) },
            effect = u.profileEffect,
            onEffect = { e -> save(kotlinx.serialization.json.buildJsonObject { put("profileEffect", e?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull) }) },
            nameStyle = u.nameStyle,
            onNameStyle = { ns ->
                save(kotlinx.serialization.json.buildJsonObject {
                    put("nameStyle", if (ns == null) kotlinx.serialization.json.JsonNull else kotlinx.serialization.json.buildJsonObject {
                        put("font", kotlinx.serialization.json.JsonPrimitive(ns.font))
                        put("colours", kotlinx.serialization.json.JsonArray(ns.colours.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                    })
                })
            },
        )
        OutlinedTextField(value = pronouns, onValueChange = { pronouns = it.take(40) }, label = { Text("Pronouns") }, placeholder = { Text("he/him, she/her, they/them…") },
            singleLine = true, shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = emoji, onValueChange = { emoji = it.take(16) }, label = { Text("Emoji") }, placeholder = { Text("🎮") },
                singleLine = true, shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.width(96.dp))
            OutlinedTextField(value = status, onValueChange = { status = it.take(128) }, label = { Text("Custom status") }, placeholder = { Text("What are you up to?") },
                singleLine = true, shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.weight(1f))
        }
        ChunkyButton(
            "Save card details",
            onClick = {
                save(kotlinx.serialization.json.buildJsonObject {
                    put("pronouns", kotlinx.serialization.json.JsonPrimitive(pronouns.trim()))
                    put("customStatus", kotlinx.serialization.json.JsonPrimitive(status.trim()))
                    put("statusEmoji", kotlinx.serialization.json.JsonPrimitive(emoji.trim()))
                })
                toast(context, "Saved")
            },
            enabled = pronouns != u.pronouns || status != u.customStatus || emoji != u.statusEmoji,
            busy = saving,
            fill = b.card, content = b.onCard,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
