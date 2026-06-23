package app.danmaku.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ButtonColors
import androidx.tv.material3.ButtonDefaults
import app.danmaku.domain.LibraryMediaItem

internal val TvAppBackground = Color(0xFF090B0E)
internal val TvPanelColor = Color(0xFF15191D)
internal val TvPanelRaisedColor = Color(0xFF20262B)
internal val TvCardColor = Color(0xFF111820)
internal val TvAccentBlue = Color(0xFF7DD3FC)
internal val TvMutedText = Color(0xFFB7C0C9)
internal val TvButtonColor = Color(0xFF1A2633)
internal val TvButtonFocusedColor = Color(0xFF2563A8)
internal val TvButtonSelectedColor = Color(0xFF0E7490)
internal val TvButtonDisabledColor = Color(0xFF151B22)

@Composable
internal fun tvButtonColors(selected: Boolean = false): ButtonColors =
    ButtonDefaults.colors(
        containerColor = if (selected) TvButtonSelectedColor else TvButtonColor,
        contentColor = Color(0xFFE5EEF7),
        focusedContainerColor = TvButtonFocusedColor,
        focusedContentColor = Color.White,
        pressedContainerColor = TvAccentBlue,
        pressedContentColor = Color(0xFF031018),
        disabledContainerColor = TvButtonDisabledColor,
        disabledContentColor = Color(0xFF64748B),
    )

internal data class LibraryPosterEndpoint(
    val baseUrl: String,
    val pairingToken: String,
) {
    fun posterUrl(item: LibraryMediaItem): String? {
        val path = item.posterPath ?: return null
        return "${baseUrl.trim().trimEnd('/')}$path?token=${pairingToken.encodedQueryValue()}"
    }
}

@Composable
internal fun Modifier.tvFocusHalo(
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.035f else 1f,
        label = "tv-focus-halo-scale",
    )
    return this
        .scale(scale)
        .border(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) TvAccentBlue else Color.Transparent,
            shape = shape,
        )
        .onFocusChanged { isFocused = it.isFocused }
}
