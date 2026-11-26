package com.wellsey.bants.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.wellsey.bants.R

// "Loud & cheeky": thick outlines, chunky offset shadows, bold colours.
val BantsPurple = Color(0xFF5B4BFF)
val BantsPurpleDark = Color(0xFF3D2FE0)
val BantsYellow = Color(0xFFFFD23F)
val BantsPink = Color(0xFFFF4D8D)
val BantsInk = Color(0xFF111111)
val BantsCream = Color(0xFFFFF6E5)
val ReadTick = BantsYellow
val OnlineGreen = Color(0xFF2ECC71)

val Bricolage = FontFamily(
    Font(R.font.bricolage_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_semibold, FontWeight.Bold),
    Font(R.font.bricolage_extrabold, FontWeight.ExtraBold),
    Font(R.font.bricolage_extrabold, FontWeight.Black),
)

/** Colours the Material scheme has no slot for. */
@Immutable
data class BantsColors(
    val outline: Color,
    val card: Color,
    val unreadCard: Color,
    val theirBubble: Color,
    val onCard: Color,
    val muted: Color,
)

private val LightBants = BantsColors(
    outline = BantsInk,
    card = Color.White,
    unreadCard = Color(0xFFF1EEFF),
    theirBubble = Color.White,
    onCard = BantsInk,
    muted = Color(0xFF4A4A4A),
)

private val DarkBants = BantsColors(
    outline = Color(0xFFF3E6C9),
    card = Color(0xFF24222B),
    unreadCard = Color(0xFF2E2A45),
    theirBubble = Color(0xFF24222B),
    onCard = Color(0xFFF6EFE2),
    muted = Color(0xFFC9C2B4),
)

val LocalBants = staticCompositionLocalOf { LightBants }

private val LightColors = lightColorScheme(
    primary = BantsPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E5FF),
    onPrimaryContainer = Color(0xFF17106B),
    secondary = BantsYellow,
    onSecondary = BantsInk,
    tertiary = BantsPink,
    onTertiary = BantsInk,
    background = BantsCream,
    onBackground = BantsInk,
    surface = BantsCream,
    onSurface = BantsInk,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF4A4A4A),
    outline = BantsInk,
    error = Color(0xFFD1243B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8C7FFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3D2FE0),
    onPrimaryContainer = Color.White,
    secondary = BantsYellow,
    onSecondary = BantsInk,
    tertiary = BantsPink,
    onTertiary = BantsInk,
    background = Color(0xFF17161B),
    onBackground = Color(0xFFF6EFE2),
    surface = Color(0xFF17161B),
    onSurface = Color(0xFFF6EFE2),
    surfaceVariant = Color(0xFF24222B),
    onSurfaceVariant = Color(0xFFC9C2B4),
    outline = Color(0xFFF3E6C9),
    error = Color(0xFFFF6B7D),
)

private val base = Typography()
private val BantsTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold),
    titleLarge = base.titleLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold),
    titleMedium = base.titleMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold),
)

val HeadingStyle = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold)

@Composable
fun BantsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalBants provides if (dark) DarkBants else LightBants) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = BantsTypography,
            content = content,
        )
    }
}

val TitleSize = 22.sp
