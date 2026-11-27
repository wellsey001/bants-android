@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wellsey.bants.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wellsey.bants.data.Bants
import com.wellsey.bants.data.BantsServer
import com.wellsey.bants.data.ChannelMessage
import com.wellsey.bants.data.Perm
import com.wellsey.bants.data.ServerChannel
import com.wellsey.bants.data.ServerMember
import com.wellsey.bants.data.Servers
import com.wellsey.bants.data.has
import com.wellsey.bants.data.shownName
import kotlinx.coroutines.launch

private val QuickReactions = listOf("👍", "😂", "❤️", "🔥", "😮", "😢")

@Composable
fun ChannelScreen(channelId: Long, nav: Navigator) {
    val allServers by Servers.servers.collectAsState()
    val threads by Servers.threads.collectAsState()
    val allMessages by Servers.messages.collectAsState()
    val more by Servers.hasMore.collectAsState()
    val allMembers by Servers.members.collectAsState()
    val typingAll by Servers.typing.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val b = LocalBants.current

    val channel: ServerChannel? = allServers.values.firstNotNullOfOrNull { s -> s.channels.firstOrNull { it.id == channelId } } ?: threads[channelId]
    val server: BantsServer? = channel?.let { allServers[it.serverId] }
    val list = allMessages[channelId].orEmpty()
    val members = server?.let { allMembers[it.id] }.orEmpty()

    var field by remember { mutableStateOf(TextFieldValue("")) }
    var replyingTo by remember { mutableStateOf<ChannelMessage?>(null) }
    var editing by remember { mutableStateOf<ChannelMessage?>(null) }
    var picking by remember { mutableStateOf<ChannelMessage?>(null) }
    var threadsOpen by remember { mutableStateOf(false) }
    var pinsOpen by remember { mutableStateOf(false) }
    var loadingOlder by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(channelId) {
        runCatching { Servers.loadMessages(channelId) }.onFailure { toast(context, it.message) }
        Servers.markRead(channelId)
    }
    LaunchedEffect(server?.id) {
        val sid = server?.id ?: return@LaunchedEffect
        if (Servers.members.value[sid] == null) runCatching { Servers.loadMembers(sid) }
    }
    DisposableEffect(channelId) {
        Servers.activeChannel = channelId
        onDispose { if (Servers.activeChannel == channelId) Servers.activeChannel = null }
    }
    // Keep the badge clear while you're looking at it.
    LaunchedEffect(list.lastOrNull()?.id) { Servers.markRead(channelId) }

    // Load older messages when you scroll near the top.
    val nearTop by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 6 } }
    LaunchedEffect(nearTop, list.size) {
        if (nearTop && more[channelId] == true && !loadingOlder && list.isNotEmpty()) {
            loadingOlder = true
            runCatching { Servers.loadOlder(channelId) }
            loadingOlder = false
        }
    }

    if (channel == null || server == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val perms = channel.permissions
    val canSend = perms.has(Perm.SEND_MESSAGES)
    val myId = Bants.myId
    val timedOut = (server.me?.timeoutUntil ?: 0) > System.currentTimeMillis()
    val text = field.text

    fun send() {
        val e = editing
        if (e != null) {
            scope.launch { runCatching { Servers.edit(e, text) }.onFailure { toast(context, it.message) } }
            editing = null
        } else {
            Servers.send(channelId, text, replyingTo?.id)
            replyingTo = null
        }
        field = TextFieldValue("")
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ChunkyTopBar(if (channel.type == "thread" || channel.type == "voice") channel.name else "#${channel.name}", nav) {
            if (channel.type == "text" || channel.type == "announcement") {
                ChunkyIconButton(Icons.Default.Forum, "Threads", { threadsOpen = true })
            }
            ChunkyIconButton(Icons.Default.PushPin, "Pinned messages", { pinsOpen = true })
        }
        if (channel.topic.isNotBlank()) {
            Text(channel.topic, fontSize = 13.sp, color = b.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 8.dp))
        }
        if (channel.type == "voice") VoiceCard(channel, server, members)
        Box(Modifier.fillMaxWidth().height(2.5.dp).background(b.outline))

        Box(Modifier.weight(1f)) {
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyNote(
                        if (channel.type == "thread") "new thread" else if (channel.type == "voice") channel.name else "#${channel.name}",
                        if (channel.type == "voice") "Chat for whoever's in voice. Links, memes, \"can you hear me?\"" else "This is the very start. Say something cheeky.",
                    )
                }
            }
            val reversed = remember(list) { list.asReversed() }
            LazyColumn(
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(reversed.size, key = { reversed[it].id }) { i ->
                    val m = reversed[i]
                    val prev = reversed.getOrNull(i + 1) // older message, shown above
                    val grouped = prev != null && prev.kind == "default" && m.kind == "default" &&
                        prev.author?.id == m.author?.id && m.createdAt - prev.createdAt < 7 * 60_000 && sameDay(prev.createdAt, m.createdAt) && m.replyTo == null
                    Column {
                        if (prev == null || !sameDay(prev.createdAt, m.createdAt)) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) { Pill(dayLabel(m.createdAt)) }
                        }
                        ChannelMessageRow(
                            m, server, members, list, grouped, perms, myId,
                            onReply = { replyingTo = it; editing = null },
                            onEdit = { editing = it; replyingTo = null; field = TextFieldValue(plainBody(it.body, members), TextRange(plainBody(it.body, members).length)) },
                            onReactMore = { picking = it },
                            onThread = { msg ->
                                scope.launch {
                                    runCatching { Servers.startThread(channelId, msg.body.take(40), msg.id) }
                                        .onSuccess { nav.go(Screen.Channel(it.id)) }
                                        .onFailure { toast(context, it.message) }
                                }
                            },
                        )
                    }
                }
            }
        }

        // Who's typing
        val typers = typingAll[channelId].orEmpty().keys.filter { it != myId }
            .map { uid -> members.firstOrNull { it.user.id == uid }?.shownName() ?: "Someone" }
        if (typers.isNotEmpty()) {
            Text(
                when (typers.size) {
                    1 -> "${typers[0]} is typing…"
                    2 -> "${typers[0]} and ${typers[1]} are typing…"
                    else -> "Loads of people are typing…"
                },
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 18.dp, bottom = 2.dp),
            )
        }

        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.navigationBarsPadding().imePadding()) {
                if (!canSend) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp).chunky(RoundedCornerShape(14.dp), b.card, b.outline, shadow = 0.dp).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = b.muted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            when {
                                timedOut -> "You're in timeout. You can read but not post for now."
                                channel.type == "thread" && channel.locked -> "This thread is locked."
                                else -> "You can't send messages in this channel."
                            },
                            color = b.muted, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    return@Column
                }
                val e = editing
                val r = replyingTo
                if (e != null) {
                    ComposeBanner(Icons.Default.Edit, "Editing message", null) { editing = null; field = TextFieldValue("") }
                } else if (r != null) {
                    ComposeBanner(Icons.AutoMirrored.Filled.Reply, "Replying to ${r.author?.shownName() ?: "message"}", plainBody(r.body, members)) { replyingTo = null }
                }
                // @mention suggestions
                val cursor = field.selection.end.coerceIn(0, text.length)
                val query = Regex("(^|\\s)@([A-Za-z0-9_.]{0,24})$").find(text.substring(0, cursor))?.groupValues?.get(2)
                val suggestions = if (query == null) emptyList() else members.filter {
                    it.user.id != myId && (it.user.username.startsWith(query, true) || it.shownName().startsWith(query, true))
                }.take(6)
                if (suggestions.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        suggestions.forEach { m ->
                            Row(
                                Modifier
                                    .chunkyClickable(RoundedCornerShape(50), b.card, b.outline, shadow = 0.dp, border = 2.dp) {
                                        val start = cursor - (query?.length ?: 0)
                                        val insert = m.user.username + " "
                                        val newText = text.substring(0, start) + insert + text.substring(cursor)
                                        field = TextFieldValue(newText, TextRange(start + insert.length))
                                    }
                                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(m.user.displayName, m.user.avatar, size = 26.dp, outlined = false)
                                Spacer(Modifier.width(6.dp))
                                Text(m.shownName(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = b.onCard)
                                Text(" @${m.user.username}", fontSize = 13.sp, color = b.muted)
                            }
                        }
                    }
                }
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = field,
                        onValueChange = {
                            field = it
                            if (it.text.isNotEmpty() && editing == null) Servers.sendTyping(channelId)
                        },
                        placeholder = { Text(if (channel.type == "thread") "Reply in thread…" else if (channel.type == "voice") "Message ${channel.name}" else "Message #${channel.name}") },
                        maxLines = 5,
                        shape = FieldShape,
                        colors = chunkyFieldColors(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    ChunkyIconButton(
                        if (editing != null) Icons.Default.Done else Icons.AutoMirrored.Filled.Send,
                        "Send",
                        { send() },
                        fill = BantsPurple,
                        tint = Color.White,
                        size = 52.dp,
                        shadow = 4.dp,
                        enabled = text.isNotBlank(),
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }
    }

    picking?.let { m ->
        EmojiReactionSheet(onDismiss = { picking = null }) { emoji ->
            picking = null
            scope.launch { runCatching { Servers.toggleReaction(m, emoji) }.onFailure { toast(context, it.message) } }
        }
    }
    if (threadsOpen) ThreadsDialog(channel, nav) { threadsOpen = false }
    if (pinsOpen) PinsDialog(channel, members) { pinsOpen = false }
}

@Composable
private fun ComposeBanner(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String?, onClose: () -> Unit) {
    val b = LocalBants.current
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp).chunky(RoundedCornerShape(14.dp), b.card, b.outline, shadow = 0.dp, border = 2.dp)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = BantsPurple, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = b.onCard)
            if (detail != null) Text(detail, fontSize = 13.sp, color = b.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ChunkyIconButton(Icons.Default.Close, "Cancel", onClose, size = 32.dp)
    }
}

/** Swaps mention tokens for names, for editing and previews. */
private fun plainBody(body: String, members: List<ServerMember>): String =
    Regex("<@(\\d+)>").replace(body) { m ->
        val who = members.firstOrNull { it.user.id == m.groupValues[1].toLong() }
        if (who != null) "@${who.user.username}" else "@someone"
    }

/** Message text with mentions, role pings and channel links picked out. */
private fun richBody(body: String, server: BantsServer, members: List<ServerMember>, myId: Long, myRoles: List<Long>): AnnotatedString = buildAnnotatedString {
    val token = Regex("<@(\\d+)>|<@&(\\d+)>|<#(\\d+)>|@everyone|@here")
    var last = 0
    for (m in token.findAll(body)) {
        append(body.substring(last, m.range.first))
        val userId = m.groupValues[1].toLongOrNull()
        val roleId = m.groupValues[2].toLongOrNull()
        val channelId = m.groupValues[3].toLongOrNull()
        val (label, mine) = when {
            userId != null -> "@" + (members.firstOrNull { it.user.id == userId }?.shownName() ?: "someone") to (userId == myId)
            roleId != null -> "@" + (server.roles.firstOrNull { it.id == roleId }?.name ?: "role") to (roleId in myRoles)
            channelId != null -> "#" + (server.channels.firstOrNull { it.id == channelId }?.name ?: "channel") to false
            else -> m.value to true
        }
        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = BantsPurple, background = if (mine) BantsYellow.copy(alpha = 0.55f) else BantsPurple.copy(alpha = 0.12f))) { append(label) }
        last = m.range.last + 1
    }
    append(body.substring(last))
}

@Composable
private fun ChannelMessageRow(
    m: ChannelMessage,
    server: BantsServer,
    members: List<ServerMember>,
    all: List<ChannelMessage>,
    grouped: Boolean,
    perms: Long,
    myId: Long,
    onReply: (ChannelMessage) -> Unit,
    onEdit: (ChannelMessage) -> Unit,
    onReactMore: (ChannelMessage) -> Unit,
    onThread: (ChannelMessage) -> Unit,
) {
    val b = LocalBants.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun act(block: suspend () -> Unit) {
        menu = false
        scope.launch { runCatching { block() }.onFailure { toast(context, it.message) } }
    }

    // System lines: joins, pins, new threads
    if (m.kind != "default") {
        val who = m.author?.shownName() ?: "Someone"
        val line = when (m.kind) {
            "join" -> "👋 $who just landed. Say hi!"
            "pin" -> "📌 $who pinned a message."
            "thread_created" -> "🧵 $who started a thread."
            else -> m.body
        }
        Text(line, fontSize = 13.sp, color = b.muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp))
        return
    }

    val mine = m.author?.id == myId
    val mentionsMe = m.mentionsEveryone || m.body.contains("<@$myId>")
    val myRoles = server.me?.roleIds.orEmpty()
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .bouncyClickable(squash = 0.01f, onLongClick = { if (!m.pending) menu = true }, onClick = {})
                .background(if (mentionsMe && !mine) BantsYellow.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(12.dp))
                .padding(start = 6.dp, end = 6.dp, top = if (grouped) 1.dp else 8.dp, bottom = 1.dp),
        ) {
            Box(Modifier.width(44.dp)) {
                if (!grouped) Avatar(m.author?.displayName ?: "?", m.author?.avatar, size = 38.dp)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                m.replyTo?.let { rid ->
                    val quoted = all.firstOrNull { it.id == rid }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = b.muted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (quoted == null) "Replying to an older message" else "${quoted.author?.shownName() ?: "Someone"}: ${plainBody(quoted.body, members)}",
                            fontSize = 12.sp, color = b.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!grouped) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        val colour = m.author?.let { Servers.nameColour(server.id, it.id) }
                        Text(
                            m.author?.shownName() ?: "Deleted account",
                            fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                            color = colour?.let { Color(0xFF000000 or it) } ?: b.onCard,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (m.author?.id == server.ownerId) Text(" 👑", fontSize = 13.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(clockTime(m.createdAt), fontSize = 12.sp, color = b.muted)
                    }
                }
                if (m.body.isNotEmpty()) {
                    Text(
                        richBody(m.body, server, members, myId, myRoles),
                        fontSize = 15.sp,
                        color = if (m.pending) b.muted else b.onCard,
                    )
                }
                if (m.editedAt != null) Text("(edited)", fontSize = 11.sp, color = b.muted)
                m.attachments.forEach { a ->
                    if (a.mime.startsWith("image/")) {
                        AsyncImage(
                            model = mediaUrl(a.id),
                            contentDescription = a.filename,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.padding(top = 6.dp).heightIn(max = 260.dp).widthIn(max = 280.dp)
                                .chunky(RoundedCornerShape(14.dp), b.card, b.outline, shadow = 3.dp),
                        )
                    } else {
                        Pill("📎 ${a.filename.ifBlank { "file" }}", fill = b.card, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                if (m.reactions.isNotEmpty()) {
                    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        m.reactions.forEach { r ->
                            val iReacted = myId in r.userIds
                            Row(
                                Modifier
                                    .chunkyClickable(RoundedCornerShape(50), if (iReacted) BantsYellow else b.card, b.outline, shadow = 0.dp, border = 2.dp) {
                                        scope.launch { runCatching { Servers.toggleReaction(m, r.emoji) }.onFailure { toast(context, it.message) } }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(r.emoji, fontSize = 14.sp)
                                Spacer(Modifier.width(4.dp))
                                Text("${r.userIds.size}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (iReacted) BantsInk else b.onCard)
                            }
                        }
                    }
                }
                if (m.failed) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Text("Didn't send.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { Servers.retry(m) }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retry", fontSize = 12.sp)
                        }
                        TextButton(onClick = { Servers.discard(m) }) { Text("Delete", fontSize = 12.sp) }
                    }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                (Bants.session.recentReactions + QuickReactions).distinct().take(6).forEach { e ->
                    Text(e, fontSize = 24.sp, modifier = Modifier.bouncyClickable { act { Servers.toggleReaction(m, e) } }.padding(6.dp))
                }
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("More reactions") }, leadingIcon = { Icon(Icons.Default.AddReaction, null) }, onClick = { menu = false; onReactMore(m) })
            if (perms.has(Perm.SEND_MESSAGES)) {
                DropdownMenuItem(text = { Text("Reply") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, null) }, onClick = { menu = false; onReply(m) })
            }
            DropdownMenuItem(text = { Text("Copy text") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = {
                menu = false
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Message", plainBody(m.body, members)))
            })
            val ch = Servers.channel(m.channelId)
            if (ch != null && ch.type != "thread" && perms.has(Perm.CREATE_THREADS)) {
                DropdownMenuItem(text = { Text("Start a thread") }, leadingIcon = { Icon(Icons.Default.Forum, null) }, onClick = { menu = false; onThread(m) })
            }
            if (mine) {
                DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit(m) })
            }
            if (perms.has(Perm.MANAGE_MESSAGES)) {
                DropdownMenuItem(
                    text = { Text(if (m.pinned) "Unpin" else "Pin") },
                    leadingIcon = { Icon(Icons.Default.PushPin, null) },
                    onClick = { act { Servers.setPinned(m, !m.pinned) } },
                )
            }
            if (mine || perms.has(Perm.MANAGE_MESSAGES)) {
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; confirmDelete = true },
                )
            }
        }
    }
    if (confirmDelete) ConfirmDialog("Delete this message?", "It goes for everyone. This can't be undone.", "Delete", onDismiss = { confirmDelete = false }) {
        confirmDelete = false
        act { Servers.delete(m) }
    }
}

@Composable
private fun ThreadsDialog(channel: ServerChannel, nav: Navigator, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<ServerChannel>?>(null) }
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    LaunchedEffect(channel.id) { list = runCatching { Servers.loadThreads(channel.id) }.getOrElse { toast(context, it.message); emptyList() } }
    val b = LocalBants.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Threads in #${channel.name}", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val threads = list
                when {
                    threads == null -> Text("Loading…", color = b.muted)
                    threads.isEmpty() && !naming -> Text("No threads yet. Long-press a message to start one, or make one here.", color = b.muted)
                    else -> threads.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().chunkyClickable(RoundedCornerShape(12.dp), b.card, b.outline, shadow = 0.dp, border = 2.dp) {
                                onDismiss()
                                nav.go(Screen.Channel(t.id))
                            }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Forum, contentDescription = null, tint = b.muted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(t.name, fontWeight = if (t.unread) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (t.locked) Icon(Icons.Default.Lock, contentDescription = "Locked", tint = b.muted, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                if (naming) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Thread name") }, singleLine = true, shape = FieldShape, colors = chunkyFieldColors())
                }
            }
        },
        confirmButton = {
            if (channel.permissions.has(Perm.CREATE_THREADS)) {
                TextButton(enabled = !naming || name.isNotBlank(), onClick = {
                    if (!naming) naming = true else scope.launch {
                        runCatching { Servers.startThread(channel.id, name, null) }
                            .onSuccess { onDismiss(); nav.go(Screen.Channel(it.id)) }
                            .onFailure { toast(context, it.message) }
                    }
                }) { Text(if (naming) "Start" else "New thread", fontWeight = FontWeight.ExtraBold) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun PinsDialog(channel: ServerChannel, members: List<ServerMember>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var list by remember { mutableStateOf<List<ChannelMessage>?>(null) }
    LaunchedEffect(channel.id) { list = runCatching { Servers.pins(channel.id) }.getOrElse { toast(context, it.message); emptyList() } }
    val b = LocalBants.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pinned", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val pins = list
                when {
                    pins == null -> Text("Loading…", color = b.muted)
                    pins.isEmpty() -> Text("Nothing pinned yet. Mods can pin from a message's long-press menu.", color = b.muted)
                    else -> pins.take(20).forEach { p ->
                        Column(Modifier.fillMaxWidth().chunky(RoundedCornerShape(12.dp), b.card, b.outline, shadow = 0.dp, border = 2.dp).padding(10.dp)) {
                            Text(p.author?.shownName() ?: "Someone", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            Text(plainBody(p.body, members), fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            Text(shortTime(p.createdAt), fontSize = 11.sp, color = b.muted)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}


/** Top of a voice channel's chat: who's in there, and Join / Open / Leave. */
@Composable
private fun VoiceCard(channel: ServerChannel, server: BantsServer, members: List<ServerMember>) {
    val b = LocalBants.current
    val context = LocalContext.current
    val call by com.wellsey.bants.calls.CallManager.state.collectAsState()
    val inHere = call?.voiceChannelId == channel.id && call?.status != com.wellsey.bants.calls.CallStatus.Ended
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) com.wellsey.bants.calls.CallManager.joinVoice(channel.id, voiceTitle(channel, server))
        else toast(context, "Bants needs the microphone for voice channels")
    }
    fun join() {
        if (!channel.permissions.has(Perm.CONNECT)) return toast(context, "You can't join this voice channel")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            com.wellsey.bants.calls.CallManager.joinVoice(channel.id, voiceTitle(channel, server))
        } else mic.launch(Manifest.permission.RECORD_AUDIO)
    }
    Column(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
            .chunky(RoundedCornerShape(16.dp), if (inHere) OnlineGreen else b.card, b.outline, shadow = 3.dp)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val here = channel.voiceMembers
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    inHere -> "You're in voice"
                    here.isEmpty() -> "Nobody's in voice yet"
                    here.size == 1 -> "1 person in voice"
                    else -> "${here.size} people in voice"
                },
                fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                color = if (inHere) BantsInk else b.onCard, modifier = Modifier.weight(1f),
            )
            if (here.any { it.screen }) {
                Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.background(Color(0xFFE5484D), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
            }
        }
        if (here.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                here.forEach { v ->
                    val m = members.firstOrNull { it.user.id == v.id }
                    Avatar(m?.user?.displayName ?: "?", m?.user?.avatar, size = 34.dp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (inHere) {
                ChunkyButton("Open call", onClick = { com.wellsey.bants.calls.CallManager.showCallScreen() }, fill = Color.White, content = BantsInk, modifier = Modifier.weight(1f))
                ChunkyButton("Leave", onClick = { com.wellsey.bants.calls.CallManager.hangUp() }, fill = BantsPink, content = BantsInk, modifier = Modifier.weight(1f))
            } else {
                ChunkyButton("Join voice", onClick = { join() }, fill = BantsPurple, content = Color.White, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
