package app.danmaku.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem

@Composable
internal fun TvLibraryContentSections(
    catalog: LibraryCatalog?,
    libraryViewState: TvLibraryViewState,
    controls: TvLibraryControlsState,
    posterEndpoint: LibraryPosterEndpoint?,
    favoriteMediaIds: Set<String>,
    nextUpFocusRequester: FocusRequester,
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit,
    onPlay: (LibraryMediaItem) -> Unit,
) {
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
    libraryViewState.selectedSeries?.let { summary ->
        TvSeriesDetail(
            series = summary,
            watchSummary = libraryViewState.seriesWatchSummaryById[summary.id],
            onPlay = onPlay,
        )
    }
    TvLibraryEpisodeResults(
        catalog = catalog,
        libraryViewState = libraryViewState,
        controls = controls,
        posterEndpoint = posterEndpoint,
        favoriteMediaIds = favoriteMediaIds,
        onSetFavorite = onSetFavorite,
        onPlay = onPlay,
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
}

@Composable
private fun TvLibraryEpisodeResults(
    catalog: LibraryCatalog?,
    libraryViewState: TvLibraryViewState,
    controls: TvLibraryControlsState,
    posterEndpoint: LibraryPosterEndpoint?,
    favoriteMediaIds: Set<String>,
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit,
    onPlay: (LibraryMediaItem) -> Unit,
) {
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
            if (libraryViewState.filteredItems.size <= 6) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    libraryViewState.filteredItems.forEach { item ->
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
            } else {
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
