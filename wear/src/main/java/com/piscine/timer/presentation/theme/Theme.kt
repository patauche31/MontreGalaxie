package com.piscine.timer.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Colors

// ── Palette ──────────────────────────────────────────────────────────────────
val Blue400    = Color(0xFF42A5F5)   // Chrono principal
val Cyan300    = Color(0xFF4DD0E1)   // Longueur en cours
val Teal200    = Color(0xFF80CBC4)   // Accent secondaire
val Amber400   = Color(0xFFFFCA28)   // Best lap
val Red400     = Color(0xFFEF5350)   // Worst lap / Stop
val White      = Color(0xFFFFFFFF)
val DarkBg     = Color(0xFF000000)   // Fond noir — économie batterie OLED

private val PiscineColors = Colors(
    primary          = Blue400,
    primaryVariant   = Cyan300,
    secondary        = Teal200,
    secondaryVariant = Teal200,
    background       = DarkBg,
    surface          = Color(0xFF1A1A1A),
    error            = Red400,
    onPrimary        = DarkBg,
    onSecondary      = DarkBg,
    onBackground     = White,
    onSurface        = White,
    onError          = White
)

@Composable
fun PiscineTimerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = PiscineColors,
        content = content
    )
}
