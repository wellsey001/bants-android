@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wellsey.bants.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wellsey.bants.data.Bants
import com.wellsey.bants.data.BantsServer
import com.wellsey.bants.data.ChannelMessage
import com.wellsey.bants.data.CustomEmoji
import com.wellsey.bants.data.Perm
import com.wellsey.bants.data.ServerChannel
import com.wellsey.bants.data.ServerEvent
import com.wellsey.bants.data.Servers
import com.wellsey.bants.data.Webhook
import com.wellsey.bants.data.has
import com.wellsey.bants.data.shownName
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun whenText(ts: Long) = SimpleDateFormat("EEE d MMM, HH:mm", Locale.UK).format(Date(ts))

// ───────────────────────── polls ─────────────────────────

@Composable
fun PollCard(m: ChannelMessage) {
    val p = m.poll ?: return
    val b = LocalBants.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = Bants.myId
    val total = p.votes.flatten().toSet().size
    val closed = p.endsAt != null && System.currentTimeMillis() > p.endsAt
    val mine = p.votes.map { me in it }
    val max = maxOf(1, p.votes.maxOfOrNull { it.size } ?: 0)
    Column(
        Modifier.padding(top = 6.dp).widthIn(max = 340.dp).chunky(RoundedCornerShape(14.dp), b.card, b.outline, shadow = 3.dp).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("📊 ${p.question}", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = b.onCard)
        p.options.forEachIndexed { i, o ->
            val n = p.votes.getOrNull(i)?.size ?: 0
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .border(2.dp, if (mine.getOrElse(i) { false }) BantsPurple else b.outline, RoundedCornerShape(10.dp))
                    .bouncyClickable(enabled = !closed, squash = 0.02f) {
                        var picks = mine.mapIndexedNotNull { j, x -> if (x) j else null }
                        picks = if (p.multi) { if (i in picks) picks - i else picks + i } else { if (i in picks) emptyList() else listOf(i) }
                        scope.launch { runCatching { Servers.vote(m, picks) }.onFailure { toast(context, it.message) } }
                    },
            ) {
                Box(Modifier.matchParentSize()) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(n.toFloat() / max).background(BantsPurple.copy(alpha = if (mine.getOrElse(i) { false }) 0.38f else 0.2f)))
                }
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text((if (mine.getOrElse(i) { false }) "✓ " else "") + o, fontWeight = FontWeight.Bold, color = b.onCard, modifier = Modifier.weight(1f))
                    Text("$n", fontWeight = FontWeight.Black, color = b.onCard)
                }
            }
        }
        Text(
            "$total ${if (total == 1) "vote" else "votes"}" + (if (p.multi) " · pick any" else "") +
                (if (closed) " · closed" else p.endsAt?.let { " · closes ${whenText(it)}" } ?: ""),
            fontSize = 12.sp, color = b.muted,
        )
    }
}

@Composable
fun PollDialog(channelId: Long, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var multi by remember { mutableStateOf(false) }
    var hours by remember { mutableIntStateOf(24) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Make a poll") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(question, { question = it.take(300) }, label = { Text("Question") }, shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.fillMaxWidth())
                options.forEachIndexed { i, o ->
                    OutlinedTextField(o, { options[i] = it.take(100) }, label = { Text("Answer ${i + 1}") }, singleLine = true, shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.fillMaxWidth())
                }
                if (options.size < 10) TextButton(onClick = { options.add("") }) { Text("＋ Add answer") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(multi, { multi = it })
                    Text("Let people pick more than one")
                }
                Text("Closes", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1 to "1 hour", 24 to "1 day", 72 to "3 days", 168 to "1 week", 0 to "Never").forEach { (h, l) -> ChunkyChip(l, hours == h) { hours = h } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val opts = options.map { it.trim() }.filter { it.isNotEmpty() }
                if (question.isBlank() || opts.size < 2) { toast(context, "Ask a question and give at least two answers"); return@TextButton }
                scope.launch { runCatching { Servers.createPoll(channelId, question, opts, multi, hours) }.onFailure { toast(context, it.message) } }
                onDismiss()
            }) { Text("Post poll") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ───────────────────────── rules ─────────────────────────

@Composable
fun RulesDialog(s: BantsServer, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Welcome to ${s.name}!") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (s.welcome.isNotBlank()) Text(s.welcome)
                Text("SERVER RULES", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = LocalBants.current.muted)
                Text(s.rules)
                if (!s.rulesAccepted) Text("You can read everything now. Accept the rules to post, react and join in.", fontSize = 12.sp, color = LocalBants.current.muted)
            }
        },
        confirmButton = {
            if (!s.rulesAccepted) TextButton(onClick = {
                scope.launch { runCatching { Servers.acceptRules(s.id) }.onFailure { toast(context, it.message) } }
                onDismiss()
            }) { Text("I accept") } else TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = { if (!s.rulesAccepted) TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
fun RulesEditor(s: BantsServer, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rules by remember { mutableStateOf(s.rules) }
    var welcome by remember { mutableStateOf(s.welcome) }
    var require by remember { mutableStateOf(s.requireRules) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rules & welcome") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(welcome, { welcome = it.take(1000) }, label = { Text("Welcome message") }, minLines = 2, shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(rules, { rules = it.take(4000) }, label = { Text("Rules") }, minLines = 5, placeholder = { Text("1. Be nice\n2. No spam") }, shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("New members must accept before posting", modifier = Modifier.weight(1f))
                    Switch(require, { require = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch { runCatching { Servers.saveRules(s.id, rules, welcome, require) }.onSuccess { toast(context, "Saved") }.onFailure { toast(context, it.message) } }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ───────────────────────── events ─────────────────────────

@Composable
fun EventsScreen(serverId: Long, nav: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val servers by Servers.servers.collectAsState()
    val all by Servers.events.collectAsState()
    val s = servers[serverId] ?: return
    val list = all[serverId]
    var editing by remember { mutableStateOf<ServerEvent?>(null) }
    var creating by remember { mutableStateOf(false) }
    val can = s.permissions.has(Perm.MANAGE_SERVER)
    LaunchedEffect(serverId) { runCatching { Servers.loadEvents(serverId) }.onFailure { toast(context, it.message) } }
    val b = LocalBants.current
    Scaffold(topBar = { ChunkyTopBar("Events", nav) { if (can) ChunkyIconButton(Icons.Default.Add, "New event", { creating = true }) } }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (list == null) item { Text("Loading…", color = b.muted) }
            else if (list.isEmpty()) item { Text(if (can) "No events yet. Tap ＋ to plan game night." else "Nothing coming up.", color = b.muted) }
            items(list.orEmpty(), key = { it.id }) { e ->
                val live = System.currentTimeMillis() >= e.startsAt
                val vc = e.channelId?.let { id -> s.channels.firstOrNull { it.id == id } }
                Column(Modifier.fillMaxWidth().chunky(RoundedCornerShape(16.dp), b.card, b.outline, shadow = 3.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (live) "🔴 Happening now" else "📅 ${whenText(e.startsAt)}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = BantsPurple)
                    Text(e.title, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = b.onCard)
                    if (e.description.isNotBlank()) Text(e.description, color = b.muted)
                    if (vc != null) Text("🔊 ${vc.name}", color = b.muted) else if (e.location.isNotBlank()) Text("📍 ${e.location}", color = b.muted)
                    Text("${e.going.size} going" + if (e.interested.isNotEmpty()) " · ${e.interested.size} interested" else "", fontSize = 13.sp, color = b.onCard)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        ChunkyChip(if (e.myRsvp == "going") "✓ Going" else "Going", e.myRsvp == "going") {
                            scope.launch { runCatching { Servers.rsvp(e, if (e.myRsvp == "going") null else "going") }.onFailure { toast(context, it.message) } }
                        }
                        ChunkyChip(if (e.myRsvp == "interested") "★ Interested" else "Interested", e.myRsvp == "interested") {
                            scope.launch { runCatching { Servers.rsvp(e, if (e.myRsvp == "interested") null else "interested") }.onFailure { toast(context, it.message) } }
                        }
                        if (vc != null && live) ChunkyChip("Open voice", false) { nav.go(Screen.Channel(vc.id)) }
                        if (can) ChunkyChip("Edit", false) { editing = e }
                    }
                }
            }
        }
    }
    if (creating || editing != null) EventEditor(s, editing) { creating = false; editing = null }
}

@Composable
private fun EventEditor(s: BantsServer, e: ServerEvent?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(e?.title.orEmpty()) }
    val defaultStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis < System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
    }
    var start by remember { mutableLongStateOf(e?.startsAt ?: defaultStart) }
    var hours by remember { mutableIntStateOf(e?.endsAt?.let { ((it - e.startsAt) / 3_600_000).toInt() } ?: 2) }
    var channelId by remember { mutableStateOf(e?.channelId) }
    var location by remember { mutableStateOf(e?.location.orEmpty()) }
    var desc by remember { mutableStateOf(e?.description.orEmpty()) }
    val voices = s.channels.filter { it.type == "voice" }
    fun pickDate() {
        val c = Calendar.getInstance().apply { timeInMillis = start }
        android.app.DatePickerDialog(context, { _, y, mo, d ->
            c.set(y, mo, d)
            android.app.TimePickerDialog(context, { _, h, mi ->
                c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, mi)
                start = c.timeInMillis
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (e == null) "New event" else "Edit event") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it.take(100) }, label = { Text("Name") }, placeholder = { Text("Game night") }, singleLine = true, shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.fillMaxWidth())
                ChunkyButton("📅 ${whenText(start)}", onClick = { pickDate() }, fill = LocalBants.current.card, content = LocalBants.current.onCard, modifier = Modifier.fillMaxWidth())
                Text("How long", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "No end", 1 to "1h", 2 to "2h", 3 to "3h", 5 to "5h").forEach { (h, l) -> ChunkyChip(l, hours == h) { hours = h } }
                }
                Text("Where", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChunkyChip("Somewhere else", channelId == null) { channelId = null }
                    voices.forEach { v -> ChunkyChip("🔊 ${v.name}", channelId == v.id) { channelId = v.id } }
                }
                if (channelId == null) OutlinedTextField(location, { location = it.take(100) }, label = { Text("Place") }, singleLine = true, shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(desc, { desc = it.take(1000) }, label = { Text("Details") }, minLines = 2, shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) { toast(context, "Give it a name"); return@TextButton }
                scope.launch {
                    runCatching { Servers.saveEvent(s.id, e, title, start, if (hours > 0) start + hours * 3_600_000L else null, channelId, location, desc) }
                        .onSuccess { onDismiss() }.onFailure { toast(context, it.message) }
                }
            }) { Text(if (e == null) "Create" else "Save") }
        },
        dismissButton = {
            Row {
                if (e != null) TextButton(onClick = { scope.launch { runCatching { Servers.deleteEvent(e) }.onFailure { toast(context, it.message) } }; onDismiss() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

// ───────────────────────── forums ─────────────────────────

@Composable
fun ForumScreen(channel: ServerChannel, server: BantsServer, nav: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var posts by remember { mutableStateOf<List<ServerChannel>?>(null) }
    var writing by remember { mutableStateOf(false) }
    val members by Servers.members.collectAsState()
    val threads by Servers.threads.collectAsState()
    LaunchedEffect(channel.id, threads.size) {
        runCatching {
            (Servers.loadThreads(channel.id) + runCatching { Servers.loadThreads(channel.id, archived = true) }.getOrDefault(emptyList()))
                .sortedByDescending { it.lastMessageId }
        }.onSuccess { posts = it }.onFailure { toast(context, it.message) }
    }
    LaunchedEffect(channel.id) { runCatching { Servers.markRead(channel.id) } }
    val can = channel.permissions.has(Perm.CREATE_THREADS) && channel.permissions.has(Perm.SEND_MESSAGES)
    val b = LocalBants.current
    Scaffold(topBar = { ChunkyTopBar("💬 ${channel.name}", nav) { if (can) ChunkyIconButton(Icons.Default.Add, "New post", { writing = true }) } }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (channel.topic.isNotBlank()) item { Text(channel.topic, color = b.muted, fontSize = 13.sp) }
            val list = posts
            if (list == null) item { Text("Loading…", color = b.muted) }
            else if (list.isEmpty()) item { Text(if (can) "No posts yet. Tap ＋ to start the first one." else "No posts yet.", color = b.muted) }
            items(list.orEmpty(), key = { it.id }) { t ->
                val f = t.firstMessage
                val img = f?.attachments?.firstOrNull { it.mime.startsWith("image/") }
                val who = f?.author?.let { a -> members[server.id]?.firstOrNull { it.user.id == a.id }?.shownName() ?: a.nickname ?: a.displayName }
                Row(
                    Modifier.fillMaxWidth().chunkyClickable(RoundedCornerShape(16.dp), b.card, b.outline, shadow = 3.dp) { nav.go(Screen.Channel(t.id)) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(t.name + (if (t.locked) " 🔒" else "") + (if (t.archived) " · archived" else ""), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = b.onCard)
                        if (f != null) Text((who?.let { "$it: " } ?: "") + com.wellsey.bants.data.Emojis.plain(f.body), maxLines = 2, overflow = TextOverflow.Ellipsis, color = b.muted, fontSize = 14.sp)
                        val replies = maxOf(0, t.messageCount - 1)
                        Text("💬 $replies ${if (replies == 1) "reply" else "replies"}", fontSize = 12.sp, color = b.muted)
                    }
                    if (img != null) {
                        Spacer(Modifier.width(10.dp))
                        AsyncImage(model = mediaUrl(img.id), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)))
                    }
                }
            }
        }
    }
    if (writing) NewPostDialog(channel, onDismiss = { writing = false }) { t -> writing = false; nav.go(Screen.Channel(t.id)) }
}

@Composable
private fun NewPostDialog(forum: ServerChannel, onDismiss: () -> Unit, onPosted: (ServerChannel) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var picture by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { busy = true; runCatching { Bants.uploadImage(uri) }.onSuccess { picture = it }.onFailure { toast(context, it.message) }; busy = false }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New post in ${forum.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it.take(100) }, label = { Text("Title") }, singleLine = true, shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(body, { body = it.take(4000) }, label = { Text("Post") }, minLines = 4, shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.fillMaxWidth())
                ChunkyChip(if (picture != null) "🖼 Picture added" else if (busy) "Uploading…" else "🖼 Add a picture", picture != null) {
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (title.isBlank() || (body.isBlank() && picture == null)) { toast(context, "Give it a title and say something"); return@TextButton }
                scope.launch { runCatching { Servers.createPost(forum.id, title, body, picture) }.onSuccess(onPosted).onFailure { toast(context, it.message) } }
            }) { Text("Post") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ───────────────────────── server emojis ─────────────────────────

@Composable
fun ServerEmojisScreen(serverId: Long, nav: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val servers by Servers.servers.collectAsState()
    val s = servers[serverId] ?: return
    val can = s.permissions.has(Perm.MANAGE_EMOJIS) || s.ownerId == Bants.myId
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CustomEmoji?>(null) }
    val b = LocalBants.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            for (u in uris) runCatching { Servers.addServerEmoji(context, serverId, u) }.onFailure { toast(context, it.message) }
            busy = false
        }
    }
    Scaffold(topBar = { ChunkyTopBar("${s.name} emojis", nav) }) { padding ->
        LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Up to 50 emojis that belong to the server. Everyone in it can use them in messages and reactions.", color = b.muted, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.emojis.size} / 50", fontWeight = FontWeight.ExtraBold, color = b.onCard, modifier = Modifier.weight(1f))
                        if (can) ChunkyButton(if (busy) "Adding…" else "Add emojis", onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy && s.emojis.size < 50, busy = busy, icon = Icons.Default.Add)
                    }
                }
            }
            items(s.emojis, key = { it.id }) { e ->
                Column(
                    Modifier.chunkyClickable(RoundedCornerShape(16.dp), b.card, b.outline, shadow = 3.dp, enabled = can) { editing = e }.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncImage(model = mediaUrl(e.media), contentDescription = ":${e.name}:", contentScale = ContentScale.Fit, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(":${e.name}:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = b.onCard, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    editing?.let { e ->
        var name by remember(e.id) { mutableStateOf(e.name) }
        AlertDialog(
            onDismissRequest = { editing = null },
            icon = { AsyncImage(model = mediaUrl(e.media), contentDescription = null, modifier = Modifier.size(56.dp)) },
            title = { Text(":${e.name}:") },
            text = { OutlinedTextField(name, { v -> name = v.filter { it.isLetterOrDigit() || it == '_' }.take(32) }, label = { Text("Name") }, singleLine = true, shape = FieldShape, colors = chunkyFieldColors()) },
            confirmButton = { TextButton(enabled = name.length >= 2, onClick = { scope.launch { runCatching { if (name != e.name) Servers.renameServerEmoji(e, name) }.onFailure { toast(context, it.message) } }; editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { scope.launch { runCatching { Servers.deleteServerEmoji(e) }.onFailure { toast(context, it.message) } }; editing = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        )
    }
}

// ───────────────────────── webhooks ─────────────────────────

@Composable
fun WebhooksScreen(channelId: Long, nav: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hooks by remember { mutableStateOf<List<Webhook>?>(null) }
    var name by remember { mutableStateOf("") }
    suspend fun reload() { hooks = Servers.webhooks(channelId) }
    LaunchedEffect(channelId) { runCatching { reload() }.onFailure { toast(context, it.message) } }
    fun copy(url: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Webhook", url))
        toast(context, "Webhook URL copied")
    }
    val b = LocalBants.current
    Scaffold(topBar = { ChunkyTopBar("Webhooks", nav) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Let other apps post here: Unraid notifications, game servers, GitHub, Home Assistant… Anything that can send to a Discord webhook works. Paste the URL where it asks for one.", color = b.muted, fontSize = 14.sp)
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(name, { name = it.take(80) }, label = { Text("New webhook name") }, placeholder = { Text("Unraid") }, singleLine = true, shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    ChunkyButton("Create", onClick = { scope.launch { runCatching { Servers.createWebhook(channelId, name); name = ""; reload() }.onFailure { toast(context, it.message) } } })
                }
            }
            val list = hooks
            if (list == null) item { Text("Loading…", color = b.muted) } else if (list.isEmpty()) item { Text("No webhooks yet.", color = b.muted) }
            items(list.orEmpty(), key = { it.id }) { h ->
                Column(Modifier.fillMaxWidth().chunky(RoundedCornerShape(14.dp), b.card, b.outline, shadow = 2.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🤖 ${h.name}", fontWeight = FontWeight.ExtraBold, color = b.onCard)
                    Text(h.url, fontSize = 11.sp, color = b.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChunkyChip("Copy URL", false) { copy(h.url) }
                        ChunkyChip("New URL", false) { scope.launch { runCatching { Servers.newWebhookUrl(h); reload() }.onFailure { toast(context, it.message) } } }
                        ChunkyChip("Delete", false) { scope.launch { runCatching { Servers.deleteWebhook(h); reload() }.onFailure { toast(context, it.message) } } }
                    }
                }
            }
        }
    }
}

// ───────────────────────── channel options: notifications ─────────────────────────

@Composable
fun ChannelOptionsDialog(c: ServerChannel, canManage: Boolean, onEdit: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var level by remember { mutableStateOf(c.notify) }
    fun set(l: String?) {
        level = l
        scope.launch { runCatching { Servers.setNotify(c.id, l) }.onFailure { toast(context, it.message) } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (c.type == "category") c.name else "#${c.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (c.type != "category") {
                    Text("NOTIFICATIONS", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = LocalBants.current.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChunkyChip("Server default", level == null) { set(null) }
                        ChunkyChip("All messages", level == "all") { set("all") }
                        ChunkyChip("Only @mentions", level == "mentions") { set("mentions") }
                        ChunkyChip("Nothing", level == "none") { set("none") }
                    }
                    Text(
                        when (level) {
                            "all" -> "You'll get a notification for every message here."
                            "mentions" -> "Only when someone @mentions you or a role you're in."
                            "none" -> "No notifications from this channel at all."
                            else -> "Every message, unless you've muted the server (then just @mentions)."
                        },
                        fontSize = 13.sp, color = LocalBants.current.muted,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { if (canManage) TextButton(onClick = onEdit) { Text("Edit channel") } },
    )
}

// ───────────────────────── search ─────────────────────────

@Composable
fun SearchSheet(s: BantsServer, nav: Navigator, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var everywhere by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<ChannelMessage>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val servers by Servers.servers.collectAsState()
    val members by Servers.members.collectAsState()
    LaunchedEffect(query, everywhere) {
        if (query.trim().length < 2) { results = null; return@LaunchedEffect }
        kotlinx.coroutines.delay(300)
        runCatching { if (everywhere) Servers.searchAll(query) else Servers.search(s.id, query) }
            .onSuccess { results = it; error = null }.onFailure { error = it.message }
    }
    val b = LocalBants.current
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = b.card) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 16.dp)) {
            OutlinedTextField(query, { query = it.take(100) }, label = { Text("Search messages") }, singleLine = true, shape = FieldShape, colors = chunkyFieldColors(), modifier = Modifier.fillMaxWidth())
            Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChunkyChip("In ${s.name}", !everywhere) { everywhere = false }
                ChunkyChip("All my servers", everywhere) { everywhere = true }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
                val list = results
                when {
                    error != null -> item { Text(error!!, color = b.muted) }
                    query.trim().length < 2 -> item { Text("Type at least two letters.", color = b.muted) }
                    list == null -> item { Text("Searching…", color = b.muted) }
                    list.isEmpty() -> item { Text("Nothing found.", color = b.muted) }
                }
                items(list.orEmpty(), key = { it.id }) { m ->
                    val srv = servers[m.serverId]
                    val ch = srv?.channels?.firstOrNull { it.id == m.channelId } ?: Servers.threads.value[m.channelId]
                    val who = m.author?.let { a -> members[m.serverId]?.firstOrNull { it.user.id == a.id }?.shownName() ?: a.nickname ?: a.displayName } ?: "Someone"
                    Column(
                        Modifier.fillMaxWidth().chunkyClickable(RoundedCornerShape(12.dp), b.card, b.outline, shadow = 2.dp) {
                            onDismiss()
                            Servers.openChannelRequest.value = m.channelId
                        }.padding(10.dp),
                    ) {
                        Text((if (everywhere) "${srv?.name ?: "Server"} · " else "") + (ch?.let { "#${it.name}" } ?: "a thread") + " · ${whenText(m.createdAt)}", fontSize = 12.sp, color = b.muted)
                        Text(who, fontWeight = FontWeight.ExtraBold, color = b.onCard)
                        Text(com.wellsey.bants.data.Emojis.plain(m.body), maxLines = 4, overflow = TextOverflow.Ellipsis, color = b.onCard)
                    }
                }
            }
        }
    }
}

// ───────────────────────── voice messages in channels ─────────────────────────

/** A voice message attachment: play/pause with a progress bar. Reuses the chat voice-note player. */
@Composable
fun ChannelVoice(a: com.wellsey.bants.data.ChannelAttachment) {
    val b = LocalBants.current
    val playing by AudioPlayback.playingKey.collectAsState()
    val progress by AudioPlayback.progress.collectAsState()
    var file by remember(a.id) { mutableStateOf<java.io.File?>(null) }
    val scope = rememberCoroutineScope()
    val key = "ch-${a.id}"
    val isPlaying = playing == key
    Row(
        Modifier.padding(top = 6.dp).width(240.dp).chunky(RoundedCornerShape(50), b.card, b.outline, shadow = 2.dp).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChunkyIconButton(
            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            if (isPlaying) "Stop" else "Play voice message",
            {
                scope.launch {
                    val f = file ?: runCatching { Bants.mediaFile(a.id, a.filename.ifBlank { "voice.m4a" }) }.getOrNull()?.also { file = it }
                    if (f != null) AudioPlayback.toggle(key, f)
                }
            },
            fill = BantsPurple, tint = Color.White, size = 36.dp,
        )
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.LinearProgressIndicator(
            progress = { if (isPlaying) progress else 0f },
            color = BantsPurple, trackColor = b.outline.copy(alpha = 0.2f),
            modifier = Modifier.weight(1f).padding(end = 10.dp),
        )
        Text("🎤", fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
    }
}
