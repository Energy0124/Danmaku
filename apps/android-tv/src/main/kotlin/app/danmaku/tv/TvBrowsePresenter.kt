package app.danmaku.tv

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogQuery
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LibrarySeriesWatchSummary
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.LibraryWatchStatus
import app.danmaku.domain.LibraryWatchState
import app.danmaku.domain.continueWatchingItems
import app.danmaku.domain.filteredItems
import app.danmaku.domain.groupedSeries
import app.danmaku.domain.nextUpItems
import app.danmaku.domain.recentlyWatchedItems
import app.danmaku.domain.watchStatusByMediaId

internal class TvBrowsePresenter {
    // Query and progress changes can reuse the catalog-only grouping and lookup indexes.
    private var preparedCatalog: TvPreparedCatalog? = null

    fun present(
        session: TvSessionUiState,
        query: TvBrowseQuery,
    ): TvBrowseUiState {
        val catalog = session.catalog ?: return TvBrowseUiState(query = query)
        val prepared = preparedCatalog(catalog)
        val libraryItems = catalog.filteredItems(
            LibraryCatalogQuery(
                sort = query.sort.domainSort(),
                subtitleFilter = query.subtitleFilter,
                favoriteFilter = LibraryFavoriteFilter.ANY,
                favoriteMediaIds = session.favoriteMediaIds,
            ),
        ).filterByReleaseYear(query.releaseYear)
        val filteredItems = if (
            query.searchText.isBlank() &&
            query.favoriteFilter == LibraryFavoriteFilter.ANY
        ) {
            libraryItems
        } else {
            catalog.filteredItems(
                LibraryCatalogQuery(
                    searchText = query.searchText,
                    sort = query.sort.domainSort(),
                    subtitleFilter = query.subtitleFilter,
                    favoriteFilter = query.favoriteFilter,
                    favoriteMediaIds = session.favoriteMediaIds,
                ),
            ).filterByReleaseYear(query.releaseYear)
        }
        val favoriteCatalog = catalog.withItems(
            catalog.items.filter { it.id in session.favoriteMediaIds },
        )
        val progressByMediaId = session.playbackProgresses
            .groupBy { it.mediaId }
            .mapValues { (_, progresses) -> progresses.maxOf { it.updatedAtEpochMs } }
        val sortSeries: (List<LibrarySeries>) -> List<LibrarySeries> = { series ->
            series.sortedForTv(query.sort, progressByMediaId)
        }
        val librarySeries = if (
            query.releaseYear == null &&
            query.subtitleFilter == LibrarySubtitleFilter.ANY
        ) {
            sortSeries(prepared.series)
        } else {
            sortSeries(catalog.withItems(libraryItems).groupedSeries())
        }
        val visibleSeries = if (
            query.searchText.isBlank() &&
            query.favoriteFilter == LibraryFavoriteFilter.ANY
        ) {
            librarySeries
        } else {
            sortSeries(catalog.withItems(filteredItems).groupedSeries())
        }
        val allNextUp = catalog.nextUpItems(session.playbackProgresses, limit = HOME_RAIL_LIMIT)
        val allContinue = catalog.continueWatchingItems(
            session.playbackProgresses,
            limit = HOME_RAIL_LIMIT,
        )
        val resumeHero = allContinue.firstOrNull()?.mediaItem
        val hero = resumeHero ?: allNextUp.firstOrNull()?.mediaItem
        val continueWatching = allContinue.filterNot { it.mediaItem.id == hero?.id }
        val continueIds = continueWatching.mapTo(mutableSetOf()) { it.mediaItem.id }
        val nextUp = allNextUp.filterNot {
            it.mediaItem.id == hero?.id || it.mediaItem.id in continueIds
        }
        val usedMediaIds = buildSet {
            hero?.id?.let(::add)
            continueWatching.forEach { add(it.mediaItem.id) }
            nextUp.forEach { add(it.mediaItem.id) }
        }
        val watchStatusById = catalog.watchStatusByMediaId(session.playbackProgresses)
        return TvBrowseUiState(
            catalog = catalog,
            query = query,
            filteredItems = filteredItems,
            series = visibleSeries,
            librarySeries = librarySeries,
            availableReleaseYears = prepared.releaseYears,
            seriesById = prepared.seriesById,
            seriesIdByMediaId = prepared.seriesIdByMediaId,
            favoriteSeries = sortSeries(favoriteCatalog.groupedSeries()),
            nextUp = nextUp,
            continueWatching = continueWatching,
            recentlyWatched = catalog
                .recentlyWatchedItems(session.playbackProgresses, limit = HOME_RAIL_LIMIT)
                .filterNot { it.mediaItem.id in usedMediaIds },
            recentlyAdded = prepared.recentlyAdded
                .asSequence()
                .filterNot { it.id in usedMediaIds }
                .take(HOME_RAIL_LIMIT)
                .toList(),
            watchStatusById = watchStatusById,
            seriesWatchSummaryById = prepared.series.watchSummaries(watchStatusById),
            heroItem = hero,
            heroIsResume = resumeHero != null,
        )
    }

    private fun preparedCatalog(catalog: LibraryCatalog): TvPreparedCatalog {
        preparedCatalog?.takeIf { it.catalog === catalog }?.let { return it }
        val series = catalog.groupedSeries()
        return TvPreparedCatalog(
            catalog = catalog,
            series = series,
            releaseYears = catalog.releaseYears(),
            seriesById = series.associateBy(LibrarySeries::id),
            seriesIdByMediaId = buildMap(catalog.items.size) {
                series.forEach { itemSeries ->
                    itemSeries.seasons.forEach { season ->
                        season.items.forEach { item -> put(item.id, itemSeries.id) }
                    }
                }
            },
            recentlyAdded = catalog.items.sortedWith(
                compareByDescending<LibraryMediaItem> { it.indexedAtEpochMs }
                    .thenBy { it.seriesTitle }
                    .thenBy { it.episodeTitle },
            ),
        ).also { preparedCatalog = it }
    }

    private companion object {
        const val HOME_RAIL_LIMIT = 12
    }
}

private data class TvPreparedCatalog(
    val catalog: LibraryCatalog,
    val series: List<LibrarySeries>,
    val releaseYears: List<Int>,
    val seriesById: Map<String, LibrarySeries>,
    val seriesIdByMediaId: Map<String, String>,
    val recentlyAdded: List<LibraryMediaItem>,
)

private fun List<LibrarySeries>.watchSummaries(
    watchStatusById: Map<String, LibraryWatchStatus>,
): Map<String, LibrarySeriesWatchSummary> =
    associate { series ->
        var watchedCount = 0
        var inProgressCount = 0
        var newCount = 0
        series.seasons.forEach { season ->
            season.items.forEach { item ->
                when (watchStatusById.getValue(item.id).state) {
                    LibraryWatchState.WATCHED -> watchedCount += 1
                    LibraryWatchState.IN_PROGRESS -> inProgressCount += 1
                    LibraryWatchState.NEW -> newCount += 1
                }
            }
        }
        series.id to LibrarySeriesWatchSummary(
            seriesId = series.id,
            totalCount = series.episodeCount,
            watchedCount = watchedCount,
            inProgressCount = inProgressCount,
            newCount = newCount,
        )
    }

private fun LibraryCatalog.withItems(items: List<LibraryMediaItem>): LibraryCatalog =
    LibraryCatalog(
        rootName = rootName,
        indexedAtEpochMs = indexedAtEpochMs,
        items = items,
    )

private fun TvLibrarySort.domainSort(): LibraryCatalogSort =
    if (this == TvLibrarySort.PATH) LibraryCatalogSort.PATH else LibraryCatalogSort.TITLE

private fun List<LibraryMediaItem>.filterByReleaseYear(year: Int?): List<LibraryMediaItem> =
    if (year == null) this else filter { it.animeMetadata?.startYear == year }

private fun LibraryCatalog.releaseYears(): List<Int> =
    items
        .mapNotNull { it.animeMetadata?.startYear }
        .distinct()
        .sortedDescending()

private fun List<LibrarySeries>.sortedForTv(
    sort: TvLibrarySort,
    progressByMediaId: Map<String, Long>,
): List<LibrarySeries> {
    val titleComparator = compareBy<LibrarySeries>(
        { it.title.lowercase() },
        LibrarySeries::id,
    )
    val comparator = when (sort) {
        TvLibrarySort.TITLE -> titleComparator
        TvLibrarySort.PATH -> compareBy<LibrarySeries>(
            { it.items().minOf { item -> item.relativePath.lowercase() } },
            { it.title.lowercase() },
            LibrarySeries::id,
        )
        TvLibrarySort.NEWEST_ADDED -> compareByDescending<LibrarySeries> {
            it.items().maxOf(LibraryMediaItem::indexedAtEpochMs)
        }.then(titleComparator)
        TvLibrarySort.LAST_WATCHED -> compareByDescending<LibrarySeries> { series ->
            series.items()
                .mapNotNull { progressByMediaId[it.id] }
                .maxOrNull()
                ?: Long.MIN_VALUE
        }.then(titleComparator)
        TvLibrarySort.RELEASE_YEAR -> compareByDescending<LibrarySeries> { series ->
            series.items()
                .mapNotNull { it.animeMetadata?.startYear }
                .maxOrNull()
                ?: Int.MIN_VALUE
        }.then(titleComparator)
        TvLibrarySort.EPISODE_COUNT -> compareByDescending<LibrarySeries> {
            it.episodeCount
        }.then(titleComparator)
    }
    return sortedWith(comparator)
}

private fun LibrarySeries.items(): List<LibraryMediaItem> =
    seasons.flatMap { it.items }
