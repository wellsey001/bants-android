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
// The accents follow the app theme picked in Settings (see Looks).
val BantsPurple: Color get() = Looks.palette.purple
val BantsPurpleDark: Color get() = Looks.palette.purpleDark
val BantsYellow: Color get() = Looks.palette.yellow
val BantsPink: Color get() = Looks.palette.pink
val BantsInk = Color(0xFF111111)
val BantsCream: Color get() = Looks.palette.cream
val ReadTick: Color get() = BantsYellow
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

private fun lightColors() = lightColorScheme(
    primary = BantsPurple,
    onPrimary = Color.White,
    primaryContainer = androidx.compose.ui.graphics.lerp(BantsPurple, Color.White, 0.85f),
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

private fun darkColors(p: Palette) = darkColorScheme(
    primary = if (p.id == "bants") Color(0xFF8C7FFF) else p.purple,
    onPrimary = Color.White,
    primaryContainer = p.purpleDark,
    onPrimaryContainer = Color.White,
    secondary = BantsYellow,
    onSecondary = BantsInk,
    tertiary = BantsPink,
    onTertiary = BantsInk,
    background = p.darkBg,
    onBackground = Color(0xFFF6EFE2),
    surface = p.darkBg,
    onSurface = Color(0xFFF6EFE2),
    surfaceVariant = p.darkCard,
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
    val p = Looks.palette
    val dark = isSystemInDarkTheme() || p.alwaysDark
    val bants = if (dark) DarkBants.copy(card = p.darkCard, theirBubble = p.darkCard) else LightBants
    CompositionLocalProvider(LocalBants provides bants) {
        MaterialTheme(
            colorScheme = if (dark) darkColors(p) else lightColors(),
            typography = BantsTypography,
            content = content,
        )
    }
}

val TitleSize = 22.sp
