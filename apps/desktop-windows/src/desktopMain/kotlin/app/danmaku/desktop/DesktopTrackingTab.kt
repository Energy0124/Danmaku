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
import app.danmaku.domain.ExternalAnimeListEntry
import app.danmaku.domain.ExternalAnimeMatchCandidate
import app.danmaku.domain.ExternalAnimeMatchQuery
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeMappingSource
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeSyncFailure
import app.danmaku.domain.ExternalAnimeTrackingPlan
import app.danmaku.domain.ExternalAnimeTrackingPlanConflict
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
internal fun TrackingTab(
    strings: DesktopStrings,
    indexedLibrary: IndexedLocalLibrary?,
    externalAnimeMappings: List<ExternalAnimeMapping>,
    playbackProgresses: List<PlaybackProgress>,
    externalAnimeListEntries: List<ExternalAnimeListEntry>,
    externalAnimeSyncFailures: List<ExternalAnimeSyncFailure>,
    isExternalAnimeSyncing: Boolean,
    isExternalAnimeReadbackRefreshing: Boolean,
    isExternalAnimeProgressImporting: Boolean,
    externalAnimeProviderSettings: ExternalAnimeProviderSettings,
    onSyncExternalAnimePlan: (ExternalAnimeTrackingPlan) -> Unit,
    onRefreshExternalAnimeReadback: (List<ExternalAnimeMapping>) -> Unit,
    onApplyExternalAnimeProgressImport: (List<ExternalAnimeTrackingPlanConflict>) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val catalog = indexedLibrary?.catalog
    val plan = remember(
        catalog,
        externalAnimeMappings,
        playbackProgresses,
        externalAnimeListEntries,
        externalAnimeSyncFailures,
    ) {
        catalog?.externalAnimeTrackingPlan(
            mappings = externalAnimeMappings,
            progresses = playbackProgresses,
            externalEntries = externalAnimeListEntries,
            failures = externalAnimeSyncFailures,
        )
    }
    TabScaffold {
        val seriesById = remember(catalog) {
            catalog?.groupedSeries()?.associateBy(LibrarySeries::id).orEmpty()
        }
        val trackingRows = remember(plan, externalAnimeMappings, externalAnimeListEntries, seriesById) {
            buildTrackingTableRows(
                strings = strings,
                plan = plan,
                mappings = externalAnimeMappings,
                externalEntries = externalAnimeListEntries,
                seriesById = seriesById,
            )
        }
        var selectedTrackingRowId by remember(trackingRows) {
            mutableStateOf(trackingRows.firstOrNull()?.id)
        }
        val selectedTrackingRow = trackingRows.firstOrNull { it.id == selectedTrackingRowId }
            ?: trackingRows.firstOrNull()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TrackingProviderCard(
                title = "MyAnimeList",
                status = externalAnimeProviderSettings.myAnimeListStatusLabel(strings),
                ready = externalAnimeProviderSettings.hasMyAnimeListAccessToken,
                detail = if (externalAnimeProviderSettings.myAnimeListClientId != null) {
                    strings.myAnimeListClientSavedLabel
                } else {
                    strings.trackingCredentialsNeededLabel
                },
                actionLabel = strings.providerSettingsAction,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.weight(1f),
            )
            TrackingProviderCard(
                title = "Bangumi",
                status = externalAnimeProviderSettings.bangumiStatusLabel(strings),
                ready = externalAnimeProviderSettings.hasBangumiAccessToken,
                detail = externalAnimeProviderSettings.bangumiBaseUrl,
                actionLabel = strings.providerSettingsAction,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                title = strings.mappedSummaryTitle,
                value = externalAnimeMappings.size.toString(),
                caption = strings.mappedSummaryCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.readySummaryTitle,
                value = (plan?.summary?.updateCount ?: 0).toString(),
                caption = strings.readySummaryCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.conflictsSummaryTitle,
                value = (plan?.summary?.conflictCount ?: 0).toString(),
                caption = strings.conflictsSummaryCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.failuresSummaryTitle,
                value = (plan?.summary?.failureCount ?: 0).toString(),
                caption = strings.failuresSummaryCaption,
                modifier = Modifier.weight(1f),
            )
        }
        HomeSectionHeader(
            title = strings.trackingSyncPreviewTitle,
            actionLabel = strings.openLibraryAction,
            onAction = onOpenLibrary,
        )
        if (catalog == null) {
            EmptyState(strings.trackingNoLibraryText, strings.openLibraryAction, onOpenLibrary)
        } else {
            TrackingWorkspace(
                strings = strings,
                plan = plan,
                rows = trackingRows,
                selectedRow = selectedTrackingRow,
                isSyncing = isExternalAnimeSyncing,
                isReadbackRefreshing = isExternalAnimeReadbackRefreshing,
                isProgressImporting = isExternalAnimeProgressImporting,
                onSelectRow = { row -> selectedTrackingRowId = row.id },
                onSync = onSyncExternalAnimePlan,
                onRefreshReadback = onRefreshExternalAnimeReadback,
                onApplyProgressImport = onApplyExternalAnimeProgressImport,
            )
        }
    }
}

internal enum class TrackingRowKind {
    UPDATE,
    CONFLICT,
    SKIPPED,
    FAILURE,
}

internal data class TrackingTableRow(
    val id: String,
    val kind: TrackingRowKind,
    val seriesTitle: String,
    val localSeriesId: String,
    val providerLabel: String,
    val animeIdText: String,
    val providerUrl: String?,
    val localProgress: String,
    val providerProgress: String,
    val plannedAction: String,
    val confidence: String,
    val statusLabel: String,
    val statusColor: Color,
    val mapping: ExternalAnimeMapping?,
    val update: ExternalAnimeTrackingPlanUpdate? = null,
    val conflict: ExternalAnimeTrackingPlanConflict? = null,
    val skip: app.danmaku.domain.ExternalAnimeTrackingPlanSkip? = null,
    val failure: ExternalAnimeSyncFailure? = null,
)

internal fun buildTrackingTableRows(
    strings: DesktopStrings,
    plan: ExternalAnimeTrackingPlan?,
    mappings: List<ExternalAnimeMapping>,
    externalEntries: List<ExternalAnimeListEntry>,
    seriesById: Map<String, LibrarySeries>,
): List<TrackingTableRow> {
    if (plan == null) {
        return emptyList()
    }
    val mappingsByAnimeId = mappings.associateBy(ExternalAnimeMapping::animeId)
    val mappingsBySeriesAndProvider = mappings.associateBy { it.localSeriesId to it.animeId.provider }
    val externalEntryByAnimeId = externalEntries.associateBy(ExternalAnimeListEntry::animeId)
    val updateRows = plan.updates.map { update ->
        val mapping = update.mapping
        val externalEntry = externalEntryByAnimeId[mapping.animeId]
        TrackingTableRow(
            id = "update-${mapping.localSeriesId}-${mapping.animeId.provider}-${mapping.animeId.value}",
            kind = TrackingRowKind.UPDATE,
            seriesTitle = update.series.title,
            localSeriesId = mapping.localSeriesId,
            providerLabel = mapping.animeId.provider.displayName,
            animeIdText = mapping.animeId.value.toString(),
            providerUrl = mapping.animeId.webUrl,
            localProgress = "${update.update.watchedEpisodes ?: 0}/${update.series.episodeCount}",
            providerProgress = externalEntry.providerProgressLabel(strings, update.series.episodeCount),
            plannedAction = "${update.update.status.localizedLabel(strings)}, ${strings.watchedCountLabel(update.update.watchedEpisodes ?: 0)}",
            confidence = mapping.confidence.formatConfidence(),
            statusLabel = strings.readyStatusLabel,
            statusColor = DanmakuColors.Good,
            mapping = mapping,
            update = update,
        )
    }
    val conflictRows = plan.conflicts.map { conflict ->
        val mapping = conflict.mapping
        TrackingTableRow(
            id = "conflict-${mapping.localSeriesId}-${mapping.animeId.provider}-${mapping.animeId.value}",
            kind = TrackingRowKind.CONFLICT,
            seriesTitle = conflict.series.title,
            localSeriesId = mapping.localSeriesId,
            providerLabel = mapping.animeId.provider.displayName,
            animeIdText = mapping.animeId.value.toString(),
            providerUrl = mapping.animeId.webUrl,
            localProgress = "${conflict.localUpdate.watchedEpisodes ?: 0}/${conflict.series.episodeCount}",
            providerProgress = "${conflict.externalEntry.watchedEpisodes ?: 0}/${conflict.series.episodeCount}",
            plannedAction = strings.reviewConflictAction,
            confidence = mapping.confidence.formatConfidence(),
            statusLabel = strings.conflictStatusLabel,
            statusColor = DanmakuColors.Warning,
            mapping = mapping,
            conflict = conflict,
        )
    }
    val skippedRows = plan.skipped.map { skip ->
        val mapping = skip.provider?.let { provider ->
            mappingsBySeriesAndProvider[skip.localSeriesId to provider]
        }
        val series = seriesById[skip.localSeriesId]
        TrackingTableRow(
            id = "skip-${skip.localSeriesId}-${skip.provider?.name ?: "provider"}-${skip.reason.name}",
            kind = TrackingRowKind.SKIPPED,
            seriesTitle = series?.title ?: skip.localSeriesId,
            localSeriesId = skip.localSeriesId,
            providerLabel = skip.provider?.displayName ?: strings.externalProviderLabel,
            animeIdText = mapping?.animeId?.value?.toString() ?: strings.notLinkedLabel,
            providerUrl = mapping?.animeId?.webUrl,
            localProgress = series?.let { "0/${it.episodeCount}" } ?: "-",
            providerProgress = "-",
            plannedAction = skip.reason.localizedLabel(strings),
            confidence = mapping?.confidence?.formatConfidence() ?: strings.noLinkLabel,
            statusLabel = if (mapping == null) strings.needsMappingLabel else strings.missingLocalSeriesLabel,
            statusColor = DanmakuColors.TextMuted,
            mapping = mapping,
            skip = skip,
        )
    }
    val failureRows = plan.failures.map { failure ->
        val mapping = mappingsByAnimeId[failure.animeId]
        val series = mapping?.let { seriesById[it.localSeriesId] }
        TrackingTableRow(
            id = "failure-${failure.animeId.provider}-${failure.animeId.value}-${failure.failedAtEpochMs}",
            kind = TrackingRowKind.FAILURE,
            seriesTitle = series?.title ?: failure.animeId.webUrl,
            localSeriesId = mapping?.localSeriesId ?: "-",
            providerLabel = failure.animeId.provider.displayName,
            animeIdText = failure.animeId.value.toString(),
            providerUrl = failure.animeId.webUrl,
            localProgress = "-",
            providerProgress = strings.retryAtLabel(failure.retryAfterEpochMs),
            plannedAction = failure.message,
            confidence = mapping?.confidence?.formatConfidence() ?: "-",
            statusLabel = strings.failedAttemptsLabel(failure.attemptCount),
            statusColor = DanmakuColors.Warning,
            mapping = mapping,
            failure = failure,
        )
    }
    return (conflictRows + updateRows + failureRows + skippedRows).sortedWith(
        compareBy<TrackingTableRow> { it.kind.ordinal }
            .thenBy { it.seriesTitle.lowercase() }
            .thenBy { it.providerLabel }
            .thenBy { it.animeIdText },
    )
}

private fun ExternalAnimeListEntry?.providerProgressLabel(
    strings: DesktopStrings,
    episodeCount: Int,
): String =
    this?.let { entry ->
        val watchedEpisodes = entry.watchedEpisodes ?: 0
        val status = entry.status.localizedLabel(strings)
        "$watchedEpisodes/$episodeCount, $status"
    } ?: strings.readbackPendingLabel

@Composable
internal fun TrackingWorkspace(
    strings: DesktopStrings,
    plan: ExternalAnimeTrackingPlan?,
    rows: List<TrackingTableRow>,
    selectedRow: TrackingTableRow?,
    isSyncing: Boolean,
    isReadbackRefreshing: Boolean,
    isProgressImporting: Boolean,
    onSelectRow: (TrackingTableRow) -> Unit,
    onSync: (ExternalAnimeTrackingPlan) -> Unit,
    onRefreshReadback: (List<ExternalAnimeMapping>) -> Unit,
    onApplyProgressImport: (List<ExternalAnimeTrackingPlanConflict>) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 1180.dp
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TrackingTablePanel(
                    strings = strings,
                    plan = plan,
                    rows = rows,
                    selectedRow = selectedRow,
                    isSyncing = isSyncing,
                    isReadbackRefreshing = isReadbackRefreshing,
                    isProgressImporting = isProgressImporting,
                    onSelectRow = onSelectRow,
                    onSync = onSync,
                    onRefreshReadback = onRefreshReadback,
                    onApplyProgressImport = onApplyProgressImport,
                )
                TrackingInspectorPanel(
                    strings = strings,
                    selectedRow = selectedRow,
                    isSyncing = isSyncing,
                    isReadbackRefreshing = isReadbackRefreshing,
                    isProgressImporting = isProgressImporting,
                    onSync = onSync,
                    onRefreshReadback = onRefreshReadback,
                    onApplyProgressImport = onApplyProgressImport,
                )
                ExternalSyncPreviewView(strings = strings, plan = plan, isSyncing = isSyncing, onSync = onSync)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TrackingTablePanel(
                        strings = strings,
                        plan = plan,
                        rows = rows,
                        selectedRow = selectedRow,
                        isSyncing = isSyncing,
                        isReadbackRefreshing = isReadbackRefreshing,
                        isProgressImporting = isProgressImporting,
                        onSelectRow = onSelectRow,
                        onSync = onSync,
                        onRefreshReadback = onRefreshReadback,
                        onApplyProgressImport = onApplyProgressImport,
                    )
                    ExternalSyncPreviewView(strings = strings, plan = plan, isSyncing = isSyncing, onSync = onSync)
                }
                TrackingInspectorPanel(
                    strings = strings,
                    selectedRow = selectedRow,
                    isSyncing = isSyncing,
                    isReadbackRefreshing = isReadbackRefreshing,
                    isProgressImporting = isProgressImporting,
                    onSync = onSync,
                    onRefreshReadback = onRefreshReadback,
                    onApplyProgressImport = onApplyProgressImport,
                    modifier = Modifier.width(380.dp),
                )
            }
        }
    }
}

@Composable
internal fun TrackingTablePanel(
    strings: DesktopStrings,
    plan: ExternalAnimeTrackingPlan?,
    rows: List<TrackingTableRow>,
    selectedRow: TrackingTableRow?,
    isSyncing: Boolean,
    isReadbackRefreshing: Boolean,
    isProgressImporting: Boolean,
    onSelectRow: (TrackingTableRow) -> Unit,
    onSync: (ExternalAnimeTrackingPlan) -> Unit,
    onRefreshReadback: (List<ExternalAnimeMapping>) -> Unit,
    onApplyProgressImport: (List<ExternalAnimeTrackingPlanConflict>) -> Unit,
) {
    SectionCard(strings.trackingTableTitle) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(strings.trackingTableDescription, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(
                enabled = plan?.updates?.isNotEmpty() == true &&
                    !isSyncing &&
                    !isReadbackRefreshing &&
                    !isProgressImporting,
                onClick = { plan?.let(onSync) },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isSyncing) strings.syncingAction else strings.syncAllReadyAction)
            }
            Button(
                enabled = plan != null &&
                    rows.any { it.mapping != null } &&
                    !isSyncing &&
                    !isReadbackRefreshing &&
                    !isProgressImporting,
                onClick = {
                    rows
                        .mapNotNull(TrackingTableRow::mapping)
                        .distinctBy { mapping -> mapping.animeId }
                        .let(onRefreshReadback)
                },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isReadbackRefreshing) strings.refreshingAction else strings.refreshProviderStateAction)
            }
        }
        if (rows.isEmpty()) {
            EmptyState(strings.trackingRowsEmptyText)
            return@SectionCard
        }
        TrackingTableHeader(strings)
        LazyColumn(
            modifier = Modifier.heightIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(rows, key = TrackingTableRow::id) { row ->
                TrackingTableRowView(
                    row = row,
                    selected = selectedRow?.id == row.id,
                    onClick = { onSelectRow(row) },
                )
            }
        }
    }
}

@Composable
internal fun TrackingTableHeader(strings: DesktopStrings) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(strings.localSeriesHeader, color = DanmakuColors.TextMuted, modifier = Modifier.weight(1.6f), maxLines = 1)
        Text(strings.providerHeader, color = DanmakuColors.TextMuted, modifier = Modifier.weight(1.0f), maxLines = 1)
        Text(strings.progressLabel, color = DanmakuColors.TextMuted, modifier = Modifier.weight(0.9f), maxLines = 1)
        Text(strings.actionHeader, color = DanmakuColors.TextMuted, modifier = Modifier.weight(1.2f), maxLines = 1)
        Text(strings.statusLabel, color = DanmakuColors.TextMuted, modifier = Modifier.weight(0.9f), maxLines = 1)
    }
}

@Composable
internal fun TrackingTableRowView(
    row: TrackingTableRow,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DanmakuColors.AccentSoft else DanmakuColors.SurfaceRaised.copy(alpha = 0.58f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1.6f)) {
            Text(row.seriesTitle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.localSeriesId, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(modifier = Modifier.weight(1.0f)) {
            Text(row.providerLabel, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("#${row.animeIdText}", color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(modifier = Modifier.weight(0.9f)) {
            Text(row.localProgress, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.providerProgress, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(row.plannedAction, color = Color.White, modifier = Modifier.weight(1.2f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        StatusPill(row.statusLabel, active = row.kind == TrackingRowKind.UPDATE, color = row.statusColor, modifier = Modifier.weight(0.9f))
    }
}

@Composable
internal fun TrackingInspectorPanel(
    strings: DesktopStrings,
    selectedRow: TrackingTableRow?,
    isSyncing: Boolean,
    isReadbackRefreshing: Boolean,
    isProgressImporting: Boolean,
    onSync: (ExternalAnimeTrackingPlan) -> Unit,
    onRefreshReadback: (List<ExternalAnimeMapping>) -> Unit,
    onApplyProgressImport: (List<ExternalAnimeTrackingPlanConflict>) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(strings.mappingInspectorTitle, modifier = modifier) {
        if (selectedRow == null) {
            EmptyState(strings.mappingInspectorEmptyText)
            return@SectionCard
        }
        Text(selectedRow.seriesTitle, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        MetadataRow(strings.providerHeader, selectedRow.providerLabel)
        MetadataRow(strings.animeIdLabel, selectedRow.animeIdText)
        selectedRow.providerUrl?.let { MetadataRow(strings.providerUrlLabel, it) }
        MetadataRow(strings.localSeriesIdLabel, selectedRow.localSeriesId)
        MetadataRow(strings.localProgressLabel, selectedRow.localProgress)
        MetadataRow(strings.providerProgressLabel, selectedRow.providerProgress)
        MetadataRow(strings.confidenceLabel, selectedRow.confidence)
        MetadataRow(strings.statusLabel, selectedRow.statusLabel, selectedRow.statusColor)
        selectedRow.conflict?.let { conflict ->
            MetadataRow(strings.conflictLabel, conflict.reason.localizedLabel(strings), DanmakuColors.Warning)
            MetadataRow(strings.externalWatchedLabel, (conflict.externalEntry.watchedEpisodes ?: 0).toString())
        }
        selectedRow.failure?.let { failure ->
            MetadataRow(strings.failureLabel, failure.message, DanmakuColors.Warning)
            MetadataRow(strings.nextRetryLabel, failure.retryAfterEpochMs.formatEpochTime())
        }
        selectedRow.skip?.let { skip ->
            MetadataRow(strings.skippedLabel, skip.reason.localizedLabel(strings))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = selectedRow.update != null &&
                    !isSyncing &&
                    !isReadbackRefreshing &&
                    !isProgressImporting,
                onClick = {
                    selectedRow.update?.let { update ->
                        onSync(
                            ExternalAnimeTrackingPlan(
                                updates = listOf(update),
                                skipped = emptyList(),
                            ),
                        )
                    }
                },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isSyncing) strings.syncingAction else strings.syncSelectedAction)
            }
            Button(
                enabled = selectedRow.mapping != null &&
                    !isSyncing &&
                    !isReadbackRefreshing &&
                    !isProgressImporting,
                onClick = {
                    selectedRow.mapping?.let { mapping ->
                        onRefreshReadback(listOf(mapping))
                    }
                },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isReadbackRefreshing) strings.refreshingAction else strings.refreshProviderStateAction)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = false, onClick = {}) {
                Text(strings.removeMappingAction)
            }
            Button(
                enabled = selectedRow.conflict != null &&
                    !isSyncing &&
                    !isReadbackRefreshing &&
                    !isProgressImporting,
                onClick = {
                    selectedRow.conflict?.let { conflict ->
                        onApplyProgressImport(listOf(conflict))
                    }
                },
            ) {
                Text(strings.resolveConflictAction)
            }
        }
        Text(
            strings.trackingPlannedControlsText,
            color = DanmakuColors.TextMuted,
        )
    }
}

@Composable
internal fun TrackingProviderCard(
    title: String,
    status: String,
    ready: Boolean,
    detail: String,
    actionLabel: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(title, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                imageVector = if (ready) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (ready) DanmakuColors.Good else DanmakuColors.Warning,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(status, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        LibraryActionButton(
            imageVector = Icons.Filled.MoreHoriz,
            label = actionLabel,
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenSettings,
        )
    }
}


