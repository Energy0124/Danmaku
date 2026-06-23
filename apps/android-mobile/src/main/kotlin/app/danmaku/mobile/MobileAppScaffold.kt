package app.danmaku.mobile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.player.android.Media3PlaybackController

internal data class MobileAppUiState(
    val selectedTab: MobileTab,
    val controller: Media3PlaybackController?,
    val catalog: LibraryCatalog?,
    val posterEndpoint: LibraryPosterEndpoint?,
    val playbackProgresses: List<PlaybackProgress>,
    val filteredItems: List<LibraryMediaItem>,
    val totalCount: Int,
    val snapshot: PlaybackSnapshot,
    val nowPlaying: LibraryMediaItem?,
    val playbackError: String?,
    val serverUrl: String,
    val pairingToken: String,
    val savedConnections: List<LanLibraryConnectionProfile>,
    val libraryError: String?,
    val searchText: String,
    val sort: LibraryCatalogSort,
    val subtitleFilter: LibrarySubtitleFilter,
    val favoriteMediaIds: Set<String>,
    val favoriteFilter: LibraryFavoriteFilter,
)

internal data class MobileAppActions(
    val onTabSelected: (MobileTab) -> Unit,
    val onPlay: (LibraryMediaItem) -> Unit,
    val onPlayPause: () -> Unit,
    val onOpenPlayer: () -> Unit,
    val onOpenLibrary: () -> Unit,
    val onShowLibraryItem: (LibraryMediaItem) -> Unit,
    val onConnect: () -> Unit,
    val onOpenVideo: () -> Unit,
    val onSeekTo: (Long) -> Unit,
    val onSetVolume: (Int) -> Unit,
    val onSelectAudio: (String) -> Unit,
    val onSelectSubtitle: (String?) -> Unit,
    val onSearchTextChange: (String) -> Unit,
    val onSortChange: (LibraryCatalogSort) -> Unit,
    val onSubtitleFilterChange: (LibrarySubtitleFilter) -> Unit,
    val onFavoriteFilterChange: (LibraryFavoriteFilter) -> Unit,
    val onSetFavorite: (LibraryMediaItem, Boolean) -> Unit,
    val onServerUrlChange: (String) -> Unit,
    val onPairingTokenChange: (String) -> Unit,
    val onSelectConnection: (LanLibraryConnectionProfile) -> Unit,
    val onEditConnection: (LanLibraryConnectionProfile) -> Unit,
    val onForgetConnection: (LanLibraryConnectionProfile) -> Unit,
    val onSaveConnection: () -> Unit,
    val onDiscover: () -> Unit,
    val onRefresh: () -> Unit,
)

@Composable
internal fun MobileAppScaffold(
    state: MobileAppUiState,
    actions: MobileAppActions,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppBackground,
        bottomBar = {
            MobileBottomBar(
                selectedTab = state.selectedTab,
                onTabSelected = actions.onTabSelected,
            )
        },
    ) { innerPadding ->
        when (state.selectedTab) {
            MobileTab.Home -> HomePage(
                contentPadding = innerPadding,
                catalog = state.catalog,
                posterEndpoint = state.posterEndpoint,
                playbackProgresses = state.playbackProgresses,
                snapshot = state.snapshot,
                nowPlaying = state.nowPlaying,
                onPlay = actions.onPlay,
                onPlayPause = actions.onPlayPause,
                onOpenPlayer = actions.onOpenPlayer,
                onOpenLibrary = actions.onOpenLibrary,
                onShowLibraryItem = actions.onShowLibraryItem,
                onConnect = actions.onConnect,
            )
            MobileTab.Watch -> WatchPage(
                contentPadding = innerPadding,
                controller = state.controller,
                snapshot = state.snapshot,
                nowPlaying = state.nowPlaying,
                playbackError = state.playbackError,
                onOpen = actions.onOpenVideo,
                onPlayPause = actions.onPlayPause,
                onSeekTo = actions.onSeekTo,
                onSetVolume = actions.onSetVolume,
                onSelectAudio = actions.onSelectAudio,
                onSelectSubtitle = actions.onSelectSubtitle,
                onBrowseLibrary = actions.onOpenLibrary,
            )
            MobileTab.Library -> LibraryPage(
                contentPadding = innerPadding,
                catalog = state.catalog,
                posterEndpoint = state.posterEndpoint,
                playbackProgresses = state.playbackProgresses,
                filteredItems = state.filteredItems,
                totalCount = state.totalCount,
                snapshot = state.snapshot,
                nowPlaying = state.nowPlaying,
                searchText = state.searchText,
                onSearchTextChange = actions.onSearchTextChange,
                sort = state.sort,
                onSortChange = actions.onSortChange,
                subtitleFilter = state.subtitleFilter,
                onSubtitleFilterChange = actions.onSubtitleFilterChange,
                favoriteMediaIds = state.favoriteMediaIds,
                favoriteFilter = state.favoriteFilter,
                onFavoriteFilterChange = actions.onFavoriteFilterChange,
                onSetFavorite = actions.onSetFavorite,
                onPlay = actions.onPlay,
                onPlayPause = actions.onPlayPause,
                onOpenPlayer = actions.onOpenPlayer,
                onConnect = actions.onConnect,
            )
            MobileTab.Connect -> ConnectPage(
                contentPadding = innerPadding,
                catalog = state.catalog,
                snapshot = state.snapshot,
                nowPlaying = state.nowPlaying,
                serverUrl = state.serverUrl,
                savedConnections = state.savedConnections,
                libraryError = state.libraryError,
                onServerUrlChange = actions.onServerUrlChange,
                onSelectConnection = actions.onSelectConnection,
                onEditConnection = actions.onEditConnection,
                onForgetConnection = actions.onForgetConnection,
                onSaveConnection = actions.onSaveConnection,
                onDiscover = actions.onDiscover,
                onRefresh = actions.onRefresh,
                onPlayPause = actions.onPlayPause,
                onOpenPlayer = actions.onOpenPlayer,
            )
        }
    }
}
