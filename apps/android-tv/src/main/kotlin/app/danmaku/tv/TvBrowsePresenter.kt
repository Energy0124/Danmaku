package app.danmaku.tv

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogQuery
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.continueWatchingItems
import app.danmaku.domain.filteredItems
import app.danmaku.domain.groupedSeries
import app.danmaku.domain.nextUpItems
import app.danmaku.domain.recentlyWatchedItems
import app.danmaku.domain.seriesWatchSummaryById
import app.danmaku.domain.watchStatusByMediaId

internal class TvBrowsePresenter {
    fun present(
        session: TvSessionUiState,
        query: TvBrowseQuery,
    ): TvBrowseUiState {
        val catalog = session.catalog ?: return TvBrowseUiState(query = query)
        val filteredItems = catalog.filteredItems(
            LibraryCatalogQuery(
                searchText = query.searchText,
                sort = query.sort.domainSort(),
                subtitleFilter = query.subtitleFilter,
                favoriteFilter = query.favoriteFilter,
                favoriteMediaIds = session.favoriteMediaIds,
            ),
        ).filterByReleaseYear(query.releaseYear)
        val filteredCatalog = catalog.withItems(filteredItems)
        val libraryCatalog = catalog.withItems(
            catalog.filteredItems(
                LibraryCatalogQuery(
                    sort = query.sort.domainSort(),
                    subtitleFilter = query.subtitleFilter,
                    favoriteFilter = LibraryFavoriteFilter.ANY,
                    favoriteMediaIds = session.favoriteMediaIds,
                ),
            ).filterByReleaseYear(query.releaseYear),
        )
        val favoriteCatalog = catalog.withItems(
            catalog.items.filter { it.id in session.favoriteMediaIds },
        )
        val allSeries = catalog.groupedSeries()
        val progressByMediaId = session.playbackProgresses
            .groupBy { it.mediaId }
            .mapValues { (_, progresses) -> progresses.maxOf { it.updatedAtEpochMs } }
        val seriesIdByMediaId = allSeries.flatMap { series ->
            series.seasons.flatMap { season -> season.items.map { it.id to series.id } }
        }.toMap()
        val sortSeries: (List<LibrarySeries>) -> List<LibrarySeries> = { series ->
            series.sortedForTv(query.sort, progressByMediaId)
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
        return TvBrowseUiState(
            catalog = catalog,
            query = query,
            filteredItems = filteredItems,
            series = sortSeries(filteredCatalog.groupedSeries()),
            librarySeries = sortSeries(libraryCatalog.groupedSeries()),
            availableReleaseYears = catalog.releaseYears(),
            seriesById = allSeries.associateBy { it.id },
            seriesIdByMediaId = seriesIdByMediaId,
            favoriteSeries = sortSeries(favoriteCatalog.groupedSeries()),
            nextUp = nextUp,
            continueWatching = continueWatching,
            recentlyWatched = catalog
                .recentlyWatchedItems(session.playbackProgresses, limit = HOME_RAIL_LIMIT)
                .filterNot { it.mediaItem.id in usedMediaIds },
            recentlyAdded = catalog.items
                .asSequence()
                .filterNot { it.id in usedMediaIds }
                .sortedWith(
                    compareByDescending<LibraryMediaItem> { it.indexedAtEpochMs }
                        .thenBy { it.seriesTitle }
                        .thenBy { it.episodeTitle },
                )
                .take(HOME_RAIL_LIMIT)
                .toList(),
            watchStatusById = catalog.watchStatusByMediaId(session.playbackProgresses),
            seriesWatchSummaryById = catalog.seriesWatchSummaryById(session.playbackProgresses),
            heroItem = hero,
            heroIsResume = resumeHero != null,
        )
    }

    private companion object {
        const val HOME_RAIL_LIMIT = 12
    }
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
