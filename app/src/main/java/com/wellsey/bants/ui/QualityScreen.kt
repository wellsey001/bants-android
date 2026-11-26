package com.wellsey.bants.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellsey.bants.data.Bants
import com.wellsey.bants.media.Quality

@Composable
fun QualityScreen(nav: Navigator) {
    val b = LocalBants.current
    var photo by remember { mutableStateOf(Quality.of(Bants.session.photoQuality)) }
    var video by remember { mutableStateOf(Quality.of(Bants.session.videoQuality)) }
    val maxMb = Bants.maxUploadBytes / (1024 * 1024)

    Scaffold(topBar = { ChunkyTopBar("Photo & video quality", nav) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            QualityPicker(
                title = "PHOTOS",
                selected = photo,
                describe = { q ->
                    when (q) {
                        Quality.Auto -> "Original on Wi-Fi, Standard on mobile data."
                        Quality.Standard -> "Up to 2048 px. Quick to send, still sharp on a phone."
                        Quality.HD -> "Up to 4096 px (4K). Great for zooming in."
                        Quality.Original -> "The exact photo from your gallery, full resolution."
                    }
                },
            ) {
                photo = it
                Bants.session.photoQuality = it.key
            }
            QualityPicker(
                title = "VIDEOS",
                selected = video,
                describe = { q ->
                    when (q) {
                        Quality.Auto -> "Original on Wi-Fi, Standard on mobile data."
                        Quality.Standard -> "Shrunk to 720p. Much smaller, quicker to send."
                        Quality.HD -> "Shrunk to 1080p. Sharp, about half the size of 4K."
                        Quality.Original -> "Sent untouched, up to 4K. Big files take a while."
                    }
                },
            ) {
                video = it
                Bants.session.videoQuality = it.key
            }
            ChunkyCard(Modifier.fillMaxWidth(), shadow = 0.dp) {
                Text(
                    "Your server accepts files up to $maxMb MB. A minute of 4K video is roughly 300–400 MB, so long 4K clips may need HD instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = b.muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun QualityPicker(title: String, selected: Quality, describe: (Quality) -> String, onPick: (Quality) -> Unit) {
    val b = LocalBants.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = b.muted, modifier = Modifier.padding(top = 6.dp))
        Quality.values().toList().chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { q -> ChunkyChip(q.label, selected == q) { onPick(q) } }
            }
        }
        Text(describe(selected), style = MaterialTheme.typography.bodySmall, color = b.onCard)
    }
}
