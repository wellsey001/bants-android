@file:OptIn(ExperimentalMaterial3Api::class)

package com.wellsey.bants.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellsey.bants.data.Bants
import com.wellsey.bants.data.Conversation
import com.wellsey.bants.data.Message
import com.wellsey.bants.data.MessageInfo
import com.wellsey.bants.data.avatarMedia
import com.wellsey.bants.data.isGroup
import com.wellsey.bants.data.title
import kotlinx.coroutines.launch

// ───────────── polls ─────────────

/** A poll inside a message bubble: tap an option to vote, tap again to take it back. */
@Composable
fun PollBubble(m: Message, conv: Conversation, myId: Long, ink: Color, enabled: Boolean) {
    val poll = m.meta?.poll ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voters = poll.options.flatMap { it.voterIds }.toSet()
    val total = voters.size
    val mine = poll.options.filter { myId in it.voterIds }.map { it.id }
    Column(Modifier.widthIn(min = 220.dp, max = 280.dp).padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("📊 " + poll.question, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = ink)
        Text(
            (if (poll.multi) "Pick any" else "Pick one") + " · " + if (total == 1) "1 vote" else "$total votes",
            fontSize = 12.sp,
            color = ink.copy(alpha = 0.7f),
        )
        poll.options.forEach { o ->
            val picked = o.id in mine
            val share by animateFloatAsState(if (total == 0) 0f else o.voterIds.size.toFloat() / total, label = "poll")
            Box(
                Modifier
                    .fillMaxWidth()
                    .chunkyClickable(RoundedCornerShape(12.dp), if (picked) BantsYellow else Color.White, BantsInk, shadow = 0.dp, border = 2.dp, enabled = enabled) {
                        val next = when {
                            picked -> mine - o.id
                            poll.multi -> mine + o.id
                            else -> listOf(o.id)
                        }
                        scope.launch { runCatching { Bants.vote(m, next) }.onFailure { toast(context, it.message) } }
                    },
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        // Results bar behind the text.
                        .drawBehind { drawRect(BantsPurple.copy(alpha = 0.2f), size = Size(size.width * share.coerceIn(0f, 1f), size.height)) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (picked) "✓ " + o.text else o.text, color = BantsInk, fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (o.voterIds.isNotEmpty()) {
                        // Little avatars of who voted, then the count.
                        o.voterIds.take(3).forEach { id ->
                            val u = conv.members.firstOrNull { it.user.id == id }?.user
                            Box(Modifier.padding(start = 2.dp)) { Avatar(u?.displayName ?: "?", u?.avatar, size = 18.dp, outlined = false) }
                        }
                        Text("  ${o.voterIds.size}", color = BantsInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** Ask a question with 2–12 answers. */
@Composable
fun PollDialog(onDismiss: () -> Unit, onSend: (String, List<String>, Boolean) -> Unit) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var multi by remember { mutableStateOf(false) }
    val filled = options.map { it.trim() }.filter { it.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New poll") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(question, { question = it.take(200) }, label = { Text("Question") }, placeholder = { Text("Pub or takeaway?") }, modifier = Modifier.fillMaxWidth())
                options.forEachIndexed { i, text ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            text,
                            { v ->
                                options[i] = v.take(100)
                                // Typing in the last box adds another one.
                                if (i == options.lastIndex && v.isNotBlank() && options.size < 12) options.add("")
                            },
                            label = { Text("Option ${i + 1}") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        if (options.size > 2) {
                            IconButton(onClick = { options.removeAt(i) }) { Icon(Icons.Default.Close, contentDescription = "Remove option") }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Let people pick more than one", modifier = Modifier.weight(1f))
                    Switch(multi, { multi = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = question.isNotBlank() && filled.size >= 2,
                onClick = { onSend(question.trim(), filled, multi) },
            ) { Text("Send poll", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ───────────── forwarding ─────────────

/** Pick chats to forward a message to. */
@Composable
fun ForwardSheet(m: Message, myId: Long, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val b = LocalBants.current
    val conversations by Bants.conversations.collectAsState()
    val chats = conversations.values.filter { !it.archived }.sortedByDescending { it.updatedAt }
    val picked = remember { mutableStateListOf<Long>() }
    var sending by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = b.card) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
            Text("Forward to…", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = b.onCard, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(chats, key = { it.id }) { c ->
                    val on = c.id in picked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .bouncyClickable(squash = 0.02f) { if (on) picked.remove(c.id) else picked.add(c.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(c.title(myId), c.avatarMedia(myId), size = 42.dp, group = c.isGroup)
                        Spacer(Modifier.width(12.dp))
                        Text(c.title(myId), fontWeight = FontWeight.Bold, color = b.onCard, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Checkbox(on, { if (it) picked.add(c.id) else picked.remove(c.id) })
                    }
                }
            }
            ChunkyButton(
                if (picked.isEmpty()) "Pick some chats" else "Forward to ${picked.size}",
                onClick = {
                    sending = true
                    scope.launch {
                        runCatching { Bants.forward(m, picked.toList()) }
                            .onSuccess { toast(context, if (it == 1) "Forwarded" else "Forwarded to $it chats"); onDismiss() }
                            .onFailure { toast(context, it.message) }
                        sending = false
                    }
                },
                enabled = picked.isNotEmpty(),
                busy = sending,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

/** "↪ Forwarded" above a forwarded message. */
@Composable
fun ForwardedLabel(ink: Color) {
    Text("↪ Forwarded", fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = ink.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 2.dp))
}

// ───────────── message info ─────────────

/** Who's got your message and who's read it, with times. */
@Composable
fun MessageInfoDialog(m: Message, conv: Conversation, onDismiss: () -> Unit) {
    var info by remember { mutableStateOf<MessageInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(m.id) { runCatching { Bants.messageInfo(m) }.onSuccess { info = it }.onFailure { error = it.message } }
    val b = LocalBants.current
    fun whenText(ts: Long?) = ts?.let { "${dayLabel(it)}, ${clockTime(it)}" } ?: "Earlier"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message info") },
        text = {
            val i = info
            when {
                error != null -> Text(error ?: "")
                i == null -> Text("Loading…", color = b.muted)
                else -> Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sent ${whenText(i.sentAt)}", color = b.muted, fontSize = 13.sp)
                    val read = i.members.filter { it.read }.sortedBy { it.readAt ?: 0 }
                    val delivered = i.members.filter { it.delivered && !it.read }
                    val waiting = i.members.filter { !it.delivered }
                    @Composable
                    fun section(title: String, rows: List<com.wellsey.bants.data.MemberReceipt>, time: (com.wellsey.bants.data.MemberReceipt) -> String?) {
                        if (rows.isEmpty()) return
                        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.sp, color = b.muted)
                        rows.forEach { r ->
                            val u = conv.members.firstOrNull { it.user.id == r.userId }?.user
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(u?.displayName ?: "?", u?.avatar, size = 32.dp, outlined = false)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(u?.displayName ?: "Someone", fontWeight = FontWeight.Bold)
                                    time(r)?.let { Text(it, fontSize = 12.sp, color = b.muted) }
                                }
                            }
                        }
                    }
                    section("READ BY", read) { whenText(it.readAt) }
                    section("DELIVERED TO", delivered) { whenText(it.deliveredAt) }
                    section("NOT DELIVERED YET", waiting) { null }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
