package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

@Composable
internal fun TvConsumerShell(
    route: TvRoute,
    navigation: TvNavigationState,
    navigator: TvNavigator,
    session: TvSessionUiState,
    browse: TvBrowseUiState,
    playback: TvPlaybackUiState,
    sessionViewModel: TvSessionViewModel,
    browseViewModel: TvBrowseViewModel,
    playbackViewModel: TvPlaybackViewModel,
    onNavigate: (TvRoute) -> Unit,
    onShowOverlay: (TvOverlay) -> Unit,
    onCloseOverlay: () -> Boolean,
) {
    val railFocusRequester = remember(route) { FocusRequester() }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        TvCompactNavigationRail(
            currentRoute = route,
            session = session,
            focusRequester = railFocusRequester,
            onNavigate = onNavigate,
        )
        CompositionLocalProvider(
            LocalTvNavigationRailFocusRequester provides railFocusRequester,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
        when (route) {
            TvRoute.Home ->
                TvHomeScreen(
                    navigation = navigation,
                    navigator = navigator,
                    session = session,
                    browse = browse,
                    onOpenSeries = { onNavigate(TvRoute.SeriesDetail(it)) },
                    onPlay = playbackViewModel::play,
                    onOpenPc = { onNavigate(TvRoute.Pc) },
                )
            TvRoute.Library ->
                TvLibraryGridScreen(
                    route = route,
                    navigation = navigation,
                    navigator = navigator,
                    session = session,
                    browse = browse,
                    onOpenSeries = { onNavigate(TvRoute.SeriesDetail(it)) },
                    onShowFilters = { onShowOverlay(TvOverlay.LibraryFilters) },
                )
            TvRoute.Search ->
                TvSearchScreen(
                    navigation = navigation,
                    navigator = navigator,
                    session = session,
                    browse = browse,
                    onSearch = browseViewModel::setSearchText,
                    onOpenSeries = { onNavigate(TvRoute.SeriesDetail(it)) },
                )
            TvRoute.Favorites ->
                TvFavoritesScreen(
                    navigation = navigation,
                    navigator = navigator,
                    session = session,
                    browse = browse,
                    onOpenSeries = { onNavigate(TvRoute.SeriesDetail(it)) },
                )
            TvRoute.Pc ->
                TvPcScreen(
                    navigation = navigation,
                    navigator = navigator,
                    session = session,
                    onServerUrlChange = sessionViewModel::updateServerUrl,
                    onPairingTokenChange = sessionViewModel::updatePairingToken,
                    onRefresh = { sessionViewModel.refreshLibrary() },
                    onDiscover = sessionViewModel::discoverPc,
                    onSave = sessionViewModel::saveConnection,
                    onSelectConnection = sessionViewModel::selectConnection,
                    onForgetConnection = sessionViewModel::forgetConnection,
                    onLoadTracking = sessionViewModel::loadTracking,
                    onReadTracking = sessionViewModel::readTracking,
                    onSyncTracking = sessionViewModel::syncTracking,
                )
            is TvRoute.FolderBrowser ->
                TvFolderBrowserScreen(
                    route = route,
                    navigation = navigation,
                    navigator = navigator,
                    browse = browse,
                    onOpenFolder = { folder ->
                        onNavigate(TvRoute.FolderBrowser(route.path + folder))
                    },
                    onOpenFile = { mediaId ->
                        browse.seriesIdByMediaId[mediaId]?.let { seriesId ->
                            onNavigate(TvRoute.SeriesDetail(seriesId))
                        } ?: browse.catalog
                            ?.items
                            ?.firstOrNull { it.id == mediaId }
                            ?.let(playbackViewModel::play)
                    },
                    onNavigateUp = { navigator.back() },
                )
            is TvRoute.SeriesDetail ->
                TvSeriesDetailScreen(
                    route = route,
                    navigation = navigation,
                    navigator = navigator,
                    session = session,
                    browse = browse,
                    onPlay = playbackViewModel::play,
                    onSetFavorite = browseViewModel::setFavorite,
                )
            TvRoute.Onboarding,
            is TvRoute.Player,
            -> Unit
        }
        if (navigation.overlay == TvOverlay.LibraryFilters) {
            Dialog(
                onDismissRequest = { onCloseOverlay() },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    TvLibraryFiltersOverlay(
                        query = browse.query,
                        availableReleaseYears = browse.availableReleaseYears,
                        onSetSort = browseViewModel::setSort,
                        onSetReleaseYear = browseViewModel::setReleaseYear,
                        onToggleSubtitles = browseViewModel::toggleSubtitles,
                        onReset = browseViewModel::resetFilters,
                        onClose = { onCloseOverlay() },
                    )
                }
            }
        }
            if (playback.error == TvPlaybackError.ControllerConnecting) {
                Text(
                    text = stringResource(R.string.playback_controller_connecting),
                    color = TvContent,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .clip(RoundedCornerShape(16.dp))
                        .background(TvSurfaceRaised)
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                        .testTag("playback-connecting-notice"),
                )
            }
        }
    }
}
}

@Composable
private fun TvCompactNavigationRail(
    currentRoute: TvRoute,
    session: TvSessionUiState,
    focusRequester: FocusRequester,
    onNavigate: (TvRoute) -> Unit,
) {
    var railFocused by remember { mutableStateOf(false) }
    val width = if (railFocused) 240.dp else 96.dp
    val destinations = listOf(
        TvNavItem(TvRoute.Home, R.string.nav_home, Icons.Default.Home),
        TvNavItem(TvRoute.Library, R.string.nav_library, Icons.AutoMirrored.Filled.List),
        TvNavItem(TvRoute.FolderBrowser(), R.string.nav_folders, Icons.Default.Folder),
        TvNavItem(TvRoute.Search, R.string.nav_search, Icons.Default.Search),
        TvNavItem(TvRoute.Favorites, R.string.nav_favorites, Icons.Default.Favorite),
        TvNavItem(TvRoute.Pc, R.string.nav_pc, Icons.Default.Settings),
    )
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .onFocusChanged { railFocused = it.hasFocus }
            .background(TvSurface)
            .padding(12.dp)
            .testTag("tv-app-rail"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "D",
            color = TvAccent,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        destinations.forEach { item ->
            val selected = currentRoute.matchesTopLevel(item.route)
            Button(
                onClick = { onNavigate(item.route) },
                modifier = Modifier
                    .width(if (railFocused) 216.dp else 68.dp)
                    .tvFocusHalo(RoundedCornerShape(18.dp))
                    .then(if (selected) Modifier.focusRequester(focusRequester) else Modifier)
                    .testTag("tv-route:${item.route.javaClass.simpleName}"),
                colors = tvButtonColors(selected),
                scale = ButtonDefaults.scale(focusedScale = 1f),
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = stringResource(item.label),
                )
                if (railFocused) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(item.label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = if (session.catalog == null) {
                stringResource(R.string.pc_offline)
            } else if (session.isOffline) {
                stringResource(R.string.status_cached_offline)
            } else {
                stringResource(R.string.pc_ready)
            },
            color = if (session.catalog == null) TvSecondaryContent else TvSuccess,
            maxLines = 1,
        )
    }
}

private data class TvNavItem(
    val route: TvRoute,
    val label: Int,
    val icon: ImageVector,
)

private fun TvRoute.matchesTopLevel(other: TvRoute): Boolean =
    this == other ||
        (this is TvRoute.SeriesDetail && other == TvRoute.Library) ||
        (this is TvRoute.FolderBrowser && other is TvRoute.FolderBrowser && other.path.isEmpty())
