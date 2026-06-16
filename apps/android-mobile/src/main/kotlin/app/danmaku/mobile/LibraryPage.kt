package app.danmaku.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.LibraryWatchStatus
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.continueWatchingItems
import app.danmaku.domain.episodeDetail
import app.danmaku.domain.groupedSeries
import app.danmaku.domain.nextUpItems
import app.danmaku.domain.recentlyWatchedItems
import app.danmaku.domain.seriesWatchSummaryById
import app.danmaku.domain.watchStatusByMediaId

@Composable
internal fun LibraryPage(
    contentPadding: PaddingValues,
    catalog: LibraryCatalog?,
    posterEndpoint: LibraryPosterEndpoint? = null,
    playbackProgresses: List<PlaybackProgress>,
    filteredItems: List<LibraryMediaItem>,
    totalCount: Int,
    snapshot: PlaybackSnapshot,
    nowPlaying: LibraryMediaItem?,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    sort: LibraryCatalogSort,
    onSortChange: (LibraryCatalogSort) -> Unit,
    subtitleFilter: LibrarySubtitleFilter,
    onSubtitleFilterChange: (LibrarySubtitleFilter) -> Unit,
    favoriteMediaIds: Set<String> = emptySet(),
    favoriteFilter: LibraryFavoriteFilter = LibraryFavoriteFilter.ANY,
    onFavoriteFilterChange: (LibraryFavoriteFilter) -> Unit = {},
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit = { _, _ -> },
    onPlay: (LibraryMediaItem) -> Unit,
    onPlayPause: () -> Unit,
    onOpenPlayer: () -> Unit,
    onConnect: () -> Unit,
) {
    var selectedEpisodeId by remember(catalog) { mutableStateOf<String?>(null) }
    val series = catalog?.groupedSeries().orEmpty()
    val nextUpItems = catalog?.nextUpItems(playbackProgresses, limit = 5).orEmpty()
    val continueWatchingItems = catalog?.continueWatchingItems(playbackProgresses, limit = 5).orEmpty()
    val recentlyWatchedItems = catalog?.recentlyWatchedItems(playbackProgresses, limit = 5).orEmpty()
    val watchStatusById = catalog?.watchStatusByMediaId(playbackProgresses).orEmpty()
    val seriesWatchSummaryById = catalog?.seriesWatchSummaryById(playbackProgresses).orEmpty()
    val selectedSeries = searchText
        .takeIf(String::isNotBlank)
        ?.let { selectedTitle ->
            series.firstOrNull { it.title.equals(selectedTitle.trim(), ignoreCase = true) }
        }
    val selectedDetailId = selectedEpisodeId
        ?.takeIf { id -> filteredItems.any { it.id == id } }
        ?: nowPlaying?.id?.takeIf { id -> filteredItems.any { it.id == id } }
        ?: filteredItems.firstOrNull()?.id
    val selectedEpisodeDetail = selectedDetailId
        ?.let { id -> catalog?.episodeDetail(id, playbackProgresses) }

    fun LazyListScope.libraryFeedItems(showInlineEpisodeDetail: Boolean) {
        item(key = "library-page-header") {
            PageHeader(
                icon = Icons.Filled.VideoLibrary,
                title = stringResource(R.string.library_title),
                subtitle = if (catalog == null) {
                    stringResource(R.string.library_connect_browse_subtitle)
                } else {
                    stringResource(R.string.library_episode_count, filteredItems.size, totalCount)
                },
            )
        }
        if (snapshot.source != null) {
            item(key = "mini-player") {
                MiniPlayerBar(
                    snapshot = snapshot,
                    nowPlaying = nowPlaying,
                    onPlayPause = onPlayPause,
                    onOpenPlayer = onOpenPlayer,
                )
            }
        }
        item(key = "library-header") {
            LibraryHeader(
                catalog = catalog,
                filteredCount = filteredItems.size,
                totalCount = totalCount,
                searchText = searchText,
                onSearchTextChange = onSearchTextChange,
                sort = sort,
                onSortChange = onSortChange,
                subtitleFilter = subtitleFilter,
                onSubtitleFilterChange = onSubtitleFilterChange,
                favoriteMediaIds = favoriteMediaIds,
                favoriteFilter = favoriteFilter,
                onFavoriteFilterChange = onFavoriteFilterChange,
                onConnect = onConnect,
            )
        }
        catalog?.takeIf { it.items.isNotEmpty() }?.let {
            if (nextUpItems.isNotEmpty()) {
                item(key = "library-next-up") {
                    NextUpPanel(
                        items = nextUpItems,
                        posterEndpoint = posterEndpoint,
                        onShowDetails = { selectedEpisodeId = it.id },
                        onPlay = onPlay,
                    )
                }
            }
            if (continueWatchingItems.isNotEmpty()) {
                item(key = "library-continue-watching") {
                    ProgressRail(
                        title = stringResource(R.string.home_continue_watching),
                        subtitle = stringResource(R.string.library_continue_watching_subtitle),
                        tag = "library-continue-watching",
                        itemTagPrefix = "continue-watching",
                        items = continueWatchingItems,
                        posterEndpoint = posterEndpoint,
                        onShowDetails = { selectedEpisodeId = it.id },
                        onPlay = onPlay,
                    )
                }
            }
            if (recentlyWatchedItems.isNotEmpty()) {
                item(key = "library-recently-watched") {
                    ProgressRail(
                        title = stringResource(R.string.home_recently_watched),
                        subtitle = stringResource(R.string.library_recently_watched_subtitle),
                        tag = "library-recently-watched",
                        itemTagPrefix = "recently-watched",
                        items = recentlyWatchedItems,
                        posterEndpoint = posterEndpoint,
                        onShowDetails = { selectedEpisodeId = it.id },
                        onPlay = onPlay,
                    )
                }
            }
            item(key = "library-series-rail") {
                SeriesRail(
                    series = series.take(12),
                    watchSummaryById = seriesWatchSummaryById,
                    posterEndpoint = posterEndpoint,
                    searchText = searchText,
                    onSelectSeries = onSearchTextChange,
                    onClearSearch = { onSearchTextChange("") },
                )
            }
            selectedSeries?.let { series ->
                item(key = "library-series-detail-${series.id}") {
                    SeriesDetailPanel(
                        series = series,
                        watchSummary = seriesWatchSummaryById[series.id],
                    )
                }
            }
        }
        if (showInlineEpisodeDetail) {
            selectedEpisodeDetail?.let { detail ->
                item(key = "episode-detail-${detail.mediaItem.id}") {
                    EpisodeDetailPanel(
                        detail = detail,
                        posterEndpoint = posterEndpoint,
                        isFavorite = detail.mediaItem.id in favoriteMediaIds,
                        onSetFavorite = { onSetFavorite(detail.mediaItem, it) },
                        onPlay = onPlay,
                        onSelectEpisode = { selectedEpisodeId = it.id },
                    )
                }
            }
        }
        if (catalog == null) {
            item(key = "library-empty") {
                EmptyLibraryState(onConnect = onConnect)
            }
        } else if (filteredItems.isEmpty()) {
            item(key = "library-no-results") {
                EmptyResultsState(
                    onResetFilters = {
                        onSearchTextChange("")
                        onSortChange(LibraryCatalogSort.TITLE)
                        onSubtitleFilterChange(LibrarySubtitleFilter.ANY)
                        onFavoriteFilterChange(LibraryFavoriteFilter.ANY)
                    },
                )
            }
        } else {
            items(filteredItems, key = LibraryMediaItem::id) { item ->
                EpisodeRow(
                    item = item,
                    posterEndpoint = posterEndpoint,
                    selected = nowPlaying?.id == item.id,
                    watchStatus = watchStatusById[item.id],
                    isFavorite = item.id in favoriteMediaIds,
                    onSetFavorite = { onSetFavorite(item, it) },
                    onShowDetails = { selectedEpisodeId = item.id },
                    onPlay = { onPlay(item) },
                )
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(contentPadding)
            .safeDrawingPadding(),
    ) {
        val useTwoPane = maxWidth >= 840.dp
        if (useTwoPane) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("library-master-pane"),
                    contentPadding = PaddingValues(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    libraryFeedItems(showInlineEpisodeDetail = false)
                }
                Column(
                    modifier = Modifier
                        .width(380.dp)
                        .verticalScroll(rememberScrollState())
                        .testTag("library-detail-pane"),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    selectedEpisodeDetail?.let { detail ->
                        EpisodeDetailPanel(
                            detail = detail,
                            posterEndpoint = posterEndpoint,
                            isFavorite = detail.mediaItem.id in favoriteMediaIds,
                            onSetFavorite = { onSetFavorite(detail.mediaItem, it) },
                            onPlay = onPlay,
                            onSelectEpisode = { selectedEpisodeId = it.id },
                        )
                    } ?: TabletDetailPlaceholder(catalog = catalog)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                libraryFeedItems(showInlineEpisodeDetail = true)
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    catalog: LibraryCatalog?,
    filteredCount: Int,
    totalCount: Int,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    sort: LibraryCatalogSort,
    onSortChange: (LibraryCatalogSort) -> Unit,
    subtitleFilter: LibrarySubtitleFilter,
    onSubtitleFilterChange: (LibrarySubtitleFilter) -> Unit,
    favoriteMediaIds: Set<String>,
    favoriteFilter: LibraryFavoriteFilter,
    onFavoriteFilterChange: (LibraryFavoriteFilter) -> Unit,
    onConnect: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PanelAltColor,
        border = BorderStroke(1.dp, Color(0xFF343D45)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.library_episodes_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        catalog?.rootName ?: stringResource(R.string.library_connect_browse_subtitle),
                        color = SubtleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.library_favorites_count, favoriteMediaIds.size)) },
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.library_episode_count, filteredCount, totalCount)) },
                )
                if (favoriteFilter == LibraryFavoriteFilter.FAVORITES_ONLY) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.library_favorites_only)) })
                }
                if (subtitleFilter == LibrarySubtitleFilter.WITH_SUBTITLES) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.library_subtitles_only)) })
                }
                if (sort == LibraryCatalogSort.PATH) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.library_path_sort)) })
                }
            }

            OutlinedTextField(
                value = searchText,
                onValueChange = onSearchTextChange,
                label = { Text(stringResource(R.string.library_search_episodes)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = favoriteFilter == LibraryFavoriteFilter.FAVORITES_ONLY,
                    onClick = {
                        onFavoriteFilterChange(
                            if (favoriteFilter == LibraryFavoriteFilter.ANY) {
                                LibraryFavoriteFilter.FAVORITES_ONLY
                            } else {
                                LibraryFavoriteFilter.ANY
                            },
                        )
                    },
                    modifier = Modifier.testTag("library-favorites-filter"),
                    label = { Text(stringResource(R.string.library_filter_favorites)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                FilterChip(
                    selected = sort == LibraryCatalogSort.TITLE,
                    onClick = { onSortChange(LibraryCatalogSort.TITLE) },
                    label = { Text(stringResource(R.string.library_filter_title)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.SortByAlpha,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                FilterChip(
                    selected = sort == LibraryCatalogSort.PATH,
                    onClick = { onSortChange(LibraryCatalogSort.PATH) },
                    label = { Text(stringResource(R.string.library_filter_path)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                FilterChip(
                    selected = subtitleFilter == LibrarySubtitleFilter.WITH_SUBTITLES,
                    onClick = {
                        onSubtitleFilterChange(
                            if (subtitleFilter == LibrarySubtitleFilter.ANY) {
                                LibrarySubtitleFilter.WITH_SUBTITLES
                            } else {
                                LibrarySubtitleFilter.ANY
                            },
                        )
                    },
                    label = { Text(stringResource(R.string.library_filter_subtitles)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Subtitles,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }

            if (catalog == null) {
                OutlinedButton(onClick = onConnect) {
                    Text(stringResource(R.string.action_connect_library))
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    item: LibraryMediaItem,
    posterEndpoint: LibraryPosterEndpoint?,
    selected: Boolean,
    watchStatus: LibraryWatchStatus?,
    isFavorite: Boolean,
    onSetFavorite: (Boolean) -> Unit,
    onShowDetails: () -> Unit,
    onPlay: () -> Unit,
) {
    val metadata = buildList {
        add(watchStatus.statusLabel())
        add(item.formatSize())
        add(stringResource(R.string.subtitle_track_count, item.subtitles.size))
        item.animeMetadata?.let { add(stringResource(R.string.matched_anime_short, it.displayTitle)) }
        if (item.posterPath != null) {
            add(stringResource(R.string.poster_ready))
        }
        item.metadataStatusLabel(
            loadingLabel = stringResource(R.string.metadata_loading),
            failedLabel = stringResource(R.string.metadata_failed),
        )?.let(::add)
        if (isFavorite) {
            add(stringResource(R.string.action_favorite))
        }
    }.joinToString(" · ")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onShowDetails)
            .testTag("episode:${item.id}"),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Color(0xFF263847) else PanelColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) AccentBlue else Color(0xFF2B3239),
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryPosterTile(
                item = item,
                title = item.seriesTitle,
                selected = selected,
                posterEndpoint = posterEndpoint,
                progressLabel = null,
                modifier = Modifier
                    .size(52.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    item.seriesTitle,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.episodeTitle,
                    color = SubtleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    metadata,
                    color = Color(0xFF8F9AA5),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = { onSetFavorite(!isFavorite) },
                modifier = Modifier.testTag("episode-favorite:${item.id}"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (isFavorite) {
                        stringResource(R.string.action_unfavorite)
                    } else {
                        stringResource(R.string.action_favorite)
                    },
                )
            }
            TextButton(
                onClick = onPlay,
                modifier = Modifier.testTag("episode-play:${item.id}"),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_play))
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    onConnect: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF15191D),
        border = BorderStroke(1.dp, Color(0xFF2B3239)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.library_empty_connect_title),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.library_empty_connect_body),
                color = SubtleText,
            )
            OutlinedButton(onClick = onConnect) {
                Text(stringResource(R.string.action_open_connect))
            }
        }
    }
}

@Composable
private fun EmptyResultsState(onResetFilters: () -> Unit) {
    EmptyPanel(
        title = stringResource(R.string.library_no_results_title),
        body = stringResource(R.string.library_no_results_body),
        actionLabel = stringResource(R.string.action_reset_filters),
        onAction = onResetFilters,
    )
}
