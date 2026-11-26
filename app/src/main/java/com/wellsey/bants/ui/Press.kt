package com.wellsey.bants.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wellsey.bants.Haptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * How far a press has gone: 0 at rest, 1 fully pushed in.
 * It snaps down quickly and springs back with a little wobble. Even a very quick tap
 * plays the full push, so every tap gets visible feedback.
 */
@Composable
fun rememberPress(interaction: InteractionSource, buzz: Boolean = true): State<Float> {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(interaction) {
        var down: Job? = null
        interaction.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> down = launch {
                    if (buzz) Haptics.tap()
                    anim.animateTo(1f, tween(70, easing = FastOutSlowInEasing))
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    val pushing = down
                    launch {
                        pushing?.join()
                        anim.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 520f))
                    }
                }
            }
        }
    }
    return anim.asState()
}

/**
 * A clickable chunky surface. Surfaces with a shadow push down into it (the shadow stays put
 * while the face slides onto it), then pop back up. Flat ones squash slightly instead.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.chunkyClickable(
    shape: Shape,
    fill: Color,
    outline: Color,
    shadow: Dp = 4.dp,
    border: Dp = 2.5.dp,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val press = rememberPress(source)
    val sinks = shadow > 0.dp
    return this
        .graphicsLayer {
            val p = press.value.coerceAtLeast(-0.35f)
            if (sinks) {
                translationX = shadow.toPx() * p
                translationY = shadow.toPx() * p
            } else {
                scaleX = 1f - 0.06f * p
                scaleY = 1f - 0.06f * p
            }
        }
        .chunky(shape, fill, outline, shadow, border, press = if (sinks) ({ press.value.coerceAtLeast(-0.35f) }) else null)
        .combinedClickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onLongClick = onLongClick?.let { f -> { Haptics.longPress(); f() } },
            onLongClickLabel = onLongClickLabel,
            onClick = onClick,
        )
}

/** Clickable that squashes a little when pressed and bounces back. For avatars, rows, images and pills. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    squash: Float = 0.06f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .pressScale(source, squash)
        .combinedClickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onLongClick = onLongClick?.let { f -> { Haptics.longPress(); f() } },
            onClick = onClick,
        )
}

/** Squash-on-press for things that handle their own clicks, given their interaction source. */
@Composable
fun Modifier.pressScale(source: InteractionSource, squash: Float = 0.06f, buzz: Boolean = true): Modifier {
    val press = rememberPress(source, buzz)
    return graphicsLayer {
        val s = 1f - squash * press.value.coerceAtLeast(-0.5f)
        scaleX = s
        scaleY = s
    }
}
