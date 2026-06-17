package app.danmaku.desktop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.danmaku.domain.ExternalAnimeTrackingPlan
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibraryNextUpItem
import app.danmaku.domain.LibraryPlaybackProgressItem
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LibrarySeriesWatchSummary
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.LibraryWatchStatus
import java.nio.file.Path

@Composable
internal fun LibraryCenterWorkspace(
    strings: DesktopStrings,
    selectedView: WindowsLibraryView,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    sort: LibraryCatalogSort,
    onSortChange: (LibraryCatalogSort) -> Unit,
    subtitleFilter: LibrarySubtitleFilter,
    onToggleSubtitleFilter: () -> Unit,
    favoriteFilter: LibraryFavoriteFilter,
    onToggleFavoriteFilter: () -> Unit,
    localWatchListFilter: LocalWatchListFilter,
    onLocalWatchListFilterChange: (LocalWatchListFilter) -> Unit,
    catalog: LibraryCatalog?,
    visibleSeries: List<LibrarySeries>,
    selectedSeries: LibrarySeries?,
    filteredEpisodes: List<LibraryMediaItem>,
    selectedMediaId: String?,
    coverBySeriesId: Map<String, Path?>,
    originalSeriesTitleByMediaId: Map<String, String>,
    refreshingMetadataMediaIds: Set<String>,
    refreshingMetadataSeriesIds: Set<String>,
    continueWatchingItems: List<LibraryPlaybackProgressItem>,
    nextUpItems: List<LibraryNextUpItem>,
    recentlyWatchedItems: List<LibraryPlaybackProgressItem>,
    watchStatusById: Map<String, LibraryWatchStatus>,
    seriesWatchSummaryById: Map<String, LibrarySeriesWatchSummary>,
    favoriteMediaIds: Set<String>,
    localWatchListCount: Int,
    externalTrackingPlan: ExternalAnimeTrackingPlan?,
    isExternalAnimeSyncing: Boolean,
    isPreparing: Boolean,
    compact: Boolean,
    onSelectSeries: (LibrarySeries) -> Unit,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
    onRefreshSeriesMetadata: (LibrarySeries) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
    onSyncExternalAnimePlan: (ExternalAnimeTrackingPlan) -> Unit,
    onResetFilters: () -> Unit,
    remoteBrowser: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkspacePanel(modifier = modifier.fillMaxHeight()) {
        if (selectedView == WindowsLibraryView.PAIRED) {
            Text(strings.pairedLibraryTitle, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
            Text(strings.pairedLibraryDescription, color = DanmakuColors.TextMuted)
            Divider(color = DanmakuColors.SurfaceRaised)
            remoteBrowser()
            return@WorkspacePanel
        }

        LibraryWorkspaceToolbar(
            strings = strings,
            selectedView = selectedView,
            searchText = searchText,
            onSearchTextChange = onSearchTextChange,
            sort = sort,
            onSortChange = onSortChange,
            subtitleFilter = subtitleFilter,
            onToggleSubtitleFilter = onToggleSubtitleFilter,
            favoriteFilter = favoriteFilter,
            onToggleFavoriteFilter = onToggleFavoriteFilter,
            localWatchListFilter = localWatchListFilter,
            onLocalWatchListFilterChange = onLocalWatchListFilterChange,
            compact = compact,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusPill(
                strings.episodeCountSummary(filteredEpisodes.size, catalog?.items?.size ?: 0),
                icon = Icons.AutoMirrored.Filled.ViewList,
            )
            StatusPill(strings.favoriteCountSummary(favoriteMediaIds.size), icon = Icons.Filled.Star)
            StatusPill(strings.localWatchListCountSummary(localWatchListCount), icon = Icons.Filled.CheckCircle)
            externalTrackingPlan?.summary?.let { summary ->
                StatusPill(
                    summary.localizedLabel(strings),
                    icon = Icons.Filled.Refresh,
                    active = summary.updateCount > 0,
                )
            }
            if (subtitleFilter != LibrarySubtitleFilter.ANY) {
                StatusPill(strings.subtitlesOnlyLabel, icon = Icons.Filled.Subtitles, active = true)
            }
            if (selectedView == WindowsLibraryView.FAVORITES || favoriteFilter == LibraryFavoriteFilter.FAVORITES_ONLY) {
                StatusPill(strings.favoritesOnlyAction, icon = Icons.Filled.Star, active = true)
            }
            if (localWatchListFilter != LocalWatchListFilter.ANY) {
                StatusPill(localWatchListFilter.localizedLabel(strings), icon = Icons.Filled.CheckCircle, active = true)
            }
            if (sort != LibraryCatalogSort.TITLE) {
                StatusPill(strings.pathSortLabel, icon = Icons.Filled.FolderOpen, active = true)
            }
            if (!compact) {
                selectedSeries?.let { StatusPill(it.title) }
            }
        }
        when (selectedView) {
            WindowsLibraryView.CONTINUE_WATCHING -> ContinueWatchingList(
                strings = strings,
                items = continueWatchingItems,
                selectedMediaId = selectedMediaId,
                refreshingMetadataMediaIds = refreshingMetadataMediaIds,
                isPreparing = isPreparing,
                compact = compact,
                onShowDetails = onShowDetails,
                onRefreshEpisodeMetadata = onRefreshEpisodeMetadata,
                onPlayLocalPlayback = onPlayLocalPlayback,
            )
            WindowsLibraryView.NEXT_UP -> NextUpList(
                strings = strings,
                items = nextUpItems,
                selectedMediaId = selectedMediaId,
                refreshingMetadataMediaIds = refreshingMetadataMediaIds,
                isPreparing = isPreparing,
                compact = compact,
                onShowDetails = onShowDetails,
                onRefreshEpisodeMetadata = onRefreshEpisodeMetadata,
                onPrepareLocalPlayback = onPrepareLocalPlayback,
                onPlayLocalPlayback = onPlayLocalPlayback,
            )
            WindowsLibraryView.RECENTLY_WATCHED -> RecentlyWatchedList(
                strings = strings,
                items = recentlyWatchedItems,
                selectedMediaId = selectedMediaId,
                refreshingMetadataMediaIds = refreshingMetadataMediaIds,
                isPreparing = isPreparing,
                compact = compact,
                onShowDetails = onShowDetails,
                onRefreshEpisodeMetadata = onRefreshEpisodeMetadata,
                onPrepareLocalPlayback = onPrepareLocalPlayback,
                onPlayLocalPlayback = onPlayLocalPlayback,
            )
            WindowsLibraryView.FAVORITES,
            WindowsLibraryView.FILES -> EpisodeListView(
                strings = strings,
                episodes = filteredEpisodes,
                selectedMediaId = selectedMediaId,
                watchStatusById = watchStatusById,
                favoriteMediaIds = favoriteMediaIds,
                originalSeriesTitleByMediaId = originalSeriesTitleByMediaId,
                refreshingMetadataMediaIds = refreshingMetadataMediaIds,
                isPreparing = isPreparing,
                emptyText = if (selectedView == WindowsLibraryView.FAVORITES) {
                    strings.favoriteEpisodesFilterEmptyText
                } else {
                    strings.episodesFilterEmptyText
                },
                compact = compact,
                onResetFilters = onResetFilters,
                onShowDetails = onShowDetails,
                onSetFavorite = onSetFavorite,
                onRefreshEpisodeMetadata = onRefreshEpisodeMetadata,
                onPrepareLocalPlayback = onPrepareLocalPlayback,
                onPlayLocalPlayback = onPlayLocalPlayback,
            )
            WindowsLibraryView.EXTERNAL_SYNC -> ExternalSyncPreviewView(
                strings = strings,
                plan = externalTrackingPlan,
                isSyncing = isExternalAnimeSyncing,
                onSync = onSyncExternalAnimePlan,
            )
            WindowsLibraryView.ALL_SERIES,
            WindowsLibraryView.PAIRED -> AllSeriesView(
                strings = strings,
                catalog = catalog,
                visibleSeries = visibleSeries,
                selectedSeries = selectedSeries,
                coverBySeriesId = coverBySeriesId,
                refreshingMetadataSeriesIds = refreshingMetadataSeriesIds,
                continueWatchingItems = continueWatchingItems,
                nextUpItems = nextUpItems,
                watchStatusById = watchStatusById,
                seriesWatchSummaryById = seriesWatchSummaryById,
                isPreparing = isPreparing,
                compact = compact,
                onResetFilters = onResetFilters,
                onSelectSeries = onSelectSeries,
                onShowDetails = onShowDetails,
                onRefreshSeriesMetadata = onRefreshSeriesMetadata,
                onPlayLocalPlayback = onPlayLocalPlayback,
            )
        }
    }
}

@Composable
internal fun LibraryWorkspaceToolbar(
    strings: DesktopStrings,
    selectedView: WindowsLibraryView,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    sort: LibraryCatalogSort,
    onSortChange: (LibraryCatalogSort) -> Unit,
    subtitleFilter: LibrarySubtitleFilter,
    onToggleSubtitleFilter: () -> Unit,
    favoriteFilter: LibraryFavoriteFilter,
    onToggleFavoriteFilter: () -> Unit,
    localWatchListFilter: LocalWatchListFilter,
    onLocalWatchListFilterChange: (LocalWatchListFilter) -> Unit,
    compact: Boolean,
) {
    @Composable
    fun ToolbarActions() {
        var localWatchListMenuExpanded by remember { mutableStateOf(false) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerIconButton(
                imageVector = Icons.Filled.FilterList,
                contentDescription = if (subtitleFilter == LibrarySubtitleFilter.ANY) {
                    strings.requireSubtitlesAction
                } else {
                    strings.showAllSubtitlesAction
                },
                active = subtitleFilter != LibrarySubtitleFilter.ANY,
                onClick = onToggleSubtitleFilter,
            )
            PlayerIconButton(
                imageVector = Icons.Filled.Star,
                contentDescription = if (favoriteFilter == LibraryFavoriteFilter.ANY) {
                    strings.favoritesOnlyAction
                } else {
                    strings.showAllFavoritesAction
                },
                active = selectedView == WindowsLibraryView.FAVORITES || favoriteFilter != LibraryFavoriteFilter.ANY,
                onClick = onToggleFavoriteFilter,
            )
            Box {
                PlayerIconButton(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = localWatchListFilter.localizedLabel(strings),
                    active = localWatchListFilter != LocalWatchListFilter.ANY,
                    onClick = { localWatchListMenuExpanded = true },
                )
                DropdownMenu(
                    expanded = localWatchListMenuExpanded,
                    onDismissRequest = { localWatchListMenuExpanded = false },
                ) {
                    LocalWatchListFilter.entries.forEach { option ->
                        DropdownMenuItem(
                            onClick = {
                                onLocalWatchListFilterChange(option)
                                localWatchListMenuExpanded = false
                            },
                        ) {
                            Text(
                                option.localizedLabel(strings),
                                fontWeight = if (option == localWatchListFilter) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
            PlayerIconButton(
                imageVector = if (sort == LibraryCatalogSort.TITLE) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                contentDescription = if (sort == LibraryCatalogSort.TITLE) strings.sortByPathAction else strings.sortByTitleAction,
                active = sort != LibraryCatalogSort.TITLE,
                onClick = {
                    onSortChange(
                        if (sort == LibraryCatalogSort.TITLE) {
                            LibraryCatalogSort.PATH
                        } else {
                            LibraryCatalogSort.TITLE
                        },
                    )
                },
            )
        }
    }

    @Composable
    fun LibrarySearchField(modifier: Modifier) {
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            label = { Text(strings.librarySearchLabel) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = strings.searchAction) },
            singleLine = true,
            modifier = modifier,
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stackSearch = compact || maxWidth < 760.dp
        if (stackSearch) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.libraryViewTitle(selectedView), style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                        Text(strings.libraryToolbarCompactDescription, color = DanmakuColors.TextMuted, maxLines = 1)
                    }
                    ToolbarActions()
                }
                LibrarySearchField(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.libraryViewTitle(selectedView), style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                    Text(strings.libraryToolbarDescription, color = DanmakuColors.TextMuted)
                }
                LibrarySearchField(Modifier.width(360.dp))
                ToolbarActions()
            }
        }
    }
}
