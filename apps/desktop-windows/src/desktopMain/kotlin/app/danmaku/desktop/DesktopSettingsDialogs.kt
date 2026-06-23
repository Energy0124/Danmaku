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
internal fun ServerDashboardDialog(
    strings: DesktopStrings,
    serverBaseUrl: String,
    networkUrls: List<String>,
    pairingToken: String,
    recentServerEvents: List<LocalLibraryServerEvent>,
    localServerConnectionTestStatus: SettingsConnectionTestStatus?,
    onTestLocalServerConnection: () -> Unit,
    onDismiss: () -> Unit,
) {
    fun copyToClipboard(value: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(value), null)
    }
    val recentEvents = recentServerEvents.takeLast(10).asReversed()

    AlertDialog(
        modifier = Modifier.width(760.dp),
        onDismissRequest = onDismiss,
        title = { Text(strings.serverDashboardTitle) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.pairingAndLanAccessTitle, fontWeight = FontWeight.Bold)
                    ServerDashboardCopyRow(
                        strings = strings,
                        label = strings.serverBaseUrlLabel,
                        value = serverBaseUrl,
                        onCopy = { copyToClipboard(serverBaseUrl) },
                    )

                    if (networkUrls.isEmpty()) {
                        MetadataRow(strings.lanUrlsLabel, strings.noLanUrlDetectedLabel, DanmakuColors.TextMuted)
                    } else {
                        networkUrls.forEachIndexed { index, url ->
                            ServerDashboardCopyRow(
                                strings = strings,
                                label = strings.lanUrlNumberedLabel(index + 1),
                                value = url,
                                onCopy = { copyToClipboard(url) },
                            )
                        }
                    }
                    MetadataRow(
                        strings.discoveryLabel,
                        "UDP ${app.danmaku.domain.LanLibraryServerAnnouncement.DEFAULT_DISCOVERY_PORT}",
                    )
                }

                Divider(color = DanmakuColors.SurfaceRaised)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.healthTitle, fontWeight = FontWeight.Bold)
                    localServerConnectionTestStatus?.let {
                        SettingsConnectionTestStatusRow(strings = strings, label = strings.lastTestLabel, status = it)
                    } ?: MetadataRow(strings.lastTestLabel, strings.notCheckedThisSessionLabel, DanmakuColors.TextMuted)
                    MetadataRow(strings.recentRequestsLabel, recentServerEvents.size.toString())
                    MetadataRow(strings.connectedClientsLabel, strings.connectedClientsPlannedText, DanmakuColors.TextMuted)
                    MetadataRow(strings.bandwidthLabel, strings.bandwidthPlannedText, DanmakuColors.TextMuted)
                }

                Divider(color = DanmakuColors.SurfaceRaised)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.recentServerRequestsTitle, fontWeight = FontWeight.Bold)
                    if (recentEvents.isEmpty()) {
                        Text(strings.noServerRequestsText, color = DanmakuColors.TextMuted)
                    } else {
                        recentEvents.forEach { event ->
                            ServerDashboardEventRow(event)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onTestLocalServerConnection) {
                Text(strings.testServerAction)
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
internal fun ServerDashboardCopyRow(
    strings: DesktopStrings,
    label: String,
    value: String,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, color = DanmakuColors.TextMuted, modifier = Modifier.width(110.dp), maxLines = 1)
        Text(
            value,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LibraryActionButton(
            imageVector = Icons.Filled.ContentCopy,
            label = strings.copyAction,
            onClick = onCopy,
        )
    }
}

@Composable
internal fun ServerDashboardEventRow(event: LocalLibraryServerEvent) {
    val isHealthyStatus = event.status in 200..399
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            event.occurredAtEpochMs.formatEpochTime(),
            color = DanmakuColors.TextMuted,
            modifier = Modifier.width(118.dp),
            maxLines = 1,
        )
        StatusPill(
            text = event.status.toString(),
            icon = if (isHealthyStatus) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            active = isHealthyStatus,
            color = if (isHealthyStatus) DanmakuColors.Good else DanmakuColors.Warning,
        )
        Text(
            "${event.method} ${event.path}",
            modifier = Modifier.weight(1.15f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            event.detail.redactToken(),
            color = DanmakuColors.TextMuted,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun DanmakuCacheManagerDialog(
    strings: DesktopStrings,
    cacheEntries: List<DesktopDandanplayCommentCache>,
    cacheMaxAgeDays: Int,
    onRefresh: () -> Unit,
    onDeleteEntry: (String) -> Unit,
    onCleanupExpired: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedMediaId by remember(cacheEntries) {
        mutableStateOf(cacheEntries.firstOrNull()?.mediaId)
    }
    var pendingDeleteEntry by remember { mutableStateOf<DesktopDandanplayCommentCache?>(null) }
    var confirmCleanupExpired by remember { mutableStateOf(false) }
    val selectedEntry = cacheEntries.firstOrNull { it.mediaId == selectedMediaId }
        ?: cacheEntries.firstOrNull()
    val staleCount = cacheEntries.count { it.isExpiredForCacheManager(cacheMaxAgeDays) }

    AlertDialog(
        modifier = Modifier.width(880.dp),
        onDismissRequest = onDismiss,
        title = { Text(strings.danmakuCacheManagerTitle) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard(
                        title = strings.cachedSummaryTitle,
                        value = cacheEntries.size.toString(),
                        caption = strings.episodesLabel,
                        modifier = Modifier.weight(1f),
                    )
                    SummaryCard(
                        title = strings.expiredSummaryTitle,
                        value = staleCount.toString(),
                        caption = strings.cacheDayRuleCaption(cacheMaxAgeDays),
                        modifier = Modifier.weight(1f),
                    )
                    SummaryCard(
                        title = strings.commentsSummaryTitle,
                        value = cacheEntries.sumOf { it.commentCountForCacheManager() }.toString(),
                        caption = strings.cachedEventsCaption,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1.1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(strings.persistedEntriesTitle, fontWeight = FontWeight.Bold)
                        if (cacheEntries.isEmpty()) {
                            Text(strings.noDandanplayCachesText, color = DanmakuColors.TextMuted)
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 360.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(cacheEntries, key = DesktopDandanplayCommentCache::mediaId) { entry ->
                                    DanmakuCacheEntryRow(
                                        strings = strings,
                                        entry = entry,
                                        selected = selectedEntry?.mediaId == entry.mediaId,
                                        cacheMaxAgeDays = cacheMaxAgeDays,
                                        onSelect = { selectedMediaId = entry.mediaId },
                                    )
                                }
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(strings.selectedCacheTitle, fontWeight = FontWeight.Bold)
                        if (selectedEntry == null) {
                            Text(
                                strings.selectCachePromptText,
                                color = DanmakuColors.TextMuted,
                            )
                        } else {
                            DanmakuCacheEntryDetails(
                                strings = strings,
                                entry = selectedEntry,
                                cacheMaxAgeDays = cacheMaxAgeDays,
                                onDelete = { pendingDeleteEntry = selectedEntry },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onRefresh) {
                Text(strings.refreshAction)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { confirmCleanupExpired = true },
                    enabled = cacheEntries.isNotEmpty(),
                ) {
                    Text(strings.cleanExpiredAction)
                }
                TextButton(onClick = onDismiss) {
                    Text(strings.closeAction)
                }
            }
        },
    )

    pendingDeleteEntry?.let { entry ->
        SettingsConfirmationDialog(
            title = strings.deleteCachedDanmakuTitle,
            text = strings.deleteCachedDanmakuText(entry.displayTitleForCacheManager()),
            confirmLabel = strings.deleteCacheAction,
            cancelLabel = strings.cancelAction,
            onConfirm = { onDeleteEntry(entry.mediaId) },
            onDismiss = { pendingDeleteEntry = null },
        )
    }
    if (confirmCleanupExpired) {
        SettingsConfirmationDialog(
            title = strings.cleanExpiredDanmakuCachesTitle,
            text = strings.cleanExpiredDanmakuCachesText(cacheMaxAgeDays),
            confirmLabel = strings.cleanExpiredAction,
            cancelLabel = strings.cancelAction,
            onConfirm = onCleanupExpired,
            onDismiss = { confirmCleanupExpired = false },
        )
    }
}

@Composable
internal fun DanmakuCacheEntryRow(
    strings: DesktopStrings,
    entry: DesktopDandanplayCommentCache,
    selected: Boolean,
    cacheMaxAgeDays: Int,
    onSelect: () -> Unit,
) {
    val isExpired = entry.isExpiredForCacheManager(cacheMaxAgeDays)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) DanmakuColors.AccentSoft else DanmakuColors.SurfaceRaised.copy(alpha = 0.56f))
            .clickable(onClick = onSelect)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusPill(
            text = if (isExpired) strings.expiredStatusLabel else strings.readyStatusLabel,
            icon = if (isExpired) Icons.Filled.Warning else Icons.Filled.CheckCircle,
            active = !isExpired,
            color = if (isExpired) DanmakuColors.Warning else DanmakuColors.Good,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                entry.displayTitleForCacheManager(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                strings.cacheEntryCommentSummary(
                    entry.commentCountForCacheManager(),
                    entry.fetchedAtEpochMs,
                    entry.fileName,
                ),
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun DanmakuCacheEntryDetails(
    strings: DesktopStrings,
    entry: DesktopDandanplayCommentCache,
    cacheMaxAgeDays: Int,
    onDelete: () -> Unit,
) {
    val isExpired = entry.isExpiredForCacheManager(cacheMaxAgeDays)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetadataRow(
            strings.statusLabel,
            if (isExpired) strings.expiredStatusLabel else strings.readyStatusLabel,
            if (isExpired) DanmakuColors.Warning else DanmakuColors.Good,
        )
        MetadataRow(strings.animeLabel, entry.animeTitle ?: strings.unknownAnimeLabel)
        MetadataRow(strings.episodeLabel, entry.episodeTitle ?: entry.episodeId?.toString() ?: strings.unknownEpisodeLabel)
        MetadataRow(strings.mediaIdLabel, entry.mediaId)
        MetadataRow(strings.fileLabel, entry.fileName)
        MetadataRow(strings.fileSizeLabel, entry.fileSizeBytes.formatLibrarySize())
        MetadataRow(strings.commentsLabel, entry.commentCountForCacheManager().toString())
        MetadataRow(strings.fetchedLabel, entry.fetchedAtEpochMs.formatEpochTime())
        MetadataRow(strings.shiftLabel, entry.shiftSeconds?.let(strings.shiftSecondsLabel) ?: strings.noneLabel)
        entry.renderedAssPath?.let { MetadataRow(strings.assCacheLabel, it) }
        LibraryActionButton(
            imageVector = Icons.Filled.Delete,
            label = strings.deleteCacheAction,
            onClick = onDelete,
        )
    }
}

@Composable
internal fun SettingsConfirmationDialog(
    title: String,
    text: String,
    confirmLabel: String,
    cancelLabel: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelLabel)
            }
        },
    )
}

internal fun integerRangeError(
    text: String,
    range: IntRange,
    unit: String,
): String? {
    val value = text.trim().toIntOrNull()
        ?: return "Enter a whole number from ${range.first} to ${range.last} $unit."
    return if (value in range) {
        null
    } else {
        "Enter a value from ${range.first} to ${range.last} $unit."
    }
}

internal fun longRangeError(
    text: String,
    range: LongRange,
    unit: String,
): String? {
    val value = text.trim().toLongOrNull()
        ?: return "Enter a whole number from ${range.first} to ${range.last} $unit."
    return if (value in range) {
        null
    } else {
        "Enter a value from ${range.first} to ${range.last} $unit."
    }
}

internal fun httpUrlError(
    text: String,
    label: String,
): String? {
    val value = text.trim()
    if (value.isEmpty()) {
        return "$label is required."
    }
    val uri = runCatching { java.net.URI(value) }.getOrNull()
        ?: return "$label must be a valid HTTP or HTTPS URL."
    val scheme = uri.scheme?.lowercase()
    return when {
        scheme != "http" && scheme != "https" -> "$label must use HTTP or HTTPS."
        uri.host.isNullOrBlank() -> "$label must include a host."
        else -> null
    }
}


