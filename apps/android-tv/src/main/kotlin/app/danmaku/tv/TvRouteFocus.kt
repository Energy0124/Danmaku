package app.danmaku.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

internal val LocalTvNavigationRailFocusRequester =
    staticCompositionLocalOf<FocusRequester?> { null }

@Composable
internal fun Modifier.tvRouteFocus(
    navigationState: TvNavigationState,
    navigator: TvNavigator,
    route: TvRoute,
    focusKey: String,
    isDefault: Boolean = false,
): Modifier {
    val railRequester = LocalTvNavigationRailFocusRequester.current
    val requester = remember(route, focusKey) { FocusRequester() }
    val savedFocus = navigationState.focusKeys[route]
    val shouldRequest = navigationState.route == route &&
        (savedFocus == focusKey || (savedFocus == null && isDefault))
    LaunchedEffect(shouldRequest) {
        if (shouldRequest) requester.requestFocus()
    }
    return this
        .focusRequester(requester)
        .focusProperties {
            railRequester?.let { left = it }
        }
        .onFocusChanged {
            if (it.isFocused) navigator.saveFocus(route, focusKey)
        }
}
