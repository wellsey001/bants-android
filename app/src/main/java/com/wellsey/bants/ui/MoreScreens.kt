package com.wellsey.bants.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellsey.bants.data.Bants
import com.wellsey.bants.data.Message
import com.wellsey.bants.data.avatarMedia
import com.wellsey.bants.data.isGroup
import com.wellsey.bants.data.title
import kotlinx.coroutines.delay

@Composable
fun ArchivedScreen(nav: Navigator) {
    val conversations by Bants.conversations.collectAsState()
    val typing by Bants.typing.collectAsState()
    val me by Bants.me.collectAsState()
    val myId = me?.id ?: -1L
    val archived = remember(conversations) {
        conversations.values.filter { it.archived }.sortedByDescending { maxOf(it.updatedAt, it.lastMessage?.createdAt ?: 0) }
    }
    Scaffold(topBar = { ChunkyTopBar("Archived", nav) }) { padding ->
        if (archived.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyNote("nothing here", "Long-press a chat and pick Archive to tuck it away.")
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("Archived chats come back when someone sends a new message.", fontSize = 14.sp, color = LocalBants.current.muted)
                }
                items(archived, key = { it.id }) { c ->
                    ConversationCard(c, myId, typing[c.id].orEmpty().any { it != myId }) { nav.go(Screen.Chat(c.id)) }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(nav: Navigator) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Message>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val conversations by Bants.conversations.collectAsState()
    val me by Bants.me.collectAsState()
    val myId = me?.id ?: -1L
    val focus = remember { FocusRequester() }
    val b = LocalBants.current

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = null
            return@LaunchedEffect
        }
        delay(350)
        loading = true
        try {
            results = Bants.search(query)
            error = null
        } catch (e: Exception) {
            error = e.message
        }
        loading = false
    }

    Scaffold(topBar = { ChunkyTopBar("Search", nav) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search your messages") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (loading) {
                    { CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp) }
                } else null,
                singleLine = true,
                shape = FieldShape,
                colors = chunkyFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp).focusRequester(focus),
            )
            error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error, modifier = Modifier.padding(18.dp)) }
            val list = results
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyNote("dig it up", "Find that thing someone said and now denies.")
                }
                list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyNote("nada", "Nothing matches. Maybe they deleted it.")
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.id }) { m ->
                        val c = conversations[m.conversationId]
                        val chatTitle = c?.title(myId) ?: "Chat"
                        val who = when {
                            m.senderId == myId -> "You"
                            else -> c?.members?.firstOrNull { it.user.id == m.senderId }?.user?.displayName ?: "Someone"
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .chunkyClickable(RoundedCornerShape(16.dp), b.card, b.outline, shadow = 3.dp) { nav.go(Screen.Chat(m.conversationId, jumpTo = m.id)) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Avatar(chatTitle, c?.avatarMedia(myId), size = 40.dp, group = c?.isGroup == true)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(chatTitle, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, color = b.onCard, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(shortTime(m.createdAt), fontSize = 12.sp, color = b.muted)
                                }
                                Text(highlighted("$who: ${m.body}", query), color = b.muted, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun highlighted(text: String, query: String) = buildAnnotatedString {
    val q = query.trim()
    var i = 0
    while (q.isNotEmpty()) {
        val hit = text.indexOf(q, i, ignoreCase = true)
        if (hit < 0) break
        append(text.substring(i, hit))
        pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, background = BantsYellow, color = BantsInk))
        append(text.substring(hit, hit + q.length))
        pop()
        i = hit + q.length
    }
    append(text.substring(i))
}
