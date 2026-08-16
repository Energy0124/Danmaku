package app.danmaku.tv

import app.danmaku.domain.DanmakuMode
import app.danmaku.domain.LibraryCatalog
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
import app.danmaku.library.android.ExternalTrackingDocument
import app.danmaku.library.android.ExternalTrackingOperationResponse
import app.danmaku.library.android.ProviderAccountsDocument
import kotlin.math.roundToInt

internal enum class TvTrackingOperation { READBACK, SYNC }
internal enum class TvTrackingError { ACCESS_CODE_REJECTED, PREVIEW_CHANGED, REQUEST_FAILED }

internal data class TvTrackingState(
    val accounts: ProviderAccountsDocument? = null,
    val document: ExternalTrackingDocument? = null,
    val isBusy: Boolean = false,
    val hasFreshReadback: Boolean = false,
    val error: TvTrackingError? = null,
    val errorDetail: String? = null,
    val lastOperation: TvTrackingOperation? = null,
    val lastResponse: ExternalTrackingOperationResponse? = null,
)

internal enum class TvCatalogSource {
    None,
    Cache,
    Network,
}

internal enum class TvLibrarySort {
    TITLE,
    PATH,
    NEWEST_ADDED,
    LAST_WATCHED,
    RELEASE_YEAR,
    EPISODE_COUNT,
    ;

    fun next(): TvLibrarySort {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
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
    val tracking: TvTrackingState = TvTrackingState(),
) {
    val hasConnection: Boolean
        get() = serverUrl.isNotBlank()

    val posterEndpoint: LibraryPosterEndpoint?
        get() = catalog?.let { LibraryPosterEndpoint(serverUrl, pairingToken) }
}

internal data class TvBrowseQuery(
    val searchText: String = "",
    val sort: TvLibrarySort = TvLibrarySort.TITLE,
    val releaseYear: Int? = null,
    val subtitleFilter: LibrarySubtitleFilter = LibrarySubtitleFilter.ANY,
    val favoriteFilter: LibraryFavoriteFilter = LibraryFavoriteFilter.ANY,
)

internal data class TvBrowseUiState(
    val catalog: LibraryCatalog? = null,
    val query: TvBrowseQuery = TvBrowseQuery(),
    val filteredItems: List<LibraryMediaItem> = emptyList(),
    val series: List<LibrarySeries> = emptyList(),
    val librarySeries: List<LibrarySeries> = emptyList(),
    val availableReleaseYears: List<Int> = emptyList(),
    val seriesById: Map<String, LibrarySeries> = emptyMap(),
    val seriesIdByMediaId: Map<String, String> = emptyMap(),
    val favoriteSeries: List<LibrarySeries> = emptyList(),
    val nextUp: List<LibraryNextUpItem> = emptyList(),
    val continueWatching: List<LibraryPlaybackProgressItem> = emptyList(),
    val recentlyWatched: List<LibraryPlaybackProgressItem> = emptyList(),
    val recentlyAdded: List<LibraryMediaItem> = emptyList(),
    val watchStatusById: Map<String, LibraryWatchStatus> = emptyMap(),
    val seriesWatchSummaryById: Map<String, LibrarySeriesWatchSummary> = emptyMap(),
    val heroIsResume: Boolean = false,
    val heroItem: LibraryMediaItem? = null,
) {
    val isEmpty: Boolean
        get() = catalog != null && filteredItems.isEmpty()
}

internal data class TvDanmakuPreferences(
    val enabled: Boolean = true,
    val showScrolling: Boolean = true,
    val showTop: Boolean = true,
    val showBottom: Boolean = true,
    val opacity: Float = 0.9f,
    val fontScale: Float = 1f,
    val speed: Float = 1f,
    val maxScreenArea: Float = 0.5f,
) {
    init {
        require(opacity in 0.2f..1f)
        require(fontScale in 0.1f..1.5f)
        require(speed in 0.5f..2f)
        require(maxScreenArea in 0.2f..0.8f)
    }

    fun shows(mode: DanmakuMode): Boolean = when (mode) {
        DanmakuMode.SCROLLING -> showScrolling
        DanmakuMode.TOP -> showTop
        DanmakuMode.BOTTOM -> showBottom
    }
}

internal fun adjustSteppedValue(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    increase: Boolean,
): Float {
    require(step > 0f)
    val currentStep = ((value - range.start) / step).roundToInt()
    val nextStep = currentStep + if (increase) 1 else -1
    return (range.start + nextStep * step).coerceIn(range)
}

internal enum class TvPlaybackError {
    ControllerConnecting,
    ResumeLookupFailed,
    PreparationFailed,
}

internal data class TvPlaybackUiState(
    val controllerReady: Boolean = false,
    val snapshot: PlaybackSnapshot = PlaybackSnapshot(),
    val discontinuityGeneration: Long = 0,
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
