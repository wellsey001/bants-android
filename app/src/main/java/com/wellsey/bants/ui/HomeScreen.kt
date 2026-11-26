@file:OptIn(ExperimentalMaterial3Api::class)

package com.wellsey.bants.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

@Composable
fun HomeScreen(nav: Navigator) {
    val conversations by Bants.conversations.collectAsState()
    val me by Bants.me.collectAsState()
    val requests by Bants.requests.collectAsState()
    val typing by Bants.typing.collectAsState()
    val connected by Bants.connected.collectAsState()
    val myId = me?.id ?: -1L

    val sorted = remember(conversations) {
        conversations.values.sortedByDescending { maxOf(it.updatedAt, it.lastMessage?.createdAt ?: 0) }
    }

    LaunchedEffect(Unit) {
        runCatching { Bants.refreshConversations() }
        runCatching { Bants.refreshRequests() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("bants", fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = MaterialTheme.colorScheme.primary)
                },
                actions = {
                    IconButton(onClick = { nav.go(Screen.Requests) }) {
                        BadgedBox(badge = {
                            if (requests.incoming.isNotEmpty()) Badge { Text("${requests.incoming.size}") }
                        }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Contact requests")
                        }
                    }
                    IconButton(onClick = { nav.go(Screen.Profile) }) {
                        Avatar(me?.displayName ?: "?", me?.avatar, size = 32.dp)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { nav.go(Screen.NewChat) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Edit, contentDescription = "New chat")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!connected) ConnectingBanner()
            if (sorted.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No chats yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Tap the pencil to add someone by their username or friend code, then start the bants.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(sorted, key = { it.id }) { c ->
                        ConversationRow(c, myId, typing[c.id].orEmpty().any { it != myId }) {
                            nav.go(Screen.Chat(c.id))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectingBanner() {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Text(
            "Connecting…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(6.dp),
        )
    }
}

@Composable
private fun ConversationRow(c: Conversation, myId: Long, someoneTyping: Boolean, onClick: () -> Unit) {
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(title, c.avatarMedia(myId), size = 52.dp, online = peer?.online == true, group = c.isGroup)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val time = last?.createdAt ?: c.updatedAt
                if (time > 0) {
                    Text(
                        shortTime(time),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    preview,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (someoneTyping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (c.muted) {
                    Icon(
                        Icons.Default.NotificationsOff,
                        contentDescription = "Muted",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp).padding(start = 2.dp),
                    )
                }
                if (unread) {
                    Spacer(Modifier.width(6.dp))
                    Badge(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                        Text(if (c.unreadCount > 99) "99+" else "${c.unreadCount}")
                    }
                }
            }
        }
    }
}
