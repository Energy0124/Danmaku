package app.danmaku.tv

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogQuery
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryEpisodeDetail
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibraryNextUpItem
import app.danmaku.domain.LibraryPlaybackProgressItem
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LibrarySeriesWatchSummary
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.LibraryWatchStatus
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.continueWatchingItems
import app.danmaku.domain.episodeDetail
import app.danmaku.domain.filteredItems
import app.danmaku.domain.groupedSeries
import app.danmaku.domain.nextUpItems
import app.danmaku.domain.recentlyWatchedItems
import app.danmaku.domain.seriesWatchSummaryById
import app.danmaku.domain.watchStatusByMediaId

internal data class TvLibraryViewState(
    val totalItems: List<LibraryMediaItem>,
    val filteredItems: List<LibraryMediaItem>,
    val series: List<LibrarySeries>,
    val nextUpItems: List<LibraryNextUpItem>,
    val continueWatchingItems: List<LibraryPlaybackProgressItem>,
    val recentlyWatchedItems: List<LibraryPlaybackProgressItem>,
    val watchStatusById: Map<String, LibraryWatchStatus>,
    val seriesWatchSummaryById: Map<String, LibrarySeriesWatchSummary>,
    val selectedSeries: LibrarySeries?,
    val selectedEpisodeDetail: LibraryEpisodeDetail?,
    val hasActiveFilters: Boolean,
)

internal fun buildTvLibraryViewState(
    catalog: LibraryCatalog?,
    playbackProgresses: List<PlaybackProgress>,
    favoriteMediaIds: Set<String>,
    searchText: String,
    sort: LibraryCatalogSort,
    subtitleFilter: LibrarySubtitleFilter,
    favoriteFilter: LibraryFavoriteFilter,
    selectedSeriesId: String?,
    selectedEpisodeId: String?,
): TvLibraryViewState {
    val totalItems = catalog?.items.orEmpty()
    val filteredItems = catalog
        ?.filteredItems(
            LibraryCatalogQuery(
                searchText = searchText,
                sort = sort,
                subtitleFilter = subtitleFilter,
                favoriteFilter = favoriteFilter,
                favoriteMediaIds = favoriteMediaIds,
            ),
        )
        .orEmpty()
    val series = catalog?.groupedSeries().orEmpty().take(10)
    val selectedDetailId = selectedEpisodeId
        ?.takeIf { id -> filteredItems.any { it.id == id } }
        ?: filteredItems.firstOrNull()?.id
    return TvLibraryViewState(
        totalItems = totalItems,
        filteredItems = filteredItems,
        series = series,
        nextUpItems = catalog?.nextUpItems(playbackProgresses, limit = 6).orEmpty(),
        continueWatchingItems = catalog?.continueWatchingItems(playbackProgresses, limit = 6).orEmpty(),
        recentlyWatchedItems = catalog?.recentlyWatchedItems(playbackProgresses, limit = 6).orEmpty(),
        watchStatusById = catalog?.watchStatusByMediaId(playbackProgresses).orEmpty(),
        seriesWatchSummaryById = catalog?.seriesWatchSummaryById(playbackProgresses).orEmpty(),
        selectedSeries = series.firstOrNull { it.id == selectedSeriesId },
        selectedEpisodeDetail = selectedDetailId?.let { id -> catalog?.episodeDetail(id, playbackProgresses) },
        hasActiveFilters = searchText.isNotBlank() ||
            sort != LibraryCatalogSort.TITLE ||
            subtitleFilter != LibrarySubtitleFilter.ANY ||
            favoriteFilter != LibraryFavoriteFilter.ANY,
    )
}
