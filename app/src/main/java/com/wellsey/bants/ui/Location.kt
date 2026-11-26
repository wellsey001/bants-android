@file:OptIn(ExperimentalMaterial3Api::class)

package com.wellsey.bants.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wellsey.bants.data.Bants
import com.wellsey.bants.data.Location
import com.wellsey.bants.data.Message
import com.wellsey.bants.location.Here
import com.wellsey.bants.location.LiveLocationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * A little map: OpenStreetMap tiles around a point, with a pin in the middle.
 * No API key needed.
 */
@Composable
fun MiniMap(lat: Double, lng: Double, width: Dp, height: Dp, zoom: Int = 15, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val n = 1 shl zoom
    val xf = (lng + 180.0) / 360.0 * n
    val latR = Math.toRadians(lat)
    val yf = (1.0 - ln(tan(latR) + 1.0 / cos(latR)) / PI) / 2.0 * n
    val tile = 128.dp
    val x0 = floor(xf).toInt() - 1
    val y0 = floor(yf).toInt() - 1
    // Where the point lands inside our 3×3 block of tiles.
    val px = tile * (xf - x0).toFloat()
    val py = tile * (yf - y0).toFloat()
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.size(width, height).clip(RoundedCornerShape(12.dp))) {
        val w = maxWidth
        val h = maxHeight
        Box(
            Modifier
                .offset(x = w / 2 - px, y = h / 2 - py)
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(tile * 3),
        ) {
            for (dx in 0..2) for (dy in 0..2) {
                val tx = Math.floorMod(x0 + dx, n)
                val ty = y0 + dy
                if (ty in 0 until n) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("https://tile.openstreetmap.org/$zoom/$tx/$ty.png")
                            .addHeader("User-Agent", "Bants/1.0 (https://github.com/wellsey001/bants-android)")
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.size(tile).offset(x = tile * dx, y = tile * dy),
                    )
                }
            }
        }
        Text("📍", fontSize = 30.sp, modifier = Modifier.align(Alignment.Center).offset(y = (-14).dp))
        Text(
            "© OpenStreetMap",
            fontSize = 9.sp,
            color = BantsInk,
            modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp).chunky(RoundedCornerShape(4.dp), Color.White.copy(alpha = 0.8f), Color.Transparent, shadow = 0.dp, border = 0.dp).padding(horizontal = 3.dp),
        )
    }
}

/** A location message: map, live status, tap to open in your maps app. */
@Composable
fun LocationBubble(m: Message, mine: Boolean, ink: Color, onLongPress: () -> Unit) {
    val loc: Location = m.meta?.location ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Tick every 30s so "live" turns into "ended" on time.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(loc.liveUntil) {
        while ((loc.liveUntil ?: 0) > System.currentTimeMillis()) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
        now = System.currentTimeMillis()
    }
    val live = (loc.liveUntil ?: 0) > now
    Column(Modifier.padding(top = 2.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MiniMap(
            loc.lat, loc.lng, 240.dp, 150.dp,
            modifier = Modifier.bouncyClickable(squash = 0.03f, onLongClick = onLongPress) {
                val label = Uri.encode(loc.label ?: if (loc.liveUntil != null) "Live location" else "Location")
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${loc.lat},${loc.lng}?q=${loc.lat},${loc.lng}($label)")))
                }.onFailure {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/?mlat=${loc.lat}&mlon=${loc.lng}#map=17/${loc.lat}/${loc.lng}")))
                }
            },
        )
        when {
            live -> {
                Text("🟢 Live until ${clockTime(loc.liveUntil ?: 0)} · updated ${clockTime(loc.updatedAt)}", fontSize = 12.sp, color = ink, fontWeight = FontWeight.Bold)
                if (mine) {
                    ChunkyButton(
                        "Stop sharing",
                        onClick = {
                            if (LiveLocationService.active == m.id) LiveLocationService.stop(context)
                            else scope.launch { runCatching { Bants.stopLiveLocation(m.id) } }
                        },
                        fill = Color.White,
                        content = BantsInk,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            loc.liveUntil != null -> Text("Live location ended", fontSize = 12.sp, color = ink.copy(alpha = 0.7f))
            else -> Text(loc.label ?: "📍 Location", fontSize = 13.sp, color = ink, fontWeight = FontWeight.Bold)
        }
    }
}

/** Send where you are now, or share it live for a while. */
@Composable
fun LocationSheet(convId: Long, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val b = LocalBants.current
    var fix by remember { mutableStateOf<Here.Fix?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    var allowed by remember { mutableStateOf(granted()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { allowed = granted() }
    LaunchedEffect(Unit) {
        if (!allowed) ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LaunchedEffect(allowed) {
        if (allowed) runCatching { Here.now(context) }.onSuccess { fix = it }.onFailure { error = it.message ?: "Couldn't find you" }
    }
    fun send(minutes: Int) {
        val f = fix ?: return
        sending = true
        scope.launch {
            runCatching { Bants.sendLocation(convId, f.lat, f.lng, f.accuracy, minutes) }
                .onSuccess { onDismiss() }
                .onFailure { toast(context, it.message) }
            sending = false
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = b.card) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Share location", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = b.onCard)
            val f = fix
            when {
                !allowed -> Text("Bants needs location permission for this. You can allow it in the pop-up or in your phone's settings.", color = b.muted)
                error != null -> Text(error ?: "", color = b.muted)
                f == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp)
                    Text("  Finding you…", color = b.muted)
                }
                else -> {
                    MiniMap(f.lat, f.lng, 400.dp, 170.dp, zoom = 16, modifier = Modifier.fillMaxWidth())
                    Text("Accurate to about ${f.accuracy.toInt()} m", fontSize = 12.sp, color = b.muted)
                    ChunkyButton("Send my current location", onClick = { send(0) }, busy = sending, modifier = Modifier.fillMaxWidth())
                    Text("SHARE LIVE", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = b.muted)
                    Text("Your pin moves as you do, until the time's up or you stop it.", fontSize = 13.sp, color = b.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChunkyChip("15 min", false) { if (!sending) send(15) }
                        ChunkyChip("1 hour", false) { if (!sending) send(60) }
                        ChunkyChip("8 hours", false) { if (!sending) send(480) }
                    }
                }
            }
        }
    }
}
