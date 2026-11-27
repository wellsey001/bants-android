package com.wellsey.bants.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import com.wellsey.bants.calls.CallManager
import com.wellsey.bants.calls.CallStatus
import com.wellsey.bants.data.ServerMember
import com.wellsey.bants.data.shownName
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellsey.bants.data.Bants
import com.wellsey.bants.data.BantsServer
import com.wellsey.bants.data.HomeMode
import com.wellsey.bants.data.Perm
import com.wellsey.bants.data.ServerChannel
import com.wellsey.bants.data.Servers
import com.wellsey.bants.data.has
import kotlinx.coroutines.launch

// ───────────────────────── the Chats / Servers switch ─────────────────────────

/** One press flips between your chats and your servers. Hidden until the server supports servers. */
@Composable
fun HomeSwitch(modifier: Modifier = Modifier) {
    val available by Servers.available.collectAsState()
    if (!available) return
    val mode by Servers.mode.collectAsState()
    val conversations by Bants.conversations.collectAsState()
    val servers by Servers.servers.collectAsState()
    val chatUnread = conversations.values.count { it.unreadCount > 0 && !it.archived }
    val serverPings = servers.values.sumOf { it.mentionCount }
    val serverUnread = servers.values.any { it.unread && it.me?.muted != true }
    val b = LocalBants.current

    Row(
        modifier
            .fillMaxWidth()
            .chunky(RoundedCornerShape(18.dp), b.card, b.outline, shadow = 3.dp)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SwitchHalf("Chats", Icons.Default.ChatBubble, mode == HomeMode.Chats, chatUnread, dot = false, Modifier.weight(1f)) {
            Servers.setMode(HomeMode.Chats)
        }
        SwitchHalf("Servers", Icons.Default.Groups, mode == HomeMode.Servers, serverPings, dot = serverUnread, Modifier.weight(1f)) {
            Servers.setMode(HomeMode.Servers)
        }
    }
}

@Composable
private fun SwitchHalf(label: String, icon: ImageVector, selected: Boolean, count: Int, dot: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val b = LocalBants.current
    Row(
        modifier
            .height(44.dp)
            .bouncyClickable(role = Role.Tab, onClickLabel = label, squash = 0.04f, onClick = onClick)
            .background(if (selected) BantsPurple else Color.Transparent, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val ink = if (selected) Color.White else b.onCard
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = ink)
        if (count > 0) {
            Spacer(Modifier.width(8.dp))
            Badge(count)
        } else if (dot && !selected) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(9.dp).background(b.onCard, CircleShape))
        }
    }
}

@Composable
fun Badge(count: Int) {
    val b = LocalBants.current
    Box(
        Modifier
            .size(width = if (count > 9) 30.dp else 22.dp, height = 22.dp)
            .chunky(RoundedCornerShape(50), BantsPink, b.outline, shadow = 0.dp, border = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else "$count", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = BantsInk)
    }
}

// ───────────────────────── your servers ─────────────────────────

@Composable
fun ServersHomeScreen(nav: Navigator) {
    val all by Servers.servers.collectAsState()
    val me by Bants.me.collectAsState()
    val connected by Bants.connected.collectAsState()
    val list = remember(all) { Servers.ordered(all) }
    var adding by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) { runCatching { Servers.refresh() }.onFailure { toast(context, it.message) } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 18.dp, end = 18.dp, top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Sticker("bants", size = 40.sp)
                    Spacer(Modifier.weight(1f))
                    if (!connected) {
                        Pill("Connecting…", fill = LocalBants.current.card)
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(Modifier.bouncyClickable(onClickLabel = "Your profile", squash = 0.1f) { nav.go(Screen.Profile) }) {
                        Avatar(me?.displayName ?: "?", me?.avatar, size = 44.dp)
                    }
                }
                HomeSwitch()
            }
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyNote("no servers yet", "Start one for the lads, or join with an invite link. Tap the plus.")
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it.id }) { s -> ServerCard(s) { nav.go(Screen.Server(s.id)) } }
                }
            }
        }
        VoiceBar(
            Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 12.dp, end = 100.dp, bottom = 22.dp),
        ) { id ->
            Servers.channel(id)?.let { nav.go(Screen.Server(it.serverId)) }
            nav.go(Screen.Channel(id))
        }
        ChunkyIconButton(
            Icons.Default.Add,
            "Add a server",
            { adding = true },
            fill = BantsPurple,
            tint = Color.White,
            size = 64.dp,
            shadow = 5.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 22.dp, bottom = 22.dp),
        )
    }
    if (adding) AddServerDialog(onDismiss = { adding = false }) { s ->
        adding = false
        nav.go(Screen.Server(s.id))
    }
}

@Composable
private fun ServerCard(s: BantsServer, onClick: () -> Unit) {
    val b = LocalBants.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val muted = s.me?.muted == true
    val unread = s.unread && !muted
    fun act(block: suspend () -> Unit) {
        menu = false
        scope.launch { runCatching { block() }.onFailure { toast(context, it.message) } }
    }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .chunkyClickable(RoundedCornerShape(18.dp), if (unread) b.unreadCard else b.card, b.outline, onLongClick = { menu = true }, onLongClickLabel = "Server options", onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServerIcon(s, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = b.onCard, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (s.memberCount == 1) "Just you so far" else "${s.memberCount} members",
                    fontSize = 14.sp,
                    color = b.muted,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            if (muted) Icon(Icons.Default.NotificationsOff, contentDescription = "Muted", tint = b.muted, modifier = Modifier.size(16.dp))
            if (s.mentionCount > 0) {
                Spacer(Modifier.width(6.dp))
                Badge(s.mentionCount)
            } else if (unread) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(12.dp).chunky(CircleShape, BantsPurple, b.outline, shadow = 0.dp, border = 2.dp))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Mark as read") }, leadingIcon = { Icon(Icons.Default.DoneAll, null) }, onClick = { act { Servers.markServerRead(s.id) } })
            DropdownMenuItem(text = { Text(if (muted) "Unmute" else "Mute") }, leadingIcon = { Icon(Icons.Default.NotificationsOff, null) }, onClick = { act { Servers.setMuted(s.id, !muted) } })
            if (s.ownerId != Bants.myId) {
                DropdownMenuItem(text = { Text("Leave server") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) }, onClick = { menu = false; confirmLeave = true })
            }
        }
    }
    if (confirmLeave) ConfirmDialog("Leave ${s.name}?", "You'll need a new invite to get back in.", "Leave", onDismiss = { confirmLeave = false }) {
        confirmLeave = false
        act { Servers.leave(s.id) }
    }
}

@Composable
fun ServerIcon(s: BantsServer, size: androidx.compose.ui.unit.Dp) {
    val b = LocalBants.current
    if (s.icon != null) {
        Avatar(s.name, s.icon, size = size)
    } else {
        // Initials in a rounded square, so servers don't look like people.
        val initials = s.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }
        Box(
            Modifier.size(size).chunky(RoundedCornerShape(size * 0.3f), colourFor(s.name), b.outline, shadow = 0.dp, border = 2.5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, color = Color.White, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.36f).sp)
        }
    }
}

// ───────────────────────── one server: its channels ─────────────────────────

@Composable
fun ServerScreen(serverId: Long, nav: Navigator) {
    val all by Servers.servers.collectAsState()
    val s = all[serverId]
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var inviting by remember { mutableStateOf(false) }
    var creatingIn by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ServerChannel?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val collapsed = remember { mutableStateListOf<Long>() }
    val allMembers by Servers.members.collectAsState()
    // Voice channels open their own text chat, with the Join voice card at the top (like Discord on mobile).
    fun open(c: ServerChannel) = nav.go(Screen.Channel(c.id))

    LaunchedEffect(serverId) {
        runCatching { Servers.refreshServer(serverId) }.onFailure { if (Servers.servers.value[serverId] == null) nav.back() }
        runCatching { Servers.loadMembers(serverId) }
    }
    if (s == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val perms = s.permissions
    val isOwner = s.ownerId == Bants.myId
    val b = LocalBants.current

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ChunkyTopBar(s.name, nav) {
            if (perms.has(Perm.CREATE_INVITE)) ChunkyIconButton(Icons.Default.Link, "Invite people", { inviting = true })
            ChunkyIconButton(Icons.Default.Groups, "Members", { nav.go(Screen.ServerMembers(serverId)) })
            Box {
                ChunkyIconButton(Icons.Default.MoreVert, "Server options", { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (perms.has(Perm.MANAGE_CHANNELS)) {
                        DropdownMenuItem(text = { Text("New channel") }, leadingIcon = { Icon(Icons.Default.Add, null) }, onClick = { menu = false; creatingIn = null; creating = true })
                    }
                    if (perms.has(Perm.MANAGE_SERVER) || perms.has(Perm.MANAGE_ROLES)) {
                        DropdownMenuItem(text = { Text("Server settings") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { menu = false; nav.go(Screen.ServerSettings(serverId)) })
                    }
                    DropdownMenuItem(text = { Text("Mark as read") }, leadingIcon = { Icon(Icons.Default.DoneAll, null) }, onClick = {
                        menu = false
                        scope.launch { runCatching { Servers.markServerRead(serverId) }.onFailure { toast(context, it.message) } }
                    })
                    DropdownMenuItem(text = { Text(if (s.me?.muted == true) "Unmute server" else "Mute server") }, leadingIcon = { Icon(Icons.Default.NotificationsOff, null) }, onClick = {
                        menu = false
                        scope.launch { runCatching { Servers.setMuted(serverId, s.me?.muted != true) }.onFailure { toast(context, it.message) } }
                    })
                    if (!isOwner) {
                        DropdownMenuItem(text = { Text("Leave server") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) }, onClick = { menu = false; confirm = "leave" })
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(2.5.dp).background(b.outline))

        val top = s.channels.filter { it.parentId == null && it.type != "category" }.sortedBy { it.position }
        val categories = s.channels.filter { it.type == "category" }.sortedBy { it.position }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 40.dp),
        ) {
            if (s.description.isNotBlank()) {
                item { Text(s.description, color = b.muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) }
            }
            items(top, key = { it.id }) { c ->
                ChannelRow(c, allMembers[serverId].orEmpty(), onLongClick = if (perms.has(Perm.MANAGE_CHANNELS)) ({ editing = c }) else null) { open(c) }
            }
            categories.forEach { cat ->
                val kids = s.channels.filter { it.parentId == cat.id }.sortedBy { it.position }
                val shut = cat.id in collapsed
                item(key = "cat-${cat.id}") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                            .bouncyClickable(squash = 0.02f, onClickLabel = if (shut) "Show channels" else "Hide channels", onLongClick = if (perms.has(Perm.MANAGE_CHANNELS)) ({ editing = cat }) else null) {
                                if (shut) collapsed.remove(cat.id) else collapsed.add(cat.id)
                            }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (shut) Icons.Default.ExpandMore else Icons.Default.ExpandLess, contentDescription = null, tint = b.muted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(cat.name.uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 0.6.sp, color = b.muted, modifier = Modifier.weight(1f))
                        if (perms.has(Perm.MANAGE_CHANNELS)) {
                            Icon(
                                Icons.Default.Add, contentDescription = "New channel in ${cat.name}", tint = b.muted,
                                modifier = Modifier.size(22.dp).bouncyClickable { creatingIn = cat.id; creating = true },
                            )
                        }
                    }
                }
                if (!shut) items(kids, key = { it.id }) { c ->
                    ChannelRow(c, allMembers[serverId].orEmpty(), onLongClick = if (perms.has(Perm.MANAGE_CHANNELS)) ({ editing = c }) else null) { open(c) }
                }
            }
        }
        VoiceBar(Modifier.navigationBarsPadding().padding(12.dp)) { id -> nav.go(Screen.Channel(id)) }
    }

    if (inviting) InviteDialog(s) { inviting = false }
    if (creating) CreateChannelDialog(s, creatingIn) { creating = false }
    editing?.let { c -> EditChannelDialog(c) { editing = null } }
    if (confirm == "leave") ConfirmDialog("Leave ${s.name}?", "You'll need a new invite to get back in.", "Leave", onDismiss = { confirm = null }) {
        confirm = null
        scope.launch {
            runCatching { Servers.leave(serverId) }.onSuccess { nav.back() }.onFailure { toast(context, it.message) }
        }
    }
}

fun voiceTitle(c: ServerChannel, s: BantsServer?) = if (s != null) "${c.name} · ${s.name}" else c.name

/** "You're in voice" strip with mute and leave, shown in the servers screens while you're in a voice channel. */
@Composable
fun VoiceBar(modifier: Modifier = Modifier, onChat: ((Long) -> Unit)? = null) {
    val call by CallManager.state.collectAsState()
    val c = call ?: return
    if (c.voiceChannelId == null || c.status == CallStatus.Ended) return
    val b = LocalBants.current
    Row(
        modifier
            .fillMaxWidth()
            .chunkyClickable(RoundedCornerShape(16.dp), OnlineGreen, b.outline, shadow = 3.dp, onClickLabel = "Open voice") { CallManager.showCallScreen() }
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = BantsInk, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (c.status == CallStatus.Active) "Voice connected" else "Connecting…",
                fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = BantsInk,
            )
            Text(c.peerName, fontSize = 13.sp, color = BantsInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onChat != null) {
            ChunkyIconButton(Icons.AutoMirrored.Filled.Chat, "Voice channel chat", { onChat(c.voiceChannelId) }, size = 40.dp)
            Spacer(Modifier.width(8.dp))
        }
        ChunkyIconButton(if (c.muted) Icons.Default.MicOff else Icons.Default.Mic, if (c.muted) "Unmute" else "Mute", { CallManager.toggleMute() }, size = 40.dp)
        Spacer(Modifier.width(8.dp))
        ChunkyIconButton(Icons.Default.CallEnd, "Leave voice", { CallManager.hangUp() }, fill = BantsPink, tint = BantsInk, size = 40.dp)
    }
}

fun channelIcon(c: ServerChannel): ImageVector = when (c.type) {
    "voice" -> Icons.AutoMirrored.Filled.VolumeUp
    "announcement" -> Icons.Default.Campaign
    else -> Icons.Default.Tag
}

@Composable
private fun ChannelRow(c: ServerChannel, members: List<ServerMember>, onLongClick: (() -> Unit)?, onClick: () -> Unit) {
    Column {
        ChannelRowInner(c, onLongClick, onClick)
        // Who's in a voice channel, Discord-style, under its name.
        c.voiceMembers.forEach { v ->
            val m = members.firstOrNull { it.user.id == v.id }
            val b = LocalBants.current
            Row(Modifier.padding(start = 40.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(m?.user?.displayName ?: "?", m?.user?.avatar, size = 24.dp, outlined = false)
                Spacer(Modifier.width(8.dp))
                Text(m?.shownName() ?: "Someone", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = b.onCard, modifier = Modifier.weight(1f, fill = false))
                if (v.screen) {
                    Spacer(Modifier.width(6.dp))
                    Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.background(Color(0xFFE5484D), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
                }
                if (v.video) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Videocam, contentDescription = "Camera on", tint = b.muted, modifier = Modifier.size(15.dp))
                }
                if (v.muted) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.MicOff, contentDescription = "Muted", tint = b.muted, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
private fun ChannelRowInner(c: ServerChannel, onLongClick: (() -> Unit)?, onClick: () -> Unit) {
    val b = LocalBants.current
    val hidden = !c.permissions.has(Perm.VIEW_CHANNEL)
    val unread = c.unread || c.mentionCount > 0
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .bouncyClickable(squash = 0.03f, onLongClick = onLongClick, onClick = onClick)
            .background(if (unread) b.card else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(channelIcon(c), contentDescription = null, tint = if (unread) b.onCard else b.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            c.name,
            fontSize = 16.sp,
            fontWeight = if (unread) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (unread) b.onCard else b.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (hidden || (!c.syncPerms && c.type != "category")) {
            Icon(Icons.Default.Lock, contentDescription = "Private", tint = b.muted, modifier = Modifier.size(14.dp))
        }
        if (c.mentionCount > 0) {
            Spacer(Modifier.width(6.dp))
            Badge(c.mentionCount)
        }
    }
}

// ───────────────────────── dialogs ─────────────────────────

@Composable
fun ConfirmDialog(title: String, body: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddServerDialog(onDismiss: () -> Unit, onDone: (BantsServer) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var joining by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (joining) "Join a server" else "Start a server", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChunkyChip("Start one", !joining) { joining = false; text = "" }
                    ChunkyChip("Join one", joining) { joining = true; text = "" }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(if (joining) "Invite link or code" else "Server name") },
                    placeholder = { Text(if (joining) "k3Fq9xPa" else "The Lads") },
                    singleLine = true,
                    shape = FieldShape,
                    colors = chunkyFieldColors(),
                )
                Text(
                    if (joining) "Paste the link someone sent you." else "You'll get #general and a voice channel to start with. You can add more later.",
                    fontSize = 13.sp,
                    color = LocalBants.current.muted,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    runCatching { if (joining) Servers.join(text) else Servers.create(text) }
                        .onSuccess(onDone)
                        .onFailure { toast(context, it.message) }
                    busy = false
                }
            }) { Text(if (joining) "Join" else "Create", fontWeight = FontWeight.ExtraBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun InviteDialog(s: BantsServer, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var code by remember { mutableStateOf<String?>(null) }
    var forever by remember { mutableStateOf(false) }
    LaunchedEffect(forever) {
        code = null
        runCatching { Servers.createInvite(s.id, hours = if (forever) 0 else 168) }
            .onSuccess { code = it.code }
            .onFailure { toast(context, it.message); onDismiss() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite people to ${s.name}", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Send them this code. They tap Servers, then the plus, then Join one.", color = LocalBants.current.muted, fontSize = 14.sp)
                Box(
                    Modifier.fillMaxWidth().chunky(RoundedCornerShape(14.dp), BantsYellow, LocalBants.current.outline, shadow = 3.dp).padding(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(code ?: "…", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = BantsInk, letterSpacing = 1.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Never expires", modifier = Modifier.weight(1f))
                    Switch(checked = forever, onCheckedChange = { forever = it })
                }
                if (!forever) Text("Works for 7 days.", fontSize = 13.sp, color = LocalBants.current.muted)
            }
        },
        confirmButton = {
            TextButton(enabled = code != null, onClick = {
                val c = code ?: return@TextButton
                val text = "Join ${s.name} on Bants with the invite code $c"
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share invite"))
            }) { Text("Share", fontWeight = FontWeight.ExtraBold) }
        },
        dismissButton = {
            TextButton(enabled = code != null, onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Invite code", code))
                toast(context, "Copied")
            }) { Text("Copy") }
        },
    )
}

@Composable
private fun CreateChannelDialog(s: BantsServer, parentId: Long?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("text") }
    var private by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val parentName = s.channels.firstOrNull { it.id == parentId }?.name
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (parentName != null) "New channel in $parentName" else "New channel", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChunkyChip("Text", type == "text") { type = "text" }
                    ChunkyChip("Voice", type == "voice") { type = "voice" }
                    ChunkyChip("News", type == "announcement") { type = "announcement" }
                }
                if (parentId == null) ChunkyChip("Category", type == "category") { type = "category" }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (type == "category") "Category name" else "Channel name") },
                    placeholder = { Text(if (type == "voice") "Gaming" else if (type == "category") "Banter" else "memes") },
                    singleLine = true,
                    shape = FieldShape,
                    colors = chunkyFieldColors(),
                )
                if (type != "category") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Private", fontWeight = FontWeight.Bold)
                            Text("Only you to start with. Add roles to it from Server settings.", fontSize = 13.sp, color = LocalBants.current.muted)
                        }
                        Switch(checked = private, onCheckedChange = { private = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    runCatching { Servers.createChannel(s.id, name, type, parentId, private) }
                        .onSuccess { onDismiss() }
                        .onFailure { toast(context, it.message) }
                    busy = false
                }
            }) { Text("Create", fontWeight = FontWeight.ExtraBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val slowmodes = listOf(0 to "Off", 5 to "5s", 30 to "30s", 60 to "1m", 300 to "5m")

@Composable
private fun EditChannelDialog(c: ServerChannel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(c.name) }
    var topic by remember { mutableStateOf(c.topic) }
    var slow by remember { mutableStateOf(c.slowmode) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (c.type == "category") "Edit category" else "Edit channel", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, shape = FieldShape, colors = chunkyFieldColors())
                if (c.isTextLikeChannel()) {
                    OutlinedTextField(value = topic, onValueChange = { topic = it }, label = { Text("Topic") }, maxLines = 3, shape = FieldShape, colors = chunkyFieldColors())
                    Text("Slowmode", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        slowmodes.forEach { (secs, label) -> ChunkyChip(label, slow == secs) { slow = secs } }
                    }
                }
                TextButton(onClick = { confirmDelete = true }) {
                    Text(if (c.type == "category") "Delete category" else "Delete channel", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                scope.launch {
                    runCatching { Servers.editChannel(c.id, name, topic, slow) }
                        .onSuccess { onDismiss() }
                        .onFailure { toast(context, it.message) }
                }
            }) { Text("Save", fontWeight = FontWeight.ExtraBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (confirmDelete) ConfirmDialog(
        "Delete ${c.name}?",
        if (c.type == "category") "The channels in it stay, they just lose their category." else "All its messages go with it. This can't be undone.",
        "Delete",
        onDismiss = { confirmDelete = false },
    ) {
        confirmDelete = false
        scope.launch {
            runCatching { Servers.deleteChannel(c.id) }.onSuccess { onDismiss() }.onFailure { toast(context, it.message) }
        }
    }
}

private fun ServerChannel.isTextLikeChannel() = type == "text" || type == "announcement"
