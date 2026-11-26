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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellsey.bants.Sounds
import com.wellsey.bants.data.Bants

@Composable
fun SoundsScreen(nav: Navigator) {
    val context = LocalContext.current
    val b = LocalBants.current
    var soundsOn by remember { mutableStateOf(Bants.session.soundsOn) }
    // Bumped whenever a choice changes so the screen re-reads the saved styles.
    var version by remember { mutableIntStateOf(0) }
    val chosen = remember(version) { Sounds.Kind.values().associateWith { Sounds.styleFor(it) } }
    val allSame = chosen.values.toSet().singleOrNull()

    Scaffold(topBar = { ChunkyTopBar("Sounds", nav) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ChunkyCard(Modifier.fillMaxWidth(), shadow = 0.dp) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sounds in the app", fontWeight = FontWeight.Bold, color = b.onCard)
                        Text(
                            "While Bants is open. Stays quiet when your phone is on silent or vibrate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = b.muted,
                        )
                    }
                    Switch(checked = soundsOn, onCheckedChange = {
                        soundsOn = it
                        Bants.session.soundsOn = it
                    })
                }
            }

            Text("PICK A STYLE FOR EVERYTHING", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = b.muted)
            Sounds.Style.values().toList().chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { style ->
                        ChunkyChip(style.label, allSame == style) {
                            Sounds.Kind.values().forEach { Sounds.setStyle(context, it, style) }
                            Sounds.preview(Sounds.Kind.Message, style)
                            version++
                        }
                    }
                }
            }

            Text("OR MIX AND MATCH", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = b.muted, modifier = Modifier.padding(top = 6.dp))
            Sounds.Kind.values().forEach { kind ->
                ChunkyCard(Modifier.fillMaxWidth(), shadow = 3.dp) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column {
                            Text(kind.label, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = b.onCard)
                            Text(kind.about, fontSize = 13.sp, color = b.muted)
                        }
                        Sounds.Style.values().toList().chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { style ->
                                    ChunkyChip(style.label, chosen[kind] == style) {
                                        Sounds.setStyle(context, kind, style)
                                        Sounds.preview(kind, style)
                                        version++
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text(
                "Tap a sound to hear it. Your choices are used for notifications too. " +
                    "If a notification still plays an old sound, check Bants in your phone's notification settings.",
                fontSize = 13.sp,
                color = b.muted,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}
