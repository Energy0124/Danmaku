package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.PlaybackProgress

@Composable
internal fun LibraryItems(
    catalog: LibraryCatalog?,
    posterEndpoint: LibraryPosterEndpoint? = null,
    playbackProgresses: List<PlaybackProgress> = emptyList(),
    favoriteMediaIds: Set<String> = emptySet(),
    initialFavoriteFilter: LibraryFavoriteFilter = LibraryFavoriteFilter.ANY,
    focusSearchOnStart: Boolean = false,
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit = { _, _ -> },
    onPlay: (LibraryMediaItem) -> Unit,
) {
    val controls = rememberTvLibraryControlsState(catalog, initialFavoriteFilter)
    val libraryViewState = buildTvLibraryViewState(
        catalog = catalog,
        playbackProgresses = playbackProgresses,
        favoriteMediaIds = favoriteMediaIds,
        searchText = controls.searchText,
        sort = controls.sort,
        subtitleFilter = controls.subtitleFilter,
        favoriteFilter = controls.favoriteFilter,
        selectedSeriesId = controls.selectedSeriesId,
        selectedEpisodeId = controls.selectedEpisodeId,
    )
    val nextUpFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(libraryViewState.nextUpItems.firstOrNull()?.mediaItem?.id, focusSearchOnStart) {
        if (!focusSearchOnStart && libraryViewState.nextUpItems.isNotEmpty()) {
            nextUpFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(focusSearchOnStart, catalog?.indexedAtEpochMs) {
        if (focusSearchOnStart) {
            searchFocusRequester.requestFocus()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(TvPanelColor)
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        TvLibraryNavigationRail(
            catalog = catalog,
            filteredCount = libraryViewState.filteredItems.size,
            totalCount = libraryViewState.totalItems.size,
            favoriteCount = favoriteMediaIds.size,
            hasActiveFilters = libraryViewState.hasActiveFilters,
            searchActive = controls.searchText.isNotBlank(),
            favoritesActive = controls.favoriteFilter == LibraryFavoriteFilter.FAVORITES_ONLY,
            subtitlesActive = controls.subtitleFilter == LibrarySubtitleFilter.WITH_SUBTITLES,
            onResetFilters = controls::resetFilters,
            onFocusSearch = { searchFocusRequester.requestFocus() },
            onToggleFavorites = controls::toggleFavorites,
            onToggleSubtitles = controls::toggleSubtitles,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvLibraryHeader(
                catalog = catalog,
                filteredCount = libraryViewState.filteredItems.size,
                totalCount = libraryViewState.totalItems.size,
            )
            TvLibrarySearchField(
                searchText = controls.searchText,
                searchFocusRequester = searchFocusRequester,
                onSearchTextChange = { controls.searchText = it },
            )
            TvLibraryFilterBar(
                sort = controls.sort,
                favoriteFilter = controls.favoriteFilter,
                subtitleFilter = controls.subtitleFilter,
                onSelectSort = { controls.sort = it },
                onToggleFavorites = controls::toggleFavorites,
                onToggleSubtitles = controls::toggleSubtitles,
            )
            if (libraryViewState.nextUpItems.isNotEmpty()) {
                TvNextUpRail(
                    items = libraryViewState.nextUpItems,
                    posterEndpoint = posterEndpoint,
                    initialFocusRequester = nextUpFocusRequester,
                    onShowDetails = controls::selectEpisode,
                    onPlay = onPlay,
                )
            }
            if (libraryViewState.continueWatchingItems.isNotEmpty()) {
                TvProgressRail(
                    title = stringResource(R.string.home_continue_watching),
                    tag = "library-continue-watching",
                    itemTagPrefix = "continue-watching",
                    items = libraryViewState.continueWatchingItems,
                    posterEndpoint = posterEndpoint,
                    onShowDetails = controls::selectEpisode,
                    onPlay = onPlay,
                )
            }
            if (libraryViewState.recentlyWatchedItems.isNotEmpty()) {
                TvProgressRail(
                    title = stringResource(R.string.home_recently_watched),
                    tag = "library-recently-watched",
                    itemTagPrefix = "recently-watched",
                    items = libraryViewState.recentlyWatchedItems,
                    posterEndpoint = posterEndpoint,
                    onShowDetails = controls::selectEpisode,
                    onPlay = onPlay,
                )
            }
            if (libraryViewState.series.isNotEmpty()) {
                TvSeriesPickerRail(
                    series = libraryViewState.series,
                    canClearSelection = controls.searchText.isNotBlank() || controls.selectedSeriesId != null,
                    posterEndpoint = posterEndpoint,
                    seriesWatchSummaryById = libraryViewState.seriesWatchSummaryById,
                    onShowAllSeries = controls::showAllSeries,
                    onToggleSeries = controls::toggleSeries,
                )
            }
            libraryViewState.selectedSeries?.let { summary ->
                TvSeriesDetail(
                    series = summary,
                    watchSummary = libraryViewState.seriesWatchSummaryById[summary.id],
                    onPlay = onPlay,
                )
            }
            libraryViewState.selectedEpisodeDetail?.let { detail ->
                TvEpisodeDetail(
                    detail = detail,
                    posterEndpoint = posterEndpoint,
                    isFavorite = detail.mediaItem.id in favoriteMediaIds,
                    onSetFavorite = { onSetFavorite(detail.mediaItem, it) },
                    onPlay = onPlay,
                    onSelectEpisode = controls::selectEpisode,
                )
            }
            when {
                catalog == null || libraryViewState.totalItems.isEmpty() -> {
                    TvLibraryEmptyPanel(
                        title = stringResource(R.string.library_no_pc_title),
                        body = stringResource(R.string.library_no_pc_body),
                    )
                }
                libraryViewState.filteredItems.isEmpty() -> {
                    TvLibraryEmptyPanel(
                        title = stringResource(R.string.library_no_results_title),
                        body = stringResource(R.string.library_no_results_body),
                        actionLabel = stringResource(R.string.action_reset_filters),
                        onAction = controls::resetFilters,
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(libraryViewState.filteredItems, key = LibraryMediaItem::id) { item ->
                            TvEpisodeButton(
                                item = item,
                                posterEndpoint = posterEndpoint,
                                watchStatus = libraryViewState.watchStatusById[item.id],
                                isFavorite = item.id in favoriteMediaIds,
                                onSetFavorite = { onSetFavorite(item, it) },
                                onShowDetails = { controls.selectEpisode(item) },
                                onPlay = { onPlay(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}
