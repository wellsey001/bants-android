@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.wellsey.bants.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.wellsey.bants.Notifications
import com.wellsey.bants.data.Bants
import com.wellsey.bants.data.Conversation
import com.wellsey.bants.data.Message
import com.wellsey.bants.data.avatarMedia
import com.wellsey.bants.data.isGroup
import com.wellsey.bants.data.peer
import com.wellsey.bants.data.title
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(convId: Long, nav: Navigator) {
    val conversations by Bants.conversations.collectAsState()
    val allMessages by Bants.messages.collectAsState()
    val typingMap by Bants.typing.collectAsState()
    val me by Bants.me.collectAsState()
    val myId = me?.id ?: -1L
    val conv = conversations[convId]
    val msgs = allMessages[convId].orEmpty()
    val newestFirst = remember(msgs) { msgs.reversed() }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var text by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Message?>(null) }
    var viewer by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }

    DisposableEffect(convId) {
        Bants.activeConversation = convId
        Notifications.cancel(context, convId)
        onDispose { if (Bants.activeConversation == convId) Bants.activeConversation = null }
    }
    LaunchedEffect(convId) {
        runCatching { Bants.loadMessages(convId) }.onFailure { toast(context, it.message) }
        Bants.markRead(convId)
    }
    LaunchedEffect(msgs.lastOrNull()?.id) {
        Bants.markRead(convId)
        if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }
    // Load older messages when scrolled near the top.
    LaunchedEffect(listState, convId) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                val total = listState.layoutInfo.totalItemsCount
                if (total > 0 && last >= total - 5) runCatching { Bants.loadOlder(convId) }
            }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) Bants.sendImage(convId, uri)
    }

    if (conv == null) {
        LaunchedEffect(Unit) { runCatching { Bants.refreshConversations() } }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val peer = conv.peer(myId)
    val title = conv.title(myId)
    val typers = typingMap[convId].orEmpty().filter { it != myId }
    val subtitle = when {
        typers.isNotEmpty() && conv.isGroup -> typers.mapNotNull { id ->
            conv.members.firstOrNull { it.user.id == id }?.user?.displayName
        }.joinToString() + " typing…"
        typers.isNotEmpty() -> "typing…"
        peer != null -> lastSeenLabel(peer)
        else -> conv.members.joinToString { if (it.user.id == myId) "You" else it.user.displayName }
    }

    fun send() {
        val t = text
        val e = editing
        if (t.isBlank()) return
        text = ""
        editing = null
        if (e != null) {
            scope.launch { runCatching { Bants.editMessage(e, t) }.onFailure { toast(context, it.message) } }
        } else {
            Bants.sendText(convId, t)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(nav) },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(title, conv.avatarMedia(myId), size = 40.dp, online = peer?.online == true, group = conv.isGroup)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    subtitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (typers.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (conv.muted) "Unmute" else "Mute notifications") },
                                onClick = {
                                    menuOpen = false
                                    scope.launch { runCatching { Bants.setMuted(convId, !conv.muted) }.onFailure { toast(context, it.message) } }
                                },
                            )
                            if (conv.isGroup) {
                                DropdownMenuItem(text = { Text("Leave group") }, onClick = { menuOpen = false; confirm = "leave" })
                            } else if (peer != null) {
                                DropdownMenuItem(text = { Text("Block ${peer.displayName}") }, onClick = { menuOpen = false; confirm = "block" })
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding().imePadding()) {
                    editing?.let {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Editing message", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            IconButton(onClick = { editing = null; text = "" }) { Icon(Icons.Default.Close, contentDescription = "Cancel edit") }
                        }
                    }
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Bottom) {
                        if (editing == null) {
                            IconButton(
                                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                modifier = Modifier.padding(bottom = 2.dp),
                            ) {
                                Icon(Icons.Default.Image, contentDescription = "Send a photo", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        OutlinedTextField(
                            value = text,
                            onValueChange = {
                                text = it
                                if (it.isNotEmpty() && editing == null) Bants.sendTyping(convId)
                            },
                            placeholder = { Text("Message") },
                            maxLines = 5,
                            shape = RoundedCornerShape(26.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(6.dp))
                        FilledIconButton(
                            onClick = { send() },
                            enabled = text.isNotBlank(),
                            modifier = Modifier.size(54.dp),
                        ) {
                            Icon(if (editing != null) Icons.Default.Done else Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background),
        ) {
            itemsIndexed(newestFirst, key = { _, m -> m.clientId?.let { "c$it" } ?: "i${m.id}" }) { i, m ->
                val older = newestFirst.getOrNull(i + 1)
                Column(Modifier.fillMaxWidth()) {
                    if (older == null || !sameDay(older.createdAt, m.createdAt)) DayChip(dayLabel(m.createdAt))
                    if (m.kind == "system") {
                        DayChip(m.body)
                    } else {
                        val showSender = conv.isGroup && m.senderId != myId &&
                            (older == null || older.senderId != m.senderId || older.kind == "system")
                        MessageBubble(
                            m = m,
                            conv = conv,
                            myId = myId,
                            showSender = showSender,
                            onImage = { viewer = it },
                            onRetry = { Bants.retry(m) },
                            onCopy = {
                                clipboard.setText(AnnotatedString(m.body))
                                toast(context, "Copied")
                            },
                            onEdit = {
                                editing = m
                                text = m.body
                            },
                            onDelete = {
                                scope.launch { runCatching { Bants.deleteMessage(m) }.onFailure { toast(context, it.message) } }
                            },
                        )
                    }
                }
            }
        }
    }

    viewer?.let { url ->
        Dialog(onDismissRequest = { viewer = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { viewer = null }) {
                AsyncImage(model = url, contentDescription = "Photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }

    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (what == "leave") "Leave ${conv.name ?: "group"}?" else "Block ${peer?.displayName}?") },
            text = {
                Text(
                    if (what == "leave") "You'll stop getting messages from this group."
                    else "They won't be able to message you or find you, and they'll be removed from your contacts.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch {
                        try {
                            if (what == "leave") Bants.leaveGroup(convId) else peer?.let { Bants.block(it.id) }
                            nav.back()
                        } catch (e: Exception) {
                            toast(context, e.message)
                        }
                    }
                }) { Text(if (what == "leave") "Leave" else "Block", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DayChip(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun MessageBubble(
    m: Message,
    conv: Conversation,
    myId: Long,
    showSender: Boolean,
    onImage: (String) -> Unit,
    onRetry: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val mine = m.senderId == myId
    var menu by remember { mutableStateOf(false) }
    val bubble = if (mine) BantsPurple else MaterialTheme.colorScheme.surfaceVariant
    val ink = if (mine) Color.White else MaterialTheme.colorScheme.onSurface
    val faint = ink.copy(alpha = 0.7f)
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (mine) 18.dp else 4.dp,
        bottomEnd = if (mine) 4.dp else 18.dp,
    )

    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Box {
            Surface(
                color = bubble,
                shape = shape,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(shape)
                    .combinedClickable(
                        onClick = { if (m.failed) onRetry() },
                        onLongClick = { if (!m.deleted && !m.pending) menu = true },
                    ),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (showSender) {
                        val name = conv.members.firstOrNull { it.user.id == m.senderId }?.user?.displayName ?: "Someone"
                        Text(name, color = colourFor(name), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    if (m.deleted) {
                        Text("This message was deleted", fontStyle = FontStyle.Italic, color = faint)
                    } else {
                        val imageModel = m.localUri ?: m.media?.let { mediaUrl(it) }
                        if (m.kind == "image" && imageModel != null) {
                            AsyncImage(
                                model = imageModel,
                                contentDescription = "Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .padding(top = 2.dp, bottom = 4.dp)
                                    .size(240.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onImage(imageModel) },
                            )
                        } else if (m.media != null) {
                            Text("📎 Attachment", color = ink)
                        }
                        if (m.body.isNotEmpty()) Text(m.body, color = ink, fontSize = 16.sp)
                    }
                    Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        if (m.failed) {
                            Text("Not sent · tap to retry  ", fontSize = 11.sp, color = faint)
                        } else if (m.editedAt != null && !m.deleted) {
                            Text("edited  ", fontSize = 11.sp, color = faint)
                        }
                        Text(clockTime(m.createdAt), fontSize = 11.sp, color = faint)
                        if (mine) {
                            Spacer(Modifier.width(4.dp))
                            Ticks(m, conv, myId)
                        }
                    }
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (m.body.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = { menu = false; onCopy() },
                    )
                }
                if (mine && m.kind == "text" && !m.failed && System.currentTimeMillis() - m.createdAt < 15 * 60_000) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { menu = false; onEdit() },
                    )
                }
                if (mine) {
                    DropdownMenuItem(
                        text = { Text(if (m.failed) "Discard" else "Delete for everyone") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun Ticks(m: Message, conv: Conversation, myId: Long) {
    val others = conv.members.filter { it.user.id != myId }
    val dim = Color.White.copy(alpha = 0.7f)
    val (icon, tint, label) = when {
        m.failed -> Triple(Icons.Default.ErrorOutline, Color(0xFFFFC2C2), "Not sent")
        m.pending -> Triple(Icons.Default.Schedule, dim, "Sending")
        others.isNotEmpty() && others.all { it.lastRead >= m.id } -> Triple(Icons.Default.DoneAll, ReadTick, "Read")
        others.isNotEmpty() && others.all { it.lastDelivered >= m.id } -> Triple(Icons.Default.DoneAll, dim, "Delivered")
        else -> Triple(Icons.Default.Done, dim, "Sent")
    }
    Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
}
