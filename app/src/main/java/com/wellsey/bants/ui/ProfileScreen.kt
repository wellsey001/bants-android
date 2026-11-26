@file:OptIn(ExperimentalMaterial3Api::class)

package com.wellsey.bants.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.wellsey.bants.data.Bants
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(nav: Navigator) {
    val me by Bants.me.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var displayName by remember { mutableStateOf(me?.displayName ?: "") }
    var about by remember { mutableStateOf(me?.about ?: "") }
    var saving by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { runCatching { Bants.refreshMe() } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            uploading = true
            runCatching { Bants.uploadAvatar(uri) }.onFailure { toast(context, it.message) }
            uploading = false
        }
    }
    val changed = displayName.trim() != (me?.displayName ?: "") || about.trim() != (me?.about ?: "")
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    Scaffold(topBar = { ChunkyTopBar("Profile", nav) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.clip(CircleShape).bouncyClickable(squash = 0.08f) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Avatar(me?.displayName ?: "?", me?.avatar, size = 112.dp)
                    if (uploading) CircularProgressIndicator()
                }
            }
            Text(
                "Tap your picture to change it · @${me?.username ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            OutlinedTextField(
                shape = FieldShape,
                colors = chunkyFieldColors(),
                value = displayName,
                onValueChange = { displayName = it.take(50) },
                label = { Text("Display name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                shape = FieldShape,
                colors = chunkyFieldColors(),
                value = about,
                onValueChange = { about = it.take(140) },
                label = { Text("About") },
                placeholder = { Text("Something cheeky") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            ChunkyButton(
                "Save",
                onClick = {
                    scope.launch {
                        saving = true
                        runCatching { Bants.updateProfile(displayName, about) }
                            .onSuccess { toast(context, "Saved") }
                            .onFailure { toast(context, it.message) }
                        saving = false
                    }
                },
                enabled = changed && displayName.isNotBlank(),
                busy = saving,
                modifier = Modifier.fillMaxWidth(),
            )

            if (me?.isAdmin == true) {
                ChunkyButton(
                    "Open server admin",
                    onClick = { openUrl(context, Bants.session.serverUrl + "/admin") },
                    fill = BantsYellow,
                    content = BantsInk,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ChunkyButton(
                "Sounds & vibration",
                onClick = { nav.go(Screen.Sounds) },
                fill = LocalBants.current.card,
                content = LocalBants.current.onCard,
                icon = androidx.compose.material.icons.Icons.Default.MusicNote,
                modifier = Modifier.fillMaxWidth(),
            )
            FriendCodeCard(me?.friendCode)
            TextButton(onClick = { confirmReset = true }) { Text("Get a new friend code") }

            val update by com.wellsey.bants.Updater.available.collectAsState()
            var updateOpen by remember { mutableStateOf(false) }
            var checking by remember { mutableStateOf(false) }
            ChunkyButton(
                if (update != null) "Update to ${update?.version}" else "Check for updates",
                onClick = {
                    if (update != null) {
                        updateOpen = true
                    } else {
                        scope.launch {
                            checking = true
                            runCatching { com.wellsey.bants.Updater.check(context, force = true) }
                                .onSuccess { if (it == null) toast(context, "You're on the newest version") else updateOpen = true }
                                .onFailure { toast(context, it.message ?: "Couldn't check for updates") }
                            checking = false
                        }
                    }
                },
                busy = checking,
                fill = if (update != null) BantsYellow else LocalBants.current.card,
                content = if (update != null) BantsInk else LocalBants.current.onCard,
                icon = androidx.compose.material.icons.Icons.Default.SystemUpdate,
                modifier = Modifier.fillMaxWidth(),
            )
            update?.let { r -> if (updateOpen) UpdateDialog(r) { updateOpen = false } }

            Text(
                "Server: ${Bants.session.serverUrl}\nVersion $version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChunkyButton(
                "Log out",
                onClick = { confirmLogout = true },
                fill = LocalBants.current.card,
                content = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("New friend code?") },
            text = { Text("Your old code will stop working. Anyone who already added you stays in your contacts.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch { runCatching { Bants.resetFriendCode() }.onFailure { toast(context, it.message) } }
                }) { Text("Get new code") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    Bants.logout()
                }) { Text("Log out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
        )
    }
}
