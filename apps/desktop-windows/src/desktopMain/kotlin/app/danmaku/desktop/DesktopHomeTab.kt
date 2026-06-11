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
internal fun ShellHeader(
    selectedTab: DesktopShellTab,
    strings: DesktopStrings,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    searchFocusRequester: FocusRequester,
    onRefresh: () -> Unit,
    onShowSettings: () -> Unit,
    playerStatus: String,
    episodeCount: Int,
    isRefreshEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DanmakuColors.Surface)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.width(180.dp)) {
            Text(strings.tabTitle(selectedTab), style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
            Text(strings.shellSubtitle, color = DanmakuColors.TextMuted, maxLines = 1)
        }
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            label = { Text(strings.searchLabel) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = strings.searchAction) },
            modifier = Modifier
                .weight(1f)
                .focusRequester(searchFocusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                        onSubmitSearch()
                        true
                    } else {
                        false
                    }
                },
            singleLine = true,
        )
        StatusPill("${strings.playerStatusPrefix} $playerStatus", icon = Icons.Filled.PlayArrow, active = playerStatus == PlaybackStatus.PLAYING.name)
        StatusPill("$episodeCount ${strings.episodesSuffix}", icon = Icons.AutoMirrored.Filled.LibraryBooks)
        PlayerIconButton(
            imageVector = Icons.Filled.Refresh,
            contentDescription = strings.rescanLibrary,
            enabled = isRefreshEnabled,
            onClick = onRefresh,
        )
        PlayerIconButton(
            imageVector = Icons.Filled.MoreHoriz,
            contentDescription = strings.settingsTitle,
            onClick = onShowSettings,
        )
    }
}

@Composable
internal fun AppNavigationRail(
    selectedTab: DesktopShellTab,
    strings: DesktopStrings,
    onTabSelected: (DesktopShellTab) -> Unit,
    playbackLabel: String?,
    playbackStatus: PlaybackStatus,
    episodeCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(DanmakuColors.Surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DanmakuColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("D", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("Danmaku", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                Text(strings.mediaHub, color = DanmakuColors.TextMuted, maxLines = 1)
            }
        }
        Divider(color = DanmakuColors.SurfaceRaised)
        AppRailItem(
            icon = Icons.Filled.Home,
            label = strings.tabTitle(DesktopShellTab.HOME),
            selected = selectedTab == DesktopShellTab.HOME,
            onClick = { onTabSelected(DesktopShellTab.HOME) },
        )
        AppRailItem(
            icon = Icons.AutoMirrored.Filled.LibraryBooks,
            label = strings.tabTitle(DesktopShellTab.MEDIA_LIBRARY),
            count = episodeCount,
            selected = selectedTab == DesktopShellTab.MEDIA_LIBRARY,
            onClick = { onTabSelected(DesktopShellTab.MEDIA_LIBRARY) },
        )
        AppRailItem(
            icon = Icons.Filled.FolderOpen,
            label = strings.tabTitle(DesktopShellTab.DOWNLOADS),
            selected = selectedTab == DesktopShellTab.DOWNLOADS,
            onClick = { onTabSelected(DesktopShellTab.DOWNLOADS) },
        )
        AppRailItem(
            icon = Icons.Filled.Refresh,
            label = strings.tabTitle(DesktopShellTab.TRACKING),
            selected = selectedTab == DesktopShellTab.TRACKING,
            onClick = { onTabSelected(DesktopShellTab.TRACKING) },
        )
        AppRailItem(
            icon = Icons.Filled.PlayArrow,
            label = strings.tabTitle(DesktopShellTab.PLAYBACK),
            selected = selectedTab == DesktopShellTab.PLAYBACK,
            onClick = { onTabSelected(DesktopShellTab.PLAYBACK) },
        )
        AppRailItem(
            icon = Icons.Filled.MoreHoriz,
            label = strings.tabTitle(DesktopShellTab.PROFILE),
            selected = selectedTab == DesktopShellTab.PROFILE,
            onClick = { onTabSelected(DesktopShellTab.PROFILE) },
        )
        Divider(color = DanmakuColors.SurfaceRaised)
        Text(strings.librarySlicesTitle, color = DanmakuColors.TextMuted, fontWeight = FontWeight.Bold)
        listOf(
            strings.animeSeriesSliceLabel,
            strings.moviesSliceLabel,
            strings.ovasSpecialsSliceLabel,
            strings.allEpisodesSliceLabel,
            strings.favoritesSliceLabel,
        ).forEach { label ->
            Text(
                text = label,
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onTabSelected(DesktopShellTab.MEDIA_LIBRARY) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        NowPlayingRailCard(
            strings = strings,
            playbackLabel = playbackLabel,
            playbackStatus = playbackStatus,
            onOpenPlayer = { onTabSelected(DesktopShellTab.PLAYBACK) },
        )
    }
}

@Composable
internal fun AppRailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    count: Int? = null,
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
        Icon(icon, contentDescription = label, tint = if (selected) DanmakuColors.Accent else DanmakuColors.TextMuted)
        Text(label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        count?.takeIf { it > 0 }?.let {
            Text(
                text = it.toString(),
                color = if (selected) Color.White else DanmakuColors.TextMuted,
                maxLines = 1,
                modifier = Modifier
                    .background(DanmakuColors.SurfaceRaised, RoundedCornerShape(999.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
internal fun NowPlayingRailCard(
    strings: DesktopStrings,
    playbackLabel: String?,
    playbackStatus: PlaybackStatus,
    onOpenPlayer: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DanmakuColors.SurfaceRaised.copy(alpha = 0.72f))
            .clickable(onClick = onOpenPlayer)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(strings.nowPlayingTitle, color = DanmakuColors.TextMuted, fontWeight = FontWeight.Bold)
        Text(
            playbackLabel ?: strings.noMediaLoadedLabel,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        StatusPill(
            text = playbackStatus.name.lowercase().replaceFirstChar { it.uppercase() },
            icon = Icons.Filled.PlayArrow,
            active = playbackStatus == PlaybackStatus.PLAYING,
        )
    }
}

@Composable
internal fun HomeTab(
    strings: DesktopStrings,
    playbackSnapshot: PlaybackSnapshot,
    registeredRoots: List<DesktopLibraryRoot>,
    indexedLibrary: IndexedLocalLibrary?,
    seriesPosterById: Map<String, Path?>,
    playbackProgresses: List<PlaybackProgress>,
    favoriteMediaIds: Set<String>,
    externalAnimeMappings: List<ExternalAnimeMapping>,
    externalAnimeSyncFailures: List<ExternalAnimeSyncFailure>,
    isIndexing: Boolean,
    isPreparingLocalPlayback: Boolean,
    isRefreshingSeriesPosters: Boolean,
    refreshingMetadataMediaIds: Set<String>,
    refreshingMetadataSeriesIds: Set<String>,
    dandanplayCacheStatus: DandanplayPlaybackUiStatus?,
    episodeCount: Int,
    networkUrls: List<String>,
    pairingToken: String,
    overlayStatus: String,
    libraryError: String?,
    lastScanStats: LocalMediaLibraryScanStats?,
    diagnosticLog: List<DesktopDiagnosticLogEntry>,
    onOpenLibrary: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTracking: () -> Unit,
    onRefreshMetadata: () -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    val catalog = indexedLibrary?.catalog
    val series = remember(catalog) { catalog?.groupedSeries().orEmpty() }
    val seriesByMediaId = remember(series) {
        series
            .flatMap { librarySeries -> librarySeries.seasons.flatMap { season -> season.items.map { it.id to librarySeries } } }
            .toMap()
    }
    val seriesWatchSummaryById = remember(catalog, playbackProgresses) {
        catalog?.seriesWatchSummaryById(playbackProgresses).orEmpty()
    }
    val continueWatchingItems = remember(catalog, playbackProgresses) {
        catalog?.continueWatchingItems(playbackProgresses).orEmpty()
    }
    val nextUpItems = remember(catalog, playbackProgresses) {
        catalog?.nextUpItems(playbackProgresses).orEmpty()
    }
    val recentlyWatchedItems = remember(catalog, playbackProgresses) {
        catalog?.recentlyWatchedItems(playbackProgresses).orEmpty()
    }
    val recentlyAddedItems = remember(catalog) {
        catalog
            ?.items
            .orEmpty()
            .sortedWith(
                compareByDescending<LibraryMediaItem> { it.indexedAtEpochMs }
                    .thenByDescending { it.relativePath },
            )
    }
    val externalTrackingPlan = remember(catalog, externalAnimeMappings, playbackProgresses, externalAnimeSyncFailures) {
        catalog?.externalAnimeTrackingPlan(
            mappings = externalAnimeMappings,
            progresses = playbackProgresses,
            failures = externalAnimeSyncFailures,
        )
    }
    val metadataRefreshingCount = refreshingMetadataMediaIds.size + refreshingMetadataSeriesIds.size
    val posterReadyCount = seriesPosterById.values.count { it != null }

    TabScaffold {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxWidth < 1120.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    HomeMainColumn(
                        strings = strings,
                        playbackSnapshot = playbackSnapshot,
                        continueWatchingItems = continueWatchingItems,
                        nextUpItems = nextUpItems,
                        recentlyWatchedItems = recentlyWatchedItems,
                        recentlyAddedItems = recentlyAddedItems,
                        series = series,
                        seriesByMediaId = seriesByMediaId,
                        seriesPosterById = seriesPosterById,
                        seriesWatchSummaryById = seriesWatchSummaryById,
                        favoriteMediaIds = favoriteMediaIds,
                        isPreparingLocalPlayback = isPreparingLocalPlayback,
                        onOpenLibrary = onOpenLibrary,
                        onPlayLocalPlayback = onPlayLocalPlayback,
                    )
                    HomeStatusColumn(
                        strings = strings,
                        registeredRoots = registeredRoots,
                        episodeCount = episodeCount,
                        seriesCount = series.size,
                        networkUrls = networkUrls,
                        pairingToken = pairingToken,
                        overlayStatus = overlayStatus,
                        libraryError = libraryError,
                        lastScanStats = lastScanStats,
                        isIndexing = isIndexing,
                        isRefreshingSeriesPosters = isRefreshingSeriesPosters,
                        metadataRefreshingCount = metadataRefreshingCount,
                        posterReadyCount = posterReadyCount,
                        externalTrackingPlan = externalTrackingPlan,
                        dandanplayCacheStatus = dandanplayCacheStatus,
                        diagnosticLog = diagnosticLog,
                        onOpenLibrary = onOpenLibrary,
                        onOpenDownloads = onOpenDownloads,
                        onOpenSettings = onOpenSettings,
                        onOpenTracking = onOpenTracking,
                        onRefreshMetadata = onRefreshMetadata,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HomeMainColumn(
                        strings = strings,
                        playbackSnapshot = playbackSnapshot,
                        continueWatchingItems = continueWatchingItems,
                        nextUpItems = nextUpItems,
                        recentlyWatchedItems = recentlyWatchedItems,
                        recentlyAddedItems = recentlyAddedItems,
                        series = series,
                        seriesByMediaId = seriesByMediaId,
                        seriesPosterById = seriesPosterById,
                        seriesWatchSummaryById = seriesWatchSummaryById,
                        favoriteMediaIds = favoriteMediaIds,
                        isPreparingLocalPlayback = isPreparingLocalPlayback,
                        onOpenLibrary = onOpenLibrary,
                        onPlayLocalPlayback = onPlayLocalPlayback,
                        modifier = Modifier.weight(1f),
                    )
                    HomeStatusColumn(
                        strings = strings,
                        registeredRoots = registeredRoots,
                        episodeCount = episodeCount,
                        seriesCount = series.size,
                        networkUrls = networkUrls,
                        pairingToken = pairingToken,
                        overlayStatus = overlayStatus,
                        libraryError = libraryError,
                        lastScanStats = lastScanStats,
                        isIndexing = isIndexing,
                        isRefreshingSeriesPosters = isRefreshingSeriesPosters,
                        metadataRefreshingCount = metadataRefreshingCount,
                        posterReadyCount = posterReadyCount,
                        externalTrackingPlan = externalTrackingPlan,
                        dandanplayCacheStatus = dandanplayCacheStatus,
                        diagnosticLog = diagnosticLog,
                        onOpenLibrary = onOpenLibrary,
                        onOpenDownloads = onOpenDownloads,
                        onOpenSettings = onOpenSettings,
                        onOpenTracking = onOpenTracking,
                        onRefreshMetadata = onRefreshMetadata,
                        modifier = Modifier.width(332.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeMainColumn(
    strings: DesktopStrings,
    playbackSnapshot: PlaybackSnapshot,
    continueWatchingItems: List<LibraryPlaybackProgressItem>,
    nextUpItems: List<LibraryNextUpItem>,
    recentlyWatchedItems: List<LibraryPlaybackProgressItem>,
    recentlyAddedItems: List<LibraryMediaItem>,
    series: List<LibrarySeries>,
    seriesByMediaId: Map<String, LibrarySeries>,
    seriesPosterById: Map<String, Path?>,
    seriesWatchSummaryById: Map<String, LibrarySeriesWatchSummary>,
    favoriteMediaIds: Set<String>,
    isPreparingLocalPlayback: Boolean,
    onOpenLibrary: () -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HomeHeroCard(
            strings = strings,
            playbackSnapshot = playbackSnapshot,
            continueWatchingItems = continueWatchingItems,
            nextUpItems = nextUpItems,
            seriesByMediaId = seriesByMediaId,
            seriesPosterById = seriesPosterById,
            isPreparingLocalPlayback = isPreparingLocalPlayback,
            onOpenLibrary = onOpenLibrary,
            onPlayLocalPlayback = onPlayLocalPlayback,
        )
        HomeSectionHeader(
            title = strings.recentlyAddedTitle,
            actionLabel = strings.browseAllAction,
            onAction = onOpenLibrary,
        )
        if (recentlyAddedItems.isEmpty()) {
            EmptyState(strings.newlyAddedEmptyText)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                recentlyAddedItems.take(4).forEach { mediaItem ->
                    HomeEpisodeCard(
                        strings = strings,
                        mediaItem = mediaItem,
                        coverPath = seriesByMediaId[mediaItem.id]?.let { seriesPosterById[it.id] },
                        progressPercent = null,
                        detail = mediaItem.recentlyAddedDetail(strings),
                        isPreparing = isPreparingLocalPlayback,
                        playContentDescription = strings.playAction,
                        onPlay = { onPlayLocalPlayback(mediaItem) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat((4 - recentlyAddedItems.take(4).size).coerceAtLeast(0)) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        HomeSectionHeader(
            title = strings.recentlyWatchedTitle,
            actionLabel = strings.openLibraryAction,
            onAction = onOpenLibrary,
        )
        if (recentlyWatchedItems.isEmpty()) {
            EmptyState(strings.recentPlaybackEmptyText)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                recentlyWatchedItems.take(4).forEach { item ->
                    HomeEpisodeCard(
                        strings = strings,
                        mediaItem = item.mediaItem,
                        coverPath = seriesByMediaId[item.mediaItem.id]?.let { seriesPosterById[it.id] },
                        progressPercent = item.progress.progressPercent(),
                        detail = strings.watchedAtLabel(item.progress.positionMs),
                        isPreparing = isPreparingLocalPlayback,
                        playContentDescription = strings.playAction,
                        onPlay = { onPlayLocalPlayback(item.mediaItem) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat((4 - recentlyWatchedItems.take(4).size).coerceAtLeast(0)) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        HomeSectionHeader(
            title = strings.myLibraryTitle,
            actionLabel = strings.browseAllAction,
            onAction = onOpenLibrary,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                title = strings.seriesSummaryTitle,
                value = series.size.toString(),
                caption = strings.matchedGroupsCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.favoritesSummaryTitle,
                value = favoriteMediaIds.size.toString(),
                caption = strings.savedEpisodesCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.watchingSummaryTitle,
                value = continueWatchingItems.size.toString(),
                caption = strings.inProgressCaption,
                modifier = Modifier.weight(1f),
            )
        }
        if (series.isEmpty()) {
            EmptyState(strings.homeLibraryEmptyText, strings.openLibraryAction, onOpenLibrary)
        } else {
            SectionCard(strings.librarySnapshotTitle) {
                series.take(6).forEach { librarySeries ->
                    HomeSeriesSummaryRow(
                        strings = strings,
                        series = librarySeries,
                        coverPath = seriesPosterById[librarySeries.id],
                        watchSummary = seriesWatchSummaryById[librarySeries.id],
                        onClick = onOpenLibrary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeHeroCard(
    strings: DesktopStrings,
    playbackSnapshot: PlaybackSnapshot,
    continueWatchingItems: List<LibraryPlaybackProgressItem>,
    nextUpItems: List<LibraryNextUpItem>,
    seriesByMediaId: Map<String, LibrarySeries>,
    seriesPosterById: Map<String, Path?>,
    isPreparingLocalPlayback: Boolean,
    onOpenLibrary: () -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    val resumeCards = buildList {
        continueWatchingItems.take(3).forEach { item ->
            add(
                HomeResumeCardModel(
                    mediaItem = item.mediaItem,
                    detail = strings.resumeAtLabel(item.progress.positionMs),
                    progressPercent = item.progress.progressPercent(),
                    actionLabel = strings.resumeAction,
                ),
            )
        }
        nextUpItems.take(3).forEach { item ->
            add(
                HomeResumeCardModel(
                    mediaItem = item.mediaItem,
                    detail = item.nextUpLabel(strings),
                    progressPercent = item.progress?.progressPercent(),
                    actionLabel = item.nextUpActionLabel(strings),
                ),
            )
        }
    }.distinctBy { it.mediaItem.id }.take(3)

    SectionCard(strings.continueWatchingTitle) {
        if (resumeCards.isEmpty()) {
            EmptyState(strings.noResumeQueueText, strings.openLibraryAction, onOpenLibrary)
            return@SectionCard
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            resumeCards.forEach { card ->
                HomeResumeCard(
                    strings = strings,
                    card = card,
                    coverPath = seriesByMediaId[card.mediaItem.id]?.let { seriesPosterById[it.id] },
                    isPreparing = isPreparingLocalPlayback,
                    onPlay = { onPlayLocalPlayback(card.mediaItem) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        playbackSnapshot.source?.let { source ->
            MetadataRow(strings.loadedNowLabel, source.toString().redactToken())
        }
    }
}

internal data class HomeResumeCardModel(
    val mediaItem: LibraryMediaItem,
    val detail: String,
    val progressPercent: Int?,
    val actionLabel: String,
)

@Composable
internal fun HomeResumeCard(
    strings: DesktopStrings,
    card: HomeResumeCardModel,
    coverPath: Path?,
    isPreparing: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DanmakuColors.SurfaceRaised.copy(alpha = 0.74f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.55f)
                .clip(RoundedCornerShape(6.dp))
                .background(DanmakuColors.AccentSoft),
        ) {
            SeriesPosterImage(
                coverPath = coverPath,
                title = card.mediaItem.displaySeriesTitle(),
                modifier = Modifier.fillMaxSize(),
            )
            PlayerIconButton(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = card.actionLabel,
                enabled = !isPreparing,
                onClick = onPlay,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Text(card.mediaItem.displaySeriesTitle(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(card.mediaItem.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        card.mediaItem.localSeriesLabel(strings)?.let { label ->
            Text(label, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MiniProgressBar(percent = card.progressPercent)
        Text(card.detail, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun HomeEpisodeCard(
    strings: DesktopStrings,
    mediaItem: LibraryMediaItem,
    coverPath: Path?,
    progressPercent: Int?,
    detail: String,
    isPreparing: Boolean,
    playContentDescription: String,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DanmakuColors.Surface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        SeriesPosterImage(
            coverPath = coverPath,
            title = mediaItem.displaySeriesTitle(),
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Text(mediaItem.displaySeriesTitle(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(mediaItem.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        mediaItem.localSeriesLabel(strings)?.let { label ->
            Text(label, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MiniProgressBar(percent = progressPercent)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(detail, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            PlayerIconButton(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = playContentDescription,
                enabled = !isPreparing,
                onClick = onPlay,
            )
        }
    }
}

@Composable
internal fun HomeSeriesSummaryRow(
    strings: DesktopStrings,
    series: LibrarySeries,
    coverPath: Path?,
    watchSummary: LibrarySeriesWatchSummary?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SeriesPosterImage(
            coverPath = coverPath,
            title = series.title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(series.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(watchSummary.progressLabel(), color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StatusPill(strings.episodeCountShortLabel(series.episodeCount))
    }
}

@Composable
internal fun HomeStatusColumn(
    strings: DesktopStrings,
    registeredRoots: List<DesktopLibraryRoot>,
    episodeCount: Int,
    seriesCount: Int,
    networkUrls: List<String>,
    pairingToken: String,
    overlayStatus: String,
    libraryError: String?,
    lastScanStats: LocalMediaLibraryScanStats?,
    isIndexing: Boolean,
    isRefreshingSeriesPosters: Boolean,
    metadataRefreshingCount: Int,
    posterReadyCount: Int,
    externalTrackingPlan: ExternalAnimeTrackingPlan?,
    dandanplayCacheStatus: DandanplayPlaybackUiStatus?,
    diagnosticLog: List<DesktopDiagnosticLogEntry>,
    onOpenLibrary: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTracking: () -> Unit,
    onRefreshMetadata: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OperationalStatusCard(
            icon = Icons.Filled.Computer,
            title = strings.homeServerStatusTitle,
            value = when {
                libraryError != null -> strings.attentionNeededLabel
                isIndexing -> strings.indexingLabel
                else -> strings.onlineLabel
            },
            detail = networkUrls.firstOrNull() ?: strings.noLanUrlDetectedLabel,
            statusColor = when {
                libraryError != null -> DanmakuColors.Warning
                isIndexing -> DanmakuColors.Info
                else -> DanmakuColors.Good
            },
            actionLabel = strings.openLibraryAction,
            onAction = onOpenLibrary,
        ) {
            MetadataRow(strings.pairingLabel, pairingToken)
            MetadataRow(strings.foldersLabel, registeredRoots.size.toString())
            MetadataRow(strings.episodesLabel, episodeCount.toString())
            libraryError?.let { MetadataRow(strings.errorLabel, it, DanmakuColors.Warning) }
        }
        OperationalStatusCard(
            icon = Icons.Filled.GridView,
            title = strings.metadataAndPostersTitle,
            value = when {
                isRefreshingSeriesPosters || metadataRefreshingCount > 0 -> strings.loadingLabel
                posterReadyCount > 0 -> strings.readyStatusLabel
                seriesCount > 0 -> strings.partialLabel
                else -> strings.waitingLabel
            },
            detail = strings.postersReadySummary(posterReadyCount, seriesCount),
            statusColor = when {
                isRefreshingSeriesPosters || metadataRefreshingCount > 0 -> DanmakuColors.Info
                posterReadyCount > 0 -> DanmakuColors.Good
                seriesCount > 0 -> DanmakuColors.Warning
                else -> DanmakuColors.TextMuted
            },
            actionLabel = if (isRefreshingSeriesPosters || metadataRefreshingCount > 0) strings.refreshingAction else strings.refreshAction,
            actionEnabled = !isRefreshingSeriesPosters,
            onAction = onRefreshMetadata,
        ) {
            lastScanStats?.let {
                MetadataRow(strings.lastScanLabel, strings.lastScanCountsSummary(it.reusedItemCount, it.refreshedItemCount))
            }
            MetadataRow(strings.groupsLabel, seriesCount.toString())
        }
        OperationalStatusCard(
            icon = Icons.Filled.Refresh,
            title = strings.externalSyncTitle,
            value = externalTrackingPlan?.summary?.label ?: strings.notMappedLabel,
            detail = strings.readyUpdatesLabel(externalTrackingPlan?.summary?.updateCount ?: 0),
            statusColor = if ((externalTrackingPlan?.summary?.updateCount ?: 0) > 0) DanmakuColors.Info else DanmakuColors.TextMuted,
            actionLabel = strings.openTrackingAction,
            onAction = onOpenTracking,
        )
        OperationalStatusCard(
            icon = Icons.Filled.FolderOpen,
            title = strings.tabTitle(DesktopShellTab.DOWNLOADS),
            value = strings.downloadQueueReadyLabel,
            detail = strings.downloadsImportDetail,
            statusColor = DanmakuColors.TextMuted,
            actionLabel = strings.openDownloadsAction,
            onAction = onOpenDownloads,
        )
        OperationalStatusCard(
            icon = if (dandanplayCacheStatus?.summary?.isDandanplayWarningStatus() == true) Icons.Filled.Warning else Icons.Filled.CheckCircle,
            title = strings.cachedDanmakuTitle,
            value = dandanplayCacheStatus?.summary ?: strings.notCheckedLabel,
            detail = overlayStatus,
            statusColor = when {
                dandanplayCacheStatus == null -> DanmakuColors.TextMuted
                dandanplayCacheStatus.summary.isDandanplayWarningStatus() -> DanmakuColors.Warning
                else -> DanmakuColors.Good
            },
            actionLabel = strings.manageCacheAction,
            onAction = onOpenSettings,
        )
        DiagnosticsPanel(
            strings = strings,
            diagnosticLog = diagnosticLog,
            modifier = Modifier.heightIn(max = 280.dp),
        )
    }
}

@Composable
internal fun OperationalStatusCard(
    icon: ImageVector,
    title: String,
    value: String,
    detail: String,
    statusColor: Color,
    actionLabel: String,
    actionEnabled: Boolean = true,
    onAction: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    SectionCard(title) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(value, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, color = DanmakuColors.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        content()
        LibraryActionButton(
            imageVector = Icons.Filled.PlayArrow,
            label = actionLabel,
            enabled = actionEnabled,
            modifier = Modifier.fillMaxWidth(),
            onClick = onAction,
        )
    }
}

@Composable
internal fun HomeSectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.weight(1f))
        LibraryActionButton(
            imageVector = Icons.AutoMirrored.Filled.ViewList,
            label = actionLabel,
            onClick = onAction,
        )
    }
}


