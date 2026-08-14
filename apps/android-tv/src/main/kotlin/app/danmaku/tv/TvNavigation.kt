package app.danmaku.tv

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal sealed interface TvRoute {
    data object Onboarding : TvRoute
    data object Home : TvRoute
    data object Library : TvRoute
    data object Search : TvRoute
    data object Favorites : TvRoute
    data object Pc : TvRoute
    data class FolderBrowser(val path: List<String> = emptyList()) : TvRoute
    data class SeriesDetail(val seriesKey: String) : TvRoute
    data class Player(val mediaId: String) : TvRoute
}

internal enum class TvOverlay {
    LibraryFilters,
    AudioTracks,
    SubtitleTracks,
    DanmakuSettings,
}

internal data class TvNavigationState(
    val backStack: List<TvRoute> = listOf(TvRoute.Onboarding),
    val overlay: TvOverlay? = null,
) {
    val route: TvRoute
        get() = backStack.last()
}

internal class TvNavigator(
    initialRoute: TvRoute = TvRoute.Onboarding,
) {
    // Focus changes are hot-path UI events. Keep their restoration data lifecycle-owned
    // without emitting a new global navigation snapshot for every D-pad movement.
    private val focusKeys = mutableMapOf<TvRoute, String>()
    private val mutableState = MutableStateFlow(
        TvNavigationState(backStack = listOf(initialRoute)),
    )
    val state: StateFlow<TvNavigationState> = mutableState.asStateFlow()

    fun reset(route: TvRoute) {
        mutableState.update {
            it.copy(backStack = listOf(route), overlay = null)
        }
    }

    fun navigate(route: TvRoute) {
        mutableState.update { current ->
            if (current.route == route) {
                current.copy(overlay = null)
            } else {
                current.copy(
                    backStack = current.backStack + route,
                    overlay = null,
                )
            }
        }
    }

    fun navigateTopLevel(route: TvRoute) {
        require(route.isTopLevel()) { "route must be top-level" }
        mutableState.update { current ->
            if (current.route == route) {
                return@update current.copy(overlay = null)
            }
            val existingIndex = current.backStack.indexOfLast { it == route }
            val nextStack = if (existingIndex >= 0) {
                current.backStack.take(existingIndex + 1)
            } else {
                current.backStack + route
            }
            current.copy(backStack = nextStack, overlay = null)
        }
    }

    fun showOverlay(overlay: TvOverlay) {
        mutableState.update { it.copy(overlay = overlay) }
    }

    fun closeOverlay(): Boolean {
        var closed = false
        mutableState.update { current ->
            if (current.overlay == null) {
                current
            } else {
                closed = true
                current.copy(overlay = null)
            }
        }
        return closed
    }

    fun saveFocus(
        route: TvRoute,
        focusKey: String,
    ) {
        require(focusKey.isNotBlank()) { "focusKey must not be blank" }
        focusKeys[route] = focusKey
    }

    fun savedFocus(route: TvRoute): String? = focusKeys[route]

    fun back(): Boolean {
        if (closeOverlay()) return true
        var handled = false
        mutableState.update { current ->
            if (current.backStack.size <= 1) {
                current
            } else {
                handled = true
                current.copy(backStack = current.backStack.dropLast(1))
            }
        }
        return handled
    }
}

internal fun TvRoute.isTopLevel(): Boolean =
    this == TvRoute.Home ||
        this == TvRoute.Library ||
        this == TvRoute.Search ||
        this == TvRoute.Favorites ||
        (this is TvRoute.FolderBrowser && path.isEmpty()) ||
        this == TvRoute.Pc ||
        this == TvRoute.Onboarding

internal fun TvRoute.defaultFocusKey(): String =
    when (this) {
        TvRoute.Onboarding -> "onboarding-discover"
        TvRoute.Home -> "home-hero"
        TvRoute.Library -> "library-first-series"
        TvRoute.Search -> "search-field"
        TvRoute.Favorites -> "favorites-first-series"
        TvRoute.Pc -> "pc-discover"
        is TvRoute.FolderBrowser -> "folder-first-entry"
        is TvRoute.SeriesDetail -> "series-primary-action"
        is TvRoute.Player -> "player-play-pause"
    }
