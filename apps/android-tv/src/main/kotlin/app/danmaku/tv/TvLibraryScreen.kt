package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogQuery
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.continueWatchingItems
import app.danmaku.domain.episodeDetail
import app.danmaku.domain.filteredItems
import app.danmaku.domain.groupedSeries
import app.danmaku.domain.nextUpItems
import app.danmaku.domain.recentlyWatchedItems
import app.danmaku.domain.seriesWatchSummaryById
import app.danmaku.domain.watchStatusByMediaId

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
    var searchText by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(LibraryCatalogSort.TITLE) }
    var subtitleFilter by remember { mutableStateOf(LibrarySubtitleFilter.ANY) }
    var favoriteFilter by remember(catalog, initialFavoriteFilter) { mutableStateOf(initialFavoriteFilter) }
    var selectedSeriesId by remember(catalog) { mutableStateOf<String?>(null) }
    var selectedEpisodeId by remember(catalog) { mutableStateOf<String?>(null) }
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
    val nextUpItems = catalog?.nextUpItems(playbackProgresses, limit = 6).orEmpty()
    val continueWatchingItems = catalog?.continueWatchingItems(playbackProgresses, limit = 6).orEmpty()
    val recentlyWatchedItems = catalog?.recentlyWatchedItems(playbackProgresses, limit = 6).orEmpty()
    val nextUpFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val watchStatusById = catalog?.watchStatusByMediaId(playbackProgresses).orEmpty()
    val seriesWatchSummaryById = catalog?.seriesWatchSummaryById(playbackProgresses).orEmpty()
    val selectedSeries = series.firstOrNull { it.id == selectedSeriesId }
    val selectedDetailId = selectedEpisodeId
        ?.takeIf { id -> filteredItems.any { it.id == id } }
        ?: filteredItems.firstOrNull()?.id
    val selectedEpisodeDetail = selectedDetailId
        ?.let { id -> catalog?.episodeDetail(id, playbackProgresses) }

    LaunchedEffect(nextUpItems.firstOrNull()?.mediaItem?.id, focusSearchOnStart) {
        if (!focusSearchOnStart && nextUpItems.isNotEmpty()) {
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
            filteredCount = filteredItems.size,
            totalCount = totalItems.size,
            favoriteCount = favoriteMediaIds.size,
            hasActiveFilters = searchText.isNotBlank() ||
                sort != LibraryCatalogSort.TITLE ||
                subtitleFilter != LibrarySubtitleFilter.ANY ||
                favoriteFilter != LibraryFavoriteFilter.ANY,
            searchActive = searchText.isNotBlank(),
            favoritesActive = favoriteFilter == LibraryFavoriteFilter.FAVORITES_ONLY,
            subtitlesActive = subtitleFilter == LibrarySubtitleFilter.WITH_SUBTITLES,
            onResetFilters = {
                searchText = ""
                sort = LibraryCatalogSort.TITLE
                subtitleFilter = LibrarySubtitleFilter.ANY
                favoriteFilter = LibraryFavoriteFilter.ANY
                selectedSeriesId = null
                selectedEpisodeId = null
            },
            onFocusSearch = { searchFocusRequester.requestFocus() },
            onToggleFavorites = {
                favoriteFilter = if (favoriteFilter == LibraryFavoriteFilter.ANY) {
                    LibraryFavoriteFilter.FAVORITES_ONLY
                } else {
                    LibraryFavoriteFilter.ANY
                }
            },
            onToggleSubtitles = {
                subtitleFilter = if (subtitleFilter == LibrarySubtitleFilter.ANY) {
                    LibrarySubtitleFilter.WITH_SUBTITLES
                } else {
                    LibrarySubtitleFilter.ANY
                }
            },
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.library_pc_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        catalog?.rootName ?: stringResource(R.string.library_connect_server),
                        color = TvMutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TvRailPill(
                    stringResource(R.string.library_episode_count, filteredItems.size, totalItems.size),
                    active = totalItems.isNotEmpty(),
                )
            }
            BasicTextField(
                value = searchText,
                onValueChange = { searchText = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(TvCardColor)
                    .focusRequester(searchFocusRequester)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .focusable()
                    .testTag("library-search-field"),
                decorationBox = { innerTextField ->
                    if (searchText.isBlank()) {
                        Text(stringResource(R.string.library_search_hint), color = TvMutedText)
                    }
                    innerTextField()
                },
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Button(
                        onClick = {
                            favoriteFilter = if (favoriteFilter == LibraryFavoriteFilter.ANY) {
                                LibraryFavoriteFilter.FAVORITES_ONLY
                            } else {
                                LibraryFavoriteFilter.ANY
                            }
                        },
                        modifier = Modifier.testTag("library-favorites-filter"),
                    ) {
                        Text(
                            if (favoriteFilter == LibraryFavoriteFilter.ANY) {
                                stringResource(R.string.nav_favorites)
                            } else {
                                stringResource(R.string.library_all_episodes)
                            },
                        )
                    }
                }
                item {
                    Button(
                        onClick = { sort = LibraryCatalogSort.TITLE },
                        enabled = sort != LibraryCatalogSort.TITLE,
                    ) {
                        Text(stringResource(R.string.library_sort_title))
                    }
                }
                item {
                    Button(
                        onClick = { sort = LibraryCatalogSort.PATH },
                        enabled = sort != LibraryCatalogSort.PATH,
                    ) {
                        Text(stringResource(R.string.library_sort_path))
                    }
                }
                item {
                    Button(
                        onClick = {
                            subtitleFilter = if (subtitleFilter == LibrarySubtitleFilter.ANY) {
                                LibrarySubtitleFilter.WITH_SUBTITLES
                            } else {
                                LibrarySubtitleFilter.ANY
                            }
                        },
                    ) {
                        Text(
                            if (subtitleFilter == LibrarySubtitleFilter.ANY) {
                                stringResource(R.string.subtitle_tracks_title)
                            } else {
                                stringResource(R.string.library_all)
                            },
                        )
                    }
                }
            }
            if (nextUpItems.isNotEmpty()) {
                TvNextUpRail(
                    items = nextUpItems,
                    posterEndpoint = posterEndpoint,
                    initialFocusRequester = nextUpFocusRequester,
                    onShowDetails = { selectedEpisodeId = it.id },
                    onPlay = onPlay,
                )
            }
            if (continueWatchingItems.isNotEmpty()) {
                TvProgressRail(
                    title = stringResource(R.string.home_continue_watching),
                    tag = "library-continue-watching",
                    itemTagPrefix = "continue-watching",
                    items = continueWatchingItems,
                    posterEndpoint = posterEndpoint,
                    onShowDetails = { selectedEpisodeId = it.id },
                    onPlay = onPlay,
                )
            }
            if (recentlyWatchedItems.isNotEmpty()) {
                TvProgressRail(
                    title = stringResource(R.string.home_recently_watched),
                    tag = "library-recently-watched",
                    itemTagPrefix = "recently-watched",
                    items = recentlyWatchedItems,
                    posterEndpoint = posterEndpoint,
                    onShowDetails = { selectedEpisodeId = it.id },
                    onPlay = onPlay,
                )
            }
            if (series.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    item(key = "all-series") {
                        Button(
                            onClick = {
                                selectedSeriesId = null
                                searchText = ""
                            },
                            enabled = searchText.isNotBlank() || selectedSeriesId != null,
                            modifier = Modifier
                                .tvFocusHalo(RoundedCornerShape(18.dp))
                                .testTag("series:all"),
                        ) {
                            Text(stringResource(R.string.library_all_series))
                        }
                    }
                    items(series, key = { it.id }) { summary ->
                        Button(
                            onClick = {
                                val alreadySelected = selectedSeriesId == summary.id
                                selectedSeriesId = if (alreadySelected) null else summary.id
                                searchText = if (alreadySelected) {
                                    ""
                                } else {
                                    summary.title
                                }
                            },
                            modifier = Modifier
                                .tvFocusHalo(RoundedCornerShape(22.dp))
                                .testTag("series:${summary.title}"),
                        ) {
                            Column {
                                TvPosterTile(
                                    item = summary.latestIndexedItem,
                                    title = summary.title,
                                    posterEndpoint = posterEndpoint,
                                    label = seriesWatchSummaryById[summary.id].shortProgressLabel(),
                                    modifier = Modifier
                                        .width(180.dp)
                                        .aspectRatio(0.75f),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    summary.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(summary.episodeLabel())
                                Text(seriesWatchSummaryById[summary.id].shortProgressLabel())
                            }
                        }
                    }
                }
            }
            selectedSeries?.let { summary ->
                TvSeriesDetail(
                    series = summary,
                    watchSummary = seriesWatchSummaryById[summary.id],
                    onPlay = onPlay,
                )
            }
            selectedEpisodeDetail?.let { detail ->
                TvEpisodeDetail(
                    detail = detail,
                    posterEndpoint = posterEndpoint,
                    isFavorite = detail.mediaItem.id in favoriteMediaIds,
                    onSetFavorite = { onSetFavorite(detail.mediaItem, it) },
                    onPlay = onPlay,
                    onSelectEpisode = { selectedEpisodeId = it.id },
                )
            }
            when {
                catalog == null || totalItems.isEmpty() -> {
                    TvLibraryEmptyPanel(
                        title = stringResource(R.string.library_no_pc_title),
                        body = stringResource(R.string.library_no_pc_body),
                    )
                }
                filteredItems.isEmpty() -> {
                    TvLibraryEmptyPanel(
                        title = stringResource(R.string.library_no_results_title),
                        body = stringResource(R.string.library_no_results_body),
                        actionLabel = stringResource(R.string.action_reset_filters),
                        onAction = {
                            searchText = ""
                            sort = LibraryCatalogSort.TITLE
                            subtitleFilter = LibrarySubtitleFilter.ANY
                            favoriteFilter = LibraryFavoriteFilter.ANY
                            selectedSeriesId = null
                            selectedEpisodeId = null
                        },
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filteredItems, key = LibraryMediaItem::id) { item ->
                            TvEpisodeButton(
                                item = item,
                                posterEndpoint = posterEndpoint,
                                watchStatus = watchStatusById[item.id],
                                isFavorite = item.id in favoriteMediaIds,
                                onSetFavorite = { onSetFavorite(item, it) },
                                onShowDetails = { selectedEpisodeId = item.id },
                                onPlay = { onPlay(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}
