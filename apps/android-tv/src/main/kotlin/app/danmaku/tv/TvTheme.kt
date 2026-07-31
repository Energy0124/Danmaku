package app.danmaku.tv
import androidx.compose.runtime.CompositionLocalProvider

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.darkColorScheme

internal val TvBackground = Color(0xFF070B12)
internal val TvSurface = Color(0xFF101722)
internal val TvSurfaceRaised = Color(0xFF182333)
internal val TvContent = Color(0xFFF4F7FB)
internal val TvSecondaryContent = Color(0xFFAAB7C8)
internal val TvAccent = Color(0xFF65C7F7)
internal val TvFocus = Color(0xFFB7E7FF)
internal val TvSuccess = Color(0xFF55D69E)
internal val TvWarning = Color(0xFFFFC857)
internal val TvError = Color(0xFFFF8A8A)

private val TvDarkColorScheme = darkColorScheme(
    primary = TvAccent,
    onPrimary = Color(0xFF001D2A),
    secondary = TvFocus,
    onSecondary = Color(0xFF001D2A),
    background = TvBackground,
    onBackground = TvContent,
    surface = TvSurface,
    onSurface = TvContent,
    surfaceVariant = TvSurfaceRaised,
    onSurfaceVariant = TvSecondaryContent,
    error = TvError,
    onError = Color(0xFF320000),
)

@Composable
internal fun DanmakuTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TvDarkColorScheme,
    ) {
        CompositionLocalProvider(LocalContentColor provides TvContent) {
            content()
        }
    }
}
