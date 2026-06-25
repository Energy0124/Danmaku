package app.danmaku.mobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogQuery
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.filteredItems
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.library.LanPlaybackTarget
import app.danmaku.player.android.Media3PlaybackController

internal class MobilePlayerState(
    initialSavedConnections: List<LanLibraryConnectionProfile>,
    initialFavoriteMediaIds: Set<String>,
) {
    var controller by mutableStateOf<Media3PlaybackController?>(null)
    var snapshot by mutableStateOf(PlaybackSnapshot())
    var playbackError by mutableStateOf<String?>(null)
    var serverUrl by mutableStateOf(
        initialSavedConnections.firstOrNull()?.baseUrl ?: "http://10.0.2.2:8686",
    )
    var pairingToken by mutableStateOf("")
    var savedConnections by mutableStateOf(initialSavedConnections)
    var catalog by mutableStateOf<LibraryCatalog?>(null)
    var playbackProgresses by mutableStateOf<List<PlaybackProgress>>(emptyList())
    var libraryError by mutableStateOf<String?>(null)
    var librarySearchText by mutableStateOf("")
    var librarySort by mutableStateOf(LibraryCatalogSort.TITLE)
    var librarySubtitleFilter by mutableStateOf(LibrarySubtitleFilter.ANY)
    var libraryFavoriteFilter by mutableStateOf(LibraryFavoriteFilter.ANY)
    var favoriteMediaIds by mutableStateOf(initialFavoriteMediaIds)
    var nowPlaying by mutableStateOf<LibraryMediaItem?>(null)
    var activePlaybackTarget by mutableStateOf<LanPlaybackTarget?>(null)
    var danmakuState by mutableStateOf(MobileDanmakuState.Idle)
    var playbackStartupPhase by mutableStateOf(MobilePlaybackStartupPhase.Idle)
    var selectedTab by mutableStateOf(MobileTab.Home)
    var isPlayerFullscreen by mutableStateOf(false)

    private val totalItems: List<LibraryMediaItem>
        get() = catalog?.items.orEmpty()

    private val filteredItems: List<LibraryMediaItem>
        get() = catalog
            ?.filteredItems(
                LibraryCatalogQuery(
                    searchText = librarySearchText,
                    sort = librarySort,
                    subtitleFilter = librarySubtitleFilter,
                    favoriteFilter = libraryFavoriteFilter,
                    favoriteMediaIds = favoriteMediaIds,
                ),
            )
            .orEmpty()

    private val posterEndpoint: LibraryPosterEndpoint?
        get() = catalog?.let {
            LibraryPosterEndpoint(serverUrl, pairingToken)
        }

    fun toUiState(): MobileAppUiState =
        MobileAppUiState(
            selectedTab = selectedTab,
            controller = controller,
            catalog = catalog,
            posterEndpoint = posterEndpoint,
            playbackProgresses = playbackProgresses,
            filteredItems = filteredItems,
            totalCount = totalItems.size,
            snapshot = snapshot,
            nowPlaying = nowPlaying,
            playbackError = playbackError,
            serverUrl = serverUrl,
            pairingToken = pairingToken,
            savedConnections = savedConnections,
            libraryError = libraryError,
            searchText = librarySearchText,
            sort = librarySort,
            subtitleFilter = librarySubtitleFilter,
            favoriteMediaIds = favoriteMediaIds,
            favoriteFilter = libraryFavoriteFilter,
            danmakuState = danmakuState,
            playbackStartupPhase = playbackStartupPhase,
            isPlayerFullscreen = isPlayerFullscreen,
        )
}
