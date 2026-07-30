package app.danmaku.tv

import androidx.lifecycle.ViewModel

internal class TvNavigationViewModel(
    val navigator: TvNavigator,
) : ViewModel() {
    val state = navigator.state

    fun navigate(route: TvRoute) {
        if (route.isTopLevel()) {
            navigator.navigateTopLevel(route)
        } else {
            navigator.navigate(route)
        }
    }

    fun back(): Boolean = navigator.back()

    fun showOverlay(overlay: TvOverlay) = navigator.showOverlay(overlay)

    fun closeOverlay(): Boolean = navigator.closeOverlay()

    fun saveFocus(
        route: TvRoute,
        focusKey: String,
    ) = navigator.saveFocus(route, focusKey)
}
