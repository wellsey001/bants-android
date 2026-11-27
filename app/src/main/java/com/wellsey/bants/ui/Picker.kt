@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.wellsey.bants.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wellsey.bants.data.Bants
import com.wellsey.bants.data.Gif
import com.wellsey.bants.data.Sticker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Bottom sheet with GIF search and your stickers. */
@Composable
fun GifStickerSheet(onDismiss: () -> Unit, onGif: (Gif) -> Unit, onSticker: (String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChunkyChip("GIFs", tab == 0) { tab = 0 }
                ChunkyChip("Stickers", tab == 1) { tab = 1 }
            }
            Box(Modifier.fillMaxWidth().height(460.dp).padding(top = 14.dp)) {
                if (tab == 0) GifTab(onGif) else StickerTab(onSticker)
            }
        }
    }
}

/** Just GIF search, for servers. */
@Composable
fun GifSheet(onDismiss: () -> Unit, onGif: (Gif) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Box(Modifier.fillMaxWidth().height(500.dp).padding(horizontal = 16.dp).padding(bottom = 24.dp)) { GifTab(onGif) }
    }
}

@Composable
private fun GifTab(onGif: (Gif) -> Unit) {
    val available by Bants.gifsAvailable.collectAsState()
    var query by remember { mutableStateOf("") }
    var gifs by remember { mutableStateOf(emptyList<Gif>()) }
    var next by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (!available) {
        EmptyNote("no gifs yet", "GIFs aren't switched on for this server. Whoever runs it needs to add a Giphy key.")
        return
    }

    LaunchedEffect(query) {
        delay(350)
        loading = true
        try {
            val page = Bants.findGifs(query, 0)
            gifs = page.gifs
            next = page.nextOffset
            error = null
        } catch (e: Exception) {
            error = e.message
        }
        loading = false
    }

    fun loadMore() {
        if (loading) return
        loading = true
        scope.launch {
            try {
                val page = Bants.findGifs(query, next)
                gifs = gifs + page.gifs.filter { g -> gifs.none { it.id == g.id } }
                next = page.nextOffset
            } catch (_: Exception) {
            }
            loading = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search GIFs") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = FieldShape,
            colors = chunkyFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            error != null -> Text(error ?: "", color = MaterialTheme.colorScheme.error)
            gifs.isEmpty() && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            gifs.isEmpty() -> Text("Nothing found. Try something ruder.", color = LocalBants.current.muted)
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                itemsIndexed(gifs, key = { _, g -> g.id }) { i, g ->
                    if (i >= gifs.size - 6) LaunchedEffect(g.id) { loadMore() }
                    AsyncImage(
                        model = g.preview,
                        contentDescription = g.title.ifBlank { "GIF" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio((g.width.toFloat() / g.height.coerceAtLeast(1)).coerceIn(0.6f, 2f))
                            .chunky(RoundedCornerShape(12.dp), LocalBants.current.card, LocalBants.current.outline, shadow = 0.dp, border = 2.dp)
                            .combinedClickable(role = Role.Button, onClick = { onGif(g) }),
                    )
                }
            }
        }
        Text("Powered by GIPHY", fontSize = 11.sp, color = LocalBants.current.muted, modifier = Modifier.align(Alignment.End))
    }
}

@Composable
private fun StickerTab(onSticker: (String) -> Unit) {
    val stickers by Bants.stickers.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var making by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Sticker?>(null) }

    LaunchedEffect(Unit) { runCatching { Bants.loadStickers() } }

    var picked by remember { mutableStateOf<android.net.Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) picked = uri
    }
    picked?.let { uri -> StickerMakerDialog(uri, onDismiss = { picked = null }) }
    val b = LocalBants.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Tap to send. Long-press to remove. Long-press a sticker in a chat to save it.",
            fontSize = 13.sp,
            color = b.muted,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "make") {
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .chunky(RoundedCornerShape(16.dp), BantsYellow, b.outline, shadow = 3.dp)
                        .combinedClickable(role = Role.Button, onClickLabel = "Make a sticker from a photo", onClick = {
                            if (!making) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }),
                    contentAlignment = Alignment.Center,
                ) {
                    if (making) CircularProgressIndicator(Modifier.size(24.dp), color = BantsInk, strokeWidth = 2.5.dp)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = BantsInk)
                        Text("Make", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = BantsInk, textAlign = TextAlign.Center)
                    }
                }
            }
            items(stickers, key = { it.id }) { s ->
                AsyncImage(
                    model = mediaUrl(s.media),
                    contentDescription = "Sticker",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .chunky(RoundedCornerShape(16.dp), b.card, b.outline, shadow = 0.dp, border = 2.dp)
                        .combinedClickable(
                            role = Role.Button,
                            onClick = { onSticker(s.media) },
                            onLongClick = { deleting = s },
                        ),
                )
            }
        }
        if (stickers.isEmpty()) {
            Text("No stickers yet. Make one from a photo — the uglier the better.", color = b.muted, fontSize = 14.sp)
        }
    }

    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove this sticker?") },
            text = { Text("It'll stay in chats where you've already sent it.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { runCatching { Bants.deleteSticker(s.id) }.onFailure { toast(context, it.message) } }
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Suppress("unused")
private val Transparent = Color.Transparent


/** Preview a new sticker: cut out (with a white border) or the whole photo, then save it. */
@Composable
private fun StickerMakerDialog(uri: android.net.Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cutOut by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var cutWorked by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(uri, cutOut) {
        result = null
        error = null
        runCatching { Bants.prepareSticker(uri, cutOut) }
            .onSuccess { (bmp, worked) -> result = bmp; cutWorked = worked || !cutOut }
            .onFailure { error = it.message }
    }
    val b = LocalBants.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("New sticker") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(220.dp).chunky(RoundedCornerShape(22.dp), BantsPurple.copy(alpha = 0.25f), b.outline, shadow = 0.dp, border = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val r = result
                    when {
                        error != null -> Text(error ?: "", textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                        r == null -> CircularProgressIndicator(color = BantsInk)
                        else -> androidx.compose.foundation.Image(r.asImageBitmap(), contentDescription = "Sticker preview", modifier = Modifier.size(200.dp))
                    }
                }
                if (cutOut && !cutWorked) Text("Nothing stood out to cut out, so it's the whole photo.", fontSize = 13.sp, color = b.muted, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChunkyChip("✂ Cut out", cutOut) { cutOut = true }
                    ChunkyChip("Whole photo", !cutOut) { cutOut = false }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = result != null && !saving,
                onClick = {
                    val r = result ?: return@TextButton
                    saving = true
                    scope.launch {
                        runCatching { Bants.saveStickerBitmap(r) }
                            .onSuccess { toast(context, "Sticker saved"); onDismiss() }
                            .onFailure { toast(context, it.message) }
                        saving = false
                    }
                },
            ) { Text(if (saving) "Saving…" else "Save sticker", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } },
    )
}
