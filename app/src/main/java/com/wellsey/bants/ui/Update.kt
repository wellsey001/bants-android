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
    AlertDialog(
        onDismissRequest = onDismiss,
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
                Text(
                    "Update downloads the new version in your browser. Tap the download when it finishes to install it.",
                    fontSize = 13.sp,
                    color = LocalBants.current.muted,
                )
                (status as? Updater.Status.Failed)?.let { Text(it.message, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { Updater.install(context, release); onDismiss() }) { Text("Update", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Later") }
        },
    )
}
