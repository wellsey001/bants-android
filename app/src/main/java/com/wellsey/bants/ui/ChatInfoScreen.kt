@file:OptIn(ExperimentalMaterial3Api::class)

package com.wellsey.bants.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellsey.bants.data.Bants
import com.wellsey.bants.data.Member
import com.wellsey.bants.data.avatarMedia
import com.wellsey.bants.data.isGroup
import com.wellsey.bants.data.peer
import com.wellsey.bants.data.title
import kotlinx.coroutines.launch

@Composable
fun ChatInfoScreen(convId: Long, nav: Navigator) {
    val conversations by Bants.conversations.collectAsState()
    val me by Bants.me.collectAsState()
    val myId = me?.id ?: -1L
    val conv = conversations[convId]
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var renaming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var memberMenu by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(convId) { runCatching { Bants.refreshConversation(convId) } }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            uploading = true
            runCatching { Bants.setGroupPhoto(convId, uri) }.onFailure { toast(context, it.message) }
            uploading = false
        }
    }

    if (conv == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val iAmAdmin = conv.members.any { it.user.id == myId && it.role == "admin" }
    val peer = conv.peer(myId)
    val title = conv.title(myId)
    val members = conv.members.sortedWith(compareBy<Member> { it.user.id != myId }.thenBy { it.role != "admin" }.thenBy { it.user.displayName.lowercase() })

    fun act(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                toast(context, e.message)
            }
        }
    }

    Scaffold(topBar = {
        ChunkyTopBar(if (conv.isGroup) "Group info" else "Contact info", nav)
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .clickable(enabled = conv.isGroup && iAmAdmin) {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Avatar(title, conv.avatarMedia(myId), size = 112.dp, online = peer?.online == true, group = conv.isGroup)
                        if (uploading) CircularProgressIndicator()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        if (conv.isGroup && iAmAdmin) {
                            IconButton(onClick = { newName = conv.name ?: ""; renaming = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename group", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (peer != null) {
                        Text("@${peer.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        lastSeenLabel(peer).takeIf { it.isNotEmpty() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (peer.about.isNotBlank()) {
                            Text(peer.about, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                        }
                    } else {
                        Text(
                            "Group · ${conv.members.size} members" + if (iAmAdmin) " · tap the picture to change it" else "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            item {
                ListItem(
                    headlineContent = { Text("Mute notifications") },
                    trailingContent = {
                        Switch(checked = conv.muted, onCheckedChange = { muted -> act { Bants.setMuted(convId, muted) } })
                    },
                )
            }

            if (conv.isGroup) {
                item { SectionLabel("${conv.members.size} members") }
                if (iAmAdmin) {
                    item { ActionRow(Icons.Default.PersonAdd, "Add members") { nav.go(Screen.AddMembers(convId)) } }
                }
                items(members, key = { it.user.id }) { member ->
                    val isMe = member.user.id == myId
                    Box {
                        UserRow(
                            member.user,
                            supporting = if (isMe) "You" else null,
                            onClick = if (iAmAdmin && !isMe) ({ memberMenu = member.user.id }) else null,
                            trailing = if (member.role == "admin") {
                                { Text("Admin", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                            } else null,
                        )
                        DropdownMenu(expanded = memberMenu == member.user.id, onDismissRequest = { memberMenu = null }) {
                            DropdownMenuItem(
                                text = { Text(if (member.role == "admin") "Remove as admin" else "Make admin") },
                                onClick = {
                                    memberMenu = null
                                    act { Bants.setAdmin(convId, member.user.id, member.role != "admin") }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Remove from group", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    memberMenu = null
                                    act { Bants.removeMember(convId, member.user.id) }
                                },
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.size(16.dp))
                ChunkyButton(
                    if (conv.isGroup) "Leave group" else "Block ${peer?.displayName ?: ""}",
                    onClick = { confirm = if (conv.isGroup) "leave" else "block" },
                    fill = LocalBants.current.card,
                    content = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Spacer(Modifier.size(32.dp))
            }
        }
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename group") },
            text = {
                OutlinedTextField(
                    shape = FieldShape,
                    colors = chunkyFieldColors(),
                    value = newName,
                    onValueChange = { newName = it.take(60) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            },
            confirmButton = {
                TextButton(enabled = newName.isNotBlank(), onClick = {
                    renaming = false
                    act { Bants.renameGroup(convId, newName) }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
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
                    act {
                        if (what == "leave") Bants.leaveGroup(convId) else peer?.let { Bants.block(it.id) }
                        nav.home()
                    }
                }) { Text(if (what == "leave") "Leave" else "Block", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun AddMembersScreen(convId: Long, nav: Navigator) {
    val contacts by Bants.contacts.collectAsState()
    val conversations by Bants.conversations.collectAsState()
    val already = conversations[convId]?.members?.map { it.user.id }?.toSet().orEmpty()
    val available = contacts.filter { it.id !in already }
    val selected = remember { mutableStateListOf<Long>() }
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(topBar = {
        ChunkyTopBar(
            title = "Add members",
            nav = nav,
            actions = {
                TextButton(
                    enabled = selected.isNotEmpty() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                Bants.addMembers(convId, selected.toList())
                                nav.back()
                            } catch (e: Exception) {
                                toast(context, e.message)
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text("Add", fontWeight = FontWeight.Bold) }
            },
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            if (available.isEmpty()) {
                item {
                    Text(
                        "Everyone in your contacts is already in this group.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(available, key = { it.id }) { user ->
                val checked = user.id in selected
                UserRow(
                    user,
                    onClick = { if (checked) selected.remove(user.id) else selected.add(user.id) },
                    trailing = {
                        Checkbox(checked = checked, onCheckedChange = { if (it) selected.add(user.id) else selected.remove(user.id) })
                    },
                )
            }
        }
    }
}
