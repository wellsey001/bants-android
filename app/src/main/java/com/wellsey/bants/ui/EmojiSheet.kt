@file:OptIn(ExperimentalMaterial3Api::class)

package com.wellsey.bants.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView

/** Every emoji, by category, with skin tones and your recent picks at the top. */
@Composable
fun EmojiReactionSheet(onDismiss: () -> Unit, custom: List<com.wellsey.bants.data.CustomEmoji> = emptyList(), onPick: (String) -> Unit) {
    val b = LocalBants.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = b.card,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
            Text(
                "React with anything",
                fontFamily = Bricolage,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = b.onCard,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
            )
            if (custom.isNotEmpty()) {
                Text("YOUR EMOJIS", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = b.muted, modifier = Modifier.padding(start = 20.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    items(custom.size) { i ->
                        val e = custom[i]
                        coil.compose.AsyncImage(
                            model = mediaUrl(e.media), contentDescription = ":${e.name}:",
                            modifier = Modifier.size(48.dp).bouncyClickable { onPick(e.token) }.padding(6.dp),
                        )
                    }
                }
            }
            AndroidView(
                factory = { context ->
                    val themed = android.view.ContextThemeWrapper(
                        context,
                        if (dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar,
                    )
                    EmojiPickerView(themed).apply {
                        emojiGridColumns = 8
                        setOnEmojiPickedListener { onPick(it.emoji) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .nestedScroll(rememberNestedScrollInteropConnection()),
            )
        }
    }
}
