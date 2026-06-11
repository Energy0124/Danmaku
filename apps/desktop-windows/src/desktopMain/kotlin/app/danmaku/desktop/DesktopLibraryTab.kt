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
internal fun MediaLibraryTab(
    strings: DesktopStrings,
    registeredRoots: List<DesktopLibraryRoot>,
    indexedLibrary: IndexedLocalLibrary?,
    searchSeed: String,
    searchSeedVersion: Int,
    seriesPosterById: Map<String, Path?>,
    externalAnimeMappings: List<ExternalAnimeMapping>,
    externalAnimeItemMappingsByMediaId: Map<String, List<DesktopExternalAnimeItemMapping>>,
    externalAnimeProviderSettings: ExternalAnimeProviderSettings,
    originalSeriesTitleByMediaId: Map<String, String>,
    refreshingMetadataMediaIds: Set<String>,
    refreshingMetadataSeriesIds: Set<String>,
    playbackProgresses: List<PlaybackProgress>,
    favoriteMediaIds: Set<String>,
    externalAnimeSyncFailures: List<ExternalAnimeSyncFailure>,
    isExternalAnimeSyncing: Boolean,
    isIndexing: Boolean,
    isPreparingLocalPlayback: Boolean,
    selectedLocalPlaybackPreparation: DesktopLocalPlaybackPreparation?,
    dandanplayCacheStatus: DandanplayPlaybackUiStatus?,
    autoNextLocalPlayback: Boolean,
    libraryError: String?,
    lastScanStats: LocalMediaLibraryScanStats?,
    onAddLibraryFolder: () -> Unit,
    onImportAniRssOutputFolder: () -> Unit,
    onRescanRegisteredRoots: () -> Unit,
    onRemoveRegisteredRoot: (DesktopLibraryRoot) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
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
    remoteBrowser: @Composable () -> Unit,
) {
    TabScaffold {
        val nextUpItems = remember(indexedLibrary, playbackProgresses) {
            indexedLibrary?.catalog?.nextUpItems(playbackProgresses).orEmpty()
        }
        val continueWatchingItems = remember(indexedLibrary, playbackProgresses) {
            indexedLibrary?.catalog?.continueWatchingItems(playbackProgresses).orEmpty()
        }
        val recentlyWatchedItems = remember(indexedLibrary, playbackProgresses) {
            indexedLibrary?.catalog?.recentlyWatchedItems(playbackProgresses).orEmpty()
        }
        val watchStatusById = remember(indexedLibrary, playbackProgresses) {
            indexedLibrary?.catalog?.watchStatusByMediaId(playbackProgresses).orEmpty()
        }
        val seriesWatchSummaryById = remember(indexedLibrary, playbackProgresses) {
            indexedLibrary?.catalog?.seriesWatchSummaryById(playbackProgresses).orEmpty()
        }
        var selectedSeriesId by remember(indexedLibrary?.catalog) { mutableStateOf<String?>(null) }
        var selectedEpisodeId by remember(indexedLibrary?.catalog) { mutableStateOf<String?>(null) }
        val series = remember(indexedLibrary?.catalog) {
            indexedLibrary?.catalog?.groupedSeries().orEmpty()
        }
        val externalTrackingPlan = remember(
            indexedLibrary?.catalog,
            externalAnimeMappings,
            playbackProgresses,
            externalAnimeSyncFailures,
        ) {
            indexedLibrary?.catalog?.externalAnimeTrackingPlan(
                mappings = externalAnimeMappings,
                progresses = playbackProgresses,
                failures = externalAnimeSyncFailures,
            )
        }
        fun selectSeries(series: LibrarySeries) {
            selectedSeriesId = series.id
            selectedEpisodeId = null
        }
        fun showEpisodeDetails(item: LibraryMediaItem) {
            selectedEpisodeId = item.id
            series
                .firstOrNull { librarySeries ->
                    librarySeries.seasons.any { season -> season.items.any { seriesItem -> seriesItem.id == item.id } }
                }
                ?.let { selectedSeriesId = it.id }
            onInspectCachedDandanplay(item)
        }
        val selectedSeries = series.firstOrNull { it.id == selectedSeriesId } ?: series.firstOrNull()
        val selectedEpisodeDetail = remember(
            indexedLibrary?.catalog,
            playbackProgresses,
            selectedEpisodeId,
            selectedLocalPlaybackPreparation?.item?.id,
        ) {
            val catalog = indexedLibrary?.catalog
            selectedEpisodeId
                ?.let { id -> catalog?.episodeDetail(id, playbackProgresses) }
                ?: selectedLocalPlaybackPreparation
                    ?.item
                    ?.id
                    ?.let { id -> catalog?.episodeDetail(id, playbackProgresses) }
        }
        WindowsLibraryWorkspace(
            strings = strings,
            registeredRoots = registeredRoots,
            indexedLibrary = indexedLibrary,
            searchSeed = searchSeed,
            searchSeedVersion = searchSeedVersion,
            series = series,
            seriesPosterById = seriesPosterById,
            externalTrackingPlan = externalTrackingPlan,
            isExternalAnimeSyncing = isExternalAnimeSyncing,
            externalAnimeMappings = externalAnimeMappings,
            externalAnimeItemMappingsByMediaId = externalAnimeItemMappingsByMediaId,
            externalAnimeProviderSettings = externalAnimeProviderSettings,
            originalSeriesTitleByMediaId = originalSeriesTitleByMediaId,
            refreshingMetadataMediaIds = refreshingMetadataMediaIds,
            refreshingMetadataSeriesIds = refreshingMetadataSeriesIds,
            selectedSeries = selectedSeries,
            selectedEpisodeDetail = selectedEpisodeDetail,
            selectedLocalPlaybackPreparation = selectedLocalPlaybackPreparation,
            dandanplayCacheStatus = dandanplayCacheStatus,
            autoNextLocalPlayback = autoNextLocalPlayback,
            nextUpItems = nextUpItems,
            continueWatchingItems = continueWatchingItems,
            recentlyWatchedItems = recentlyWatchedItems,
            watchStatusById = watchStatusById,
            seriesWatchSummaryById = seriesWatchSummaryById,
            favoriteMediaIds = favoriteMediaIds,
            isIndexing = isIndexing,
            isPreparing = isPreparingLocalPlayback,
            libraryError = libraryError,
            lastScanStats = lastScanStats,
            onAddLibraryFolder = onAddLibraryFolder,
            onImportAniRssOutputFolder = onImportAniRssOutputFolder,
            onRescanRegisteredRoots = onRescanRegisteredRoots,
            onRemoveRegisteredRoot = onRemoveRegisteredRoot,
            onSelectSeries = ::selectSeries,
            onShowDetails = ::showEpisodeDetails,
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
            onSyncExternalAnimePlan = onSyncExternalAnimePlan,
            onLoadPreparedPlayback = onLoadPreparedPlayback,
            onPrepareLocalPlayback = onPrepareLocalPlayback,
            onPlayLocalPlayback = onPlayLocalPlayback,
            remoteBrowser = remoteBrowser,
        )
    }
}

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

@Composable
internal fun AllSeriesView(
    strings: DesktopStrings,
    catalog: LibraryCatalog?,
    visibleSeries: List<LibrarySeries>,
    selectedSeries: LibrarySeries?,
    coverBySeriesId: Map<String, Path?>,
    refreshingMetadataSeriesIds: Set<String>,
    continueWatchingItems: List<LibraryPlaybackProgressItem>,
    nextUpItems: List<LibraryNextUpItem>,
    watchStatusById: Map<String, LibraryWatchStatus>,
    seriesWatchSummaryById: Map<String, LibrarySeriesWatchSummary>,
    isPreparing: Boolean,
    compact: Boolean,
    onResetFilters: () -> Unit,
    onSelectSeries: (LibrarySeries) -> Unit,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onRefreshSeriesMetadata: (LibrarySeries) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    LibraryProgressOverview(
        strings = strings,
        continueWatchingItems = continueWatchingItems,
        nextUpItems = nextUpItems,
        isPreparing = isPreparing,
        compact = compact,
        onShowDetails = onShowDetails,
        onPlayLocalPlayback = onPlayLocalPlayback,
    )
    Text(strings.libraryViewTitle(WindowsLibraryView.ALL_SERIES), style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
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
                .height(if (compact) 460.dp else 500.dp)
                .libraryCollectionKeyboard(
                    itemCount = visibleSeries.size,
                    selectedIndex = visibleSeries.indexOfFirst { it.id == selectedSeries?.id }.coerceAtLeast(0),
                    columnStride = if (compact) 4 else 6,
                    onSelectedIndexChange = { index -> visibleSeries.getOrNull(index)?.let(onSelectSeries) },
                    onOpenSelected = {
                        visibleSeries
                            .getOrNull(visibleSeries.indexOfFirst { it.id == selectedSeries?.id }.coerceAtLeast(0))
                            ?.let(onSelectSeries)
                    },
                    onPlaySelected = {
                        visibleSeries
                            .getOrNull(visibleSeries.indexOfFirst { it.id == selectedSeries?.id }.coerceAtLeast(0))
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
            items(visibleSeries, key = { it.id }) { librarySeries ->
                SeriesPosterCard(
                    strings = strings,
                    series = librarySeries,
                    coverPath = coverBySeriesId[librarySeries.id],
                    watchSummary = seriesWatchSummaryById[librarySeries.id],
                    isSelected = librarySeries.id == selectedSeries?.id,
                    isPreparing = isPreparing,
                    isRefreshingMetadata = librarySeries.id in refreshingMetadataSeriesIds,
                    onSelect = { onSelectSeries(librarySeries) },
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

@Composable
internal fun LibraryInspectorPane(
    strings: DesktopStrings,
    selectedSeries: LibrarySeries?,
    selectedEpisodeDetail: LibraryEpisodeDetail?,
    selectedItem: LibraryMediaItem?,
    selectedLocalPlaybackPreparation: DesktopLocalPlaybackPreparation?,
    dandanplayCacheStatus: DandanplayPlaybackUiStatus?,
    autoNextLocalPlayback: Boolean,
    externalAnimeMappings: List<ExternalAnimeMapping>,
    externalAnimeItemMappings: List<DesktopExternalAnimeItemMapping>,
    externalAnimeProviderSettings: ExternalAnimeProviderSettings,
    originalSeriesTitleByMediaId: Map<String, String>,
    refreshingMetadataMediaIds: Set<String>,
    refreshingMetadataSeriesIds: Set<String>,
    coverPath: Path?,
    watchSummary: LibrarySeriesWatchSummary?,
    watchStatusById: Map<String, LibraryWatchStatus>,
    favoriteMediaIds: Set<String>,
    isPreparing: Boolean,
    compact: Boolean,
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
    onLoadPreparedPlayback: (DesktopLocalPlaybackPreparation) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkspacePanel(modifier = modifier.fillMaxHeight()) {
        if (selectedSeries == null || selectedItem == null) {
            Text(strings.inspectorTitle, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
            EmptyState(strings.inspectorEmptyText)
            return@WorkspacePanel
        }
        val activePreparation = selectedLocalPlaybackPreparation?.takeIf { it.item.id == selectedItem.id }
        val isFavorite = selectedItem.id in favoriteMediaIds
        val status = dandanplayCacheStatus?.takeIf { it.mediaId == selectedItem.id }
        val hasDanmakuOverlay = activePreparation?.subtitles?.any(DesktopPlaybackSubtitle::isDanmakuOverlay) == true
        val isRefreshingEpisodeMetadata = selectedItem.id in refreshingMetadataMediaIds
        val isRefreshingSeriesMetadata = selectedSeries.id in refreshingMetadataSeriesIds
        val metadataReadiness = selectedItem.metadataReadiness(
            strings = strings,
            isRefreshing = isRefreshingEpisodeMetadata || isRefreshingSeriesMetadata,
            hasPoster = coverPath != null || selectedItem.posterPath != null,
        )
        val originalSeriesTitle = originalSeriesTitleByMediaId[selectedItem.id]
        var episodeActionsExpanded by remember(selectedItem.id, activePreparation?.item?.id) { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 166.dp else 210.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DanmakuColors.SurfaceRaised),
        ) {
            SeriesPosterImage(
                coverPath = coverPath,
                title = selectedSeries.title,
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                watchSummary.progressLabel(),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(topEnd = 6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                maxLines = 1,
            )
        }
        Text(selectedSeries.title, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            selectedEpisodeDetail?.mediaItem?.episodeTitle ?: strings.nextPlayableLabel(selectedItem.episodeTitle),
            color = DanmakuColors.TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        originalSeriesTitle
            ?.takeIf { it.isNotBlank() && it != selectedItem.seriesTitle }
            ?.let { fileGroup ->
                Text(strings.fileGroupLabel(fileGroup), color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        MiniProgressBar(percent = watchStatusById[selectedItem.id]?.progress?.progressPercent())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onPlayLocalPlayback(selectedItem) },
                enabled = !isPreparing,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = strings.playAction, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (isPreparing) {
                        strings.loadingAction
                    } else {
                        selectedItem.primaryPlaybackActionLabel(watchStatusById[selectedItem.id], strings)
                    },
                )
            }
            LibraryActionButton(
                imageVector = Icons.Filled.Refresh,
                label = if (isPreparing) strings.preparingAction else if (compact) strings.prepareShortAction else strings.prepareAction,
                modifier = Modifier.weight(1f),
                enabled = !isPreparing,
                onClick = { onPrepareLocalPlayback(selectedItem) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerIconButton(
                imageVector = Icons.Filled.Star,
                contentDescription = if (isFavorite) strings.unfavoriteAction else strings.favoriteAction,
                onClick = { onSetFavorite(selectedItem, !isFavorite) },
            )
            PlayerIconButton(
                imageVector = Icons.Filled.Subtitles,
                contentDescription = strings.showEpisodeDetailsAction,
                onClick = { onShowDetails(selectedItem) },
            )
            PlayerIconButton(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = strings.checkCachedDanmakuAction,
                active = status != null && !status.summary.isDandanplayWarningStatus(),
                onClick = { onInspectCachedDandanplay(selectedItem) },
            )
            Box {
                PlayerIconButton(
                    imageVector = Icons.Filled.MoreHoriz,
                    contentDescription = strings.moreEpisodeActionsAction,
                    onClick = { episodeActionsExpanded = true },
                )
                DropdownMenu(
                    expanded = episodeActionsExpanded,
                    onDismissRequest = { episodeActionsExpanded = false },
                ) {
                    DropdownMenuItem(
                        enabled = !isPreparing,
                        onClick = {
                            episodeActionsExpanded = false
                            onPrepareLocalPlayback(selectedItem)
                        },
                    ) {
                        Text(strings.preparePlaybackAction)
                    }
                    DropdownMenuItem(
                        enabled = !isPreparing && !isRefreshingEpisodeMetadata,
                        onClick = {
                            episodeActionsExpanded = false
                            onRefreshEpisodeMetadata(selectedItem)
                        },
                    ) {
                        Text(strings.refreshEpisodeMetadataAction)
                    }
                    DropdownMenuItem(
                        enabled = !isPreparing && !isRefreshingSeriesMetadata,
                        onClick = {
                            episodeActionsExpanded = false
                            onRefreshSeriesMetadata(selectedSeries)
                        },
                    ) {
                        Text(strings.refreshSeriesMetadataAction)
                    }
                    activePreparation?.let { preparation ->
                        DropdownMenuItem(
                            onClick = {
                                episodeActionsExpanded = false
                                onLoadPreparedPlayback(preparation)
                            },
                        ) {
                            Text(strings.loadIntoPlayerAction)
                        }
                        DropdownMenuItem(
                            enabled = !isPreparing,
                            onClick = {
                                episodeActionsExpanded = false
                                onRefreshDandanplay(preparation)
                            },
                        ) {
                            Text(strings.refreshDanmakuAction)
                        }
                        DropdownMenuItem(
                            enabled = !isPreparing,
                            onClick = {
                                episodeActionsExpanded = false
                                onAttachManualDanmaku(preparation)
                            },
                        ) {
                            Text(strings.attachLocalDanmakuAction)
                        }
                        DropdownMenuItem(
                            enabled = hasDanmakuOverlay && !isPreparing,
                            onClick = {
                                episodeActionsExpanded = false
                                onClearDanmakuOverlay(preparation)
                            },
                        ) {
                            Text(strings.removeOverlayAction)
                        }
                        DropdownMenuItem(
                            enabled = !isPreparing,
                            onClick = {
                                episodeActionsExpanded = false
                                onClearDandanplayCache(preparation)
                            },
                        ) {
                            Text(strings.clearDanmakuCacheAction)
                        }
                    }
                    DropdownMenuItem(
                        onClick = {
                            episodeActionsExpanded = false
                            onSetAutoNextLocalPlayback(!autoNextLocalPlayback)
                        },
                    ) {
                        Text(if (autoNextLocalPlayback) strings.disableAutoNextAction else strings.enableAutoNextAction)
                    }
                }
            }
        }
        Divider(color = DanmakuColors.SurfaceRaised)
        Text(strings.readinessTitle, fontWeight = FontWeight.Bold)
        InspectorStatusRow(
            icon = if (activePreparation != null) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            label = if (activePreparation != null) strings.preparedPlaybackLabel else strings.prepareToInspectTracksLabel,
            value = activePreparation?.resumePositionMs?.let(strings.resumeValueLabel) ?: strings.notPreparedLabel,
            color = if (activePreparation != null) DanmakuColors.Good else DanmakuColors.TextMuted,
        )
        InspectorStatusRow(
            icon = if (status?.summary?.isDandanplayWarningStatus() == true) Icons.Filled.Warning else Icons.Filled.CheckCircle,
            label = strings.danmakuTitle,
            value = status?.summary ?: strings.notCheckedYetLabel,
            color = when {
                status == null -> DanmakuColors.TextMuted
                status.summary.isDandanplayWarningStatus() -> DanmakuColors.Warning
                else -> DanmakuColors.Good
            },
        )
        InspectorStatusRow(
            icon = metadataReadiness.icon,
            label = metadataReadiness.label,
            value = metadataReadiness.detail,
            color = metadataReadiness.color,
        )
        InspectorStatusRow(
            icon = Icons.Filled.Subtitles,
            label = strings.subtitleLabel,
            value = strings.subtitlesIndexedLabel(selectedItem.subtitles.size),
            color = if (selectedItem.subtitles.isNotEmpty()) DanmakuColors.Good else DanmakuColors.TextMuted,
        )
        ExternalAnimeMappingPanel(
            strings = strings,
            selectedSeries = selectedSeries,
            selectedItem = selectedItem,
            seriesMappings = externalAnimeMappings,
            itemMappings = externalAnimeItemMappings,
            externalAnimeProviderSettings = externalAnimeProviderSettings,
            onSaveExternalAnimeMapping = onSaveExternalAnimeMapping,
            onDeleteExternalAnimeMapping = onDeleteExternalAnimeMapping,
            onSaveExternalAnimeItemMapping = onSaveExternalAnimeItemMapping,
            onDeleteExternalAnimeItemMapping = onDeleteExternalAnimeItemMapping,
            onSearchExternalAnimeMatches = onSearchExternalAnimeMatches,
            onFetchMetadataMatchPoster = onFetchMetadataMatchPoster,
        )
        selectedEpisodeDetail?.let { detail ->
            MetadataRow("Season", detail.season.label)
            MetadataRow("Watch", detail.watchStatus.statusLabel())
            MetadataRow("Size", detail.mediaItem.sizeBytes.formatLibrarySize())
        }
        Divider(color = DanmakuColors.SurfaceRaised)
        Text(strings.episodesTitle, fontWeight = FontWeight.Bold)
        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
            selectedSeries.seasons.forEach { season ->
                item(key = season.id) {
                    Text(season.label, color = DanmakuColors.TextMuted, fontWeight = FontWeight.Bold)
                }
                items(season.items, key = { it.id }) { item ->
                    CompactInspectorEpisodeRow(
                        strings = strings,
                        item = item,
                        selected = item.id == selectedItem.id,
                        watchStatus = watchStatusById[item.id],
                        originalSeriesTitle = originalSeriesTitleByMediaId[item.id],
                        isRefreshingMetadata = item.id in refreshingMetadataMediaIds,
                        onClick = { onShowDetails(item) },
                    )
                }
            }
        }
        activePreparation?.let { preparation ->
            Divider(color = DanmakuColors.SurfaceRaised)
            Text(strings.advancedTitle, fontWeight = FontWeight.Bold)
            LibraryActionButton(
                imageVector = Icons.Filled.PlayArrow,
                label = strings.loadIntoPlayerAction,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onLoadPreparedPlayback(preparation) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryActionButton(
                    imageVector = Icons.Filled.Refresh,
                    label = strings.refreshDanmakuAction,
                    modifier = Modifier.weight(1f),
                    enabled = !isPreparing,
                    onClick = { onRefreshDandanplay(preparation) },
                )
                LibraryActionButton(
                    imageVector = Icons.Filled.Subtitles,
                    label = strings.attachLocalDanmakuShortAction,
                    modifier = Modifier.weight(1f),
                    enabled = !isPreparing,
                    onClick = { onAttachManualDanmaku(preparation) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryActionButton(
                    imageVector = Icons.Filled.Refresh,
                    label = if (isRefreshingEpisodeMetadata) strings.metadataLoadingLabel else strings.refreshEpisodeMetadataAction,
                    modifier = Modifier.weight(1f),
                    enabled = !isPreparing && !isRefreshingEpisodeMetadata,
                    onClick = { onRefreshEpisodeMetadata(selectedItem) },
                )
                LibraryActionButton(
                    imageVector = Icons.Filled.Refresh,
                    label = if (isRefreshingSeriesMetadata) strings.metadataLoadingLabel else strings.refreshSeriesMetadataAction,
                    modifier = Modifier.weight(1f),
                    enabled = !isPreparing && !isRefreshingSeriesMetadata,
                    onClick = { onRefreshSeriesMetadata(selectedSeries) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryActionButton(
                    imageVector = Icons.Filled.Subtitles,
                    label = strings.removeOverlayAction,
                    modifier = Modifier.weight(1f),
                    enabled = hasDanmakuOverlay && !isPreparing,
                    onClick = { onClearDanmakuOverlay(preparation) },
                )
                LibraryActionButton(
                    imageVector = Icons.Filled.Refresh,
                    label = strings.clearCacheAction,
                    modifier = Modifier.weight(1f),
                    enabled = !isPreparing,
                    onClick = { onClearDandanplayCache(preparation) },
                )
            }
            LibraryActionButton(
                imageVector = Icons.Filled.FastForward,
                label = if (autoNextLocalPlayback) strings.autoNextOnLabel else strings.autoNextOffLabel,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSetAutoNextLocalPlayback(!autoNextLocalPlayback) },
            )
            status?.details?.forEach { detail ->
                MetadataRow(detail.label, detail.value)
            }
            status?.let {
                DandanplayMatchCandidatePicker(
                    preparation = preparation,
                    status = it,
                    isPreparing = isPreparing,
                    onSelectDandanplayMatch = onSelectDandanplayMatch,
                )
            }
        }
    }
}

@Composable
internal fun ExternalAnimeMappingPanel(
    strings: DesktopStrings,
    selectedSeries: LibrarySeries,
    selectedItem: LibraryMediaItem,
    seriesMappings: List<ExternalAnimeMapping>,
    itemMappings: List<DesktopExternalAnimeItemMapping>,
    externalAnimeProviderSettings: ExternalAnimeProviderSettings,
    onSaveExternalAnimeMapping: (LibrarySeries, ExternalAnimeProvider, String) -> Unit,
    onDeleteExternalAnimeMapping: (LibrarySeries, ExternalAnimeProvider) -> Unit,
    onSaveExternalAnimeItemMapping: (LibraryMediaItem, ExternalAnimeProvider, String) -> Unit,
    onDeleteExternalAnimeItemMapping: (LibraryMediaItem, ExternalAnimeProvider) -> Unit,
    onSearchExternalAnimeMatches: suspend (ExternalAnimeMatchQuery, Set<ExternalAnimeProvider>) -> Result<List<ExternalAnimeMatchCandidate>>,
    onFetchMetadataMatchPoster: suspend (String?) -> Path?,
) {
    var showMatchDialog by remember(selectedSeries.id) { mutableStateOf(false) }
    val malMapping = seriesMappings.firstOrNull { it.animeId.provider == ExternalAnimeProvider.MY_ANIME_LIST }
    val bangumiMapping = seriesMappings.firstOrNull { it.animeId.provider == ExternalAnimeProvider.BANGUMI }
    val dandanplayItemMapping = itemMappings.firstOrNull { it.animeId.provider == ExternalAnimeProvider.DANDANPLAY }
    val displayedDandanplayId = dandanplayItemMapping?.animeId?.value
        ?: selectedItem.animeMetadata
            ?.animeId
            ?.takeIf { it.provider == ExternalAnimeProvider.DANDANPLAY }
            ?.value

    Divider(color = DanmakuColors.SurfaceRaised)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(strings.externalIdsTitle, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        LibraryActionButton(
            imageVector = Icons.Filled.Search,
            label = strings.matchAction,
            onClick = { showMatchDialog = true },
        )
    }
    ExternalSeriesMappingRow(
        strings = strings,
        provider = ExternalAnimeProvider.MY_ANIME_LIST,
        mapping = malMapping,
        selectedSeries = selectedSeries,
        onSave = onSaveExternalAnimeMapping,
        onDelete = onDeleteExternalAnimeMapping,
    )
    ExternalSeriesMappingRow(
        strings = strings,
        provider = ExternalAnimeProvider.BANGUMI,
        mapping = bangumiMapping,
        selectedSeries = selectedSeries,
        onSave = onSaveExternalAnimeMapping,
        onDelete = onDeleteExternalAnimeMapping,
    )
    ExternalItemMappingRow(
        strings = strings,
        provider = ExternalAnimeProvider.DANDANPLAY,
        currentId = displayedDandanplayId,
        hasManualMapping = dandanplayItemMapping != null,
        selectedItem = selectedItem,
        onSave = onSaveExternalAnimeItemMapping,
        onDelete = onDeleteExternalAnimeItemMapping,
    )
    if (showMatchDialog) {
        MetadataMatchDialog(
            strings = strings,
            selectedSeries = selectedSeries,
            currentMappings = seriesMappings,
            externalAnimeProviderSettings = externalAnimeProviderSettings,
            onSearchExternalAnimeMatches = onSearchExternalAnimeMatches,
            onFetchMetadataMatchPoster = onFetchMetadataMatchPoster,
            onSaveExternalAnimeMapping = onSaveExternalAnimeMapping,
            onDismiss = { showMatchDialog = false },
        )
    }
}

@Composable
internal fun MetadataMatchDialog(
    strings: DesktopStrings,
    selectedSeries: LibrarySeries,
    currentMappings: List<ExternalAnimeMapping>,
    externalAnimeProviderSettings: ExternalAnimeProviderSettings,
    onSearchExternalAnimeMatches: suspend (ExternalAnimeMatchQuery, Set<ExternalAnimeProvider>) -> Result<List<ExternalAnimeMatchCandidate>>,
    onFetchMetadataMatchPoster: suspend (String?) -> Path?,
    onSaveExternalAnimeMapping: (LibrarySeries, ExternalAnimeProvider, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var queryText by remember(selectedSeries.id) { mutableStateOf(selectedSeries.title) }
    val myAnimeListSearchAvailable = externalAnimeProviderSettings.myAnimeListClientId != null
    val bangumiSearchAvailable = externalAnimeProviderSettings.bangumiBaseUrl.isNotBlank() &&
        externalAnimeProviderSettings.bangumiUserAgent.isNotBlank()
    var includeMyAnimeList by remember(selectedSeries.id, myAnimeListSearchAvailable) {
        mutableStateOf(myAnimeListSearchAvailable && currentMappings.none { it.animeId.provider == ExternalAnimeProvider.MY_ANIME_LIST })
    }
    var includeBangumi by remember(selectedSeries.id, bangumiSearchAvailable) {
        mutableStateOf(bangumiSearchAvailable && currentMappings.none { it.animeId.provider == ExternalAnimeProvider.BANGUMI })
    }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var candidates by remember(selectedSeries.id) { mutableStateOf<List<ExternalAnimeMatchCandidate>>(emptyList()) }

    fun runSearch() {
        val title = queryText.trim()
        if (title.isBlank() || isSearching) return
        val providers = buildSet {
            if (includeMyAnimeList) add(ExternalAnimeProvider.MY_ANIME_LIST)
            if (includeBangumi) add(ExternalAnimeProvider.BANGUMI)
        }
        if (providers.isEmpty()) {
            searchError = strings.metadataMatchSelectProviderError
            candidates = emptyList()
            return
        }
        isSearching = true
        searchError = null
        scope.launch {
            val result = onSearchExternalAnimeMatches(
                ExternalAnimeMatchQuery(
                    title = title,
                    episodeCount = selectedSeries.episodeCount.takeIf { it > 0 },
                ),
                providers,
            )
            result.onSuccess {
                candidates = it
                searchError = if (it.isEmpty()) strings.metadataMatchNoCandidates(title) else null
            }.onFailure {
                candidates = emptyList()
                searchError = it.readableMessage()
            }
            isSearching = false
        }
    }

    AlertDialog(
        modifier = Modifier.width(860.dp),
        onDismissRequest = onDismiss,
        title = { Text(strings.metadataMatchTitle) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    strings.metadataMatchDescription(selectedSeries.title),
                    color = DanmakuColors.TextMuted,
                )
                OutlinedTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    label = { Text(strings.metadataMatchSearchTitleLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetadataMatchProviderToggle(
                        label = "MyAnimeList",
                        detail = externalAnimeProviderSettings.myAnimeListStatusText,
                        selected = includeMyAnimeList,
                        enabled = myAnimeListSearchAvailable,
                        onToggle = {
                            if (myAnimeListSearchAvailable) {
                                includeMyAnimeList = !includeMyAnimeList
                            }
                        },
                    )
                    MetadataMatchProviderToggle(
                        label = "Bangumi",
                        detail = externalAnimeProviderSettings.bangumiStatusText,
                        selected = includeBangumi,
                        enabled = bangumiSearchAvailable,
                        onToggle = {
                            if (bangumiSearchAvailable) {
                                includeBangumi = !includeBangumi
                            }
                        },
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    LibraryActionButton(
                        imageVector = Icons.Filled.Search,
                        label = if (isSearching) strings.searchingAction else strings.searchAction,
                        enabled = !isSearching && queryText.isNotBlank(),
                        onClick = ::runSearch,
                    )
                }
                if (!myAnimeListSearchAvailable || !bangumiSearchAvailable) {
                    Text(
                        buildList {
                            if (!myAnimeListSearchAvailable) add(strings.metadataMatchMyAnimeListUnavailable)
                            if (!bangumiSearchAvailable) add(strings.metadataMatchBangumiUnavailable)
                        }.joinToString("; "),
                        color = DanmakuColors.Warning,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                currentMappings.takeIf { it.isNotEmpty() }?.let { mappings ->
                    Text(
                        strings.metadataMatchCurrentMappingsPrefix + " " +
                            mappings.joinToString { "${it.animeId.provider.displayName} #${it.animeId.value}" },
                        color = DanmakuColors.TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                searchError?.let { error ->
                    Text(error, color = DanmakuColors.Warning, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Divider(color = DanmakuColors.SurfaceRaised)
                if (candidates.isEmpty()) {
                    EmptyState(strings.metadataMatchEmptyState)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(candidates, key = { "${it.anime.id.provider.name}:${it.anime.id.value}" }) { candidate ->
                            MetadataMatchCandidateRow(
                                strings = strings,
                                candidate = candidate,
                                alreadyMapped = currentMappings.any { it.animeId == candidate.anime.id },
                                onFetchPoster = onFetchMetadataMatchPoster,
                                onUse = {
                                    onSaveExternalAnimeMapping(
                                        selectedSeries,
                                        candidate.anime.id.provider,
                                        candidate.anime.id.value.toString(),
                                    )
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = ::runSearch,
                enabled = !isSearching && queryText.isNotBlank(),
            ) {
                Text(if (isSearching) strings.searchingAction else strings.searchAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.closeAction)
            }
        },
    )
}

@Composable
internal fun MetadataMatchProviderToggle(
    label: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val backgroundColor = when {
        selected -> DanmakuColors.AccentSoft
        enabled -> DanmakuColors.SurfaceRaised
        else -> DanmakuColors.SurfaceRaised.copy(alpha = 0.48f)
    }
    val iconColor = when {
        selected -> DanmakuColors.Good
        enabled -> DanmakuColors.TextMuted
        else -> DanmakuColors.Warning
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundColor)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = when {
                selected -> Icons.Filled.CheckCircle
                enabled -> Icons.Filled.Search
                else -> Icons.Filled.Warning
            },
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(16.dp),
        )
        Column {
            Text(label, maxLines = 1, color = if (enabled) Color.White else DanmakuColors.TextMuted)
            Text(detail, maxLines = 1, color = if (enabled) DanmakuColors.TextMuted else DanmakuColors.Warning)
        }
    }
}

@Composable
internal fun MetadataMatchCandidateRow(
    strings: DesktopStrings,
    candidate: ExternalAnimeMatchCandidate,
    alreadyMapped: Boolean,
    onFetchPoster: suspend (String?) -> Path?,
    onUse: () -> Unit,
) {
    val anime = candidate.anime
    var posterPath by remember(anime.id, anime.imageUrl) { mutableStateOf<Path?>(null) }
    var isPosterLoading by remember(anime.id, anime.imageUrl) { mutableStateOf(!anime.imageUrl.isNullOrBlank()) }

    LaunchedEffect(anime.id, anime.imageUrl) {
        val imageUrl = anime.imageUrl
        posterPath = null
        isPosterLoading = !imageUrl.isNullOrBlank()
        if (!imageUrl.isNullOrBlank()) {
            posterPath = runCatching { onFetchPoster(imageUrl) }.getOrNull()
            isPosterLoading = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DanmakuColors.SurfaceRaised.copy(alpha = 0.62f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetadataMatchPosterPreview(
            posterPath = posterPath,
            title = anime.titles.primary,
            isLoading = isPosterLoading,
            loadingLabel = strings.posterLoadingLabel,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(anime.titles.primary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildList {
                    add("${anime.id.provider.displayName} #${anime.id.value}")
                    anime.episodeCount?.let { add("$it ${strings.episodesSuffix}") }
                    anime.startYear?.let { add(it.toString()) }
                    candidate.matchedTitle?.takeIf { it != anime.titles.primary }?.let {
                        add("${strings.metadataMatchMatchedTitlePrefix} $it")
                    }
                    if (anime.imageUrl != null && posterPath == null) {
                        add(if (isPosterLoading) strings.posterLoadingLabel else strings.posterUnavailableLabel)
                    }
                }.joinToString(" - "),
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            anime.summary?.let {
                Text(it, color = DanmakuColors.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        StatusPill(
            text = candidate.confidence.formatConfidence(),
            icon = Icons.Filled.CheckCircle,
            active = candidate.confidence >= 0.7,
            color = if (candidate.confidence >= 0.7) DanmakuColors.Good else DanmakuColors.Accent,
        )
        Button(
            enabled = !alreadyMapped,
            onClick = onUse,
        ) {
            Text(if (alreadyMapped) strings.mappedAction else strings.useAction)
        }
    }
}

@Composable
internal fun MetadataMatchPosterPreview(
    posterPath: Path?,
    title: String,
    isLoading: Boolean,
    loadingLabel: String,
) {
    val bitmap = rememberLocalImageBitmap(posterPath)
    Box(
        modifier = Modifier
            .width(54.dp)
            .height(76.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(DanmakuColors.AccentSoft),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = title.initialsForPoster(),
                color = Color.White,
                style = MaterialTheme.typography.subtitle2,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.56f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = loadingLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun ExternalSeriesMappingRow(
    strings: DesktopStrings,
    provider: ExternalAnimeProvider,
    mapping: ExternalAnimeMapping?,
    selectedSeries: LibrarySeries,
    onSave: (LibrarySeries, ExternalAnimeProvider, String) -> Unit,
    onDelete: (LibrarySeries, ExternalAnimeProvider) -> Unit,
) {
    var animeIdText by remember(selectedSeries.id, provider, mapping?.animeId?.value) {
        mutableStateOf(mapping?.animeId?.value?.toString().orEmpty())
    }
    ExternalMappingEditRow(
        label = provider.displayName,
        value = animeIdText,
        onValueChange = { animeIdText = it.filter(Char::isDigit) },
        saveLabel = if (mapping == null) strings.linkAction else strings.replaceAction,
        deleteEnabled = mapping != null,
        onSave = { onSave(selectedSeries, provider, animeIdText) },
        onDelete = { onDelete(selectedSeries, provider) },
        removeLabel = strings.removeAction,
    )
}

@Composable
internal fun ExternalItemMappingRow(
    strings: DesktopStrings,
    provider: ExternalAnimeProvider,
    currentId: Long?,
    hasManualMapping: Boolean,
    selectedItem: LibraryMediaItem,
    onSave: (LibraryMediaItem, ExternalAnimeProvider, String) -> Unit,
    onDelete: (LibraryMediaItem, ExternalAnimeProvider) -> Unit,
) {
    var animeIdText by remember(selectedItem.id, provider, currentId) {
        mutableStateOf(currentId?.toString().orEmpty())
    }
    ExternalMappingEditRow(
        label = strings.providerEpisodeLabel(provider.displayName),
        value = animeIdText,
        onValueChange = { animeIdText = it.filter(Char::isDigit) },
        saveLabel = if (hasManualMapping) strings.replaceAction else strings.correctAction,
        deleteEnabled = hasManualMapping,
        onSave = { onSave(selectedItem, provider, animeIdText) },
        onDelete = { onDelete(selectedItem, provider) },
        removeLabel = strings.removeAction,
    )
}

@Composable
internal fun ExternalMappingEditRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    saveLabel: String,
    deleteEnabled: Boolean,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    removeLabel: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            enabled = value.toLongOrNull()?.let { it > 0 } == true,
            onClick = onSave,
        ) {
            Text(saveLabel)
        }
        Button(
            enabled = deleteEnabled,
            onClick = onDelete,
        ) {
            Text(removeLabel)
        }
    }
}

@Composable
internal fun InspectorStatusRow(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DanmakuColors.SurfaceRaised.copy(alpha = 0.62f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun CompactInspectorEpisodeRow(
    strings: DesktopStrings,
    item: LibraryMediaItem,
    selected: Boolean,
    watchStatus: LibraryWatchStatus?,
    originalSeriesTitle: String?,
    isRefreshingMetadata: Boolean,
    onClick: () -> Unit,
) {
    val metadataReadiness = item.metadataReadiness(strings = strings, isRefreshing = isRefreshingMetadata)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) DanmakuColors.AccentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.episodeTitle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            originalSeriesTitle
                ?.takeIf { it.isNotBlank() && it != item.seriesTitle }
                ?.let { Text(strings.fileGroupLabel(it), color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(watchStatus.statusLabel(), color = DanmakuColors.TextMuted, maxLines = 1)
            Text(metadataReadiness.shortLabel, color = metadataReadiness.color, maxLines = 1)
        }
    }
}

@Composable
internal fun MiniProgressBar(
    percent: Int?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.12f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth((percent ?: 0).coerceIn(0, 100) / 100f)
                .fillMaxHeight()
                .background(DanmakuColors.Accent),
        )
    }
}

internal fun Modifier.libraryCollectionKeyboard(
    itemCount: Int,
    selectedIndex: Int,
    columnStride: Int = 1,
    onSelectedIndexChange: (Int) -> Unit,
    onOpenSelected: () -> Unit,
    onPlaySelected: () -> Unit,
): Modifier {
    if (itemCount <= 0) return this
    val boundedSelectedIndex = selectedIndex.coerceIn(0, itemCount - 1)
    fun moveTo(index: Int) {
        onSelectedIndexChange(index.coerceIn(0, itemCount - 1))
    }
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else {
            when (event.key) {
                Key.DirectionUp -> {
                    moveTo(boundedSelectedIndex - columnStride)
                    true
                }
                Key.DirectionDown -> {
                    moveTo(boundedSelectedIndex + columnStride)
                    true
                }
                Key.DirectionLeft -> {
                    if (columnStride > 1) {
                        moveTo(boundedSelectedIndex - 1)
                        true
                    } else {
                        false
                    }
                }
                Key.DirectionRight -> {
                    if (columnStride > 1) {
                        moveTo(boundedSelectedIndex + 1)
                        true
                    } else {
                        false
                    }
                }
                Key.Enter -> {
                    onOpenSelected()
                    true
                }
                Key.Spacebar -> {
                    onPlaySelected()
                    true
                }
                else -> false
            }
        }
    }.focusable()
}

@Composable
internal fun WorkspacePanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DanmakuColors.Surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

internal fun PlaybackProgress.progressPercent(): Int? =
    durationMs
        ?.takeIf { it > 0L }
        ?.let { duration -> ((positionMs.coerceAtLeast(0L) * 100L) / duration).coerceIn(0L, 100L).toInt() }

internal fun DesktopDownloadQueueItem.downloadProgressPercent(): Int? =
    totalBytes
        ?.takeIf { it > 0L }
        ?.let { total -> ((positionBytes.coerceAtLeast(0L) * 100L) / total).coerceIn(0L, 100L).toInt() }

internal fun DesktopDownloadQueueItem.downloadProgressLabel(): String =
    totalBytes
        ?.let { total -> "${positionBytes.formatLibrarySize()} / ${total.formatLibrarySize()}" }
        ?: positionBytes.formatLibrarySize()

internal fun DesktopDownloadQueueItem.isActiveDownload(): Boolean =
    state.equals("active", ignoreCase = true) || state.equals("running", ignoreCase = true)

internal fun DesktopDownloadQueueItem.isQueuedDownload(): Boolean =
    state.equals("queued", ignoreCase = true) || state.equals("pending", ignoreCase = true)

internal fun DesktopDownloadQueueItem.isCompletedDownload(): Boolean =
    state.equals("completed", ignoreCase = true) || state.equals("done", ignoreCase = true)

internal fun DesktopDownloadQueueItem.isFailedDownload(): Boolean =
    failureMessage != null || state.equals("failed", ignoreCase = true)

internal fun DesktopDownloadQueueItem.downloadStateColor(): Color =
    when {
        isFailedDownload() -> DanmakuColors.Warning
        isCompletedDownload() -> DanmakuColors.Good
        isActiveDownload() -> DanmakuColors.Accent
        else -> DanmakuColors.TextMuted
    }

internal fun openDownloadOutputFolder(item: DesktopDownloadQueueItem): Result<Unit> =
    runCatching {
        require(Desktop.isDesktopSupported()) { "Desktop open is not supported on this system." }
        val desktop = Desktop.getDesktop()
        require(desktop.isSupported(Desktop.Action.OPEN)) { "Desktop open action is not supported on this system." }
        val outputPath = Path.of(item.outputPath)
        val folder = if (Files.isDirectory(outputPath)) {
            outputPath
        } else {
            outputPath.parent ?: outputPath
        }
        require(Files.exists(folder)) { "Output folder does not exist: $folder" }
        desktop.open(folder.toFile())
    }

internal fun LibraryMediaItem.primaryPlaybackActionLabel(
    watchStatus: LibraryWatchStatus?,
    strings: DesktopStrings,
): String =
    if (watchStatus?.state == LibraryWatchState.IN_PROGRESS) {
        strings.resumeAction
    } else {
        strings.playAction
    }

internal fun LibraryMediaItem.displaySeriesTitle(): String =
    animeMetadata?.displayTitle?.takeIf { it.isNotBlank() } ?: seriesTitle

internal fun LibraryMediaItem.localSeriesLabel(strings: DesktopStrings): String? {
    val displayTitle = displaySeriesTitle()
    return seriesTitle
        .takeIf { it.isNotBlank() && it != displayTitle }
        ?.let(strings.fileGroupLabel)
}

internal fun LibraryMediaItem.recentlyAddedDetail(strings: DesktopStrings): String =
    if (indexedAtEpochMs > 0) {
        strings.recentlyAddedDetailLabel(indexedAtEpochMs, sizeBytes.formatLibrarySize())
    } else {
        "${mediaType.uppercase()} - ${sizeBytes.formatLibrarySize()}"
    }

internal data class LibraryMetadataReadiness(
    val label: String,
    val shortLabel: String,
    val detail: String,
    val color: Color,
    val icon: ImageVector,
)

internal fun LibraryMediaItem.metadataReadiness(
    strings: DesktopStrings,
    isRefreshing: Boolean,
    hasPoster: Boolean = posterPath != null,
): LibraryMetadataReadiness {
    val hasMatchedTitle = animeMetadata != null
    return when {
        isRefreshing || metadataStatus == LibraryItemMetadataStatus.LOADING -> LibraryMetadataReadiness(
            label = strings.metadataLoadingLabel,
            shortLabel = strings.loadingLabel,
            detail = strings.metadataLoadingDetail,
            color = DanmakuColors.Accent,
            icon = Icons.Filled.Refresh,
        )
        metadataStatus == LibraryItemMetadataStatus.FAILED -> LibraryMetadataReadiness(
            label = strings.metadataFailedLabel,
            shortLabel = strings.metadataFailedShortLabel,
            detail = strings.metadataFailedDetail,
            color = DanmakuColors.Warning,
            icon = Icons.Filled.Warning,
        )
        hasMatchedTitle && hasPoster -> LibraryMetadataReadiness(
            label = strings.metadataReadyLabel,
            shortLabel = strings.metadataReadyShortLabel,
            detail = strings.metadataReadyDetail,
            color = DanmakuColors.Good,
            icon = Icons.Filled.CheckCircle,
        )
        hasMatchedTitle || hasPoster || metadataStatus == LibraryItemMetadataStatus.READY -> LibraryMetadataReadiness(
            label = strings.metadataPartialLabel,
            shortLabel = strings.partialLabel,
            detail = when {
                hasMatchedTitle && !hasPoster -> strings.metadataPartialMatchedNoPosterDetail
                hasPoster && !hasMatchedTitle -> strings.metadataPartialPosterNoTitleDetail
                else -> strings.metadataPartialGenericDetail
            },
            color = DanmakuColors.Info,
            icon = Icons.Filled.Refresh,
        )
        else -> LibraryMetadataReadiness(
            label = strings.metadataNeededLabel,
            shortLabel = strings.metadataNeededShortLabel,
            detail = strings.metadataNeededDetail,
            color = DanmakuColors.TextMuted,
            icon = Icons.Filled.Refresh,
        )
    }
}

internal fun LibrarySeries.metadataReadiness(
    strings: DesktopStrings,
    isRefreshing: Boolean,
    hasPoster: Boolean,
): LibraryMetadataReadiness {
    val items = seasons.flatMap { season -> season.items }
    val hasAnyMatchedTitle = items.any { it.animeMetadata != null }
    val hasAnyFailed = items.any { it.metadataStatus == LibraryItemMetadataStatus.FAILED }
    val allItemsMatched = items.isNotEmpty() && items.all { item ->
        item.animeMetadata != null || item.metadataStatus == LibraryItemMetadataStatus.READY
    }
    return when {
        isRefreshing || items.any { it.metadataStatus == LibraryItemMetadataStatus.LOADING } -> LibraryMetadataReadiness(
            label = strings.metadataLoadingLabel,
            shortLabel = strings.loadingLabel,
            detail = strings.seriesMetadataLoadingDetail,
            color = DanmakuColors.Accent,
            icon = Icons.Filled.Refresh,
        )
        hasAnyFailed -> LibraryMetadataReadiness(
            label = strings.metadataFailedLabel,
            shortLabel = strings.metadataFailedShortLabel,
            detail = strings.seriesMetadataFailedDetail,
            color = DanmakuColors.Warning,
            icon = Icons.Filled.Warning,
        )
        hasPoster && allItemsMatched -> LibraryMetadataReadiness(
            label = strings.metadataReadyLabel,
            shortLabel = strings.metadataReadyShortLabel,
            detail = strings.seriesMetadataReadyDetail,
            color = DanmakuColors.Good,
            icon = Icons.Filled.CheckCircle,
        )
        hasPoster || hasAnyMatchedTitle -> LibraryMetadataReadiness(
            label = strings.metadataPartialLabel,
            shortLabel = strings.partialLabel,
            detail = strings.seriesMetadataPartialDetail,
            color = DanmakuColors.Info,
            icon = Icons.Filled.Refresh,
        )
        else -> LibraryMetadataReadiness(
            label = strings.metadataNeededLabel,
            shortLabel = strings.metadataNeededShortLabel,
            detail = strings.seriesMetadataNeededDetail,
            color = DanmakuColors.TextMuted,
            icon = Icons.Filled.Refresh,
        )
    }
}

@Composable
internal fun SeriesPosterCard(
    strings: DesktopStrings,
    series: LibrarySeries,
    coverPath: Path?,
    watchSummary: LibrarySeriesWatchSummary?,
    isSelected: Boolean,
    isPreparing: Boolean,
    isRefreshingMetadata: Boolean,
    onSelect: () -> Unit,
    onRefreshMetadata: () -> Unit,
    onPlay: () -> Unit,
) {
    val metadataReadiness = series.metadataReadiness(
        strings = strings,
        isRefreshing = isRefreshingMetadata,
        hasPoster = coverPath != null,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) DanmakuColors.SurfaceRaised else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.70f)
                .clip(RoundedCornerShape(6.dp))
                .background(DanmakuColors.SurfaceRaised),
        ) {
            SeriesPosterImage(
                coverPath = coverPath,
                title = series.title,
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                text = "${watchSummary?.watchedCount ?: 0}/${series.episodeCount}",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(topEnd = 4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                maxLines = 1,
            )
            Text(
                text = metadataReadiness.shortLabel,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(metadataReadiness.color.copy(alpha = 0.86f), RoundedCornerShape(bottomEnd = 4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                maxLines = 1,
            )
        }
        Text(
            text = series.title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${watchSummary.progressLabel()} - ${metadataReadiness.label}",
            color = DanmakuColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = onRefreshMetadata,
                enabled = !isPreparing && !isRefreshingMetadata,
                modifier = Modifier.weight(0.46f),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = strings.refreshSeriesMetadataAction, modifier = Modifier.size(16.dp))
            }
            LibraryActionButton(
                imageVector = Icons.Filled.PlayArrow,
                label = if (isPreparing) strings.loadingAction else strings.playAction,
                modifier = Modifier.weight(0.54f),
                enabled = !isPreparing,
                onClick = onPlay,
            )
        }
    }
}

@Composable
internal fun SeriesPosterImage(
    coverPath: Path?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val bitmap = rememberLocalImageBitmap(coverPath)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier.background(DanmakuColors.AccentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.initialsForPoster(),
                color = Color.White,
                style = MaterialTheme.typography.h4,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun rememberLocalImageBitmap(path: Path?): ImageBitmap? =
    remember(path) {
        path
            ?.takeIf(Files::isRegularFile)
            ?.let { imagePath ->
                runCatching {
                    SkiaImage.makeFromEncoded(Files.readAllBytes(imagePath)).toComposeImageBitmap()
                }.getOrNull()
            }
    }


