package com.wellsey.bants.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellsey.bants.R

/** Shown over everything while Bants is locked. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(BantsYellow).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.bants_logo), contentDescription = "Bants", modifier = Modifier.height(110.dp))
        Text("Locked, nosy", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = BantsInk)
        Text("Unlock with your fingerprint, face or PIN.", fontSize = 15.sp, color = BantsInk)
        ChunkyButton("Unlock", onClick = onUnlock, icon = Icons.Default.Fingerprint, modifier = Modifier.fillMaxWidth())
    }
}
