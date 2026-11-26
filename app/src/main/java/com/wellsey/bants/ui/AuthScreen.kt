package com.wellsey.bants.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellsey.bants.R
import com.wellsey.bants.data.Bants
import com.wellsey.bants.data.ServerInfo
import com.wellsey.bants.data.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AuthScreen() {
    var register by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var invite by remember { mutableStateOf("") }
    var server by remember { mutableStateOf(Bants.session.serverUrl) }
    var showServer by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<ServerInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(server) {
        delay(400)
        info = runCatching { Bants.serverInfo(server) }.getOrNull()
    }

    val closed = register && info?.registration == "closed"
    val needsInvite = register && info?.registration == "invite"

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Image(
            painterResource(R.drawable.bants_logo),
            contentDescription = "Bants logo",
            modifier = Modifier.height(120.dp),
        )
        Sticker("bants", size = 48.sp, modifier = Modifier.padding(top = 6.dp))
        Text(
            "Chat rubbish with your mates.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChunkyChip("Log in", !register) { register = false; error = null }
            ChunkyChip("Sign up", register) { register = true; error = null }
        }

        OutlinedTextField(
            shape = FieldShape,
            colors = chunkyFieldColors(),
            value = username,
            onValueChange = { username = it.trim() },
            label = { Text("Username") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        if (register) {
            OutlinedTextField(
                shape = FieldShape,
                colors = chunkyFieldColors(),
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display name (what friends see)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            shape = FieldShape,
            colors = chunkyFieldColors(),
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (needsInvite) ImeAction.Next else ImeAction.Done),
            supportingText = if (register) {
                { Text("At least 8 characters") }
            } else null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (needsInvite) {
            OutlinedTextField(
                shape = FieldShape,
                colors = chunkyFieldColors(),
                value = invite,
                onValueChange = { invite = it },
                label = { Text("Invite code") },
                singleLine = true,
                supportingText = { Text("Ask whoever runs the server") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (closed) {
            Text("This server isn't taking new sign-ups.", color = MaterialTheme.colorScheme.error)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        ChunkyButton(
            if (register) "Create account" else "Log in",
            onClick = {
                scope.launch {
                    busy = true
                    error = null
                    try {
                        Bants.session.serverUrl = server
                        if (register) Bants.register(username, password, displayName, invite)
                        else Bants.login(username, password)
                    } catch (e: Exception) {
                        error = e.message ?: "Something went wrong"
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !closed && username.length >= 3 && password.isNotEmpty(),
            busy = busy,
            modifier = Modifier.fillMaxWidth(),
        )

        TextButton(onClick = { showServer = !showServer }) {
            Text("Server: ${server.removePrefix("https://").removePrefix("http://")}")
        }
        if (showServer) {
            OutlinedTextField(
                shape = FieldShape,
                colors = chunkyFieldColors(),
                value = server,
                onValueChange = { server = it },
                label = { Text("Server address") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrect = false),
                supportingText = {
                    Text(if (info == null) "Can't reach this server" else "Connected")
                },
                isError = info == null,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { server = Session.DEFAULT_SERVER }) { Text("Reset to default") }
        }
    }
}
