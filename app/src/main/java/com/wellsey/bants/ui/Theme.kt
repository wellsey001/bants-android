package com.wellsey.bants.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BantsPurple = Color(0xFF5B4BFF)
val BantsPurpleDark = Color(0xFF3D2FE0)
val ReadTick = Color(0xFF7FE3FF)
val OnlineGreen = Color(0xFF2ECC71)

private val LightColors = lightColorScheme(
    primary = BantsPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E1FF),
    onPrimaryContainer = Color(0xFF17106B),
    secondary = BantsPurpleDark,
    onSecondary = Color.White,
    background = Color(0xFFF7F6FB),
    onBackground = Color(0xFF17171C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17171C),
    surfaceVariant = Color(0xFFECEAF4),
    onSurfaceVariant = Color(0xFF55545C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9A8FFF),
    onPrimary = Color(0xFF1A1170),
    primaryContainer = BantsPurpleDark,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFB8B0FF),
    background = Color(0xFF111116),
    onBackground = Color(0xFFECEAF4),
    surface = Color(0xFF17171C),
    onSurface = Color(0xFFECEAF4),
    surfaceVariant = Color(0xFF26252E),
    onSurfaceVariant = Color(0xFFC9C7D3),
)

@Composable
fun BantsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
