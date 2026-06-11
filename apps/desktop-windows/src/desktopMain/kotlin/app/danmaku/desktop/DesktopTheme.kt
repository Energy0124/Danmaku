package app.danmaku.desktop

import androidx.compose.material.darkColors
import androidx.compose.ui.graphics.Color

internal object DanmakuColors {
    val Background = Color(0xFF121316)
    val Surface = Color(0xFF1B1E23)
    val SurfaceRaised = Color(0xFF282D34)
    val Accent = Color(0xFFFFB547)
    val AccentSoft = Color(0xFF4B3420)
    val TextMuted = Color(0xFFB7C0CB)
    val Good = Color(0xFF5CE0A3)
    val Warning = Color(0xFFFFC857)
    val Info = Color(0xFF6BA9FF)
}

internal val DanmakuDarkColors = darkColors(
    primary = DanmakuColors.Accent,
    primaryVariant = DanmakuColors.AccentSoft,
    secondary = Color(0xFF6C5CE7),
    background = DanmakuColors.Background,
    surface = DanmakuColors.Surface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)
