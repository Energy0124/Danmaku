package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibrarySeries

@Composable
internal fun TvLibraryGridScreen(
    route: TvRoute,
    navigation: TvNavigationState,
    navigator: TvNavigator,
    session: TvSessionUiState,
    browse: TvBrowseUiState,
    onOpenSeries: (String) -> Unit,
    onShowFilters: () -> Unit,
) {
    val series = browse.librarySeries
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen-library"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TvScreenHeader(
            title = stringResource(R.string.nav_library),
            subtitle = if (session.catalog == null) {
                stringResource(R.string.library_empty)
            } else {
                stringResource(R.string.library_series_count, series.size)
            },
            action = {
                Button(
                    onClick = onShowFilters,
                    modifier = Modifier
                        .tvRouteFocus(
                            navigation,
                            navigator,
                            route,
                            "library-filters",
                            isDefault = series.isEmpty(),
                        )
                        .tvFocusHalo(RoundedCornerShape(18.dp))
                        .testTag("library-filters"),
                    colors = tvButtonColors(),
                ) {
                    Text(stringResource(R.string.action_filters))
                }
            },
        )
        if (session.catalog == null) {
            TvEmptyState(
                title = stringResource(R.string.library_no_pc_title),
                body = stringResource(R.string.library_no_pc_body),
            )
        } else if (series.isEmpty()) {
            TvEmptyState(
                title = stringResource(R.string.library_no_results_title),
                body = stringResource(R.string.library_no_results_body),
            )
        } else {
            TvSeriesGrid(
                series = series,
                route = route,
                navigation = navigation,
                navigator = navigator,
                session = session,
                browse = browse,
                onOpenSeries = onOpenSeries,
            )
        }
    }
}

@Composable
internal fun TvSearchScreen(
    navigation: TvNavigationState,
    navigator: TvNavigator,
    session: TvSessionUiState,
    browse: TvBrowseUiState,
    onSearch: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen-search"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TvScreenHeader(
            title = stringResource(R.string.nav_search),
            subtitle = stringResource(R.string.search_body),
        )
        TvTextInput(
            value = browse.query.searchText,
            onValueChange = onSearch,
            placeholder = stringResource(R.string.library_search_hint),
            modifier = Modifier
                .fillMaxWidth()
                .tvRouteFocus(
                    navigation,
                    navigator,
                    TvRoute.Search,
                    "search-field",
                    isDefault = true,
                )
                .testTag("search-field"),
        )
        if (browse.query.searchText.isBlank()) {
            TvEmptyState(
                title = stringResource(R.string.search_start_title),
                body = stringResource(R.string.search_start_body),
            )
        } else if (browse.series.isEmpty()) {
            TvEmptyState(
                title = stringResource(R.string.library_no_results_title),
                body = stringResource(R.string.library_no_results_body),
            )
        } else {
            TvSeriesGrid(
                series = browse.series,
                route = TvRoute.Search,
                navigation = navigation,
                navigator = navigator,
                session = session,
                browse = browse,
                onOpenSeries = onOpenSeries,
            )
        }
    }
}

@Composable
internal fun TvFavoritesScreen(
    navigation: TvNavigationState,
    navigator: TvNavigator,
    session: TvSessionUiState,
    browse: TvBrowseUiState,
    onOpenSeries: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen-favorites"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TvScreenHeader(
            title = stringResource(R.string.nav_favorites),
            subtitle = stringResource(R.string.favorites_series_count, browse.favoriteSeries.size),
        )
        if (browse.favoriteSeries.isEmpty()) {
            TvEmptyState(
                title = stringResource(R.string.favorites_empty_title),
                body = stringResource(R.string.favorites_empty_body),
                modifier = Modifier
                    .tvRouteFocus(
                        navigation,
                        navigator,
                        TvRoute.Favorites,
                        "favorites-empty",
                        isDefault = true,
                    )
                    .focusable()
                    .testTag("favorites-empty"),
            )
        } else {
            TvSeriesGrid(
                series = browse.favoriteSeries,
                route = TvRoute.Favorites,
                navigation = navigation,
                navigator = navigator,
                session = session,
                browse = browse,
                onOpenSeries = onOpenSeries,
            )
        }
    }
}

@Composable
private fun TvSeriesGrid(
    series: List<LibrarySeries>,
    route: TvRoute,
    navigation: TvNavigationState,
    navigator: TvNavigator,
    session: TvSessionUiState,
    browse: TvBrowseUiState,
    onOpenSeries: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 184.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("series-grid"),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(series, key = { _, item -> item.id }) { index, item ->
            TvSeriesCard(
                series = item,
                endpoint = session.posterEndpoint,
                navigation = navigation,
                navigator = navigator,
                route = route,
                focusKey = "${route.defaultFocusKey()}:${item.id}",
                isDefault = index == 0,
                summary = browse.seriesWatchSummaryById[item.id],
                onClick = { onOpenSeries(item.id) },
            )
        }
    }
}

@Composable
internal fun TvLibraryFiltersOverlay(
    query: TvBrowseQuery,
    onSetSort: (LibraryCatalogSort) -> Unit,
    onToggleSubtitles: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(360.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .background(TvSurfaceRaised)
            .padding(24.dp)
            .testTag("library-filter-overlay"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.library_filters_title))
        Button(
            onClick = {
                onSetSort(
                    if (query.sort == LibraryCatalogSort.TITLE) {
                        LibraryCatalogSort.PATH
                    } else {
                        LibraryCatalogSort.TITLE
                    },
                )
            },
            colors = tvButtonColors(query.sort != LibraryCatalogSort.TITLE),
        ) {
            Text(
                if (query.sort == LibraryCatalogSort.TITLE) {
                    stringResource(R.string.library_sort_title)
                } else {
                    stringResource(R.string.library_sort_path)
                },
            )
        }
        Button(
            onClick = onToggleSubtitles,
            colors = tvButtonColors(
                query.subtitleFilter == app.danmaku.domain.LibrarySubtitleFilter.WITH_SUBTITLES,
            ),
        ) {
            Text(stringResource(R.string.library_subtitles_only))
        }
        Button(onClick = onReset, colors = tvButtonColors()) {
            Text(stringResource(R.string.action_reset_filters))
        }
        Button(onClick = onClose, colors = tvButtonColors(selected = true)) {
            Text(stringResource(R.string.action_close))
        }
    }
}
