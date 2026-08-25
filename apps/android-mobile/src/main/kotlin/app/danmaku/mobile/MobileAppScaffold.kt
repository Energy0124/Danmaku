package app.danmaku.mobile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.danmaku.domain.DanmakuDisplaySettings
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.player.android.Media3PlaybackController
import app.danmaku.library.android.OfflineCacheEntry
import app.danmaku.library.android.OfflineCacheState
import app.danmaku.domain.itemsInFolder
import androidx.compose.ui.res.stringResource

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
    val danmakuState: MobileDanmakuState,
    val danmakuDisplaySettings: DanmakuDisplaySettings,
    val playbackStartupPhase: MobilePlaybackStartupPhase,
    val isPlayerFullscreen: Boolean,
    val tracking: MobileTrackingState = MobileTrackingState(),
    val folderRefreshInProgress: Boolean = false,
    val folderRefreshFilesSeen: Long? = null,
    val folderRefreshError: MobileFolderRefreshError? = null,
    val folderRefreshErrorDetail: String? = null,
    val cacheEntries: List<OfflineCacheEntry> = emptyList(),
    val pendingCacheItems: List<LibraryMediaItem> = emptyList(),
    val isDownloadsOpen: Boolean = false,
    val cacheError: String? = null,
    val cacheAvailableBytes: Long = 0,
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
    val onSetPlaybackRate: (Float) -> Unit,
    val onUpdateDanmakuDisplaySettings: (DanmakuDisplaySettings) -> Unit,
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
    val onRefreshFolder: (List<String>) -> Unit,
    val onTogglePlayerFullscreen: () -> Unit,
    val onLoadTracking: () -> Unit,
    val onReadTracking: () -> Unit,
    val onSyncTracking: () -> Unit,
    val onOpenDownloads: () -> Unit,
    val onCloseDownloads: () -> Unit,
    val onRequestCache: (List<LibraryMediaItem>) -> Unit,
    val onConfirmCache: () -> Unit,
    val onDismissCache: () -> Unit,
    val onPauseCache: (String) -> Unit,
    val onResumeCache: (String) -> Unit,
    val onDeleteCache: (String) -> Unit,
    val onClearCache: () -> Unit,
    val onPlayCached: (String) -> Unit,
)

@Composable
internal fun MobileAppScaffold(
    state: MobileAppUiState,
    actions: MobileAppActions,
) {
    var folderPath by remember(state.serverUrl) { mutableStateOf(emptyList<String>()) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppBackground,
        bottomBar = {
            if (!state.isPlayerFullscreen && !state.isDownloadsOpen) {
                MobileBottomBar(
                    selectedTab = state.selectedTab,
                    onTabSelected = actions.onTabSelected,
                )
            }
        },
        floatingActionButton = {
            if (
                !state.isDownloadsOpen && !state.isPlayerFullscreen &&
                state.selectedTab in setOf(MobileTab.Home, MobileTab.Library)
            ) {
                ExtendedFloatingActionButton(
                    onClick = actions.onOpenDownloads,
                    modifier = Modifier.testTag("open-downloads"),
                    icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                    text = { Text(stringResource(R.string.downloads_title)) },
                )
            }
        },
    ) { innerPadding ->
        if (state.isDownloadsOpen) {
            DownloadsPage(
                contentPadding = innerPadding,
                entries = state.cacheEntries,
                availableBytes = state.cacheAvailableBytes,
                error = state.cacheError,
                onBack = actions.onCloseDownloads,
                onPlay = actions.onPlayCached,
                onPause = actions.onPauseCache,
                onResume = actions.onResumeCache,
                onDelete = actions.onDeleteCache,
                onClear = actions.onClearCache,
            )
        } else when (state.selectedTab) {
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
                isFullscreen = state.isPlayerFullscreen,
                danmakuState = state.danmakuState,
                danmakuDisplaySettings = state.danmakuDisplaySettings,
                playbackStartupPhase = state.playbackStartupPhase,
                onOpen = actions.onOpenVideo,
                onPlayPause = actions.onPlayPause,
                onSeekTo = actions.onSeekTo,
                onSetVolume = actions.onSetVolume,
                onSetPlaybackRate = actions.onSetPlaybackRate,
                onUpdateDanmakuDisplaySettings = actions.onUpdateDanmakuDisplaySettings,
                onSelectAudio = actions.onSelectAudio,
                onSelectSubtitle = actions.onSelectSubtitle,
                onBrowseLibrary = actions.onOpenLibrary,
                onToggleFullscreen = actions.onTogglePlayerFullscreen,
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
                cachedMediaIds = state.cacheEntries
                    .filter {
                        it.serverUrl == state.serverUrl && it.state == OfflineCacheState.READY
                    }
                    .mapTo(mutableSetOf()) { it.item.id },
                onRequestCache = actions.onRequestCache,
                onOpenDownloads = actions.onOpenDownloads,
            )
            MobileTab.Folders -> FolderPage(
                contentPadding = innerPadding,
                catalog = state.catalog,
                path = folderPath,
                onOpenFolder = { folderPath = folderPath + it },
                onNavigateUp = { folderPath = folderPath.dropLast(1) },
                onPlay = actions.onPlay,
                onConnect = actions.onConnect,
                isRefreshing = state.folderRefreshInProgress,
                refreshFilesSeen = state.folderRefreshFilesSeen,
                refreshError = state.folderRefreshError,
                refreshErrorDetail = state.folderRefreshErrorDetail,
                onRefresh = actions.onRefreshFolder,
                cachedMediaIds = state.cacheEntries
                    .filter {
                        it.serverUrl == state.serverUrl && it.state == OfflineCacheState.READY
                    }
                    .mapTo(mutableSetOf()) { it.item.id },
                onCacheFile = { actions.onRequestCache(listOf(it)) },
                onCacheFolder = { path ->
                    state.catalog?.itemsInFolder(path)?.let(actions.onRequestCache)
                },
                onOpenDownloads = actions.onOpenDownloads,
            )
            MobileTab.Connect -> ConnectPage(
                contentPadding = innerPadding,
                catalog = state.catalog,
                snapshot = state.snapshot,
                nowPlaying = state.nowPlaying,
                serverUrl = state.serverUrl,
                pairingToken = state.pairingToken,
                savedConnections = state.savedConnections,
                libraryError = state.libraryError,
                tracking = state.tracking,
                onServerUrlChange = actions.onServerUrlChange,
                onSelectConnection = actions.onSelectConnection,
                onEditConnection = actions.onEditConnection,
                onForgetConnection = actions.onForgetConnection,
                onSaveConnection = actions.onSaveConnection,
                onDiscover = actions.onDiscover,
                onRefresh = actions.onRefresh,
                onPairingTokenChange = actions.onPairingTokenChange,
                onLoadTracking = actions.onLoadTracking,
                onReadTracking = actions.onReadTracking,
                onSyncTracking = actions.onSyncTracking,
                onPlayPause = actions.onPlayPause,
                onOpenPlayer = actions.onOpenPlayer,
            )
        }
    }
    if (state.pendingCacheItems.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = actions.onDismissCache,
            title = { Text(stringResource(R.string.cache_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.cache_confirm_body,
                        state.pendingCacheItems.size,
                        state.pendingCacheItems.sumOf(LibraryMediaItem::sizeBytes).formatCacheSize(),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = actions.onConfirmCache) {
                    Text(stringResource(R.string.action_cache))
                }
            },
            dismissButton = {
                TextButton(onClick = actions.onDismissCache) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

internal fun Long.formatCacheSize(): String {
    val value = coerceAtLeast(0)
    return when {
        value >= 1024L * 1024L * 1024L -> "%.1f GB".format(value / (1024.0 * 1024.0 * 1024.0))
        value >= 1024L * 1024L -> "%.1f MB".format(value / (1024.0 * 1024.0))
        value >= 1024L -> "%.1f KB".format(value / 1024.0)
        else -> "$value B"
    }
}
