package com.wellsey.bants.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellsey.bants.Updater
import kotlinx.coroutines.launch

/** Yellow card at the top of the chat list when a new version is out. */
@Composable
fun UpdateBanner(release: Updater.Release, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val b = LocalBants.current
    Row(
        modifier
            .fillMaxWidth()
            .chunkyClickable(RoundedCornerShape(18.dp), BantsYellow, b.outline, shadow = 4.dp, onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = BantsInk, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("New Bants just dropped", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = BantsInk)
            Text("Version ${release.version} is ready. Tap to update.", fontSize = 13.sp, color = BantsInk)
        }
    }
}

/** What's new, plus the download progress. */
@Composable
fun UpdateDialog(release: Updater.Release, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by Updater.status.collectAsState()
    val downloading = status as? Updater.Status.Downloading
    AlertDialog(
        onDismissRequest = { if (downloading == null) onDismiss() },
        title = { Text("Update to ${release.version}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "You're on ${Updater.currentVersion(context)}. Your chats stay put.",
                    color = LocalBants.current.muted,
                    fontSize = 14.sp,
                )
                if (release.notes.isNotBlank()) {
                    Text(
                        release.notes,
                        fontSize = 14.sp,
                        modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    )
                }
                when (val s = status) {
                    is Updater.Status.Downloading -> {
                        if (s.progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                        Text(
                            if (s.progress == null) "Downloading…" else "Downloading… ${(s.progress * 100).toInt()}%",
                            fontSize = 13.sp,
                            color = LocalBants.current.muted,
                        )
                    }
                    Updater.Status.NeedsPermission -> Text(
                        "Turn on \"Allow from this source\" for Bants, come back, then tap Update again. You only need to do this once.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    is Updater.Status.Failed -> Text(s.message, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    Updater.Status.Idle -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = downloading == null,
                onClick = { scope.launch { Updater.install(context, release) } },
            ) { Text("Update", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            if (downloading == null) TextButton(onClick = onDismiss) { Text("Later") }
        },
    )
}
