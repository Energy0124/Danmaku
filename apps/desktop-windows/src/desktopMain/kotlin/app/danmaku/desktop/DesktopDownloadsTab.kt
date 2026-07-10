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
internal fun DownloadsTab(
    strings: DesktopStrings,
    isIndexing: Boolean,
    webhookUrls: List<String>,
    webhookToken: String,
    registeredRoots: List<DesktopLibraryRoot>,
    downloadQueueItems: List<DesktopDownloadQueueItem>,
    onAddAniRssOutputFolder: () -> Unit,
    onRefreshQueue: () -> Unit,
    onRemoveQueueItem: (DesktopDownloadQueueItem) -> Unit,
    onOpenOutputFolder: (DesktopDownloadQueueItem) -> Unit,
) {
    TabScaffold {
        val aniRssRoots = registeredRoots.filter {
            it.provenance == DesktopLibraryRootProvenance.ANI_RSS_OUTPUT_FOLDER
        }
        val activeDownloads = downloadQueueItems.count(DesktopDownloadQueueItem::isActiveDownload)
        val queuedDownloads = downloadQueueItems.count(DesktopDownloadQueueItem::isQueuedDownload)
        val completedDownloads = downloadQueueItems.count(DesktopDownloadQueueItem::isCompletedDownload)
        val failedDownloads = downloadQueueItems.count(DesktopDownloadQueueItem::isFailedDownload)
        var selectedFilter by remember { mutableStateOf(DownloadQueueFilter.ALL) }
        val filteredDownloadQueueItems = remember(downloadQueueItems, selectedFilter) {
            downloadQueueItems.filter(selectedFilter::matches)
        }
        var selectedDownloadId by remember(downloadQueueItems) {
            mutableStateOf(downloadQueueItems.firstOrNull()?.id)
        }
        val selectedDownload = filteredDownloadQueueItems.firstOrNull { it.id == selectedDownloadId }
            ?: filteredDownloadQueueItems.firstOrNull()
            ?: downloadQueueItems.firstOrNull()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                title = strings.downloadsActiveTitle,
                value = activeDownloads.toString(),
                caption = strings.downloadsActiveCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.downloadsQueuedTitle,
                value = queuedDownloads.toString(),
                caption = strings.downloadsQueuedCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.downloadsCompletedTitle,
                value = completedDownloads.toString(),
                caption = strings.downloadsCompletedCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.downloadsFailedTitle,
                value = failedDownloads.toString(),
                caption = strings.downloadsFailedCaption,
                modifier = Modifier.weight(1f),
            )
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 1180.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DownloadsQueuePanel(
                        strings = strings,
                        downloadQueueItems = filteredDownloadQueueItems,
                        totalItemCount = downloadQueueItems.size,
                        selectedFilter = selectedFilter,
                        selectedItem = selectedDownload,
                        onFilterChange = { selectedFilter = it },
                        onSelectItem = { selectedDownloadId = it.id },
                        onRefreshQueue = onRefreshQueue,
                        onRemoveQueueItem = onRemoveQueueItem,
                        onOpenOutputFolder = onOpenOutputFolder,
                    )
                    DownloadInspectorPanel(
                        strings = strings,
                        selectedItem = selectedDownload,
                        onRemoveQueueItem = onRemoveQueueItem,
                        onOpenOutputFolder = onOpenOutputFolder,
                    )
                    DownloadsSetupPanel(
                        strings = strings,
                        isIndexing = isIndexing,
                        webhookUrls = webhookUrls,
                        webhookToken = webhookToken,
                        aniRssRoots = aniRssRoots,
                        onAddAniRssOutputFolder = onAddAniRssOutputFolder,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DownloadsQueuePanel(
                        strings = strings,
                        downloadQueueItems = filteredDownloadQueueItems,
                        totalItemCount = downloadQueueItems.size,
                        selectedFilter = selectedFilter,
                        selectedItem = selectedDownload,
                        onFilterChange = { selectedFilter = it },
                        onSelectItem = { selectedDownloadId = it.id },
                        onRefreshQueue = onRefreshQueue,
                        onRemoveQueueItem = onRemoveQueueItem,
                        onOpenOutputFolder = onOpenOutputFolder,
                        modifier = Modifier.weight(1f),
                    )
                    Column(
                        modifier = Modifier.width(360.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        DownloadInspectorPanel(
                            strings = strings,
                            selectedItem = selectedDownload,
                            onRemoveQueueItem = onRemoveQueueItem,
                            onOpenOutputFolder = onOpenOutputFolder,
                        )
                        DownloadsSetupPanel(
                            strings = strings,
                            isIndexing = isIndexing,
                            webhookUrls = webhookUrls,
                            webhookToken = webhookToken,
                            aniRssRoots = aniRssRoots,
                            onAddAniRssOutputFolder = onAddAniRssOutputFolder,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DownloadsQueuePanel(
    strings: DesktopStrings,
    downloadQueueItems: List<DesktopDownloadQueueItem>,
    totalItemCount: Int,
    selectedFilter: DownloadQueueFilter,
    selectedItem: DesktopDownloadQueueItem?,
    onFilterChange: (DownloadQueueFilter) -> Unit,
    onSelectItem: (DesktopDownloadQueueItem) -> Unit,
    onRefreshQueue: () -> Unit,
    onRemoveQueueItem: (DesktopDownloadQueueItem) -> Unit,
    onOpenOutputFolder: (DesktopDownloadQueueItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingRemoval by remember { mutableStateOf<DesktopDownloadQueueItem?>(null) }
    SectionCard(strings.downloadQueueTitle, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DownloadQueueFilter.entries.forEach { filter ->
                    Button(
                        onClick = { onFilterChange(filter) },
                        enabled = selectedFilter != filter,
                    ) {
                        Text(strings.downloadFilterTitle(filter))
                    }
                }
            }
            PlayerIconButton(
                imageVector = Icons.Filled.Refresh,
                contentDescription = strings.refreshDownloadQueueAction,
                onClick = onRefreshQueue,
            )
        }
        Text(
            strings.downloadQueuePolicyText,
            color = DanmakuColors.TextMuted,
        )
        if (downloadQueueItems.isEmpty()) {
            EmptyState(
                if (totalItemCount == 0) {
                    strings.downloadQueueEmptyText
                } else {
                    strings.downloadQueueFilterEmptyText(strings.downloadFilterTitle(selectedFilter))
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(downloadQueueItems, key = DesktopDownloadQueueItem::id) { item ->
                    DownloadQueueRow(
                        strings = strings,
                        item = item,
                        selected = selectedItem?.id == item.id,
                        onSelect = { onSelectItem(item) },
                        onOpenOutputFolder = { onOpenOutputFolder(item) },
                        onRemoveQueueItem = { pendingRemoval = item },
                    )
                }
            }
        }
    }
    pendingRemoval?.let { item ->
        SettingsConfirmationDialog(
            title = strings.removeDownloadTitle,
            text = strings.removeDownloadText(item.outputPath),
            confirmLabel = strings.removeAction,
            onConfirm = { onRemoveQueueItem(item) },
            onDismiss = { pendingRemoval = null },
        )
    }
}

@Composable
internal fun DownloadsSetupPanel(
    strings: DesktopStrings,
    isIndexing: Boolean,
    webhookUrls: List<String>,
    webhookToken: String,
    aniRssRoots: List<DesktopLibraryRoot>,
    onAddAniRssOutputFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(strings.downloadSetupAuthorizedSourcesTitle) {
            Text(
                strings.downloadSetupAuthorizedSourcesText,
                color = DanmakuColors.TextMuted,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusPill(strings.authorizedImportsOnlyLabel, icon = Icons.Filled.CheckCircle, active = true, color = DanmakuColors.Good)
                StatusPill(strings.queueExecutionPlannedLabel, icon = Icons.Filled.Warning, color = DanmakuColors.Warning)
                StatusPill(strings.importRootCountLabel(aniRssRoots.size), icon = Icons.Filled.FolderOpen)
            }
            LibraryActionButton(
                imageVector = Icons.Filled.FolderOpen,
                label = strings.addAniRssOutputFolderAction,
                enabled = !isIndexing,
                modifier = Modifier.fillMaxWidth(),
                onClick = onAddAniRssOutputFolder,
            )
        }
        SectionCard(strings.aniRssWebhookTitle) {
            if (webhookUrls.isEmpty()) {
                EmptyState(strings.noWebhookUrlText)
            } else {
                webhookUrls.forEach { url ->
                    MetadataRow(strings.webhookUrlLabel, url)
                }
            }
            MetadataRow(strings.webhookHeaderLabel, "X-Danmaku-Webhook-Token")
            MetadataRow(strings.webhookTokenLabel, webhookToken)
        }
        SectionCard(strings.importRootsTitle) {
            if (aniRssRoots.isEmpty()) {
                EmptyState(strings.noAniRssRootsText)
            } else {
                aniRssRoots.forEach { root -> MediaRootRow(root) }
            }
        }
    }
}

@Composable
internal fun DownloadQueueRow(
    strings: DesktopStrings,
    item: DesktopDownloadQueueItem,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpenOutputFolder: () -> Unit,
    onRemoveQueueItem: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DanmakuColors.AccentSoft else DanmakuColors.SurfaceRaised)
            .clickable(onClick = onSelect)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (item.failureMessage == null) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (item.failureMessage == null) DanmakuColors.TextMuted else DanmakuColors.Warning,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.sourceUri.redactToken(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.outputPath, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.failureMessage?.let {
                Text(it, color = DanmakuColors.Warning, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MiniProgressBar(percent = item.downloadProgressPercent())
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StatusPill(item.state, color = item.downloadStateColor())
            Text(item.downloadProgressLabel(), color = DanmakuColors.TextMuted, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlayerIconButton(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = strings.openOutputFolderAction,
                    onClick = onOpenOutputFolder,
                )
                PlayerIconButton(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = strings.removeQueueItemAction,
                    onClick = onRemoveQueueItem,
                )
            }
        }
    }
}

@Composable
internal fun DownloadInspectorPanel(
    strings: DesktopStrings,
    selectedItem: DesktopDownloadQueueItem?,
    onRemoveQueueItem: (DesktopDownloadQueueItem) -> Unit,
    onOpenOutputFolder: (DesktopDownloadQueueItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingRemoval by remember { mutableStateOf<DesktopDownloadQueueItem?>(null) }
    SectionCard(strings.downloadInspectorTitle, modifier = modifier) {
        if (selectedItem == null) {
            EmptyState(strings.downloadInspectorEmptyText)
            return@SectionCard
        }
        Text(selectedItem.sourceUri.redactToken(), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        MetadataRow(strings.stateLabel, selectedItem.state, selectedItem.downloadStateColor())
        MetadataRow(strings.progressLabel, selectedItem.downloadProgressLabel())
        MetadataRow(strings.createdLabel, selectedItem.createdAtEpochMs.formatEpochTime())
        MetadataRow(strings.updatedLabel, selectedItem.updatedAtEpochMs.formatEpochTime())
        MetadataRow(strings.outputLabel, selectedItem.outputPath)
        MetadataRow(strings.sourceLabel, selectedItem.sourceUri.redactToken())
        selectedItem.failureMessage?.let { failure ->
            MetadataRow(strings.failureLabel, failure, DanmakuColors.Warning)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryActionButton(
                imageVector = Icons.Filled.FolderOpen,
                label = strings.openFolderAction,
                onClick = { onOpenOutputFolder(selectedItem) },
            )
            LibraryActionButton(
                imageVector = Icons.Filled.Delete,
                label = strings.removeAction,
                onClick = { pendingRemoval = selectedItem },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryActionButton(
                imageVector = Icons.Filled.Pause,
                label = strings.pauseAction,
                enabled = false,
                onClick = {},
            )
            LibraryActionButton(
                imageVector = Icons.Filled.PlayArrow,
                label = strings.resumeAction,
                enabled = false,
                onClick = {},
            )
            LibraryActionButton(
                imageVector = Icons.Filled.Refresh,
                label = strings.retryAction,
                enabled = false,
                onClick = {},
            )
            LibraryActionButton(
                imageVector = Icons.Filled.Delete,
                label = strings.cancelAction,
                enabled = false,
                onClick = {},
            )
        }
        Text(
            strings.downloadExecutionPlannedText,
            color = DanmakuColors.TextMuted,
        )
    }
    pendingRemoval?.let { item ->
        SettingsConfirmationDialog(
            title = strings.removeDownloadTitle,
            text = strings.removeDownloadText(item.outputPath),
            confirmLabel = strings.removeAction,
            onConfirm = { onRemoveQueueItem(item) },
            onDismiss = { pendingRemoval = null },
        )
    }
}

