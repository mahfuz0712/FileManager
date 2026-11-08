package com.mux.android.files

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class HudPalette(
    val name: String,
    val primary: Color,
    val dim: Color,
    val bg: Color,
    val panel: Color,
    val accent: Color,
    val danger: Color,
    val good: Color,
    val text: Color
)

val CyanPalette = HudPalette(
    name = "CYAN",
    primary = Color(0xFF19E3FF),
    dim = Color(0xFF3C8A99),
    bg = Color(0xFF040A12),
    panel = Color(0xFF08141F),
    accent = Color(0xFFFFB000),
    danger = Color(0xFFFF5252),
    good = Color(0xFF3DDC84),
    text = Color(0xFFD6F8FF)
)

val AmberPalette = HudPalette(
    name = "AMBER",
    primary = Color(0xFFFFA726),
    dim = Color(0xFFA6732A),
    bg = Color(0xFF0D0804),
    panel = Color(0xFF1A1008),
    accent = Color(0xFF19E3FF),
    danger = Color(0xFFFF5252),
    good = Color(0xFF3DDC84),
    text = Color(0xFFFFEBD0)
)

val LocalHud = staticCompositionLocalOf { CyanPalette }

object Hud {
    val colors: HudPalette
        @Composable get() = LocalHud.current
}

@Composable
fun MuxTheme(palette: HudPalette, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHud provides palette) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = palette.primary,
                background = palette.bg,
                surface = palette.panel
            )
        ) {
            content()
        }
    }
}
