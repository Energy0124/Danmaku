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
import app.danmaku.domain.LibraryQualityIssue
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
import app.danmaku.domain.libraryQualityReport
import app.danmaku.domain.nextItem
import app.danmaku.domain.nextUpItems
import app.danmaku.domain.previousItem
import app.danmaku.domain.recentlyWatchedItems
import app.danmaku.domain.stableKey
import app.danmaku.domain.toPlaybackProgress
import app.danmaku.domain.seriesWatchSummaryById
import app.danmaku.domain.watchStatusByMediaId
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.LanPlaybackPreparation
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.LanPlaybackTarget
import app.danmaku.library.jvm.JvmLanLibraryClient
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
    localAnimeListEntryBySeriesId: Map<String, LocalAnimeListEntry>,
    libraryQualityIssueDecisions: List<DesktopLibraryQualityIssueDecision>,
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
    onSetLibraryQualityIssueDecision: (LibraryQualityIssue, DesktopLibraryQualityIssueDecisionState?) -> Unit,
    onApplyLibraryQualityIssueMappings: (LibraryQualityIssue) -> Unit,
    onRefreshLibraryQualityIssueMetadata: (LibraryQualityIssue) -> Unit,
    onSaveLocalAnimeListEntry: (LibrarySeries, LocalAnimeListStatus, Int?, String?) -> Unit,
    onDeleteLocalAnimeListEntry: (LibrarySeries) -> Unit,
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
    var localWatchListFilter by remember { mutableStateOf(LocalWatchListFilter.ANY) }
    LaunchedEffect(searchSeed, searchSeedVersion) {
        val query = searchSeed.trim()
        if (query.isNotBlank()) {
            selectedView = WindowsLibraryView.ALL_SERIES
            searchText = query
        }
    }
    val catalog = indexedLibrary?.catalog
    val libraryQualityReport = remember(catalog) {
        catalog?.libraryQualityReport()
    }
    val libraryQualityDecisionByKey = remember(libraryQualityIssueDecisions) {
        libraryQualityIssueDecisions.associateBy(DesktopLibraryQualityIssueDecision::issueKey)
    }
    val openLibraryQualityIssueCount = remember(libraryQualityReport, libraryQualityDecisionByKey) {
        libraryQualityReport
            ?.issues
            ?.count { issue -> issue.stableKey() !in libraryQualityDecisionByKey }
            ?: 0
    }
    val effectiveFavoriteFilter = if (selectedView == WindowsLibraryView.FAVORITES) {
        LibraryFavoriteFilter.FAVORITES_ONLY
    } else {
        favoriteFilter
    }
    val seriesIdByMediaId = remember(series) { series.seriesIdByMediaId() }
    val filteredEpisodes = remember(
        catalog,
        searchText,
        sort,
        subtitleFilter,
        effectiveFavoriteFilter,
        favoriteMediaIds,
        seriesIdByMediaId,
        localAnimeListEntryBySeriesId,
        localWatchListFilter,
    ) {
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
            ?.filterByLocalWatchList(
                seriesIdByMediaId = seriesIdByMediaId,
                localAnimeListEntryBySeriesId = localAnimeListEntryBySeriesId,
                filter = localWatchListFilter,
            )
            .orEmpty()
    }
    val filtersAreDefault = searchText.isBlank() &&
        sort == LibraryCatalogSort.TITLE &&
        subtitleFilter == LibrarySubtitleFilter.ANY &&
        effectiveFavoriteFilter == LibraryFavoriteFilter.ANY &&
        localWatchListFilter == LocalWatchListFilter.ANY
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
                libraryQualityIssueCount = openLibraryQualityIssueCount,
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
                localWatchListFilter = localWatchListFilter,
                onLocalWatchListFilterChange = { localWatchListFilter = it },
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
                localAnimeListEntryBySeriesId = localAnimeListEntryBySeriesId,
                localWatchListCount = localAnimeListEntryBySeriesId.size,
                libraryQualityReport = libraryQualityReport,
                libraryQualityDecisionByKey = libraryQualityDecisionByKey,
                externalTrackingPlan = externalTrackingPlan,
                isExternalAnimeSyncing = isExternalAnimeSyncing,
                isPreparing = isPreparing,
                compact = compactWorkspace,
                onSelectSeries = onSelectSeries,
                onShowDetails = onShowDetails,
                onSetFavorite = onSetFavorite,
                onSetLibraryQualityIssueDecision = onSetLibraryQualityIssueDecision,
                onApplyLibraryQualityIssueMappings = onApplyLibraryQualityIssueMappings,
                onRefreshLibraryQualityIssueMetadata = onRefreshLibraryQualityIssueMetadata,
                onSaveLocalAnimeListEntry = onSaveLocalAnimeListEntry,
                onDeleteLocalAnimeListEntry = onDeleteLocalAnimeListEntry,
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
                    localWatchListFilter = LocalWatchListFilter.ANY
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
                localAnimeListEntry = selectedInspectorSeries?.id?.let(localAnimeListEntryBySeriesId::get),
                isPreparing = isPreparing,
                compact = compactWorkspace,
                onShowDetails = onShowDetails,
                onInspectCachedDandanplay = onInspectCachedDandanplay,
                onSetFavorite = onSetFavorite,
                onSaveLocalAnimeListEntry = onSaveLocalAnimeListEntry,
                onDeleteLocalAnimeListEntry = onDeleteLocalAnimeListEntry,
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
