package com.wellsey.bants.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wellsey.bants.data.Conversation
import com.wellsey.bants.data.LinkPreview
import com.wellsey.bants.data.Message

private val TOKEN = Regex("""(@[A-Za-z0-9_.]{3,24})|(https?://[^\s<>"']+[^\s<>"'.,!?;:)\]])""")

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        toast(context, "No app can open that link")
    }
}

/** Message text with @mentions highlighted and links you can tap. */
@Composable
fun MessageText(m: Message, conv: Conversation, myId: Long, ink: Color) {
    val context = LocalContext.current
    val usernames = remember(conv.members) { conv.members.associate { it.user.username.lowercase() to it.user.id } }
    val annotated: AnnotatedString = remember(m.body, usernames, ink) {
        buildAnnotatedString {
            var last = 0
            for (match in TOKEN.findAll(m.body)) {
                append(m.body.substring(last, match.range.first))
                val token = match.value
                if (token.startsWith("@")) {
                    val id = usernames[token.drop(1).lowercase()]
                    if (id != null) {
                        pushStyle(
                            SpanStyle(
                                fontWeight = FontWeight.ExtraBold,
                                background = BantsYellow.copy(alpha = if (id == myId) 1f else 0.85f),
                                color = BantsInk,
                            ),
                        )
                        append(token)
                        pop()
                    } else {
                        append(token)
                    }
                } else {
                    pushStringAnnotation("url", token)
                    pushStyle(SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold))
                    append(token)
                    pop()
                    pop()
                }
                last = match.range.last + 1
            }
            append(m.body.substring(last))
        }
    }
    val style = TextStyle(color = ink, fontSize = 16.sp, lineHeight = 22.sp)
    if (annotated.getStringAnnotations("url", 0, annotated.length).isEmpty()) {
        Text(annotated, style = style)
    } else {
        ClickableText(annotated, style = style) { offset ->
            annotated.getStringAnnotations("url", offset, offset).firstOrNull()?.let { openUrl(context, it.item) }
        }
    }
}

@Composable
fun LinkPreviewCard(p: LinkPreview, mine: Boolean) {
    val context = LocalContext.current
    val b = LocalBants.current
    Column(
        Modifier
            .padding(top = 6.dp, bottom = 2.dp)
            .widthIn(max = 270.dp)
            .chunky(RoundedCornerShape(12.dp), b.card, b.outline, shadow = 0.dp, border = 2.dp)
            .clickable { openUrl(context, p.url) },
    ) {
        if (p.image != null) {
            AsyncImage(
                model = mediaUrl(p.image),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(140.dp),
            )
        }
        Column(Modifier.padding(10.dp)) {
            p.siteName?.let { Text(it.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = b.muted, maxLines = 1) }
            Text(p.title, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = b.onCard, maxLines = 2, overflow = TextOverflow.Ellipsis)
            p.description?.let { Text(it, fontSize = 13.sp, color = b.muted, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        }
    }
}
