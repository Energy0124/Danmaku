package app.danmaku.tv

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibraryNextUpItem
import app.danmaku.domain.LibraryPlaybackProgressItem
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LibrarySeriesWatchSummary
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.LibraryWatchStatus
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.library.LanPlaybackTarget

internal enum class TvCatalogSource {
    None,
    Cache,
    Network,
}

internal data class TvSessionUiState(
    val savedConnections: List<LanLibraryConnectionProfile> = emptyList(),
    val serverUrl: String = "",
    val pairingToken: String = "",
    val catalog: LibraryCatalog? = null,
    val playbackProgresses: List<PlaybackProgress> = emptyList(),
    val favoriteMediaIds: Set<String> = emptySet(),
    val catalogSource: TvCatalogSource = TvCatalogSource.None,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
) {
    val hasConnection: Boolean
        get() = serverUrl.isNotBlank()

    val posterEndpoint: LibraryPosterEndpoint?
        get() = catalog?.let { LibraryPosterEndpoint(serverUrl, pairingToken) }
}

internal data class TvBrowseQuery(
    val searchText: String = "",
    val sort: LibraryCatalogSort = LibraryCatalogSort.TITLE,
    val subtitleFilter: LibrarySubtitleFilter = LibrarySubtitleFilter.ANY,
    val favoriteFilter: LibraryFavoriteFilter = LibraryFavoriteFilter.ANY,
)

internal data class TvBrowseUiState(
    val catalog: LibraryCatalog? = null,
    val query: TvBrowseQuery = TvBrowseQuery(),
    val filteredItems: List<LibraryMediaItem> = emptyList(),
    val series: List<LibrarySeries> = emptyList(),
    val librarySeries: List<LibrarySeries> = emptyList(),
    val seriesById: Map<String, LibrarySeries> = emptyMap(),
    val seriesIdByMediaId: Map<String, String> = emptyMap(),
    val favoriteSeries: List<LibrarySeries> = emptyList(),
    val nextUp: List<LibraryNextUpItem> = emptyList(),
    val continueWatching: List<LibraryPlaybackProgressItem> = emptyList(),
    val recentlyWatched: List<LibraryPlaybackProgressItem> = emptyList(),
    val recentlyAdded: List<LibraryMediaItem> = emptyList(),
    val watchStatusById: Map<String, LibraryWatchStatus> = emptyMap(),
    val seriesWatchSummaryById: Map<String, LibrarySeriesWatchSummary> = emptyMap(),
    val heroItem: LibraryMediaItem? = null,
) {
    val isEmpty: Boolean
        get() = catalog != null && filteredItems.isEmpty()
}

internal data class TvDanmakuPreferences(
    val enabled: Boolean = true,
    val opacity: Float = 0.9f,
    val fontScale: Float = 1f,
    val speed: Float = 1f,
    val maxScreenArea: Float = 0.5f,
) {
    init {
        require(opacity in 0.2f..1f)
        require(fontScale in 0.75f..1.5f)
        require(speed in 0.5f..2f)
        require(maxScreenArea in 0.2f..0.8f)
    }
}

internal enum class TvPlaybackError {
    ControllerConnecting,
    ResumeLookupFailed,
    PreparationFailed,
}

internal data class TvPlaybackUiState(
    val controllerReady: Boolean = false,
    val snapshot: PlaybackSnapshot = PlaybackSnapshot(),
    val item: LibraryMediaItem? = null,
    val target: LanPlaybackTarget? = null,
    val startupPhase: TvPlaybackStartupPhase = TvPlaybackStartupPhase.Idle,
    val controlsVisible: Boolean = true,
    val error: TvPlaybackError? = null,
    val danmaku: TvDanmakuState = TvDanmakuState.Idle,
    val danmakuPreferences: TvDanmakuPreferences = TvDanmakuPreferences(),
    val nextItem: LibraryMediaItem? = null,
) {
    val isActive: Boolean
        get() = item != null && target != null
}
