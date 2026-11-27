@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wellsey.bants.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wellsey.bants.data.CustomEmoji
import com.wellsey.bants.data.Emojis
import kotlinx.coroutines.launch

/** A reaction or quick-pick emoji: the picture for custom ones, the character for normal ones. */
@Composable
fun EmojiGlyph(emoji: String, size: Dp = 20.dp, fontSize: TextUnit = 16.sp, lookup: Map<Long, CustomEmoji>? = null) {
    val known by Emojis.known.collectAsState()
    val t = Emojis.parse(emoji)
    if (t == null) {
        Text(emoji, fontSize = fontSize)
        return
    }
    val e = lookup?.get(t.first) ?: known[t.first]
    if (e == null) Text(":${t.second}:", fontSize = fontSize * 0.8f, fontWeight = FontWeight.Bold)
    else AsyncImage(model = mediaUrl(e.media), contentDescription = ":${e.name}:", contentScale = ContentScale.Fit, modifier = Modifier.size(size))
}

/** Pictures for every custom emoji in some text, for Text(inlineContent = …). Unknown ones stay as :name:. */
fun customEmojiInline(body: String, lookup: Map<Long, CustomEmoji>, big: Boolean): Map<String, InlineTextContent> {
    val size = if (big) 44.sp else 21.sp
    return Emojis.TOKEN.findAll(body).mapNotNull { m ->
        val e = lookup[m.groupValues[3].toLong()] ?: return@mapNotNull null
        "emoji:${e.id}" to InlineTextContent(Placeholder(size, size, PlaceholderVerticalAlign.TextCenter)) {
            AsyncImage(model = mediaUrl(e.media), contentDescription = ":${e.name}:", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }.toMap()
}

/** Your emojis, to drop into a message. */
@Composable
fun CustomEmojiSheet(onDismiss: () -> Unit, onManage: () -> Unit, server: List<CustomEmoji> = emptyList(), onPick: (CustomEmoji) -> Unit) {
    val mineOnly by Emojis.mine.collectAsState()
    // This server's emojis first, then yours.
    val mine = server + mineOnly
    val b = LocalBants.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = b.card) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (server.isNotEmpty()) "Emojis" else "Your emojis", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = b.onCard, modifier = Modifier.weight(1f))
                TextButton(onClick = onManage) { Text(if (mineOnly.isEmpty()) "Add yours" else "Manage yours") }
            }
            if (mine.isEmpty()) {
                Text("Upload your own pictures or GIFs and use them in any server. Up to 50.", color = b.muted, modifier = Modifier.padding(vertical = 24.dp))
            } else {
                LazyVerticalGrid(GridCells.Adaptive(52.dp), Modifier.fillMaxWidth().height(320.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(mine, key = { it.id }) { e ->
                        Box(Modifier.size(52.dp).bouncyClickable { onPick(e) }.padding(6.dp), contentAlignment = Alignment.Center) {
                            AsyncImage(model = mediaUrl(e.media), contentDescription = ":${e.name}:", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

/** Your custom emojis: add (stills or GIFs), rename and delete. */
@Composable
fun EmojisScreen(nav: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mine by Emojis.mine.collectAsState()
    val max by Emojis.max.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CustomEmoji?>(null) }
    val b = LocalBants.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            runCatching { Emojis.add(context, uris) }
                .onSuccess { n -> if (n > 0) toast(context, if (n == 1) "Emoji added" else "$n emojis added") }
                .onFailure { toast(context, it.message) }
            busy = false
        }
    }
    androidx.compose.material3.Scaffold(topBar = { ChunkyTopBar("Your emojis", nav) }) { padding ->
        LazyVerticalGrid(
            GridCells.Adaptive(104.dp),
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Upload up to $max of your own, still or animated, and use them in any server. Tap one to rename or delete it.", color = b.muted, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${mine.size} / $max · ${mine.count { it.animated }} animated", fontWeight = FontWeight.ExtraBold, color = b.onCard, modifier = Modifier.weight(1f))
                        ChunkyButton(
                            if (busy) "Adding…" else "Add emojis",
                            onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = !busy && mine.size < max,
                            busy = busy,
                            icon = Icons.Default.Add,
                        )
                    }
                    if (mine.isEmpty()) Text("No emojis yet. PNGs and JPEGs make still ones, GIFs make animated ones.", color = b.muted, modifier = Modifier.padding(vertical = 20.dp))
                }
            }
            items(mine, key = { it.id }) { e ->
                Column(
                    Modifier.chunkyClickable(RoundedCornerShape(16.dp), b.card, b.outline, shadow = 3.dp) { editing = e }.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        AsyncImage(model = mediaUrl(e.media), contentDescription = ":${e.name}:", contentScale = ContentScale.Fit, modifier = Modifier.size(52.dp))
                        if (e.animated) Text("GIF", fontSize = 9.sp, fontWeight = FontWeight.Black, color = BantsInk,
                            modifier = Modifier.align(Alignment.TopStart).background(BantsYellow, RoundedCornerShape(5.dp)).padding(horizontal = 4.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(":${e.name}:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = b.onCard, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    editing?.let { e ->
        var name by remember(e.id) { mutableStateOf(e.name) }
        AlertDialog(
            onDismissRequest = { editing = null },
            icon = { AsyncImage(model = mediaUrl(e.media), contentDescription = null, modifier = Modifier.size(56.dp)) },
            title = { Text(":${e.name}:") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { v -> name = v.filter { it.isLetterOrDigit() || it == '_' }.take(32) },
                    label = { Text("Name") }, singleLine = true, shape = FieldShape, colors = chunkyFieldColors(),
                )
            },
            confirmButton = {
                TextButton(enabled = name.length >= 2, onClick = {
                    scope.launch { runCatching { if (name != e.name) Emojis.rename(e, name) }.onFailure { toast(context, it.message) } }
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch { runCatching { Emojis.delete(e) }.onSuccess { toast(context, "Deleted :${e.name}:") }.onFailure { toast(context, it.message) } }
                    editing = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}
