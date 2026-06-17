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
    localAnimeListEntry: LocalAnimeListEntry?,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onInspectCachedDandanplay: (LibraryMediaItem) -> Unit,
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit,
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
                watchSummary.progressLabel(strings),
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
        LocalAnimeListEditor(
            strings = strings,
            series = selectedSeries,
            entry = localAnimeListEntry,
            onSave = onSaveLocalAnimeListEntry,
            onDelete = onDeleteLocalAnimeListEntry,
        )
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
private fun LocalAnimeListEditor(
    strings: DesktopStrings,
    series: LibrarySeries,
    entry: LocalAnimeListEntry?,
    onSave: (LibrarySeries, LocalAnimeListStatus, Int?, String?) -> Unit,
    onDelete: (LibrarySeries) -> Unit,
) {
    var status by remember(series.id, entry) { mutableStateOf(entry?.status ?: LocalAnimeListStatus.WATCHING) }
    var scoreText by remember(series.id, entry) { mutableStateOf(entry?.score?.toString().orEmpty()) }
    var notesText by remember(series.id, entry) { mutableStateOf(entry?.notes.orEmpty()) }
    var statusMenuExpanded by remember(series.id) { mutableStateOf(false) }
    val score = scoreText.trim().takeIf(String::isNotBlank)?.toIntOrNull()
    val scoreIsValid = scoreText.isBlank() || score?.let { it in 0..10 } == true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DanmakuColors.SurfaceRaised.copy(alpha = 0.58f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(strings.localWatchListTitle, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                Button(onClick = { statusMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(status.localizedLabel(strings), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(
                    expanded = statusMenuExpanded,
                    onDismissRequest = { statusMenuExpanded = false },
                ) {
                    LocalAnimeListStatus.entries.forEach { option ->
                        DropdownMenuItem(
                            onClick = {
                                status = option
                                statusMenuExpanded = false
                            },
                        ) {
                            Text(option.localizedLabel(strings))
                        }
                    }
                }
            }
            OutlinedTextField(
                value = scoreText,
                onValueChange = { value -> scoreText = value.filter(Char::isDigit).take(2) },
                label = { Text(strings.localScoreLabel) },
                isError = !scoreIsValid,
                singleLine = true,
                modifier = Modifier.weight(0.72f),
            )
        }
        OutlinedTextField(
            value = notesText,
            onValueChange = { notesText = it.take(240) },
            label = { Text(strings.localNotesLabel) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = scoreIsValid,
                onClick = {
                    onSave(
                        series,
                        status,
                        score,
                        notesText.trim().takeIf(String::isNotBlank),
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(strings.saveLocalWatchListAction)
            }
            Button(
                enabled = entry != null,
                onClick = { onDelete(series) },
                modifier = Modifier.weight(1f),
            ) {
                Text(strings.clearLocalWatchListAction)
            }
        }
    }
}
