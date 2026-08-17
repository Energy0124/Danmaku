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
import app.danmaku.domain.LibraryFolderListing
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.fileName
import app.danmaku.domain.folderHeading
import app.danmaku.domain.folderListing

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
                    scale = tvButtonScale(),
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
internal fun TvFolderBrowserScreen(
    route: TvRoute.FolderBrowser,
    navigation: TvNavigationState,
    navigator: TvNavigator,
    session: TvSessionUiState,
    browse: TvBrowseUiState,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
) {
    val catalog = browse.catalog
    val listing = remember(catalog, route.path) {
        catalog?.folderListing(route.path) ?: LibraryFolderListing()
    }
    val validFocusKeys = remember(catalog, listing, route.path) {
        buildSet {
            if (route.path.isNotEmpty()) add("folder-up")
            if (catalog != null) add("folder-refresh")
            if (catalog == null || (listing.folders.isEmpty() && listing.files.isEmpty())) {
                add("folder-empty")
            }
            listing.folders.forEach { add("folder:${it.name}") }
            listing.files.forEach { add("file:${it.id}") }
        }
    }
    val fallbackToDefault = navigator.savedFocus(route) !in validFocusKeys
    val refreshErrorText = session.folderRefresh.error?.let {
        stringResource(
            when (it) {
                TvFolderRefreshError.ALREADY_RUNNING ->
                    R.string.library_folder_refresh_already_running
                TvFolderRefreshError.SCAN_FAILED ->
                    R.string.library_folder_refresh_scan_failed
                TvFolderRefreshError.REQUEST_FAILED ->
                    R.string.library_folder_refresh_request_failed
            },
        )
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
            action = if (route.path.isNotEmpty() || catalog != null) {
                {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (route.path.isNotEmpty()) {
                            Button(
                                onClick = onNavigateUp,
                                modifier = Modifier
                                    .tvRouteFocus(
                                        navigation,
                                        navigator,
                                        route,
                                        "folder-up",
                                        isDefault = listing.folders.isEmpty() && listing.files.isEmpty(),
                                        fallbackToDefault = fallbackToDefault,
                                    )
                                    .tvFocusHalo(RoundedCornerShape(18.dp))
                                    .testTag("folder-up"),
                                colors = tvButtonColors(),
                                scale = tvButtonScale(),
                            ) {
                                Text(stringResource(R.string.action_up))
                            }
                        }
                        if (catalog != null) {
                            Button(
                                onClick = {
                                    if (!session.folderRefresh.isBusy) onRefresh()
                                },
                                modifier = Modifier
                                    .tvRouteFocus(
                                        navigation,
                                        navigator,
                                        route,
                                        "folder-refresh",
                                        isDefault = false,
                                        fallbackToDefault = fallbackToDefault,
                                    )
                                    .tvFocusHalo(RoundedCornerShape(18.dp))
                                    .testTag("folder-refresh"),
                                colors = tvButtonColors(),
                                scale = tvButtonScale(),
                            ) {
                                Text(
                                    if (session.folderRefresh.isBusy) {
                                        session.folderRefresh.filesSeen?.let { files ->
                                            stringResource(R.string.library_folder_refresh_progress, files)
                                        } ?: stringResource(R.string.library_folder_refresh_scanning)
                                    } else {
                                        stringResource(R.string.library_folder_refresh_action)
                                    },
                                )
                            }
                        }
                    }
                }
            } else {
                null
            },
        )
        refreshErrorText?.let { error ->
            Text(
                text = buildString {
                    append(error)
                    session.folderRefresh.errorDetail?.takeIf(String::isNotBlank)?.let {
                        append(" ")
                        append(it)
                    }
                },
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("folder-refresh-error"),
            )
        }
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
                        isDefault = route.path.isEmpty(),
                        fallbackToDefault = fallbackToDefault,
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
                        fallbackToDefault = fallbackToDefault,
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
                        fallbackToDefault = fallbackToDefault,
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
                        fallbackToDefault = fallbackToDefault,
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
    fallbackToDefault: Boolean,
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
                fallbackToDefault = fallbackToDefault,
            )
            .tvFocusHalo(RoundedCornerShape(18.dp))
            .testTag(testTag),
        colors = tvButtonColors(),
        scale = tvButtonScale(),
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
            scale = tvButtonScale(),
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
            scale = tvButtonScale(),
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
            scale = tvButtonScale(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library-filter-subtitles"),
        ) {
            Text(stringResource(R.string.library_subtitles_only))
        }
        Button(
            onClick = onReset,
            colors = tvButtonColors(),
            scale = tvButtonScale(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library-filter-reset"),
        ) {
            Text(stringResource(R.string.action_reset_filters))
        }
        Button(
            onClick = onClose,
            colors = tvButtonColors(selected = true),
            scale = tvButtonScale(),
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
