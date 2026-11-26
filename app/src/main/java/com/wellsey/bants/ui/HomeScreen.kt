package com.wellsey.bants.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellsey.bants.data.Bants
import com.wellsey.bants.data.Conversation
import com.wellsey.bants.data.avatarMedia
import com.wellsey.bants.data.isGroup
import com.wellsey.bants.data.peer
import com.wellsey.bants.data.preview
import com.wellsey.bants.data.title

private enum class Filter(val label: String) { All("All"), Unread("Unread"), Groups("Groups") }

@Composable
fun HomeScreen(nav: Navigator) {
    val conversations by Bants.conversations.collectAsState()
    val me by Bants.me.collectAsState()
    val requests by Bants.requests.collectAsState()
    val typing by Bants.typing.collectAsState()
    val connected by Bants.connected.collectAsState()
    val myId = me?.id ?: -1L
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    val b = LocalBants.current

    val sorted = remember(conversations, filter) {
        conversations.values
            .filter {
                when (filter) {
                    Filter.All -> true
                    Filter.Unread -> it.unreadCount > 0
                    Filter.Groups -> it.isGroup
                }
            }
            .sortedByDescending { maxOf(it.updatedAt, it.lastMessage?.createdAt ?: 0) }
    }
    val unreadChats = conversations.values.count { it.unreadCount > 0 }

    LaunchedEffect(Unit) {
        runCatching { Bants.refreshConversations() }
        runCatching { Bants.refreshRequests() }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 18.dp, end = 18.dp, top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Sticker("bants", size = 40.sp)
                    Spacer(Modifier.weight(1f))
                    Box {
                        ChunkyIconButton(Icons.Default.PersonAdd, "Contact requests", { nav.go(Screen.Requests) })
                        if (requests.incoming.isNotEmpty()) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 8.dp, y = (-8).dp)
                                    .size(22.dp)
                                    .chunky(RoundedCornerShape(50), BantsPink, b.outline, shadow = 0.dp, border = 2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${requests.incoming.size}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = BantsInk)
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.clickable(role = Role.Button, onClickLabel = "Your profile") { nav.go(Screen.Profile) }) {
                        Avatar(me?.displayName ?: "?", me?.avatar, size = 44.dp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Filter.values().forEach { f ->
                        val label = if (f == Filter.Unread && unreadChats > 0) "Unread $unreadChats" else f.label
                        ChunkyChip(label, filter == f) { filter = f }
                    }
                    Spacer(Modifier.weight(1f))
                    if (!connected) Pill("Connecting…", fill = b.card)
                }
            }

            if (sorted.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        conversations.isEmpty() -> EmptyNote("no bants yet", "Tap the pencil to add someone by their username or friend code.")
                        filter == Filter.Unread -> EmptyNote("all caught up", "No unread chats. Go outside.")
                        else -> EmptyNote("no groups", "Start one from the pencil button.")
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(sorted, key = { it.id }) { c ->
                        ConversationCard(c, myId, typing[c.id].orEmpty().any { it != myId }) {
                            nav.go(Screen.Chat(c.id))
                        }
                    }
                }
            }
        }

        ChunkyIconButton(
            Icons.Default.Edit,
            "New chat",
            { nav.go(Screen.NewChat) },
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
}

@Composable
private fun ConversationCard(c: Conversation, myId: Long, someoneTyping: Boolean, onClick: () -> Unit) {
    val b = LocalBants.current
    val peer = c.peer(myId)
    val title = c.title(myId)
    val last = c.lastMessage
    val preview = when {
        someoneTyping -> "typing…"
        last == null -> if (c.isGroup) "Group created" else "Say hello 👋"
        last.kind == "system" -> last.body
        last.senderId == myId -> "You: ${last.preview()}"
        c.isGroup -> {
            val who = c.members.firstOrNull { it.user.id == last.senderId }?.user?.displayName ?: "Someone"
            "$who: ${last.preview()}"
        }
        else -> last.preview()
    }
    val unread = c.unreadCount > 0

    Row(
        Modifier
            .fillMaxWidth()
            .chunky(RoundedCornerShape(18.dp), if (unread) b.unreadCard else b.card, b.outline)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(title, c.avatarMedia(myId), size = 48.dp, online = peer?.online == true, group = c.isGroup)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontFamily = Bricolage,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = b.onCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val time = last?.createdAt ?: c.updatedAt
                if (time > 0) Text(shortTime(time), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = b.muted)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    preview,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                    color = if (someoneTyping) MaterialTheme.colorScheme.primary else b.muted,
                    fontWeight = if (unread || someoneTyping) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (c.muted) {
                    Icon(Icons.Default.NotificationsOff, contentDescription = "Muted", tint = b.muted, modifier = Modifier.size(16.dp))
                }
                if (unread) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.size(width = if (c.unreadCount > 9) 32.dp else 26.dp, height = 26.dp)
                            .chunky(RoundedCornerShape(50), BantsPink, b.outline, shadow = 0.dp, border = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (c.unreadCount > 99) "99+" else "${c.unreadCount}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = BantsInk)
                    }
                }
            }
        }
    }
}
