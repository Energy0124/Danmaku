package app.danmaku.tv

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogQuery
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
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
                sort = query.sort,
                subtitleFilter = query.subtitleFilter,
                favoriteFilter = query.favoriteFilter,
                favoriteMediaIds = session.favoriteMediaIds,
            ),
        )
        val filteredCatalog = catalog.withItems(filteredItems)
        val libraryCatalog = catalog.withItems(
            catalog.filteredItems(
                LibraryCatalogQuery(
                    sort = query.sort,
                    subtitleFilter = query.subtitleFilter,
                    favoriteFilter = LibraryFavoriteFilter.ANY,
                    favoriteMediaIds = session.favoriteMediaIds,
                ),
            ),
        )
        val favoriteCatalog = catalog.withItems(
            catalog.items.filter { it.id in session.favoriteMediaIds },
        )
        val allSeries = catalog.groupedSeries()
        val seriesIdByMediaId = allSeries.flatMap { series ->
            series.seasons.flatMap { season -> season.items.map { it.id to series.id } }
        }.toMap()
        val allNextUp = catalog.nextUpItems(session.playbackProgresses, limit = HOME_RAIL_LIMIT)
        val allContinue = catalog.continueWatchingItems(
            session.playbackProgresses,
            limit = HOME_RAIL_LIMIT,
        )
        val hero = allContinue.firstOrNull()?.mediaItem ?: allNextUp.firstOrNull()?.mediaItem
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
            series = filteredCatalog.groupedSeries(),
            librarySeries = libraryCatalog.groupedSeries(),
            seriesById = allSeries.associateBy { it.id },
            seriesIdByMediaId = seriesIdByMediaId,
            favoriteSeries = favoriteCatalog.groupedSeries(),
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
