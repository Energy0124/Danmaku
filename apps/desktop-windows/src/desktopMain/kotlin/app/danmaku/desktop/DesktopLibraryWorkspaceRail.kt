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
