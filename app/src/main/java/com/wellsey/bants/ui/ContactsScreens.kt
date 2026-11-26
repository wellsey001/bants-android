@file:OptIn(ExperimentalMaterial3Api::class)

package com.wellsey.bants.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.wellsey.bants.data.AddContactResult
import com.wellsey.bants.data.Bants
import kotlinx.coroutines.launch

@Composable
fun NewChatScreen(nav: Navigator) {
    val contacts by Bants.contacts.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { Bants.refreshContacts() } }

    Scaffold(topBar = { ChunkyTopBar("New chat", nav) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { ActionRow(Icons.Default.PersonAdd, "Add contact", "By username or friend code") { nav.go(Screen.AddContact) } }
            item { ActionRow(Icons.Default.Group, "New group") { nav.go(Screen.NewGroup) } }
            item { SectionLabel("Contacts") }
            if (contacts.isEmpty()) {
                item {
                    Text(
                        "No contacts yet. Add someone above to get started.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(contacts, key = { it.id }) { user ->
                UserRow(user, onClick = {
                    scope.launch {
                        try {
                            val id = Bants.startDirect(user.id)
                            nav.replace(Screen.Chat(id))
                        } catch (e: Exception) {
                            toast(context, e.message)
                        }
                    }
                })
            }
        }
    }
}

@Composable
fun AddContactScreen(nav: Navigator) {
    val me by Bants.me.collectAsState()
    var byCode by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<AddContactResult?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { ChunkyTopBar("Add contact", nav) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FriendCodeCard(me?.friendCode)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChunkyChip("By username", !byCode) { byCode = false; input = ""; error = null }
                ChunkyChip("By friend code", byCode) { byCode = true; input = ""; error = null }
            }
            OutlinedTextField(
                shape = FieldShape,
                colors = chunkyFieldColors(),
                value = input,
                onValueChange = { input = it; error = null },
                label = { Text(if (byCode) "Their friend code" else "Their username") },
                placeholder = { Text(if (byCode) "ABCD-2345" else "e.g. dave") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = if (byCode) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
                    autoCorrect = false,
                ),
                supportingText = {
                    Text(if (byCode) "Friend codes connect you straight away." else "They'll get a request to accept.")
                },
                modifier = Modifier.fillMaxWidth(),
            )
            ChunkyButton(
                "Add",
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            result = if (byCode) Bants.addContact(friendCode = input) else Bants.addContact(username = input)
                            input = ""
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = input.isNotBlank(),
                busy = busy,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            result?.let { r ->
                ChunkyCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (r.status == "accepted") {
                            Text("${r.contact?.displayName ?: "They"} is now in your contacts!", fontWeight = FontWeight.SemiBold)
                            r.conversation?.let { c ->
                                ChunkyButton("Say hi", onClick = { nav.replace(Screen.Chat(c.id)) })
                            }
                        } else {
                            Text(
                                "Request sent to ${r.request?.user?.displayName ?: "them"}. They'll appear in your contacts once they accept.",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RequestsScreen(nav: Navigator) {
    val requests by Bants.requests.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { Bants.refreshRequests() } }

    Scaffold(topBar = { ChunkyTopBar("Contact requests", nav) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { SectionLabel("Waiting for you") }
            if (requests.incoming.isEmpty()) {
                item { Text("Nothing here.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            }
            items(requests.incoming, key = { "in${it.id}" }) { r ->
                UserRow(r.user, supporting = "@${r.user.username} wants to add you", trailing = {
                    Row {
                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    val res = Bants.acceptRequest(r.id)
                                    toast(context, "${r.user.displayName} added")
                                    res.conversation?.let { nav.replace(Screen.Chat(it.id)) }
                                } catch (e: Exception) {
                                    toast(context, e.message)
                                }
                            }
                        }) { Icon(Icons.Default.Check, contentDescription = "Accept", tint = MaterialTheme.colorScheme.primary) }
                        IconButton(onClick = {
                            scope.launch { runCatching { Bants.declineRequest(r.id) }.onFailure { toast(context, it.message) } }
                        }) { Icon(Icons.Default.Close, contentDescription = "Decline") }
                    }
                })
            }
            item { SectionLabel("Sent by you") }
            if (requests.outgoing.isEmpty()) {
                item { Text("Nothing pending.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            }
            items(requests.outgoing, key = { "out${it.id}" }) { r ->
                UserRow(r.user, supporting = "Waiting for them to accept", trailing = {
                    TextButton(onClick = {
                        scope.launch { runCatching { Bants.declineRequest(r.id) }.onFailure { toast(context, it.message) } }
                    }) { Text("Cancel") }
                })
            }
        }
    }
}

@Composable
fun NewGroupScreen(nav: Navigator) {
    val contacts by Bants.contacts.collectAsState()
    var name by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<Long>() }
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(topBar = {
        ChunkyTopBar(
            title = "New group",
            nav = nav,
            actions = {
                TextButton(
                    enabled = name.isNotBlank() && selected.isNotEmpty() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val id = Bants.createGroup(name, selected.toList())
                                nav.replace(Screen.Chat(id))
                            } catch (e: Exception) {
                                toast(context, e.message)
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text("Create", fontWeight = FontWeight.Bold) }
            },
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().imePadding()) {
            item {
                OutlinedTextField(
                    shape = FieldShape,
                    colors = chunkyFieldColors(),
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Group name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            item { SectionLabel("Add people (${selected.size})") }
            if (contacts.isEmpty()) {
                item { Text("Add some contacts first.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            }
            items(contacts, key = { it.id }) { user ->
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
