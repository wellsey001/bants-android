package com.wellsey.bants.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** One app theme: the accent colours and the background. Midnight is always dark. */
data class Palette(
    val id: String,
    val name: String,
    val purple: Color,
    val purpleDark: Color,
    val yellow: Color,
    val pink: Color,
    val cream: Color,
    val alwaysDark: Boolean = false,
    val darkBg: Color = Color(0xFF17161B),
    val darkCard: Color = Color(0xFF24222B),
)

/** App themes and launcher icons, picked in Settings and kept on this phone. */
object Looks {
    val THEMES = listOf(
        Palette("bants", "Bants", Color(0xFF5B4BFF), Color(0xFF3D2FE0), Color(0xFFFFD23F), Color(0xFFFF4D8D), Color(0xFFFFF6E5)),
        Palette("midnight", "Midnight", Color(0xFF8B7CFF), Color(0xFF5B4BCC), Color(0xFFFFC94D), Color(0xFFFF6FA8), Color(0xFF0E0D1A), alwaysDark = true, darkBg = Color(0xFF0E0D1A), darkCard = Color(0xFF1B1930)),
        Palette("ocean", "Ocean", Color(0xFF0077B6), Color(0xFF005A8C), Color(0xFF48CAE4), Color(0xFFF26B5B), Color(0xFFE6F6FB)),
        Palette("forest", "Forest", Color(0xFF2D6A4F), Color(0xFF1E4D38), Color(0xFF95D5B2), Color(0xFFD08C60), Color(0xFFEEF5E8)),
        Palette("sunset", "Sunset", Color(0xFFE8590C), Color(0xFFB84508), Color(0xFFFFB4A2), Color(0xFFC9184A), Color(0xFFFFF0E6)),
        Palette("candy", "Candy", Color(0xFFD6336C), Color(0xFFA61E52), Color(0xFFB197FC), Color(0xFFFF8FAB), Color(0xFFFFF0F7)),
        Palette("mono", "Mono", Color(0xFF222222), Color(0xFF000000), Color(0xFFBBBBBB), Color(0xFF555555), Color(0xFFF4F4F4)),
    )

    /** Launcher icons: the B's colour and the background behind it. */
    data class Icon(val id: String, val name: String, val b: Color, val bg: Color)
    val ICONS = listOf(
        Icon("classic", "Classic", Color(0xFF5B4BFF), Color(0xFFFFD23F)),
        Icon("lime", "Lime", Color(0xFF2D6A4F), Color(0xFFB7F171)),
        Icon("sky", "Sky", Color(0xFF0077B6), Color(0xFF8EE3F5)),
        Icon("berry", "Berry", Color(0xFFD6336C), Color(0xFFFFC2D9)),
        Icon("candy", "Candy", Color(0xFF7C3AED), Color(0xFFFFB4E6)),
        Icon("shadow", "Shadow", Color(0xFF2B2B2B), Color(0xFF9A9A9A)),
    )

    var palette by mutableStateOf(THEMES[0])
        private set

    private fun prefs(c: Context) = c.getSharedPreferences("bants", Context.MODE_PRIVATE)

    fun init(c: Context) {
        val id = prefs(c).getString("appTheme", "bants")
        palette = THEMES.firstOrNull { it.id == id } ?: THEMES[0]
    }

    fun setTheme(c: Context, id: String) {
        prefs(c).edit().putString("appTheme", id).apply()
        palette = THEMES.firstOrNull { it.id == id } ?: THEMES[0]
    }

    fun icon(c: Context): String = prefs(c).getString("appIcon", "classic") ?: "classic"

    /** Switches which launcher alias is on. Some launchers take a moment to show the new icon. */
    fun setIcon(c: Context, id: String) {
        val pm = c.packageManager
        for (i in ICONS) {
            val name = ComponentName(c.packageName, "com.wellsey.bants.Icon" + i.id.replaceFirstChar { it.uppercase() })
            pm.setComponentEnabledSetting(
                name,
                if (i.id == id) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        prefs(c).edit().putString("appIcon", id).apply()
    }
}
