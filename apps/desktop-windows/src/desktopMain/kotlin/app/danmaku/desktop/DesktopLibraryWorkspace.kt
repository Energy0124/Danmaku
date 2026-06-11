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
import app.danmaku.server.LocalLibraryDiscoveryAnnouncer
import app.danmaku.server.LocalLibraryServerEvent
import app.danmaku.server.PublicGetHookResponse
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
internal fun WindowsLibraryWorkspace(
    strings: DesktopStrings,
    registeredRoots: List<DesktopLibraryRoot>,
    indexedLibrary: IndexedLocalLibrary?,
    searchSeed: String,
    searchSeedVersion: Int,
    series: List<LibrarySeries>,
    seriesPosterById: Map<String, Path?>,
    externalTrackingPlan: ExternalAnimeTrackingPlan?,
    isExternalAnimeSyncing: Boolean,
    externalAnimeMappings: List<ExternalAnimeMapping>,
    externalAnimeItemMappingsByMediaId: Map<String, List<DesktopExternalAnimeItemMapping>>,
    externalAnimeProviderSettings: ExternalAnimeProviderSettings,
    originalSeriesTitleByMediaId: Map<String, String>,
    refreshingMetadataMediaIds: Set<String>,
    refreshingMetadataSeriesIds: Set<String>,
    selectedSeries: LibrarySeries?,
    selectedEpisodeDetail: LibraryEpisodeDetail?,
    selectedLocalPlaybackPreparation: DesktopLocalPlaybackPreparation?,
    dandanplayCacheStatus: DandanplayPlaybackUiStatus?,
    autoNextLocalPlayback: Boolean,
    nextUpItems: List<LibraryNextUpItem>,
    continueWatchingItems: List<LibraryPlaybackProgressItem>,
    recentlyWatchedItems: List<LibraryPlaybackProgressItem>,
    watchStatusById: Map<String, LibraryWatchStatus>,
    seriesWatchSummaryById: Map<String, LibrarySeriesWatchSummary>,
    favoriteMediaIds: Set<String>,
    isIndexing: Boolean,
    isPreparing: Boolean,
    libraryError: String?,
    lastScanStats: LocalMediaLibraryScanStats?,
    onAddLibraryFolder: () -> Unit,
    onImportAniRssOutputFolder: () -> Unit,
    onRescanRegisteredRoots: () -> Unit,
    onRemoveRegisteredRoot: (DesktopLibraryRoot) -> Unit,
    onSelectSeries: (LibrarySeries) -> Unit,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onInspectCachedDandanplay: (LibraryMediaItem) -> Unit,
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit,
    onSetAutoNextLocalPlayback: (Boolean) -> Unit,
    onRefreshDandanplay: (DesktopLocalPlaybackPreparation) -> Unit,
    onSelectDandanplayMatch: (DesktopLocalPlaybackPreparation, DandanplayMatch) -> Unit,
    onClearDandanplayCache: (DesktopLocalPlaybackPreparation) -> Unit,
    onClearDanmakuOverlay: (DesktopLocalPlaybackPreparation) -> Unit,
    onAttachManualDanmaku: (DesktopLocalPlaybackPreparation) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
    onRefreshSeriesMetadata: (LibrarySeries) -> Unit,
    onSaveExternalAnimeMapping: (LibrarySeries, ExternalAnimeProvider, String) -> Unit,
    onDeleteExternalAnimeMapping: (LibrarySeries, ExternalAnimeProvider) -> Unit,
    onSaveExternalAnimeItemMapping: (LibraryMediaItem, ExternalAnimeProvider, String) -> Unit,
    onDeleteExternalAnimeItemMapping: (LibraryMediaItem, ExternalAnimeProvider) -> Unit,
    onSearchExternalAnimeMatches: suspend (ExternalAnimeMatchQuery, Set<ExternalAnimeProvider>) -> Result<List<ExternalAnimeMatchCandidate>>,
    onFetchMetadataMatchPoster: suspend (String?) -> Path?,
    onSyncExternalAnimePlan: (ExternalAnimeTrackingPlan) -> Unit,
    onLoadPreparedPlayback: (DesktopLocalPlaybackPreparation) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
    remoteBrowser: @Composable () -> Unit,
) {
    var selectedView by remember { mutableStateOf(WindowsLibraryView.ALL_SERIES) }
    var showLibraryImportPanel by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(LibraryCatalogSort.TITLE) }
    var subtitleFilter by remember { mutableStateOf(LibrarySubtitleFilter.ANY) }
    var favoriteFilter by remember { mutableStateOf(LibraryFavoriteFilter.ANY) }
    LaunchedEffect(searchSeed, searchSeedVersion) {
        val query = searchSeed.trim()
        if (query.isNotBlank()) {
            selectedView = WindowsLibraryView.ALL_SERIES
            searchText = query
        }
    }
    val catalog = indexedLibrary?.catalog
    val effectiveFavoriteFilter = if (selectedView == WindowsLibraryView.FAVORITES) {
        LibraryFavoriteFilter.FAVORITES_ONLY
    } else {
        favoriteFilter
    }
    val filteredEpisodes = remember(catalog, searchText, sort, subtitleFilter, effectiveFavoriteFilter, favoriteMediaIds) {
        catalog
            ?.filteredItems(
                LibraryCatalogQuery(
                    searchText = searchText,
                    sort = sort,
                    subtitleFilter = subtitleFilter,
                    favoriteFilter = effectiveFavoriteFilter,
                    favoriteMediaIds = favoriteMediaIds,
                ),
            )
            .orEmpty()
    }
    val filtersAreDefault = searchText.isBlank() &&
        sort == LibraryCatalogSort.TITLE &&
        subtitleFilter == LibrarySubtitleFilter.ANY &&
        effectiveFavoriteFilter == LibraryFavoriteFilter.ANY
    val visibleSeries = remember(series, filteredEpisodes, filtersAreDefault) {
        val visibleMediaIds = filteredEpisodes.mapTo(mutableSetOf(), LibraryMediaItem::id)
        if (visibleMediaIds.isEmpty() && filtersAreDefault) {
            series
        } else {
            series.filter { librarySeries ->
                librarySeries.seasons.any { season ->
                    season.items.any { item -> item.id in visibleMediaIds }
                }
            }
        }
    }
    val selectedInspectorSeries = selectedEpisodeDetail?.series ?: selectedSeries
    val selectedInspectorItem = selectedEpisodeDetail?.mediaItem
        ?: selectedLocalPlaybackPreparation?.item
        ?: selectedSeries?.let { nextPlayableEpisode(it, watchStatusById) }
    LaunchedEffect(selectedInspectorItem?.id) {
        selectedInspectorItem?.let(onInspectCachedDandanplay)
    }
    val selectedInspectorSeriesMappings = selectedInspectorSeries
        ?.let { series -> externalAnimeMappings.filter { mapping -> mapping.localSeriesId == series.id } }
        .orEmpty()
    val selectedInspectorItemMappings = selectedInspectorItem
        ?.let { item -> externalAnimeItemMappingsByMediaId[item.id] }
        .orEmpty()
    var inspectorWidthOverride by remember { mutableStateOf<Float?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compactWorkspace = maxWidth < 1380.dp
        val railWidth = if (compactWorkspace) 204.dp else 230.dp
        val defaultInspectorWidth = if (compactWorkspace) 372f else 460f
        val minInspectorWidth = if (compactWorkspace) 328f else 380f
        val maxInspectorWidth = minOf(if (compactWorkspace) 460f else 620f, maxWidth.value * 0.45f)
        val inspectorWidth = (inspectorWidthOverride ?: defaultInspectorWidth)
            .coerceIn(minInspectorWidth, maxInspectorWidth)
        val panelGap = if (compactWorkspace) 10.dp else 14.dp
        val workspaceMinHeight = if (compactWorkspace) 660.dp else 720.dp
        val density = LocalDensity.current

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = workspaceMinHeight),
            horizontalArrangement = Arrangement.spacedBy(panelGap),
        ) {
            LibraryWorkspaceRail(
                strings = strings,
                selectedView = selectedView,
                registeredRoots = registeredRoots,
                localEpisodeCount = catalog?.items?.size ?: 0,
                nextUpCount = nextUpItems.size,
                continueWatchingCount = continueWatchingItems.size,
                recentlyWatchedCount = recentlyWatchedItems.size,
                favoriteCount = favoriteMediaIds.size,
                seriesCount = series.size,
                externalTrackingPlan = externalTrackingPlan,
                isIndexing = isIndexing,
                libraryError = libraryError,
                lastScanStats = lastScanStats,
                compact = compactWorkspace,
                onSelectView = { selectedView = it },
                onOpenImportPanel = { showLibraryImportPanel = true },
                onRescanRegisteredRoots = onRescanRegisteredRoots,
                modifier = Modifier.width(railWidth),
            )
            LibraryCenterWorkspace(
                strings = strings,
                selectedView = selectedView,
                searchText = searchText,
                onSearchTextChange = { searchText = it },
                sort = sort,
                onSortChange = { sort = it },
                subtitleFilter = subtitleFilter,
                onToggleSubtitleFilter = {
                    subtitleFilter = if (subtitleFilter == LibrarySubtitleFilter.ANY) {
                        LibrarySubtitleFilter.WITH_SUBTITLES
                    } else {
                        LibrarySubtitleFilter.ANY
                    }
                },
                favoriteFilter = favoriteFilter,
                onToggleFavoriteFilter = {
                    favoriteFilter = if (favoriteFilter == LibraryFavoriteFilter.ANY) {
                        LibraryFavoriteFilter.FAVORITES_ONLY
                    } else {
                        LibraryFavoriteFilter.ANY
                    }
                },
                catalog = catalog,
                visibleSeries = visibleSeries,
                selectedSeries = selectedSeries,
                filteredEpisodes = filteredEpisodes,
                selectedMediaId = selectedInspectorItem?.id,
                coverBySeriesId = seriesPosterById,
                originalSeriesTitleByMediaId = originalSeriesTitleByMediaId,
                refreshingMetadataMediaIds = refreshingMetadataMediaIds,
                refreshingMetadataSeriesIds = refreshingMetadataSeriesIds,
                continueWatchingItems = continueWatchingItems,
                nextUpItems = nextUpItems,
                recentlyWatchedItems = recentlyWatchedItems,
                watchStatusById = watchStatusById,
                seriesWatchSummaryById = seriesWatchSummaryById,
                favoriteMediaIds = favoriteMediaIds,
                externalTrackingPlan = externalTrackingPlan,
                isExternalAnimeSyncing = isExternalAnimeSyncing,
                isPreparing = isPreparing,
                compact = compactWorkspace,
                onSelectSeries = onSelectSeries,
                onShowDetails = onShowDetails,
                onSetFavorite = onSetFavorite,
                onRefreshEpisodeMetadata = onRefreshEpisodeMetadata,
                onRefreshSeriesMetadata = onRefreshSeriesMetadata,
                onPrepareLocalPlayback = onPrepareLocalPlayback,
                onPlayLocalPlayback = onPlayLocalPlayback,
                onSyncExternalAnimePlan = onSyncExternalAnimePlan,
                onResetFilters = {
                    searchText = ""
                    sort = LibraryCatalogSort.TITLE
                    subtitleFilter = LibrarySubtitleFilter.ANY
                    favoriteFilter = LibraryFavoriteFilter.ANY
                },
                remoteBrowser = remoteBrowser,
                modifier = Modifier.weight(1f),
            )
            InspectorResizeHandle(
                label = strings.inspectorResizeHandleLabel,
                resetLabel = strings.resetInspectorWidthAction,
                isCustomWidth = inspectorWidthOverride != null,
                onReset = { inspectorWidthOverride = null },
                modifier = Modifier
                    .pointerInput(compactWorkspace, minInspectorWidth, maxInspectorWidth) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            val deltaDp = with(density) { dragAmount.toDp().value }
                            inspectorWidthOverride = (inspectorWidth - deltaDp)
                                .coerceIn(minInspectorWidth, maxInspectorWidth)
                        }
                    },
            )
            LibraryInspectorPane(
                strings = strings,
                selectedSeries = selectedInspectorSeries,
                selectedEpisodeDetail = selectedEpisodeDetail,
                selectedItem = selectedInspectorItem,
                selectedLocalPlaybackPreparation = selectedLocalPlaybackPreparation,
                dandanplayCacheStatus = dandanplayCacheStatus,
                autoNextLocalPlayback = autoNextLocalPlayback,
                externalAnimeMappings = selectedInspectorSeriesMappings,
                externalAnimeItemMappings = selectedInspectorItemMappings,
                externalAnimeProviderSettings = externalAnimeProviderSettings,
                originalSeriesTitleByMediaId = originalSeriesTitleByMediaId,
                refreshingMetadataMediaIds = refreshingMetadataMediaIds,
                refreshingMetadataSeriesIds = refreshingMetadataSeriesIds,
                coverPath = selectedInspectorSeries?.let { seriesPosterById[it.id] },
                watchSummary = selectedInspectorSeries?.let { seriesWatchSummaryById[it.id] },
                watchStatusById = watchStatusById,
                favoriteMediaIds = favoriteMediaIds,
                isPreparing = isPreparing,
                compact = compactWorkspace,
                onShowDetails = onShowDetails,
                onInspectCachedDandanplay = onInspectCachedDandanplay,
                onSetFavorite = onSetFavorite,
                onSetAutoNextLocalPlayback = onSetAutoNextLocalPlayback,
                onRefreshDandanplay = onRefreshDandanplay,
                onSelectDandanplayMatch = onSelectDandanplayMatch,
                onClearDandanplayCache = onClearDandanplayCache,
                onClearDanmakuOverlay = onClearDanmakuOverlay,
                onAttachManualDanmaku = onAttachManualDanmaku,
                onRefreshEpisodeMetadata = onRefreshEpisodeMetadata,
                onRefreshSeriesMetadata = onRefreshSeriesMetadata,
                onSaveExternalAnimeMapping = onSaveExternalAnimeMapping,
                onDeleteExternalAnimeMapping = onDeleteExternalAnimeMapping,
                onSaveExternalAnimeItemMapping = onSaveExternalAnimeItemMapping,
                onDeleteExternalAnimeItemMapping = onDeleteExternalAnimeItemMapping,
                onSearchExternalAnimeMatches = onSearchExternalAnimeMatches,
                onFetchMetadataMatchPoster = onFetchMetadataMatchPoster,
                onLoadPreparedPlayback = onLoadPreparedPlayback,
                onPrepareLocalPlayback = onPrepareLocalPlayback,
                onPlayLocalPlayback = onPlayLocalPlayback,
                modifier = Modifier.width(inspectorWidth.dp),
            )
        }
    }
    if (showLibraryImportPanel) {
        LibraryImportPanelDialog(
            strings = strings,
            registeredRoots = registeredRoots,
            indexedLibrary = indexedLibrary,
            isIndexing = isIndexing,
            libraryError = libraryError,
            lastScanStats = lastScanStats,
            onAddLibraryFolder = onAddLibraryFolder,
            onImportAniRssOutputFolder = onImportAniRssOutputFolder,
            onRescanRegisteredRoots = onRescanRegisteredRoots,
            onRemoveRegisteredRoot = onRemoveRegisteredRoot,
            onDismiss = { showLibraryImportPanel = false },
        )
    }
}

@Composable
internal fun InspectorResizeHandle(
    label: String,
    resetLabel: String,
    isCustomWidth: Boolean,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (isCustomWidth) {
        DanmakuColors.AccentSoft.copy(alpha = 0.88f)
    } else {
        DanmakuColors.SurfaceRaised.copy(alpha = 0.74f)
    }
    val contentColor = if (isCustomWidth) DanmakuColors.Accent else DanmakuColors.TextMuted
    Box(
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundColor)
            .clickable(enabled = isCustomWidth, onClick = onReset),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = if (isCustomWidth) resetLabel else label,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun LibraryWorkspaceRail(
    strings: DesktopStrings,
    selectedView: WindowsLibraryView,
    registeredRoots: List<DesktopLibraryRoot>,
    localEpisodeCount: Int,
    nextUpCount: Int,
    continueWatchingCount: Int,
    recentlyWatchedCount: Int,
    favoriteCount: Int,
    seriesCount: Int,
    externalTrackingPlan: ExternalAnimeTrackingPlan?,
    isIndexing: Boolean,
    libraryError: String?,
    lastScanStats: LocalMediaLibraryScanStats?,
    compact: Boolean,
    onSelectView: (WindowsLibraryView) -> Unit,
    onOpenImportPanel: () -> Unit,
    onRescanRegisteredRoots: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkspacePanel(modifier = modifier.fillMaxHeight()) {
        Text("Danmaku", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
        if (!compact) {
            Text(strings.libraryHostSubtitle, color = DanmakuColors.TextMuted, maxLines = 1)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LibraryRailNavigationItem(
            icon = Icons.Filled.History,
            label = strings.libraryViewTitle(WindowsLibraryView.CONTINUE_WATCHING),
            count = continueWatchingCount,
            selected = selectedView == WindowsLibraryView.CONTINUE_WATCHING,
            onClick = { onSelectView(WindowsLibraryView.CONTINUE_WATCHING) },
        )
        LibraryRailNavigationItem(
            icon = Icons.Filled.PlayArrow,
            label = strings.libraryViewTitle(WindowsLibraryView.NEXT_UP),
            count = nextUpCount,
            selected = selectedView == WindowsLibraryView.NEXT_UP,
            onClick = { onSelectView(WindowsLibraryView.NEXT_UP) },
        )
        LibraryRailNavigationItem(
            icon = Icons.Filled.GridView,
            label = strings.libraryViewTitle(WindowsLibraryView.ALL_SERIES),
            count = seriesCount,
            selected = selectedView == WindowsLibraryView.ALL_SERIES,
            onClick = { onSelectView(WindowsLibraryView.ALL_SERIES) },
        )
        LibraryRailNavigationItem(
            icon = Icons.Filled.History,
            label = strings.libraryViewTitle(WindowsLibraryView.RECENTLY_WATCHED),
            count = recentlyWatchedCount,
            selected = selectedView == WindowsLibraryView.RECENTLY_WATCHED,
            onClick = { onSelectView(WindowsLibraryView.RECENTLY_WATCHED) },
        )
        LibraryRailNavigationItem(
            icon = Icons.Filled.Star,
            label = strings.libraryViewTitle(WindowsLibraryView.FAVORITES),
            count = favoriteCount,
            selected = selectedView == WindowsLibraryView.FAVORITES,
            onClick = { onSelectView(WindowsLibraryView.FAVORITES) },
        )
        LibraryRailNavigationItem(
            icon = Icons.AutoMirrored.Filled.ViewList,
            label = strings.libraryViewTitle(WindowsLibraryView.FILES),
            count = localEpisodeCount,
            selected = selectedView == WindowsLibraryView.FILES,
            onClick = { onSelectView(WindowsLibraryView.FILES) },
        )
        LibraryRailNavigationItem(
            icon = Icons.Filled.Refresh,
            label = strings.libraryViewTitle(WindowsLibraryView.EXTERNAL_SYNC),
            count = externalTrackingPlan?.summary?.updateCount ?: 0,
            selected = selectedView == WindowsLibraryView.EXTERNAL_SYNC,
            onClick = { onSelectView(WindowsLibraryView.EXTERNAL_SYNC) },
        )
        LibraryRailNavigationItem(
            icon = Icons.Filled.Devices,
            label = strings.libraryViewTitle(WindowsLibraryView.PAIRED),
            count = 0,
            selected = selectedView == WindowsLibraryView.PAIRED,
            onClick = { onSelectView(WindowsLibraryView.PAIRED) },
        )
        Divider(color = DanmakuColors.SurfaceRaised)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerIconButton(
                imageVector = Icons.Filled.Add,
                contentDescription = strings.openLibraryImportPanelAction,
                enabled = !isIndexing,
                onClick = onOpenImportPanel,
            )
            PlayerIconButton(
                imageVector = Icons.Filled.Refresh,
                contentDescription = strings.rescanFoldersAction,
                enabled = registeredRoots.isNotEmpty() && !isIndexing,
                onClick = onRescanRegisteredRoots,
            )
        }
        LibrarySourceStatus(
            icon = Icons.Filled.Computer,
            label = strings.localPcLabel,
            value = if (isIndexing) strings.indexingLabel else "$localEpisodeCount ${strings.episodesSuffix}",
            statusColor = if (libraryError == null) DanmakuColors.Good else DanmakuColors.Warning,
        )
        LibrarySourceStatus(
            icon = Icons.Filled.Refresh,
            label = strings.externalListsLabel,
            value = externalTrackingPlan?.summary?.label ?: strings.noLibraryLabel,
            statusColor = if ((externalTrackingPlan?.summary?.updateCount ?: 0) > 0) {
                DanmakuColors.Good
            } else {
                DanmakuColors.TextMuted
            },
        )
        if (!compact) {
            LibrarySourceStatus(
                icon = Icons.Filled.Devices,
                label = strings.pairedDevicesLabel,
                value = strings.lanBrowserReadyLabel,
                statusColor = DanmakuColors.Accent,
            )
        }
        libraryError?.let { error ->
            Text(error, color = DanmakuColors.Warning, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        lastScanStats?.let { stats ->
            Text(
                strings.lastScanSummary(stats.reusedItemCount, stats.refreshedItemCount),
                color = DanmakuColors.TextMuted,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Divider(color = DanmakuColors.SurfaceRaised)
        Text(strings.foldersLabel, color = DanmakuColors.TextMuted, fontWeight = FontWeight.Bold)
        if (registeredRoots.isEmpty()) {
            Text(strings.noFoldersLabel, color = DanmakuColors.TextMuted)
        } else {
            val visibleRootCount = if (compact) 3 else 5
            registeredRoots.take(visibleRootCount).forEach { root ->
                Text(root.displayName, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (registeredRoots.size > visibleRootCount) {
                Text(strings.moreItemsLabel(registeredRoots.size - visibleRootCount), color = DanmakuColors.TextMuted)
            }
        }
    }
}

@Composable
internal fun LibraryImportPanelDialog(
    strings: DesktopStrings,
    registeredRoots: List<DesktopLibraryRoot>,
    indexedLibrary: IndexedLocalLibrary?,
    isIndexing: Boolean,
    libraryError: String?,
    lastScanStats: LocalMediaLibraryScanStats?,
    onAddLibraryFolder: () -> Unit,
    onImportAniRssOutputFolder: () -> Unit,
    onRescanRegisteredRoots: () -> Unit,
    onRemoveRegisteredRoot: (DesktopLibraryRoot) -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingRemovalRoot by remember { mutableStateOf<DesktopLibraryRoot?>(null) }
    val aniRssRootCount = registeredRoots.count {
        it.provenance == DesktopLibraryRootProvenance.ANI_RSS_OUTPUT_FOLDER
    }

    AlertDialog(
        modifier = Modifier.width(880.dp),
        onDismissRequest = onDismiss,
        title = { Text(strings.libraryImportTitle) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard(
                        title = strings.foldersLabel,
                        value = registeredRoots.size.toString(),
                        caption = strings.aniRssRootCountLabel(aniRssRootCount),
                        modifier = Modifier.weight(1f),
                    )
                    SummaryCard(
                        title = strings.episodesLabel,
                        value = (indexedLibrary?.catalog?.items?.size ?: 0).toString(),
                        caption = if (isIndexing) strings.scanRunningLabel else strings.publishedLabel,
                        modifier = Modifier.weight(1f),
                    )
                    SummaryCard(
                        title = strings.lastScanTitle,
                        value = lastScanStats?.refreshedItemCount?.toString() ?: "-",
                        caption = lastScanStats?.let {
                            strings.reusedCountLabel(it.reusedItemCount)
                        } ?: strings.notRunLabel,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibraryActionButton(
                        imageVector = Icons.Filled.FolderOpen,
                        label = strings.addFolderAction,
                        enabled = !isIndexing,
                        onClick = onAddLibraryFolder,
                    )
                    LibraryActionButton(
                        imageVector = Icons.Filled.Refresh,
                        label = strings.importAniRssOutputAction,
                        enabled = !isIndexing,
                        onClick = onImportAniRssOutputFolder,
                    )
                    LibraryActionButton(
                        imageVector = Icons.Filled.Refresh,
                        label = if (isIndexing) strings.scanningAction else strings.rescanAllAction,
                        enabled = registeredRoots.isNotEmpty() && !isIndexing,
                        onClick = onRescanRegisteredRoots,
                    )
                }

                libraryError?.let { error ->
                    Text(error, color = DanmakuColors.Warning, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (isIndexing) {
                    StatusPill(
                        text = strings.indexingLibraryRootsLabel,
                        icon = Icons.Filled.Refresh,
                        active = true,
                        color = DanmakuColors.Accent,
                    )
                }

                Divider(color = DanmakuColors.SurfaceRaised)
                Text(strings.registeredRootsTitle, fontWeight = FontWeight.Bold)
                if (registeredRoots.isEmpty()) {
                    Text(
                        strings.libraryImportEmptyText,
                        color = DanmakuColors.TextMuted,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 330.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(registeredRoots, key = DesktopLibraryRoot::id) { root ->
                            LibraryImportRootRow(
                                strings = strings,
                                root = root,
                                enabled = !isIndexing,
                                onRemove = { pendingRemovalRoot = root },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onRescanRegisteredRoots,
                enabled = registeredRoots.isNotEmpty() && !isIndexing,
            ) {
                Text(if (isIndexing) strings.scanningAction else strings.rescanAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.closeAction)
            }
        },
    )

    pendingRemovalRoot?.let { root ->
        SettingsConfirmationDialog(
            title = strings.removeLibraryFolderTitle,
            text = strings.removeLibraryFolderText(root.displayName),
            confirmLabel = strings.removeFolderAction,
            onConfirm = { onRemoveRegisteredRoot(root) },
            onDismiss = { pendingRemovalRoot = null },
        )
    }
}

@Composable
internal fun LibraryImportRootRow(
    strings: DesktopStrings,
    root: DesktopLibraryRoot,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DanmakuColors.SurfaceRaised.copy(alpha = 0.58f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (root.provenance == DesktopLibraryRootProvenance.ANI_RSS_OUTPUT_FOLDER) {
                Icons.Filled.Refresh
            } else {
                Icons.Filled.FolderOpen
            },
            contentDescription = null,
            tint = DanmakuColors.Accent,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(root.displayName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusPill(
                    text = root.state.displayLabel(strings),
                    icon = if (root.state == DesktopLibraryRootState.AVAILABLE) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    active = root.state == DesktopLibraryRootState.AVAILABLE,
                    color = if (root.state == DesktopLibraryRootState.AVAILABLE) DanmakuColors.Good else DanmakuColors.Warning,
                )
            }
            Text(root.provenance.displayLabel(strings), color = DanmakuColors.TextMuted, maxLines = 1)
            Text(
                root.normalizedPath.toString(),
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                root.lastScannedAtEpochMs?.let(strings.lastScannedAtLabel) ?: strings.notScannedYetLabel,
                color = DanmakuColors.TextMuted,
                maxLines = 1,
            )
            root.lastError?.let { error ->
                Text(error, color = DanmakuColors.Warning, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        LibraryActionButton(
            imageVector = Icons.Filled.Delete,
            label = strings.removeAction,
            enabled = enabled,
            onClick = onRemove,
        )
    }
}

@Composable
internal fun LibraryRailNavigationItem(
    icon: ImageVector,
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DanmakuColors.AccentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) Color.White else DanmakuColors.TextMuted)
        Text(label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (count > 0) {
            Text(
                count.toString(),
                color = if (selected) Color.White else DanmakuColors.TextMuted,
                maxLines = 1,
                modifier = Modifier
                    .background(
                        if (selected) Color.White.copy(alpha = 0.16f) else DanmakuColors.SurfaceRaised,
                        RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
internal fun LibrarySourceStatus(
    icon: ImageVector,
    label: String,
    value: String,
    statusColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = label, tint = DanmakuColors.TextMuted)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, maxLines = 1)
            Text(value, color = statusColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

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
            externalTrackingPlan?.summary?.let { summary ->
                StatusPill(
                    summary.label,
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
internal fun ExternalSyncPreviewView(
    strings: DesktopStrings,
    plan: ExternalAnimeTrackingPlan?,
    isSyncing: Boolean,
    onSync: (ExternalAnimeTrackingPlan) -> Unit,
) {
    if (plan == null) {
        EmptyState(strings.noExternalSyncLibraryText)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard(
                title = strings.readySummaryTitle,
                value = plan.summary.updateCount.toString(),
                caption = strings.providerUpdatesCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.conflictsSummaryTitle,
                value = plan.summary.conflictCount.toString(),
                caption = strings.externalAheadCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.skippedLabel,
                value = plan.summary.skippedCount.toString(),
                caption = strings.mappingChecksCaption,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                enabled = plan.updates.isNotEmpty() && !isSyncing,
                onClick = { onSync(plan) },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isSyncing) strings.syncingUpdatesAction else strings.syncReadyUpdatesAction)
            }
            Text(
                if (plan.updates.isEmpty()) {
                    strings.noProviderWritesReadyText
                } else {
                    strings.writesReadyUpdatesText(plan.updates.size)
                },
                color = DanmakuColors.TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (plan.summary.providerUpdateCounts.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                plan.summary.providerUpdateCounts.toSortedMap(compareBy { it.name }).forEach { (provider, count) ->
                    StatusPill("${provider.displayName}: $count", icon = Icons.Filled.Refresh, active = count > 0)
                }
            }
        }
        Text(strings.dryRunUpdatesTitle, fontWeight = FontWeight.Bold)
        if (plan.updates.isEmpty()) {
            EmptyState(strings.noExternalProgressUpdatesText)
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(plan.updates, key = { update -> "${update.mapping.localSeriesId}-${update.mapping.animeId.provider}" }) { update ->
                    ExternalSyncUpdateRow(update)
                }
            }
        }
        Text(strings.conflictsSummaryTitle, fontWeight = FontWeight.Bold)
        if (plan.conflicts.isEmpty()) {
            EmptyState(strings.noExternalProgressConflictsText)
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(plan.conflicts, key = { conflict -> "${conflict.mapping.localSeriesId}-${conflict.mapping.animeId.provider}" }) { conflict ->
                    ExternalSyncConflictRow(strings = strings, conflict = conflict)
                }
            }
        }
        Text(strings.syncFailuresTitle, fontWeight = FontWeight.Bold)
        if (plan.failures.isEmpty()) {
            EmptyState(strings.noSyncFailuresText)
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 180.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(plan.failures, key = { failure -> "${failure.animeId.provider}-${failure.animeId.value}" }) { failure ->
                    ExternalSyncFailureRow(strings = strings, failure = failure)
                }
            }
        }
        if (plan.skipped.isNotEmpty()) {
            Text(strings.skippedLabel, fontWeight = FontWeight.Bold)
            LazyColumn(
                modifier = Modifier.heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(plan.skipped.take(40), key = { skip -> "${skip.localSeriesId}-${skip.provider}-${skip.reason}" }) { skip ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DanmakuColors.SurfaceRaised, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = DanmakuColors.TextMuted)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(skip.provider?.displayName ?: strings.externalProviderLabel, color = Color.White, maxLines = 1)
                            Text(skip.reason.displayName, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(skip.localSeriesId, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (plan.skipped.size > 40) {
                Text(strings.moreSkippedLabel(plan.skipped.size - 40), color = DanmakuColors.TextMuted)
            }
        }
    }
}

@Composable
internal fun ExternalSyncConflictRow(
    strings: DesktopStrings,
    conflict: app.danmaku.domain.ExternalAnimeTrackingPlanConflict,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DanmakuColors.SurfaceRaised, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = DanmakuColors.Warning)
        Column(modifier = Modifier.weight(1f)) {
            Text(conflict.series.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${conflict.mapping.animeId.provider.displayName} #${conflict.mapping.animeId.value}",
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(conflict.reason.displayName, color = DanmakuColors.Warning, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(strings.localWatchedEpisodesLabel(conflict.localUpdate.watchedEpisodes ?: 0), color = DanmakuColors.TextMuted, maxLines = 1)
            Text(strings.externalWatchedEpisodesLabel(conflict.externalEntry.watchedEpisodes ?: 0), color = DanmakuColors.Warning, maxLines = 1)
        }
    }
}

@Composable
internal fun ExternalSyncFailureRow(
    strings: DesktopStrings,
    failure: app.danmaku.domain.ExternalAnimeSyncFailure,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DanmakuColors.SurfaceRaised, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = DanmakuColors.Warning)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${failure.animeId.provider.displayName} #${failure.animeId.value}",
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(failure.message, color = DanmakuColors.Warning, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(strings.syncAttemptLabel(failure.attemptCount), color = DanmakuColors.TextMuted, maxLines = 1)
            Text(strings.retryAtLabel(failure.retryAfterEpochMs), color = DanmakuColors.TextMuted, maxLines = 1)
        }
    }
}

@Composable
internal fun ExternalSyncUpdateRow(
    update: app.danmaku.domain.ExternalAnimeTrackingPlanUpdate,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DanmakuColors.SurfaceRaised, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = DanmakuColors.Good)
        Column(modifier = Modifier.weight(1f)) {
            Text(update.series.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${update.mapping.animeId.provider.displayName} #${update.mapping.animeId.value}",
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(update.update.status.displayName, color = DanmakuColors.Good, maxLines = 1)
            Text(
                "${update.update.watchedEpisodes ?: 0}/${update.series.episodeCount} watched",
                color = DanmakuColors.TextMuted,
                maxLines = 1,
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
    compact: Boolean,
) {
    @Composable
    fun ToolbarActions() {
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


