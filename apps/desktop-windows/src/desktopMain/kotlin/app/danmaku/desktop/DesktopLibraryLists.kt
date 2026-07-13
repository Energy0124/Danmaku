package app.danmaku.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.darkColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.danmaku.domain.DanmakuDisplaySettings
import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeMatchCandidate
import app.danmaku.domain.ExternalAnimeMatchQuery
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeMappingSource
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeSyncFailure
import app.danmaku.domain.ExternalAnimeTrackingPlan
import app.danmaku.domain.ExternalAnimeTrackingPlanUpdate
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogQuery
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryItemMetadataStatus
import app.danmaku.domain.LocalDanmakuParser
import app.danmaku.domain.LibraryEpisodeDetail
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibraryNextUpItem
import app.danmaku.domain.LibraryNextUpReason
import app.danmaku.domain.LibraryPlaybackProgressItem
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LibrarySeriesWatchSummary
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.LibraryWatchState
import app.danmaku.domain.LibraryWatchStatus
import app.danmaku.domain.LocalAnimeListEntry
import app.danmaku.domain.LocalAnimeListStatus
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackStatus
import app.danmaku.domain.PlaybackTrack
import app.danmaku.domain.PlaybackTrackKind
import app.danmaku.domain.continueWatchingItems
import app.danmaku.domain.displayName
import app.danmaku.domain.episodeDetail
import app.danmaku.domain.externalAnimeTrackingPlan
import app.danmaku.domain.externalAnimeSyncRetryAfterEpochMs
import app.danmaku.domain.filteredItems
import app.danmaku.domain.groupedSeries
import app.danmaku.domain.nextItem
import app.danmaku.domain.nextUpItems
import app.danmaku.domain.previousItem
import app.danmaku.domain.recentlyWatchedItems
import app.danmaku.domain.toPlaybackProgress
import app.danmaku.domain.seriesWatchSummaryById
import app.danmaku.domain.watchStatusByMediaId
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.LanPlaybackPreparation
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.LanPlaybackTarget
import app.danmaku.library.jvm.JvmLanLibraryClient
import app.danmaku.server.LocalLibraryServerEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Rectangle
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.Window as AwtWindow
import java.awt.datatransfer.StringSelection
import java.net.URI
import kotlin.math.roundToInt
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.jetbrains.skia.Image as SkiaImage

@Composable
internal fun AllSeriesView(
    strings: DesktopStrings,
    catalog: LibraryCatalog?,
    visibleSeries: List<LibrarySeries>,
    seriesViewMode: LibrarySeriesViewMode,
    registeredRoots: List<DesktopLibraryRoot>,
    rootIdByMediaId: Map<String, String>,
    selectedSeries: LibrarySeries?,
    coverBySeriesId: Map<String, Path?>,
    refreshingMetadataSeriesIds: Set<String>,
    continueWatchingItems: List<LibraryPlaybackProgressItem>,
    nextUpItems: List<LibraryNextUpItem>,
    watchStatusById: Map<String, LibraryWatchStatus>,
    seriesWatchSummaryById: Map<String, LibrarySeriesWatchSummary>,
    localAnimeListEntryBySeriesId: Map<String, LocalAnimeListEntry>,
    isPreparing: Boolean,
    compact: Boolean,
    onResetFilters: () -> Unit,
    onSelectSeries: (LibrarySeries) -> Unit,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onSaveLocalAnimeListEntry: (LibrarySeries, LocalAnimeListStatus, Int?, String?) -> Unit,
    onDeleteLocalAnimeListEntry: (LibrarySeries) -> Unit,
    onRefreshSeriesMetadata: (LibrarySeries) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    val groups = remember(visibleSeries, seriesViewMode, registeredRoots, rootIdByMediaId) {
        visibleSeries.groupForLibraryPresentation(seriesViewMode, registeredRoots, rootIdByMediaId)
    }
    val orderedSeries = remember(groups) { groups.flatMap(LibrarySeriesGroup::series).distinctBy(LibrarySeries::id) }
    LibraryProgressOverview(
        strings = strings,
        continueWatchingItems = continueWatchingItems,
        nextUpItems = nextUpItems,
        isPreparing = isPreparing,
        compact = compact,
        onShowDetails = onShowDetails,
        onPlayLocalPlayback = onPlayLocalPlayback,
    )
    Text(strings.librarySeriesViewModeTitle(seriesViewMode), style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
    when {
        catalog == null || catalog.items.isEmpty() -> EmptyState(strings.noIndexedSeriesText)
        visibleSeries.isEmpty() -> EmptyState(
            text = strings.noSeriesFilterMatchesText,
            actionLabel = strings.resetFiltersAction,
            onAction = onResetFilters,
        )
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = if (compact) 132.dp else 150.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 520.dp else 580.dp)
                .libraryCollectionKeyboard(
                    itemCount = orderedSeries.size,
                    selectedIndex = orderedSeries.indexOfFirst { it.id == selectedSeries?.id }.coerceAtLeast(0),
                    columnStride = if (compact) 4 else 6,
                    onSelectedIndexChange = { index -> orderedSeries.getOrNull(index)?.let(onSelectSeries) },
                    onOpenSelected = {
                        orderedSeries
                            .getOrNull(orderedSeries.indexOfFirst { it.id == selectedSeries?.id }.coerceAtLeast(0))
                            ?.let(onSelectSeries)
                    },
                    onPlaySelected = {
                        orderedSeries
                            .getOrNull(orderedSeries.indexOfFirst { it.id == selectedSeries?.id }.coerceAtLeast(0))
                            ?.let { librarySeries ->
                                onPlayLocalPlayback(
                                    nextPlayableEpisode(
                                        librarySeries = librarySeries,
                                        watchStatusById = watchStatusById,
                                    ),
                                )
                            }
                    },
                ),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 20.dp),
        ) {
            groups.forEach { group ->
                if (group.kind != LibrarySeriesGroupKind.ALL) {
                    item(key = "header:${group.key}", span = { GridItemSpan(maxLineSpan) }) {
                        val label = when (group.kind) {
                            LibrarySeriesGroupKind.RECENT_MONTH -> strings.libraryRecentMonthLabel(group.value.orEmpty())
                            LibrarySeriesGroupKind.RELEASE_YEAR -> strings.libraryReleaseYearLabel(group.value.orEmpty())
                            LibrarySeriesGroupKind.FOLDER -> group.value ?: strings.libraryUnassignedFolderLabel
                            LibrarySeriesGroupKind.UNKNOWN -> if (seriesViewMode == LibrarySeriesViewMode.RECENT) {
                                strings.libraryUnknownRecentLabel
                            } else {
                                strings.libraryUnknownSeasonLabel
                            }
                            LibrarySeriesGroupKind.ALL -> ""
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DanmakuColors.SurfaceRaised.copy(alpha = 0.58f))
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(strings.librarySeriesCountLabel(group.series.size), color = DanmakuColors.TextMuted)
                        }
                    }
                }
                items(group.series, key = { "${group.key}:${it.id}" }) { librarySeries ->
                    SeriesPosterCard(
                    strings = strings,
                    series = librarySeries,
                    coverPath = coverBySeriesId[librarySeries.id],
                    watchSummary = seriesWatchSummaryById[librarySeries.id],
                    localAnimeListEntry = localAnimeListEntryBySeriesId[librarySeries.id],
                    isSelected = librarySeries.id == selectedSeries?.id,
                    isPreparing = isPreparing,
                    isRefreshingMetadata = librarySeries.id in refreshingMetadataSeriesIds,
                    onSelect = { onSelectSeries(librarySeries) },
                    onSaveLocalAnimeListEntry = { status, score, notes ->
                        onSaveLocalAnimeListEntry(librarySeries, status, score, notes)
                    },
                    onDeleteLocalAnimeListEntry = { onDeleteLocalAnimeListEntry(librarySeries) },
                    onRefreshMetadata = { onRefreshSeriesMetadata(librarySeries) },
                    onPlay = {
                        onPlayLocalPlayback(
                            nextPlayableEpisode(
                                librarySeries = librarySeries,
                                watchStatusById = watchStatusById,
                            ),
                        )
                    },
                )
                }
            }
        }
    }
}

@Composable
internal fun LibraryProgressOverview(
    strings: DesktopStrings,
    continueWatchingItems: List<LibraryPlaybackProgressItem>,
    nextUpItems: List<LibraryNextUpItem>,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    val cards = buildList {
        continueWatchingItems.take(2).forEach { item ->
            add(
                LibraryProgressCardModel(
                    title = item.mediaItem.displaySeriesTitle(),
                    subtitle = item.mediaItem.episodeTitle,
                    fileGroupLabel = item.mediaItem.localSeriesLabel(strings),
                    detail = strings.resumeAtLabel(item.progress.positionMs),
                    progressPercent = item.progress.progressPercent(),
                    actionLabel = strings.resumeAction,
                    mediaItem = item.mediaItem,
                ),
            )
        }
        nextUpItems.take(2).forEach { item ->
            add(
                LibraryProgressCardModel(
                    title = item.mediaItem.displaySeriesTitle(),
                    subtitle = item.mediaItem.episodeTitle,
                    fileGroupLabel = item.mediaItem.localSeriesLabel(strings),
                    detail = item.nextUpLabel(strings),
                    progressPercent = item.progress?.progressPercent(),
                    actionLabel = item.nextUpActionLabel(strings),
                    mediaItem = item.mediaItem,
                ),
            )
        }
    }.distinctBy { it.mediaItem.id }.take(if (compact) 3 else 4)
    if (cards.isEmpty()) return

    Text(strings.continueWatchingTitle, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        cards.forEach { card ->
            LibraryProgressCard(
                strings = strings,
                card = card,
                isPreparing = isPreparing,
                onShowDetails = onShowDetails,
                onPlayLocalPlayback = onPlayLocalPlayback,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

internal data class LibraryProgressCardModel(
    val title: String,
    val subtitle: String,
    val fileGroupLabel: String?,
    val detail: String,
    val progressPercent: Int?,
    val actionLabel: String,
    val mediaItem: LibraryMediaItem,
)

@Composable
internal fun LibraryProgressCard(
    strings: DesktopStrings,
    card: LibraryProgressCardModel,
    isPreparing: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DanmakuColors.SurfaceRaised.copy(alpha = 0.72f))
            .clickable { onShowDetails(card.mediaItem) }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(card.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(card.subtitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        card.fileGroupLabel?.let { label ->
            Text(label, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MiniProgressBar(percent = card.progressPercent)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(card.detail, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            LibraryActionButton(
                imageVector = Icons.Filled.PlayArrow,
                label = if (isPreparing) strings.loadingAction else card.actionLabel,
                onClick = { onPlayLocalPlayback(card.mediaItem) },
                enabled = !isPreparing,
            )
        }
    }
}

@Composable
internal fun ContinueWatchingList(
    strings: DesktopStrings,
    items: List<LibraryPlaybackProgressItem>,
    selectedMediaId: String?,
    refreshingMetadataMediaIds: Set<String>,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState(strings.noInProgressLocalEpisodesText)
    } else {
        var selectedIndex by remember(items) { mutableStateOf(0) }
        val boundedSelectedIndex = selectedIndex.coerceIn(0, items.lastIndex)
        val selectedMediaIndex = items.indexOfFirst { it.mediaItem.id == selectedMediaId }
        val activeIndex = selectedMediaIndex.takeIf { it >= 0 } ?: boundedSelectedIndex
        LazyColumn(
            modifier = Modifier
                .height(560.dp)
                .libraryCollectionKeyboard(
                    itemCount = items.size,
                    selectedIndex = activeIndex,
                    onSelectedIndexChange = { index ->
                        selectedIndex = index
                        items.getOrNull(index)?.mediaItem?.let(onShowDetails)
                    },
                    onOpenSelected = { items.getOrNull(activeIndex)?.mediaItem?.let(onShowDetails) },
                    onPlaySelected = { items.getOrNull(activeIndex)?.mediaItem?.let(onPlayLocalPlayback) },
                ),
        ) {
            itemsIndexed(items, key = { _, item -> item.mediaItem.id }) { rowIndex, item ->
                ContinueWatchingRow(
                    strings = strings,
                    item = item,
                    selected = item.mediaItem.id == selectedMediaId || (selectedMediaId == null && rowIndex == boundedSelectedIndex),
                    isRefreshingMetadata = item.mediaItem.id in refreshingMetadataMediaIds,
                    isPreparing = isPreparing,
                    compact = compact,
                    onShowDetails = {
                        selectedIndex = rowIndex
                        onShowDetails(it)
                    },
                    onRefreshEpisodeMetadata = onRefreshEpisodeMetadata,
                    onPlayLocalPlayback = onPlayLocalPlayback,
                )
            }
        }
    }
}

@Composable
internal fun NextUpList(
    strings: DesktopStrings,
    items: List<LibraryNextUpItem>,
    selectedMediaId: String?,
    refreshingMetadataMediaIds: Set<String>,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState(strings.noNextUpItemText)
    } else {
        var selectedIndex by remember(items) { mutableStateOf(0) }
        val boundedSelectedIndex = selectedIndex.coerceIn(0, items.lastIndex)
        val selectedMediaIndex = items.indexOfFirst { it.mediaItem.id == selectedMediaId }
        val activeIndex = selectedMediaIndex.takeIf { it >= 0 } ?: boundedSelectedIndex
        LazyColumn(
            modifier = Modifier
                .height(560.dp)
                .libraryCollectionKeyboard(
                    itemCount = items.size,
                    selectedIndex = activeIndex,
                    onSelectedIndexChange = { index ->
                        selectedIndex = index
                        items.getOrNull(index)?.mediaItem?.let(onShowDetails)
                    },
                    onOpenSelected = { items.getOrNull(activeIndex)?.mediaItem?.let(onShowDetails) },
                    onPlaySelected = { items.getOrNull(activeIndex)?.mediaItem?.let(onPlayLocalPlayback) },
                ),
        ) {
            itemsIndexed(items, key = { _, item -> item.mediaItem.id }) { rowIndex, item ->
                NextUpRow(
                    strings = strings,
                    item = item,
                    selected = item.mediaItem.id == selectedMediaId || (selectedMediaId == null && rowIndex == boundedSelectedIndex),
                    isRefreshingMetadata = item.mediaItem.id in refreshingMetadataMediaIds,
                    isPreparing = isPreparing,
                    compact = compact,
                    onShowDetails = {
                        selectedIndex = rowIndex
                        onShowDetails(it)
                    },
                    onRefreshEpisodeMetadata = onRefreshEpisodeMetadata,
                    onPrepareLocalPlayback = onPrepareLocalPlayback,
                    onPlayLocalPlayback = onPlayLocalPlayback,
                )
            }
        }
    }
}

@Composable
internal fun RecentlyWatchedList(
    strings: DesktopStrings,
    items: List<LibraryPlaybackProgressItem>,
    selectedMediaId: String?,
    refreshingMetadataMediaIds: Set<String>,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState(strings.noRecentlyWatchedLocalEpisodesText)
    } else {
        var selectedIndex by remember(items) { mutableStateOf(0) }
        val boundedSelectedIndex = selectedIndex.coerceIn(0, items.lastIndex)
        val selectedMediaIndex = items.indexOfFirst { it.mediaItem.id == selectedMediaId }
        val activeIndex = selectedMediaIndex.takeIf { it >= 0 } ?: boundedSelectedIndex
        LazyColumn(
            modifier = Modifier
                .height(560.dp)
                .libraryCollectionKeyboard(
                    itemCount = items.size,
                    selectedIndex = activeIndex,
                    onSelectedIndexChange = { index ->
                        selectedIndex = index
                        items.getOrNull(index)?.mediaItem?.let(onShowDetails)
                    },
                    onOpenSelected = { items.getOrNull(activeIndex)?.mediaItem?.let(onShowDetails) },
                    onPlaySelected = { items.getOrNull(activeIndex)?.mediaItem?.let(onPlayLocalPlayback) },
                ),
        ) {
            itemsIndexed(items, key = { _, item -> item.mediaItem.id }) { rowIndex, item ->
                RecentlyWatchedRow(
                    strings = strings,
                    item = item,
                    selected = item.mediaItem.id == selectedMediaId || (selectedMediaId == null && rowIndex == boundedSelectedIndex),
                    isRefreshingMetadata = item.mediaItem.id in refreshingMetadataMediaIds,
                    isPreparing = isPreparing,
                    compact = compact,
                    onShowDetails = {
                        selectedIndex = rowIndex
                        onShowDetails(it)
                    },
                    onRefreshEpisodeMetadata = onRefreshEpisodeMetadata,
                    onPrepareLocalPlayback = onPrepareLocalPlayback,
                    onPlayLocalPlayback = onPlayLocalPlayback,
                )
            }
        }
    }
}

@Composable
internal fun EpisodeListView(
    strings: DesktopStrings,
    episodes: List<LibraryMediaItem>,
    selectedMediaId: String?,
    watchStatusById: Map<String, LibraryWatchStatus>,
    favoriteMediaIds: Set<String>,
    originalSeriesTitleByMediaId: Map<String, String>,
    refreshingMetadataMediaIds: Set<String>,
    isPreparing: Boolean,
    emptyText: String,
    compact: Boolean,
    onResetFilters: () -> Unit,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    if (episodes.isEmpty()) {
        EmptyState(
            text = emptyText,
            actionLabel = "Reset filters",
            onAction = onResetFilters,
        )
    } else {
        var selectedIndex by remember(episodes) { mutableStateOf(0) }
        val boundedSelectedIndex = selectedIndex.coerceIn(0, episodes.lastIndex)
        val selectedMediaIndex = episodes.indexOfFirst { it.id == selectedMediaId }
        val activeIndex = selectedMediaIndex.takeIf { it >= 0 } ?: boundedSelectedIndex
        LazyColumn(
            modifier = Modifier
                .height(560.dp)
                .libraryCollectionKeyboard(
                    itemCount = episodes.size,
                    selectedIndex = activeIndex,
                    onSelectedIndexChange = { index ->
                        selectedIndex = index
                        episodes.getOrNull(index)?.let(onShowDetails)
                    },
                    onOpenSelected = { episodes.getOrNull(activeIndex)?.let(onShowDetails) },
                    onPlaySelected = { episodes.getOrNull(activeIndex)?.let(onPlayLocalPlayback) },
                ),
        ) {
            itemsIndexed(episodes, key = { _, item -> item.id }) { rowIndex, item ->
                EpisodeRow(
                    strings = strings,
                    item = item,
                    selected = item.id == selectedMediaId || (selectedMediaId == null && rowIndex == boundedSelectedIndex),
                    watchStatus = watchStatusById[item.id],
                    isFavorite = item.id in favoriteMediaIds,
                    originalSeriesTitle = originalSeriesTitleByMediaId[item.id],
                    isRefreshingMetadata = item.id in refreshingMetadataMediaIds,
                    isPreparing = isPreparing,
                    compact = compact,
                    onShowDetails = {
                        selectedIndex = rowIndex
                        onShowDetails(it)
                    },
                    onSetFavorite = onSetFavorite,
                    onRefreshEpisodeMetadata = onRefreshEpisodeMetadata,
                    onPrepareLocalPlayback = onPrepareLocalPlayback,
                    onPlayLocalPlayback = onPlayLocalPlayback,
                )
            }
        }
    }
}


