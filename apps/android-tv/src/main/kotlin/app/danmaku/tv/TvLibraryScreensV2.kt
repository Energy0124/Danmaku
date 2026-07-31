package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
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
    onOpenFolders: () -> Unit,
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onOpenFolders,
                        enabled = session.catalog != null,
                        modifier = Modifier
                            .tvRouteFocus(
                                navigation,
                                navigator,
                                route,
                                "library-folders",
                                isDefault = series.isEmpty() && session.catalog != null,
                            )
                            .tvFocusHalo(RoundedCornerShape(18.dp))
                            .testTag("library-folders"),
                        colors = tvButtonColors(),
                    ) {
                        Text(stringResource(R.string.action_folders))
                    }
                    Button(
                        onClick = onShowFilters,
                        modifier = Modifier
                            .tvRouteFocus(
                                navigation,
                                navigator,
                                route,
                                "library-filters",
                                isDefault = series.isEmpty() && session.catalog == null,
                            )
                            .tvFocusHalo(RoundedCornerShape(18.dp))
                            .testTag("library-filters"),
                        colors = tvButtonColors(),
                    ) {
                        Text(stringResource(R.string.action_filters))
                    }
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
internal fun TvFolderBrowserScreen(
    route: TvRoute.FolderBrowser,
    navigation: TvNavigationState,
    navigator: TvNavigator,
    browse: TvBrowseUiState,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val catalog = browse.catalog
    val listing = remember(catalog, route.path) {
        catalog?.folderListing(route.path) ?: TvFolderListing()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen-folder-browser"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TvScreenHeader(
            title = catalog?.folderHeading(route.path)
                ?: stringResource(R.string.library_folders_title),
            subtitle = stringResource(
                R.string.library_folder_summary,
                listing.folders.size,
                listing.files.size,
            ),
            action = if (route.path.isNotEmpty()) {
                {
                    Button(
                        onClick = onNavigateUp,
                        modifier = Modifier
                            .tvRouteFocus(
                                navigation,
                                navigator,
                                route,
                                "folder-up",
                                isDefault = listing.folders.isEmpty() && listing.files.isEmpty(),
                            )
                            .tvFocusHalo(RoundedCornerShape(18.dp))
                            .testTag("folder-up"),
                        colors = tvButtonColors(),
                    ) {
                        Text(stringResource(R.string.action_up))
                    }
                }
            } else {
                null
            },
        )
        if (catalog == null) {
            TvEmptyState(
                title = stringResource(R.string.library_no_pc_title),
                body = stringResource(R.string.library_no_pc_body),
                modifier = Modifier
                    .tvRouteFocus(
                        navigation,
                        navigator,
                        route,
                        "folder-empty",
                        isDefault = true,
                    )
                    .focusable()
                    .testTag("folder-empty"),
            )
        } else if (listing.folders.isEmpty() && listing.files.isEmpty()) {
            TvEmptyState(
                title = stringResource(R.string.library_folder_empty_title),
                body = stringResource(R.string.library_folder_empty_body),
                modifier = Modifier
                    .tvRouteFocus(
                        navigation,
                        navigator,
                        route,
                        "folder-empty",
                        isDefault = route.path.isEmpty(),
                    )
                    .focusable()
                    .testTag("folder-empty"),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("folder-list"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = listing.folders,
                    key = { "folder:${it.name}" },
                ) { folder ->
                    val isFirst = folder == listing.folders.firstOrNull()
                    TvFolderRow(
                        title = folder.name,
                        subtitle = stringResource(R.string.library_folder_item_count, folder.itemCount),
                        route = route,
                        navigation = navigation,
                        navigator = navigator,
                        focusKey = "folder:${folder.name}",
                        isDefault = isFirst,
                        testTag = "folder-entry:${folder.name}",
                        onClick = { onOpenFolder(folder.name) },
                    )
                }
                items(
                    items = listing.files,
                    key = { "file:${it.id}" },
                ) { item ->
                    TvFolderRow(
                        title = item.fileName(),
                        subtitle = "${item.displaySeriesTitle()} · ${item.episodeTitle}",
                        route = route,
                        navigation = navigation,
                        navigator = navigator,
                        focusKey = "file:${item.id}",
                        isDefault = listing.folders.isEmpty() && item == listing.files.firstOrNull(),
                        testTag = "folder-file:${item.id}",
                        onClick = { onOpenFile(item.id) },
                    )
                }
            }
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
private fun TvFolderRow(
    title: String,
    subtitle: String,
    route: TvRoute.FolderBrowser,
    navigation: TvNavigationState,
    navigator: TvNavigator,
    focusKey: String,
    isDefault: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .tvRouteFocus(
                navigation,
                navigator,
                route,
                focusKey,
                isDefault = isDefault,
            )
            .tvFocusHalo(RoundedCornerShape(18.dp))
            .testTag(testTag),
        colors = tvButtonColors(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                color = TvSecondaryContent,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun TvLibraryFiltersOverlay(
    query: TvBrowseQuery,
    availableReleaseYears: List<Int>,
    onSetSort: (TvLibrarySort) -> Unit,
    onSetReleaseYear: (Int?) -> Unit,
    onToggleSubtitles: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    val firstFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        firstFocusRequester.requestFocus()
    }
    Column(
        modifier = Modifier
            .width(360.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .background(TvSurfaceRaised)
            .padding(24.dp)
            .focusGroup()
            .testTag("library-filter-overlay"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.library_filters_title))
        Button(
            onClick = { onSetSort(query.sort.next()) },
            colors = tvButtonColors(query.sort != TvLibrarySort.TITLE),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(firstFocusRequester)
                .testTag("library-filter-sort"),
        ) {
            Text(stringResource(query.sort.labelResource()))
        }
        Button(
            onClick = {
                val options = listOf<Int?>(null) + availableReleaseYears
                val currentIndex = options.indexOf(query.releaseYear).coerceAtLeast(0)
                onSetReleaseYear(options[(currentIndex + 1) % options.size])
            },
            enabled = availableReleaseYears.isNotEmpty(),
            colors = tvButtonColors(query.releaseYear != null),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library-filter-season"),
        ) {
            Text(
                query.releaseYear?.let {
                    stringResource(R.string.library_season_year, it)
                } ?: stringResource(R.string.library_season_all),
            )
        }
        Button(
            onClick = onToggleSubtitles,
            colors = tvButtonColors(
                query.subtitleFilter == app.danmaku.domain.LibrarySubtitleFilter.WITH_SUBTITLES,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library-filter-subtitles"),
        ) {
            Text(stringResource(R.string.library_subtitles_only))
        }
        Button(
            onClick = onReset,
            colors = tvButtonColors(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library-filter-reset"),
        ) {
            Text(stringResource(R.string.action_reset_filters))
        }
        Button(
            onClick = onClose,
            colors = tvButtonColors(selected = true),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library-filter-close"),
        ) {
            Text(stringResource(R.string.action_close))
        }
    }
}

private fun TvLibrarySort.labelResource(): Int =
    when (this) {
        TvLibrarySort.TITLE -> R.string.library_sort_title
        TvLibrarySort.PATH -> R.string.library_sort_path
        TvLibrarySort.NEWEST_ADDED -> R.string.library_sort_newest
        TvLibrarySort.LAST_WATCHED -> R.string.library_sort_last_watched
        TvLibrarySort.RELEASE_YEAR -> R.string.library_sort_release_year
        TvLibrarySort.EPISODE_COUNT -> R.string.library_sort_episode_count
    }
