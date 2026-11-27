package com.wellsey.bants.ui

import com.wellsey.bants.data.shownAvatar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellsey.bants.data.Bants
import com.wellsey.bants.data.BantsServer
import com.wellsey.bants.data.Perm
import com.wellsey.bants.data.ServerMember
import com.wellsey.bants.data.ServerRole
import com.wellsey.bants.data.Servers
import com.wellsey.bants.data.has
import com.wellsey.bants.data.shownName
import kotlinx.coroutines.launch

private fun roleColour(r: ServerRole): Color? = r.colour?.let { Color(0xFF000000 or it) }

/** Position of someone's most senior role. The owner beats everyone. */
private fun topPosition(s: BantsServer, userId: Long, roleIds: List<Long>): Int =
    if (s.ownerId == userId) Int.MAX_VALUE else s.roles.filter { it.id in roleIds }.maxOfOrNull { it.position } ?: 0

// ───────────────────────── members ─────────────────────────

@Composable
fun ServerMembersScreen(serverId: Long, nav: Navigator) {
    val all by Servers.servers.collectAsState()
    val allMembers by Servers.members.collectAsState()
    val s = all[serverId]
    val list = allMembers[serverId]
    val context = LocalContext.current
    var selected by remember { mutableStateOf<ServerMember?>(null) }
    LaunchedEffect(serverId) { runCatching { Servers.loadMembers(serverId) }.onFailure { toast(context, it.message) } }
    if (s == null) return
    val b = LocalBants.current

    // Hoisted roles get their own section, like Discord. Everyone else is split into online and offline.
    val sections = remember(s, list) {
        val members = list.orEmpty()
        val hoisted = s.roles.filter { it.hoist && !it.isDefault }.sortedByDescending { it.position }
        val placed = mutableSetOf<Long>()
        val out = mutableListOf<Pair<String, List<ServerMember>>>()
        for (r in hoisted) {
            val inIt = members.filter { it.user.id !in placed && r.id in it.roleIds }
            if (inIt.isNotEmpty()) {
                out += r.name to inIt.sortedBy { it.shownName().lowercase() }
                placed += inIt.map { it.user.id }
            }
        }
        val rest = members.filter { it.user.id !in placed }
        val on = rest.filter { it.online }.sortedBy { it.shownName().lowercase() }
        val off = rest.filter { !it.online }.sortedBy { it.shownName().lowercase() }
        if (on.isNotEmpty()) out += "Online" to on
        if (off.isNotEmpty()) out += "Offline" to off
        out
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ChunkyTopBar("Members", nav)
        Box(Modifier.fillMaxWidth().height(2.5.dp).background(b.outline))
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading…", color = b.muted) }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 40.dp)) {
            sections.forEach { (title, people) ->
                item(key = "h-$title") {
                    Text("${title.uppercase()} — ${people.size}", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = b.muted, modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 4.dp))
                }
                items(people, key = { "$title-${it.user.id}" }) { m -> MemberRow(s, m) { selected = m } }
            }
        }
    }
    selected?.let { m -> ProfileSheet(s.id, m.user.id, nav) { selected = null } }
}

@Composable
private fun MemberRow(s: BantsServer, m: ServerMember, onClick: () -> Unit) {
    val b = LocalBants.current
    val roles = s.roles.filter { it.id in m.roleIds && !it.isDefault }.sortedByDescending { it.position }
    val nameColour = roles.firstOrNull { it.colour != null }?.let { roleColour(it) }
    Row(
        Modifier.fillMaxWidth().bouncyClickable(squash = 0.03f, onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(m.user.displayName, m.shownAvatar, size = 42.dp, online = m.online)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.shownName(), fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = nameColour ?: b.onCard, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                RoleIconBadge(roles.firstOrNull { it.iconEmoji != null || it.iconMedia != null })
                if (m.user.id == s.ownerId) Text(" 👑", fontSize = 13.sp)
                if (m.timeoutUntil != null) Text(" ⏳", fontSize = 13.sp)
            }
            Text("@${m.user.username}", fontSize = 13.sp, color = b.muted, maxLines = 1)
        }
    }
}

private val timeouts = listOf(10 to "10 mins", 60 to "1 hour", 24 * 60 to "1 day", 7 * 24 * 60 to "1 week")

@Composable
internal fun MemberDialog(s: BantsServer, m: ServerMember, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val b = LocalBants.current
    val myId = Bants.myId
    val self = m.user.id == myId
    val perms = s.permissions
    val myTop = topPosition(s, myId, s.me?.roleIds.orEmpty())
    val theirTop = topPosition(s, m.user.id, m.roleIds)
    val above = !self && myTop > theirTop
    var editingRoles by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var nick by remember { mutableStateOf(m.nickname.orEmpty()) }
    fun act(block: suspend () -> Unit) {
        scope.launch { runCatching { block() }.onSuccess { onDismiss() }.onFailure { toast(context, it.message) } }
    }
    val canNick = if (self) perms.has(Perm.CHANGE_NICKNAME) || perms.has(Perm.MANAGE_NICKNAMES) else perms.has(Perm.MANAGE_NICKNAMES) && above

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(m.user.displayName, m.user.avatar, size = 44.dp, online = m.online)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(m.shownName(), fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold)
                    Text("@${m.user.username}", fontSize = 13.sp, color = b.muted)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (m.user.about.isNotBlank()) Text(m.user.about, color = b.muted)
                val roles = s.roles.filter { it.id in m.roleIds && !it.isDefault }.sortedByDescending { it.position }
                if (roles.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        roles.forEach { r -> RolePill(r) }
                    }
                }
                if (canNick) {
                    OutlinedTextField(value = nick, onValueChange = { nick = it.take(32) }, label = { Text("Nickname in this server") }, singleLine = true, shape = FieldShape, colors = chunkyFieldColors())
                    if (nick != m.nickname.orEmpty()) {
                        TextButton(onClick = { act { Servers.setNickname(s.id, m.user.id, nick) } }) { Text("Save nickname", fontWeight = FontWeight.Bold) }
                    }
                }
                if (perms.has(Perm.MANAGE_ROLES) && (above || s.ownerId == myId)) {
                    TextButton(onClick = { editingRoles = true }) { Text("Roles…", fontWeight = FontWeight.Bold) }
                }
                if (perms.has(Perm.TIMEOUT_MEMBERS) && above) {
                    Text("Timeout", fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (m.timeoutUntil != null) ChunkyChip("End timeout", false) { act { Servers.timeout(s.id, m.user.id, 0) } }
                        timeouts.forEach { (mins, label) -> ChunkyChip(label, false) { act { Servers.timeout(s.id, m.user.id, mins) } } }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (perms.has(Perm.KICK_MEMBERS) && above) {
                        TextButton(onClick = { confirm = "kick" }) { Text("Kick", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                    }
                    if (perms.has(Perm.BAN_MEMBERS) && above) {
                        TextButton(onClick = { confirm = "ban" }) {
                            Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Ban", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )

    if (editingRoles) RolePickerDialog(s, m, myTop) { editingRoles = false }
    when (confirm) {
        "kick" -> ConfirmDialog("Kick ${m.shownName()}?", "They can come back if someone invites them again.", "Kick", onDismiss = { confirm = null }) {
            confirm = null
            act { Servers.kick(s.id, m.user.id) }
        }
        "ban" -> BanDialog(m, onDismiss = { confirm = null }) { reason, wipe ->
            confirm = null
            act { Servers.ban(s.id, m.user.id, reason, wipe) }
        }
    }
}

@Composable
private fun BanDialog(m: ServerMember, onDismiss: () -> Unit, onBan: (String, Int) -> Unit) {
    var reason by remember { mutableStateOf("") }
    var wipe by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ban ${m.shownName()}?", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("They're removed and can't rejoin, even with an invite.")
                OutlinedTextField(value = reason, onValueChange = { reason = it.take(300) }, label = { Text("Reason (optional)") }, shape = FieldShape, colors = chunkyFieldColors())
                Text("Delete their recent messages", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "None", 24 to "1 day", 168 to "7 days").forEach { (h, label) -> ChunkyChip(label, wipe == h) { wipe = h } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onBan(reason, wipe) }) { Text("Ban", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.ExtraBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RolePickerDialog(s: BantsServer, m: ServerMember, myTop: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf(m.roleIds.toSet()) }
    val roles = s.roles.filter { !it.isDefault }.sortedByDescending { it.position }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Roles for ${m.shownName()}", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                if (roles.isEmpty()) Text("No roles yet. Make some in Server settings.", color = LocalBants.current.muted)
                roles.forEach { r ->
                    val allowed = r.position < myTop
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = r.id in chosen, enabled = allowed, onCheckedChange = { on -> chosen = if (on) chosen + r.id else chosen - r.id })
                        RoleDot(r)
                        Spacer(Modifier.width(8.dp))
                        Text(r.name, fontWeight = FontWeight.Bold, color = if (allowed) LocalBants.current.onCard else LocalBants.current.muted)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    runCatching { Servers.setRoles(s.id, m.user.id, chosen.toList()) }.onSuccess { onDismiss() }.onFailure { toast(context, it.message) }
                }
            }) { Text("Save", fontWeight = FontWeight.ExtraBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RoleDot(r: ServerRole) {
    Box(Modifier.size(14.dp).background(roleColour(r) ?: LocalBants.current.muted, CircleShape).border(1.5.dp, LocalBants.current.outline, CircleShape))
}

@Composable
private fun RolePill(r: ServerRole) {
    val b = LocalBants.current
    Row(
        Modifier.chunky(RoundedCornerShape(50), b.card, b.outline, shadow = 0.dp, border = 2.dp).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoleDot(r)
        Spacer(Modifier.width(6.dp))
        Text(r.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = b.onCard)
    }
}

// ───────────────────────── server settings ─────────────────────────

@Composable
fun ServerSettingsScreen(serverId: Long, nav: Navigator) {
    val all by Servers.servers.collectAsState()
    val s = all[serverId] ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val b = LocalBants.current
    var name by remember(s.id) { mutableStateOf(s.name) }
    var description by remember(s.id) { mutableStateOf(s.description) }
    var confirmDelete by remember { mutableStateOf(false) }
    val canEdit = s.permissions.has(Perm.MANAGE_SERVER)
    val isOwner = s.ownerId == Bants.myId

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ChunkyTopBar("Server settings", nav)
        Box(Modifier.fillMaxWidth().height(2.5.dp).background(b.outline))
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ServerIcon(s, 64.dp)
                    Spacer(Modifier.width(14.dp))
                    Text("${s.memberCount} members", color = b.muted, fontWeight = FontWeight.SemiBold)
                }
            }
            if (canEdit) {
                item { OutlinedTextField(value = name, onValueChange = { name = it.take(100) }, label = { Text("Server name") }, singleLine = true, shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = description, onValueChange = { description = it.take(1000) }, label = { Text("What's it about?") }, maxLines = 4, shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.fillMaxWidth()) }
                item {
                    ChunkyButton("Save", onClick = {
                        scope.launch {
                            runCatching { Servers.update(s.id, name, description) }.onSuccess { toast(context, "Saved") }.onFailure { toast(context, it.message) }
                        }
                    }, enabled = name.isNotBlank() && (name != s.name || description != s.description), modifier = Modifier.fillMaxWidth())
                }
            }
            if (canEdit) item { ServerLooksSection(s) }
            if (s.permissions.has(Perm.MANAGE_ROLES)) {
                item { ActionRow(Icons.Default.Shield, "Roles", "Who can do what") { nav.go(Screen.ServerRoles(s.id)) } }
            }
            if (isOwner) {
                item {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete server", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
    if (confirmDelete) ConfirmDialog("Delete ${s.name}?", "Every channel and message goes, for everyone. This can't be undone.", "Delete", onDismiss = { confirmDelete = false }) {
        confirmDelete = false
        scope.launch {
            runCatching { Servers.deleteServer(s.id) }.onSuccess { nav.home() }.onFailure { toast(context, it.message) }
        }
    }
}

// ───────────────────────── roles ─────────────────────────

@Composable
fun ServerRolesScreen(serverId: Long, nav: Navigator) {
    val all by Servers.servers.collectAsState()
    val s = all[serverId] ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val b = LocalBants.current
    val roles = s.roles.sortedByDescending { if (it.isDefault) -1 else it.position }
    val myTop = topPosition(s, Bants.myId, s.me?.roleIds.orEmpty())

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ChunkyTopBar("Roles", nav) {
            ChunkyIconButton(Icons.Default.Add, "New role", {
                scope.launch {
                    runCatching { Servers.createRole(s.id, "new role") }
                        .onSuccess { nav.go(Screen.RoleEdit(s.id, it.id)) }
                        .onFailure { toast(context, it.message) }
                }
            })
        }
        Box(Modifier.fillMaxWidth().height(2.5.dp).background(b.outline))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Roles higher up the list outrank the ones below. You can only edit roles below your own.", color = b.muted, fontSize = 13.sp) }
            items(roles, key = { it.id }) { r ->
                val editable = r.isDefault || r.position < myTop
                Row(
                    Modifier.fillMaxWidth()
                        .chunkyClickable(RoundedCornerShape(16.dp), b.card, b.outline, shadow = if (editable) 3.dp else 0.dp, enabled = editable) { nav.go(Screen.RoleEdit(s.id, r.id)) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoleDot(r)
                    Spacer(Modifier.width(10.dp))
                    Text(r.name, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = if (editable) b.onCard else b.muted, modifier = Modifier.weight(1f))
                    if (r.isDefault) Text("everyone", fontSize = 12.sp, color = b.muted)
                    if (editable) Icon(Icons.Default.ChevronRight, contentDescription = null, tint = b.muted)
                }
            }
        }
    }
}

private val palette = listOf(0x5B4BFFL, 0xFF4D8DL, 0x00A88FL, 0xFF8A00L, 0x2D8CFFL, 0x9B51E0L, 0xE5484DL, 0x3D9A50L, 0xFFD23FL)

@Composable
fun RoleEditScreen(serverId: Long, roleId: Long, nav: Navigator) {
    val all by Servers.servers.collectAsState()
    val s = all[serverId] ?: return
    val role = s.roles.firstOrNull { it.id == roleId } ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val b = LocalBants.current
    var name by remember(roleId) { mutableStateOf(role.name) }
    var colour by remember(roleId) { mutableStateOf(role.colour) }
    var perms by remember(roleId) { mutableLongStateOf(role.permissions) }
    var hoist by remember(roleId) { mutableStateOf(role.hoist) }
    var mentionable by remember(roleId) { mutableStateOf(role.mentionable) }
    var iconEmoji by remember(roleId) { mutableStateOf(role.iconEmoji.orEmpty()) }
    var iconMedia by remember(roleId) { mutableStateOf(role.iconMedia) }
    var confirmDelete by remember { mutableStateOf(false) }
    val mine = s.permissions

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ChunkyTopBar(if (role.isDefault) "@everyone" else "Edit role", nav) {
            ChunkyIconButton(Icons.Default.Check, "Save", {
                scope.launch {
                    runCatching { Servers.updateRole(s.id, role.id, if (role.isDefault) null else name, colour, perms, hoist, mentionable, iconEmoji, iconMedia) }
                        .onSuccess { nav.back() }
                        .onFailure { toast(context, it.message) }
                }
            }, fill = BantsPurple, tint = Color.White)
        }
        Box(Modifier.fillMaxWidth().height(2.5.dp).background(b.outline))
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!role.isDefault) {
                item { OutlinedTextField(value = name, onValueChange = { name = it.take(100) }, label = { Text("Role name") }, singleLine = true, shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.fillMaxWidth()) }
                item { RoleIconPicker(iconEmoji, iconMedia, { iconEmoji = it }, { iconMedia = it }) }
                item {
                  Column {
                    Text("Colour", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.size(34.dp).chunkyClickable(CircleShape, b.card, b.outline, shadow = 0.dp, border = if (colour == null) 4.dp else 2.dp) { colour = null },
                            contentAlignment = Alignment.Center,
                        ) { Text("–", fontWeight = FontWeight.ExtraBold) }
                        palette.forEach { c ->
                            Box(Modifier.size(34.dp).chunkyClickable(CircleShape, Color(0xFF000000 or c), b.outline, shadow = 0.dp, border = if (colour == c) 4.dp else 2.dp) { colour = c })
                        }
                    }
                  }
                }
                item { ToggleRow("Show separately in the member list", hoist) { hoist = it } }
                item { ToggleRow("Anyone can @mention this role", mentionable) { mentionable = it } }
            } else {
                item { Text("What everyone in the server can do, before any other roles.", color = b.muted) }
            }
            Perm.groups.forEach { (group, bits) ->
                item(key = "g-$group") { Text(group.uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = b.muted, modifier = Modifier.padding(top = 8.dp)) }
                bits.forEach { (bit, label) ->
                    item(key = "p-$bit") {
                        // You can't hand out what you don't have, but you can always take it away.
                        val canChange = mine.has(bit) || perms.has(bit)
                        ToggleRow(label, perms.has(bit), enabled = canChange) { on -> perms = if (on) perms or bit else perms and bit.inv() }
                    }
                }
            }
            if (!role.isDefault) {
                item {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete role", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }
    if (confirmDelete) ConfirmDialog("Delete ${role.name}?", "Everyone with it loses it.", "Delete", onDismiss = { confirmDelete = false }) {
        confirmDelete = false
        scope.launch { runCatching { Servers.deleteRole(s.id, role.id) }.onSuccess { nav.back() }.onFailure { toast(context, it.message) } }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = if (enabled) LocalBants.current.onCard else LocalBants.current.muted)
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}
