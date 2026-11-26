@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.wellsey.bants.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.wellsey.bants.Notifications
import com.wellsey.bants.calls.CallManager
import com.wellsey.bants.data.Bants
import com.wellsey.bants.data.Conversation
import com.wellsey.bants.data.Message
import com.wellsey.bants.data.avatarMedia
import com.wellsey.bants.data.isGroup
import com.wellsey.bants.data.peer
import com.wellsey.bants.data.preview
import com.wellsey.bants.data.title
import com.wellsey.bants.data.toMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val QuickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")

/** Your six most recent reactions, topped up with the defaults. */
private fun quickRow(): List<String> = (Bants.session.recentReactions + QuickReactions).distinct().take(6)

@Composable
fun ChatScreen(convId: Long, nav: Navigator, jumpTo: Long? = null) {
    val conversations by Bants.conversations.collectAsState()
    val allMessages by Bants.messages.collectAsState()
    val outboxItems by Bants.outbox.collectAsState()
    val typingMap by Bants.typing.collectAsState()
    val me by Bants.me.collectAsState()
    val myId = me?.id ?: -1L
    val conv = conversations[convId]
    val real = allMessages[convId].orEmpty()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val msgs = remember(real, outboxItems, myId, now) {
        real.filter { (it.expiresAt ?: Long.MAX_VALUE) > now } +
            outboxItems.filter { it.conversationId == convId }.map { it.toMessage(myId) }
    }
    val myStickers by Bants.stickers.collectAsState()
    val newestFirst = remember(msgs) { msgs.reversed() }
    val byId = remember(real) { real.associateBy { it.id } }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var field by remember { mutableStateOf(TextFieldValue("")) }
    val text = field.text
    var pickerOpen by remember { mutableStateOf(false) }
    var highlight by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<Message?>(null) }
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    var unsending by remember { mutableStateOf<Message?>(null) }
    var reactingTo by remember { mutableStateOf<Message?>(null) }
    var forwarding by remember { mutableStateOf<Message?>(null) }
    var infoFor by remember { mutableStateOf<Message?>(null) }
    var pollOpen by remember { mutableStateOf(false) }
    var locationOpen by remember { mutableStateOf(false) }
    // Re-read the chat's look whenever you come back to it (e.g. from the wallpaper screen).
    val chatStyle = remember(convId, nav.current) { ChatStyle.load(convId) }
    fun react(m: Message, emoji: String) {
        Bants.session.recentReactions = listOf(emoji) + Bants.session.recentReactions.filter { it != emoji }
        scope.launch { runCatching { Bants.react(m, emoji) }.onFailure { toast(context, it.message) } }
    }
    // New messages that land while you're looking get slapped on like stickers (once each).
    val openedAt = remember(convId) { System.currentTimeMillis() }
    val slapped = remember(convId) { mutableSetOf<Long>() }
    // A GIF picked from the sheet, waiting for an optional message before it's sent.
    var pendingGif by remember { mutableStateOf<com.wellsey.bants.data.Gif?>(null) }
    var viewer by remember { mutableStateOf<String?>(null) }
    var video by remember { mutableStateOf<Message?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var attachOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recordStart by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    val recorder = remember { VoiceRecorder(context) }

    DisposableEffect(convId) {
        Bants.activeConversation = convId
        Notifications.cancel(context, convId)
        onDispose {
            if (Bants.activeConversation == convId) Bants.activeConversation = null
            recorder.cancel()
            AudioPlayback.stop()
        }
    }
    LaunchedEffect(convId) {
        runCatching { Bants.loadMessages(convId) }
        Bants.markRead(convId)
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(jumpTo) {
        val target = jumpTo ?: return@LaunchedEffect
        if (runCatching { Bants.loadUntil(convId, target) }.getOrDefault(false)) {
            delay(100)
            val list = Bants.messages.value[convId].orEmpty().asReversed()
            val index = list.indexOfFirst { it.id == target }
            if (index >= 0) listState.scrollToItem(index)
            highlight = target
            delay(2_500)
            highlight = null
        } else {
            toast(context, "Couldn't find that message")
        }
    }
    LaunchedEffect(msgs.lastOrNull()?.id) {
        Bants.markRead(convId)
        if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }
    LaunchedEffect(listState, convId) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                val total = listState.layoutInfo.totalItemsCount
                if (total > 0 && last >= total - 5) runCatching { Bants.loadOlder(convId) }
            }
    }
    LaunchedEffect(recording) {
        while (recording) {
            elapsed = System.currentTimeMillis() - recordStart
            delay(200)
        }
    }

    val visualPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            Bants.sendMedia(convId, uri, replyingTo?.id)
            replyingTo = null
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            Bants.sendMedia(convId, uri, replyingTo?.id)
            replyingTo = null
        }
    }
    var pendingCallVideo by remember { mutableStateOf<Boolean?>(null) }
    val callPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val video = pendingCallVideo
        pendingCallVideo = null
        val p = conversations[convId]?.peer(myId)
        if (video != null && p != null && result.values.all { it }) CallManager.startCall(convId, p.id, p.displayName, p.avatar, video)
        else if (video != null) toast(context, "Bants needs the microphone (and camera for video) to call")
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        toast(context, if (granted) "Now hold the mic to record" else "Bants needs the microphone for voice notes")
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
    fun nameOf(userId: Long?): String =
        if (userId == myId) "You" else conv.members.firstOrNull { it.user.id == userId }?.user?.displayName ?: "Someone"

    fun send() {
        val t = text
        val e = editing
        val gif = pendingGif
        if (gif != null && e == null) {
            field = TextFieldValue("")
            pendingGif = null
            Bants.sendGif(convId, gif, replyingTo?.id, t)
            replyingTo = null
            return
        }
        if (t.isBlank()) return
        field = TextFieldValue("")
        editing = null
        if (e != null) {
            scope.launch { runCatching { Bants.editMessage(e, t) }.onFailure { toast(context, it.message) } }
        } else {
            Bants.sendText(convId, t, replyingTo?.id)
            replyingTo = null
        }
    }

    fun startCall(video: Boolean) {
        val p = peer ?: return
        val need = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (video) need += Manifest.permission.CAMERA
        if (need.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            CallManager.startCall(convId, p.id, p.displayName, p.avatar, video)
        } else {
            pendingCallVideo = video
            callPermissions.launch(need.toTypedArray())
        }
    }

    fun jumpTo(messageId: Long) {
        val index = newestFirst.indexOfFirst { it.id == messageId }
        if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
        else toast(context, "That message is further back")
    }

    Box(Modifier.fillMaxSize()) {
    ChatWallpaper(convId, chatStyle)
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { Column {
            val b = LocalBants.current
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(BantsYellow)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChunkyIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", { nav.back() }, fill = Color.White, tint = BantsInk, size = 42.dp)
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier.weight(1f).bouncyClickable(squash = 0.04f) { nav.go(Screen.ChatInfo(convId)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(title, conv.avatarMedia(myId), size = 42.dp, online = peer?.online == true, group = conv.isGroup)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = Bricolage, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = BantsInk, modifier = Modifier.weight(1f, fill = false))
                            if (conv.disappearAfter > 0) {
                                Icon(Icons.Default.Timer, contentDescription = "Disappearing messages on", tint = BantsInk, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                            }
                        }
                        if (subtitle.isNotEmpty()) {
                            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BantsInk)
                        }
                    }
                }
                if (peer != null) {
                    ChunkyIconButton(Icons.Default.Call, "Voice call", { startCall(false) }, fill = Color.White, tint = BantsInk, size = 42.dp)
                    Spacer(Modifier.width(8.dp))
                    ChunkyIconButton(Icons.Default.Videocam, "Video call", { startCall(true) }, fill = Color.White, tint = BantsInk, size = 42.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Box {
                    ChunkyIconButton(Icons.Default.MoreVert, "More", { menuOpen = true }, fill = Color.White, tint = BantsInk, size = 42.dp)
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (conv.isGroup) "Group info" else "Contact info") },
                            onClick = { menuOpen = false; nav.go(Screen.ChatInfo(convId)) },
                        )
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
            }
            Box(Modifier.fillMaxWidth().height(2.5.dp).background(b.outline))
        } },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.navigationBarsPadding().imePadding()) {
                    val quoted = replyingTo
                    if (editing != null) {
                        ComposerBanner(Icons.Default.Edit, "Editing message", null) { editing = null; field = TextFieldValue("") }
                    } else if (quoted != null) {
                        ComposerBanner(Icons.AutoMirrored.Filled.Reply, "Replying to ${nameOf(quoted.senderId)}", quoted.preview()) { replyingTo = null }
                    }
                    pendingGif?.let { g ->
                        Box(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
                            AsyncImage(
                                model = g.preview.ifBlank { g.url },
                                contentDescription = "Selected GIF",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .height(120.dp)
                                    .widthIn(min = 90.dp, max = 220.dp)
                                    .chunky(RoundedCornerShape(14.dp), LocalBants.current.card, LocalBants.current.outline, shadow = 3.dp),
                            )
                            ChunkyIconButton(
                                Icons.Default.Close, "Remove GIF", { pendingGif = null },
                                fill = Color.White, tint = BantsInk, size = 30.dp,
                                modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp),
                            )
                        }
                    }
                    val mentionQuery = if (conv.isGroup) Regex("(^|\\s)@([A-Za-z0-9_.]{0,24})$").find(text.substring(0, field.selection.end.coerceIn(0, text.length)))?.groupValues?.get(2) else null
                    val suggestions = if (mentionQuery == null) emptyList() else conv.members.map { it.user }.filter {
                        it.id != myId && (it.username.startsWith(mentionQuery, true) || it.displayName.startsWith(mentionQuery, true))
                    }.take(6)
                    if (suggestions.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            suggestions.forEach { u ->
                                Row(
                                    Modifier
                                        .chunkyClickable(RoundedCornerShape(50), LocalBants.current.card, LocalBants.current.outline, shadow = 0.dp, border = 2.dp) {
                                            val cursor = field.selection.end.coerceIn(0, text.length)
                                            val start = cursor - (mentionQuery?.length ?: 0)
                                            val insert = u.username + " "
                                            val newText = text.substring(0, start) + insert + text.substring(cursor)
                                            field = TextFieldValue(newText, TextRange(start + insert.length))
                                        }
                                        .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Avatar(u.displayName, u.avatar, size = 26.dp, outlined = false)
                                    Spacer(Modifier.width(6.dp))
                                    Text(u.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = LocalBants.current.onCard)
                                    Text(" @${u.username}", fontSize = 13.sp, color = LocalBants.current.muted)
                                }
                            }
                        }
                    }
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Bottom) {
                        if (editing == null && !recording) {
                            Box {
                                ChunkyIconButton(Icons.Default.AttachFile, "Attach", { attachOpen = true }, size = 50.dp, modifier = Modifier.padding(end = 8.dp, bottom = 3.dp))
                                DropdownMenu(expanded = attachOpen, onDismissRequest = { attachOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Photo or video") },
                                        leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                                        onClick = {
                                            attachOpen = false
                                            visualPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("File") },
                                        leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null) },
                                        onClick = {
                                            attachOpen = false
                                            filePicker.launch(arrayOf("*/*"))
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Location") },
                                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                                        onClick = {
                                            attachOpen = false
                                            locationOpen = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Poll") },
                                        leadingIcon = { Icon(Icons.Default.Poll, contentDescription = null) },
                                        onClick = {
                                            attachOpen = false
                                            pollOpen = true
                                        },
                                    )
                                }
                            }
                        }
                        if (recording) {
                            Row(
                                Modifier.weight(1f).height(56.dp).padding(start = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFFE5484D)))
                                Spacer(Modifier.width(10.dp))
                                Text("${formatDuration(elapsed)}  ·  release to send", style = MaterialTheme.typography.bodyLarge)
                            }
                        } else {
                            OutlinedTextField(
                                value = field,
                                onValueChange = {
                                    field = it
                                    if (it.text.isNotEmpty() && editing == null) Bants.sendTyping(convId)
                                },
                                trailingIcon = if (editing == null) {
                                    {
                                        IconButton(onClick = { pickerOpen = true }) {
                                            Icon(Icons.Default.EmojiEmotions, contentDescription = "GIFs and stickers", tint = LocalBants.current.muted)
                                        }
                                    }
                                } else null,
                                placeholder = { Text(if (pendingGif != null) "Add a message, or just send it…" else "Say something cheeky…") },
                                maxLines = 5,
                                shape = FieldShape,
                                colors = chunkyFieldColors(),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        if (text.isNotBlank() || editing != null || pendingGif != null) {
                            ChunkyIconButton(
                                if (editing != null) Icons.Default.Done else Icons.AutoMirrored.Filled.Send,
                                "Send",
                                { send() },
                                fill = BantsPurple,
                                tint = Color.White,
                                size = 52.dp,
                                shadow = 4.dp,
                                enabled = text.isNotBlank() || (pendingGif != null && editing == null),
                                modifier = Modifier.padding(bottom = 3.dp),
                            )
                        } else {
                            Box(
                                Modifier
                                    .padding(bottom = 3.dp)
                                    .size(52.dp)
                                    .chunky(RoundedCornerShape(14.dp), if (recording) Color(0xFFE5484D) else BantsPink, LocalBants.current.outline, shadow = if (recording) 0.dp else 4.dp)
                                    .pointerInput(convId) {
                                        detectTapGestures(onPress = {
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                            } else if (!recorder.start()) {
                                                toast(context, "Couldn't start recording")
                                            } else {
                                                recordStart = System.currentTimeMillis()
                                                elapsed = 0
                                                recording = true
                                                val released = tryAwaitRelease()
                                                recording = false
                                                val file = recorder.stop()
                                                if (file != null && released) {
                                                    Bants.sendVoice(convId, file, replyingTo?.id)
                                                    replyingTo = null
                                                } else if (file == null) {
                                                    toast(context, "Hold the mic to record a voice note")
                                                } else {
                                                    file.delete()
                                                }
                                            }
                                        })
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "Hold to record a voice note", tint = BantsInk)
                            }
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
            modifier = Modifier.fillMaxSize().padding(padding),
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
                        val quoted = m.replyTo?.let { byId[it] }
                        val slapIn = m.senderId != myId && m.id > 0 && m.createdAt >= openedAt - 2_000 && slapped.add(m.id)
                        MessageBubble(
                            m = m,
                            slapIn = slapIn,
                            conv = conv,
                            myId = myId,
                            showSender = showSender,
                            highlighted = highlight == m.id,
                            canSaveSticker = m.kind == "sticker" && m.media != null && myStickers.none { it.media == m.media },
                            onSaveSticker = {
                                m.media?.let { media ->
                                    scope.launch {
                                        runCatching { Bants.saveSticker(media) }
                                            .onSuccess { toast(context, "Saved to your stickers") }
                                            .onFailure { toast(context, it.message) }
                                    }
                                }
                            },
                            quoted = quoted,
                            quotedName = quoted?.let { nameOf(it.senderId) },
                            onQuote = { m.replyTo?.let { jumpTo(it) } },
                            onImage = { viewer = it },
                            onVideo = { video = m },
                            onReply = { if (!m.pending && !m.failed && !m.deleted) { replyingTo = m; editing = null } },
                            onReact = { emoji -> react(m, emoji) },
                            onMoreReactions = { reactingTo = m },
                            onForward = { forwarding = m },
                            style = chatStyle,
                            onInfo = { infoFor = m },
                            onRetry = { Bants.retry(m) },
                            onCopy = {
                                clipboard.setText(AnnotatedString(m.body))
                                toast(context, "Copied")
                            },
                            onEdit = {
                                editing = m
                                replyingTo = null
                                field = TextFieldValue(m.body, TextRange(m.body.length))
                            },
                            onDelete = {
                                if (m.pending || m.failed) {
                                    scope.launch { runCatching { Bants.deleteMessage(m) }.onFailure { toast(context, it.message) } }
                                } else {
                                    unsending = m
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    }

    forwarding?.let { m -> ForwardSheet(m, myId) { forwarding = null } }
    infoFor?.let { m -> conv?.let { c -> MessageInfoDialog(m, c) { infoFor = null } } }
    if (locationOpen) LocationSheet(convId) { locationOpen = false }
    if (pollOpen) {
        PollDialog(onDismiss = { pollOpen = false }) { q, options, multi ->
            pollOpen = false
            scope.launch { runCatching { Bants.sendPoll(convId, q, options, multi) }.onFailure { toast(context, it.message) } }
        }
    }

    reactingTo?.let { m ->
        EmojiReactionSheet(onDismiss = { reactingTo = null }) { emoji ->
            reactingTo = null
            react(m, emoji)
        }
    }

    unsending?.let { m ->
        AlertDialog(
            onDismissRequest = { unsending = null },
            title = { Text("Unsend this message?") },
            text = { Text("It'll disappear from everyone's phone in this chat, including any notification it popped up.") },
            confirmButton = {
                TextButton(onClick = {
                    unsending = null
                    if (editing?.id == m.id) editing = null
                    if (replyingTo?.id == m.id) replyingTo = null
                    scope.launch { runCatching { Bants.deleteMessage(m) }.onFailure { toast(context, it.message ?: "Couldn't unsend") } }
                }) { Text("Unsend", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { unsending = null }) { Text("Cancel") } },
        )
    }

    viewer?.let { url ->
        Dialog(onDismissRequest = { viewer = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { viewer = null }) {
                AsyncImage(model = url, contentDescription = "Photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }
    video?.let { VideoDialog(it) { video = null } }
    if (pickerOpen) {
        GifStickerSheet(
            onDismiss = { pickerOpen = false },
            onGif = { gif ->
                pickerOpen = false
                pendingGif = gif
            },
            onSticker = { media ->
                pickerOpen = false
                Bants.sendSticker(convId, media, replyingTo?.id)
                replyingTo = null
            },
        )
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
private fun ComposerBanner(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String?, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (detail != null) {
                Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Cancel") }
    }
}

@Composable
private fun DayChip(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Pill(text.uppercase())
    }
}

@Composable
private fun MessageBubble(
    m: Message,
    slapIn: Boolean,
    conv: Conversation,
    myId: Long,
    showSender: Boolean,
    highlighted: Boolean,
    canSaveSticker: Boolean,
    onSaveSticker: () -> Unit,
    quoted: Message?,
    quotedName: String?,
    onQuote: () -> Unit,
    onImage: (String) -> Unit,
    onVideo: () -> Unit,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onMoreReactions: () -> Unit,
    onForward: () -> Unit,
    style: ChatStyle,
    onInfo: () -> Unit,
    onRetry: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val mine = m.senderId == myId
    var menu by remember { mutableStateOf(false) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val shownX by animateFloatAsState(dragX, label = "swipe")
    val canInteract = !m.deleted && !m.pending && !m.failed
    val b = LocalBants.current
    val isSticker = m.kind == "sticker" && !m.deleted
    val bubble = if (mine) style.myBubble() else b.theirBubble
    val ink = if (isSticker) b.onCard else if (mine) style.myInk() else b.onCard
    val faint = ink.copy(alpha = 0.7f)
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (mine) 18.dp else 4.dp,
        bottomEnd = if (mine) 4.dp else 18.dp,
    )

    Column(
        Modifier
            .fillMaxWidth()
            .background(if (highlighted) BantsYellow.copy(alpha = 0.45f) else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(vertical = 2.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        val bubblePress = remember { MutableInteractionSource() }
        // Photos, videos, files and links handle their own taps, so they pass long-presses
        // and double-taps back here: you can react to them like any other message.
        val openMenu = { if (!m.deleted && !m.pending) { com.wellsey.bants.Haptics.longPress(); menu = true } }
        val quickLove = { com.wellsey.bants.Haptics.tap(); onReact("❤️") }
        val slap = remember { Animatable(if (slapIn) 0f else 1f) }
        LaunchedEffect(Unit) { if (slap.value < 1f) slap.animateTo(1f, tween(650, easing = LinearEasing)) }
        Box(Modifier.offset { IntOffset(shownX.roundToInt(), 0) }.stickerSlap { slap.value }) {
            if (shownX > 20f) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterStart).offset(x = (-36).dp),
                )
            }
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .then(
                        if (isSticker) Modifier
                        else Modifier.chunky(shape, bubble, b.outline, shadow = if (mine) 3.dp else 0.dp),
                    )
                    .draggable(
                        orientation = Orientation.Horizontal,
                        enabled = canInteract,
                        state = rememberDraggableState { delta -> dragX = (dragX + delta).coerceIn(0f, 160f) },
                        onDragStopped = {
                            if (dragX > 100f) onReply()
                            dragX = 0f
                        },
                    )
                    .pressScale(bubblePress, 0.035f, buzz = false)
                    .combinedClickable(
                        interactionSource = bubblePress,
                        indication = LocalIndication.current,
                        onClick = { if (m.failed) onRetry() },
                        onLongClick = openMenu,
                        // Double-tap anyone's message for a quick ❤️.
                        onDoubleClick = if (canInteract) ({ quickLove() }) else null,
                    ),
            ) {
                Column(Modifier.padding(horizontal = if (isSticker) 0.dp else 12.dp, vertical = if (isSticker) 0.dp else 8.dp)) {
                    if (showSender) {
                        val name = conv.members.firstOrNull { it.user.id == m.senderId }?.user?.displayName ?: "Someone"
                        Text(name, color = colourFor(name), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    }
                    if (m.replyTo != null && !m.deleted) {
                        Box(
                            Modifier
                                .padding(top = 2.dp, bottom = 5.dp)
                                .chunkyClickable(RoundedCornerShape(10.dp), BantsYellow, BantsInk, shadow = 0.dp, border = 2.dp, onClick = onQuote),
                        ) {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text(quotedName ?: "Reply", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = BantsInk)
                                Text(
                                    quoted?.preview() ?: "Earlier message",
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 13.sp,
                                    color = BantsInk,
                                )
                            }
                        }
                    }
                    if (m.deleted) {
                        Text("This message was deleted", fontStyle = FontStyle.Italic, color = faint)
                    } else {
                        if (m.meta?.forwarded == true) ForwardedLabel(ink)
                        val imageModel = m.localUri ?: m.media?.let { mediaUrl(it) }
                        when (m.kind) {
                            "image" -> if (imageModel != null) {
                                AsyncImage(
                                    model = imageModel,
                                    contentDescription = "Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .padding(top = 2.dp, bottom = 4.dp)
                                        .size(240.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .bouncyClickable(
                                            squash = 0.04f,
                                            onLongClick = openMenu,
                                            onDoubleClick = if (canInteract) ({ quickLove() }) else null,
                                        ) { onImage(imageModel) },
                                )
                            }
                            "sticker" -> AsyncImage(
                                model = imageModel,
                                contentDescription = "Sticker",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(150.dp)
                                    .chunky(RoundedCornerShape(22.dp), b.card, b.outline, shadow = 3.dp),
                            )
                            "audio" -> AudioBubble(m, ink, if (mine) ReadTick else MaterialTheme.colorScheme.primary)
                            "video" -> VideoTile(m, ink, onVideo, onLongPress = openMenu)
                            "file" -> FileTile(m, ink, onLongPress = openMenu)
                            "poll" -> PollBubble(m, conv, myId, ink, enabled = canInteract)
                            "location" -> LocationBubble(m, mine, ink, onLongPress = openMenu)
                        }
                        if (m.body.isNotEmpty() && m.kind != "poll" && m.kind != "location") MessageText(m, conv, myId, ink)
                        m.meta?.preview?.let { LinkPreviewCard(it, mine, onLongPress = openMenu) }
                    }
                    Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        if (m.failed) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = faint, modifier = Modifier.size(14.dp))
                            Text(" Not sent · tap to retry  ", fontSize = 11.sp, color = faint)
                        } else if (m.editedAt != null && !m.deleted) {
                            Text("edited  ", fontSize = 11.sp, color = faint)
                        }
                        val uploading = if (m.pending) m.clientId?.let { id -> Bants.uploadProgress.collectAsState().value[id] } else null
                        if (uploading != null) Text("Sending ${(uploading * 100).toInt()}%  ", fontSize = 11.sp, color = faint)
                        Text(clockTime(m.createdAt), fontSize = 11.sp, color = faint)
                        if (mine) {
                            Spacer(Modifier.width(4.dp))
                            Ticks(m, conv, myId)
                        }
                    }
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (canInteract) {
                    Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        val myReaction = m.reactions.firstOrNull { myId in it.userIds }?.emoji
                        quickRow().forEach { emoji ->
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (emoji == myReaction) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .bouncyClickable(squash = 0.25f) {
                                        menu = false
                                        onReact(emoji)
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Text(emoji, fontSize = 24.sp) }
                        }
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(LocalBants.current.outline.copy(alpha = 0.08f))
                                .bouncyClickable(squash = 0.25f, onClickLabel = "More emojis") {
                                    menu = false
                                    onMoreReactions()
                                },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.Add, contentDescription = "More emojis", tint = LocalBants.current.onCard) }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text("Reply") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                        onClick = { menu = false; onReply() },
                    )
                    if (canSaveSticker) {
                        DropdownMenuItem(
                            text = { Text("Save sticker") },
                            leadingIcon = { Icon(Icons.Default.BookmarkAdd, contentDescription = null) },
                            onClick = { menu = false; onSaveSticker() },
                        )
                    }
                }
                if (canInteract) {
                    DropdownMenuItem(
                        text = { Text("Forward") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null) },
                        onClick = { menu = false; onForward() },
                    )
                }
                if (mine && canInteract) {
                    DropdownMenuItem(
                        text = { Text("Info") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = { menu = false; onInfo() },
                    )
                }
                if (m.body.isNotEmpty() && m.kind != "poll") {
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = { menu = false; onCopy() },
                    )
                }
                if (mine && canInteract && m.kind == "text" && System.currentTimeMillis() - m.createdAt < 15 * 60_000) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { menu = false; onEdit() },
                    )
                }
                if (m.failed) {
                    DropdownMenuItem(
                        text = { Text("Try again") },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        onClick = { menu = false; onRetry() },
                    )
                }
                if (mine) {
                    DropdownMenuItem(
                        text = { Text(if (m.failed || m.pending) "Discard" else "Unsend") },
                        leadingIcon = { Icon(if (m.failed || m.pending) Icons.Default.Delete else Icons.AutoMirrored.Filled.Undo, contentDescription = null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
        if (m.reactions.isNotEmpty() && !m.deleted) {
            Row(
                Modifier.padding(horizontal = 6.dp).offset(y = (-4).dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                m.reactions.forEach { r ->
                    val reactedByMe = myId in r.userIds
                    Box(
                        Modifier
                            .chunkyClickable(RoundedCornerShape(50), if (reactedByMe) BantsYellow else b.card, b.outline, shadow = 0.dp, border = 2.dp) { onReact(r.emoji) },
                    ) {
                        Text(
                            if (r.userIds.size > 1) "${r.emoji} ${r.userIds.size}" else r.emoji,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (reactedByMe) BantsInk else b.onCard,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
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
        m.failed -> Triple(Icons.Default.ErrorOutline, BantsPink, "Not sent")
        m.pending -> Triple(Icons.Default.Schedule, dim, "Waiting to send")
        others.isNotEmpty() && others.all { it.lastRead >= m.id } -> Triple(Icons.Default.DoneAll, ReadTick, "Read")
        others.isNotEmpty() && others.all { it.lastDelivered >= m.id } -> Triple(Icons.Default.DoneAll, dim, "Delivered")
        else -> Triple(Icons.Default.Done, dim, "Sent")
    }
    Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
}
