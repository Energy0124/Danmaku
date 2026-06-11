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
internal fun TabScaffold(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
internal fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = DanmakuColors.Surface,
        elevation = 8.dp,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
            Divider(color = DanmakuColors.SurfaceRaised)
            content()
        }
    }
}

@Composable
internal fun SummaryCard(
    title: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.heightIn(min = 112.dp),
        backgroundColor = DanmakuColors.Surface,
        elevation = 8.dp,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, color = DanmakuColors.TextMuted)
            Text(value, style = MaterialTheme.typography.h4, fontWeight = FontWeight.Bold)
            Text(caption, color = DanmakuColors.TextMuted)
        }
    }
}

@Composable
internal fun StatusPill(
    text: String,
    icon: ImageVector? = null,
    active: Boolean = false,
    color: Color? = null,
    modifier: Modifier = Modifier,
) {
    val contentColor = color ?: if (active) Color.White else DanmakuColors.TextMuted
    Row(
        modifier = modifier
            .background(if (active) DanmakuColors.AccentSoft else DanmakuColors.SurfaceRaised, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(text = text, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun MetadataRow(
    label: String,
    value: String,
    valueColor: Color = Color.White,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = DanmakuColors.TextMuted,
            modifier = Modifier.width(140.dp),
            maxLines = 1,
        )
        Text(
            text = value,
            color = valueColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun EmptyState(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .background(DanmakuColors.SurfaceRaised, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text, color = DanmakuColors.TextMuted)
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
internal fun DiagnosticsPanel(
    strings: DesktopStrings,
    diagnosticLog: List<DesktopDiagnosticLogEntry>,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = strings.diagnosticsTitle,
        modifier = modifier,
    ) {
        if (diagnosticLog.isEmpty()) {
            EmptyState(strings.noDiagnosticsText)
            return@SectionCard
        }
        LazyColumn(modifier = Modifier.height(240.dp)) {
            items(diagnosticLog.asReversed()) { entry ->
                DiagnosticLogRow(entry)
            }
        }
    }
}

@Composable
internal fun DiagnosticLogRow(entry: DesktopDiagnosticLogEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = entry.occurredAtEpochMs.toDiagnosticTime(),
            color = DanmakuColors.TextMuted,
            modifier = Modifier.width(76.dp),
            maxLines = 1,
        )
        Text(
            text = entry.category,
            color = DanmakuColors.Accent,
            modifier = Modifier.width(96.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entry.message,
            color = Color.White,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun MediaRootRow(root: DesktopLibraryRoot) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(root.displayName, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Text(root.state.name, color = DanmakuColors.Good)
        }
        Text(root.provenance.name, color = DanmakuColors.TextMuted)
        Text(
            root.normalizedPath.toString(),
            color = DanmakuColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun DesktopSeriesRow(
    series: LibrarySeries,
    watchSummary: LibrarySeriesWatchSummary?,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) DanmakuColors.SurfaceRaised else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(series.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${series.episodeCount} episodes, ${series.seasons.size} seasons",
            color = DanmakuColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            watchSummary.progressLabel(),
            color = DanmakuColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${series.subtitleTrackCount} subtitle tracks, ${series.totalSizeBytes.formatLibrarySize()}",
            color = DanmakuColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun DandanplayMatchCandidatePicker(
    preparation: DesktopLocalPlaybackPreparation,
    status: DandanplayPlaybackUiStatus,
    isPreparing: Boolean,
    onSelectDandanplayMatch: (DesktopLocalPlaybackPreparation, DandanplayMatch) -> Unit,
) {
    val candidates = status.matchCandidates.distinctBy(DandanplayMatch::episodeId)
    if (candidates.size <= 1) return

    Spacer(modifier = Modifier.height(8.dp))
    Divider(color = DanmakuColors.SurfaceRaised)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        "Match candidates",
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(6.dp))
    LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
        items(candidates, key = { it.episodeId }) { match ->
            val isSelected = match.episodeId == status.selectedEpisodeId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        match.displayTitle,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        dandanplayMatchCandidateDetail(match),
                        color = DanmakuColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = { onSelectDandanplayMatch(preparation, match) },
                    enabled = !isPreparing && !isSelected,
                ) {
                    Text(if (isSelected) "Selected" else "Use match")
                }
            }
        }
    }
}

internal fun dandanplayMatchCandidateDetail(match: DandanplayMatch): String =
    buildList {
        add("Episode ID ${match.episodeId}")
        match.animeId?.let { add("Anime ID $it") }
        match.shiftSeconds?.let { add("Shift ${it}s") }
    }.joinToString(" / ")

internal fun LibrarySeriesWatchSummary?.progressLabel(): String =
    if (this == null) {
        "0 watched, 0 watching, 0 new"
    } else {
        "$watchedCount watched, $inProgressCount watching, $newCount new"
    }

@Composable
internal fun NextUpRow(
    strings: DesktopStrings,
    item: LibraryNextUpItem,
    selected: Boolean,
    isRefreshingMetadata: Boolean,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    val metadataReadiness = item.mediaItem.metadataReadiness(strings = strings, isRefreshing = isRefreshingMetadata)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DanmakuColors.SurfaceRaised.copy(alpha = 0.70f) else Color.Transparent)
            .clickable { onShowDetails(item.mediaItem) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.mediaItem.displaySeriesTitle(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.mediaItem.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.mediaItem.localSeriesLabel(strings)?.let { label ->
                Text(label, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "${item.nextUpLabel(strings)} - ${metadataReadiness.label}",
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (compact) {
                PlayerIconButton(
                    imageVector = Icons.Filled.Subtitles,
                    contentDescription = strings.detailsAction,
                    onClick = { onShowDetails(item.mediaItem) },
                )
            } else {
                LibraryActionButton(
                    imageVector = Icons.Filled.Subtitles,
                    label = strings.detailsAction,
                    onClick = { onShowDetails(item.mediaItem) },
                )
            }
            PlayerIconButton(
                imageVector = Icons.Filled.Refresh,
                contentDescription = if (isRefreshingMetadata) {
                    strings.refreshingEpisodeMetadataAction
                } else {
                    strings.refreshEpisodeMetadataAction
                },
                enabled = !isPreparing && !isRefreshingMetadata,
                active = isRefreshingMetadata,
                onClick = { onRefreshEpisodeMetadata(item.mediaItem) },
            )
            LibraryActionButton(
                imageVector = Icons.Filled.Refresh,
                label = if (isPreparing) strings.preparingAction else if (compact) strings.prepareShortAction else strings.prepareAction,
                enabled = !isPreparing,
                onClick = { onPrepareLocalPlayback(item.mediaItem) },
            )
            LibraryActionButton(
                imageVector = Icons.Filled.PlayArrow,
                label = if (isPreparing) strings.loadingAction else item.nextUpActionLabel(strings),
                enabled = !isPreparing,
                onClick = { onPlayLocalPlayback(item.mediaItem) },
            )
        }
    }
}

@Composable
internal fun ContinueWatchingRow(
    strings: DesktopStrings,
    item: LibraryPlaybackProgressItem,
    selected: Boolean,
    isRefreshingMetadata: Boolean,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    val metadataReadiness = item.mediaItem.metadataReadiness(strings = strings, isRefreshing = isRefreshingMetadata)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DanmakuColors.SurfaceRaised.copy(alpha = 0.70f) else Color.Transparent)
            .clickable { onShowDetails(item.mediaItem) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.mediaItem.displaySeriesTitle(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.mediaItem.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.mediaItem.localSeriesLabel(strings)?.let { label ->
                Text(label, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "Resume at ${item.progress.positionMs.formatPlaybackTime()} / " +
                    (item.progress.durationMs?.formatPlaybackTime() ?: "unknown") +
                    " - ${metadataReadiness.label}",
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (compact) {
                PlayerIconButton(
                    imageVector = Icons.Filled.Subtitles,
                    contentDescription = strings.detailsAction,
                    onClick = { onShowDetails(item.mediaItem) },
                )
            } else {
                LibraryActionButton(
                    imageVector = Icons.Filled.Subtitles,
                    label = strings.detailsAction,
                    onClick = { onShowDetails(item.mediaItem) },
                )
            }
            PlayerIconButton(
                imageVector = Icons.Filled.Refresh,
                contentDescription = if (isRefreshingMetadata) {
                    strings.refreshingEpisodeMetadataAction
                } else {
                    strings.refreshEpisodeMetadataAction
                },
                enabled = !isPreparing && !isRefreshingMetadata,
                active = isRefreshingMetadata,
                onClick = { onRefreshEpisodeMetadata(item.mediaItem) },
            )
            LibraryActionButton(
                imageVector = Icons.Filled.PlayArrow,
                label = if (isPreparing) strings.loadingAction else strings.resumeAction,
                enabled = !isPreparing,
                onClick = { onPlayLocalPlayback(item.mediaItem) },
            )
        }
    }
}

@Composable
internal fun RecentlyWatchedRow(
    strings: DesktopStrings,
    item: LibraryPlaybackProgressItem,
    selected: Boolean,
    isRefreshingMetadata: Boolean,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    val metadataReadiness = item.mediaItem.metadataReadiness(strings = strings, isRefreshing = isRefreshingMetadata)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DanmakuColors.SurfaceRaised.copy(alpha = 0.70f) else Color.Transparent)
            .clickable { onShowDetails(item.mediaItem) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.mediaItem.displaySeriesTitle(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.mediaItem.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.mediaItem.localSeriesLabel(strings)?.let { label ->
                Text(label, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "Last seen ${item.progress.updatedAtEpochMs.toDiagnosticTime()} at " +
                    "${item.progress.positionMs.formatPlaybackTime()} / " +
                    (item.progress.durationMs?.formatPlaybackTime() ?: "unknown") +
                    " - ${metadataReadiness.label}",
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (compact) {
                PlayerIconButton(
                    imageVector = Icons.Filled.Subtitles,
                    contentDescription = strings.detailsAction,
                    onClick = { onShowDetails(item.mediaItem) },
                )
            } else {
                LibraryActionButton(
                    imageVector = Icons.Filled.Subtitles,
                    label = strings.detailsAction,
                    onClick = { onShowDetails(item.mediaItem) },
                )
            }
            PlayerIconButton(
                imageVector = Icons.Filled.Refresh,
                contentDescription = if (isRefreshingMetadata) {
                    strings.refreshingEpisodeMetadataAction
                } else {
                    strings.refreshEpisodeMetadataAction
                },
                enabled = !isPreparing && !isRefreshingMetadata,
                active = isRefreshingMetadata,
                onClick = { onRefreshEpisodeMetadata(item.mediaItem) },
            )
            LibraryActionButton(
                imageVector = Icons.Filled.Refresh,
                label = if (isPreparing) strings.preparingAction else if (compact) strings.prepareShortAction else strings.prepareAction,
                enabled = !isPreparing,
                onClick = { onPrepareLocalPlayback(item.mediaItem) },
            )
            LibraryActionButton(
                imageVector = Icons.Filled.PlayArrow,
                label = if (isPreparing) strings.loadingAction else strings.playAction,
                enabled = !isPreparing,
                onClick = { onPlayLocalPlayback(item.mediaItem) },
            )
        }
    }
}

@Composable
internal fun EpisodeRow(
    strings: DesktopStrings,
    item: LibraryMediaItem,
    selected: Boolean,
    watchStatus: LibraryWatchStatus?,
    isFavorite: Boolean,
    originalSeriesTitle: String?,
    isRefreshingMetadata: Boolean,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    val metadataReadiness = item.metadataReadiness(strings = strings, isRefreshing = isRefreshingMetadata)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DanmakuColors.SurfaceRaised.copy(alpha = 0.70f) else Color.Transparent)
            .clickable { onShowDetails(item) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    item.displaySeriesTitle(),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(metadataReadiness.shortLabel, color = metadataReadiness.color, maxLines = 1)
            }
            Text(item.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            (item.localSeriesLabel(strings) ?: originalSeriesTitle
                ?.takeIf { it.isNotBlank() && it != item.displaySeriesTitle() }
                ?.let(strings.fileGroupLabel))
                ?.let { label ->
                    Text(
                        label,
                        color = DanmakuColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            Text(
                listOfNotNull(
                    watchStatus.statusLabel(),
                    strings.favoriteStatusLabel.takeIf { isFavorite },
                    metadataReadiness.label,
                ).joinToString(separator = " - "),
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (compact) {
                PlayerIconButton(
                    imageVector = Icons.Filled.Subtitles,
                    contentDescription = strings.detailsAction,
                    onClick = { onShowDetails(item) },
                )
                PlayerIconButton(
                    imageVector = Icons.Filled.Star,
                    contentDescription = if (isFavorite) strings.unfavoriteAction else strings.favoriteAction,
                    onClick = { onSetFavorite(item, !isFavorite) },
                )
            } else {
                LibraryActionButton(
                    imageVector = Icons.Filled.Subtitles,
                    label = strings.detailsAction,
                    onClick = { onShowDetails(item) },
                )
                LibraryActionButton(
                    imageVector = Icons.Filled.Star,
                    label = if (isFavorite) strings.unfavoriteAction else strings.favoriteAction,
                    onClick = { onSetFavorite(item, !isFavorite) },
                )
            }
            PlayerIconButton(
                imageVector = Icons.Filled.Refresh,
                contentDescription = if (isRefreshingMetadata) {
                    strings.refreshingEpisodeMetadataAction
                } else {
                    strings.refreshEpisodeMetadataAction
                },
                enabled = !isPreparing && !isRefreshingMetadata,
                active = isRefreshingMetadata,
                onClick = { onRefreshEpisodeMetadata(item) },
            )
            LibraryActionButton(
                imageVector = Icons.Filled.Refresh,
                label = if (isPreparing) strings.preparingAction else if (compact) strings.prepareShortAction else strings.prepareAction,
                enabled = !isPreparing,
                onClick = { onPrepareLocalPlayback(item) },
            )
            LibraryActionButton(
                imageVector = Icons.Filled.PlayArrow,
                label = if (isPreparing) strings.loadingAction else strings.playAction,
                enabled = !isPreparing,
                onClick = { onPlayLocalPlayback(item) },
            )
        }
    }
}

internal fun LibraryWatchStatus?.statusLabel(): String =
    when (this?.state) {
        LibraryWatchState.WATCHED -> "Watched"
        LibraryWatchState.IN_PROGRESS -> {
            val progress = progress
            "In progress" + if (progress == null) {
                ""
            } else {
                " at ${progress.positionMs.formatPlaybackTime()} / " +
                    (progress.durationMs?.formatPlaybackTime() ?: "unknown")
            }
        }
        LibraryWatchState.NEW,
        null -> "New"
    }

@Composable
internal fun RemoteLibraryBrowser(
    strings: DesktopStrings,
    defaultServerUrl: String,
    defaultPairingToken: String,
    appendDiagnostic: (String, String) -> Unit,
    onLoadPreparedPlayback: (LanPlaybackPreparation) -> Unit,
) {
    val libraryClient = remember { JvmLanLibraryClient() }
    val libraryConnectionSession = remember(libraryClient) { LanLibraryConnectionSession(libraryClient) }
    val playbackPreparer = remember(libraryClient) { LanPlaybackPreparer(libraryClient) }
    val scope = rememberCoroutineScope()
    var serverUrl by remember(defaultServerUrl) { mutableStateOf(defaultServerUrl) }
    var pairingToken by remember(defaultPairingToken) { mutableStateOf(defaultPairingToken) }
    var catalog by remember { mutableStateOf<LibraryCatalog?>(null) }
    var playbackProgresses by remember { mutableStateOf(emptyList<PlaybackProgress>()) }
    var libraryError by remember { mutableStateOf<String?>(null) }
    var selectedPlaybackPreparation by remember {
        mutableStateOf<LanPlaybackPreparation?>(null)
    }
    var isLoading by remember { mutableStateOf(false) }
    var isPreparingPlayback by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(LibraryCatalogSort.TITLE) }
    var subtitleFilter by remember { mutableStateOf(LibrarySubtitleFilter.ANY) }
    val totalItems = catalog?.items.orEmpty()
    val filteredItems = remember(catalog, searchText, sort, subtitleFilter) {
        catalog
            ?.filteredItems(
                LibraryCatalogQuery(
                    searchText = searchText,
                    sort = sort,
                    subtitleFilter = subtitleFilter,
                ),
            )
            .orEmpty()
    }
    val watchStatusById = remember(catalog, playbackProgresses) {
        catalog?.watchStatusByMediaId(playbackProgresses).orEmpty()
    }
    val nextUpItems = remember(catalog, playbackProgresses) {
        catalog?.nextUpItems(playbackProgresses).orEmpty().take(4)
    }
    val continueWatchingItems = remember(catalog, playbackProgresses) {
        catalog?.continueWatchingItems(playbackProgresses).orEmpty().take(4)
    }

    fun refreshCatalog() {
        val requestedServerUrl = serverUrl
        val requestedPairingToken = pairingToken
        appendDiagnostic("remote-client", "Fetching catalog from $requestedServerUrl")
        scope.launch {
            isLoading = true
            selectedPlaybackPreparation = null
            runCatching {
                withContext(Dispatchers.IO) {
                    libraryConnectionSession.fetchCatalogWithProgress(requestedServerUrl, requestedPairingToken)
                }
            }.onSuccess {
                catalog = it.catalog
                playbackProgresses = it.playbackProgresses
                libraryError = null
                appendDiagnostic(
                    "remote-client",
                    "Fetched catalog: ${it.catalog.items.size} items, ${it.playbackProgresses.size} progress rows",
                )
            }.onFailure {
                libraryError = it.message
                appendDiagnostic("remote-client", "Fetch catalog failed: ${it.message}")
            }
            isLoading = false
        }
    }

    fun prepareRemotePlayback(
        item: LibraryMediaItem,
        loadAfterPrepare: Boolean,
    ) {
        val requestedServerUrl = serverUrl
        val requestedPairingToken = pairingToken
        appendDiagnostic(
            "remote-client",
            "Preparing remote playback: ${item.id} from $requestedServerUrl",
        )
        scope.launch {
            isPreparingPlayback = true
            runCatching {
                withContext(Dispatchers.IO) {
                    playbackPreparer.prepare(
                        baseUrl = requestedServerUrl,
                        pairingToken = requestedPairingToken,
                        item = item,
                    )
                }
            }.onSuccess {
                selectedPlaybackPreparation = it
                libraryError = null
                appendDiagnostic(
                    "remote-client",
                    "Prepared remote playback: ${item.id}; stream=${it.source.url}; resume=${it.resumePositionMs}; subtitles=${it.subtitles.size}",
                )
                if (loadAfterPrepare) {
                    appendDiagnostic("remote-client", "Loading prepared remote playback: ${item.id}")
                    onLoadPreparedPlayback(it)
                }
            }.onFailure {
                libraryError = it.message
                appendDiagnostic("remote-client", "Prepare remote playback failed: ${it.message}")
            }
            isPreparingPlayback = false
        }
    }

    Text(strings.pairedLibraryTitle)
    Text(strings.pairedLibraryDescription)
    OutlinedTextField(
        value = serverUrl,
        onValueChange = {
            serverUrl = it
            selectedPlaybackPreparation = null
        },
        label = { Text(strings.pairedLibraryServerUrlLabel) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = pairingToken,
        onValueChange = {
            pairingToken = it
            selectedPlaybackPreparation = null
        },
        label = { Text(strings.pairingCodeLabel) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Button(
        onClick = ::refreshCatalog,
        enabled = !isLoading,
    ) {
        Text(if (isLoading) strings.loadingAction else strings.loadPairedCatalogAction)
    }
    libraryError?.let { Text(strings.pairedLibraryErrorLabel(it)) }
    Text(strings.pairedEpisodesLabel(totalItems.size))
    MetadataRow(strings.pairedProgressLabel, strings.savedRowsLabel(playbackProgresses.size))
    if (catalog != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(strings.pairedNextUpTitle, fontWeight = FontWeight.Bold)
                if (nextUpItems.isEmpty()) {
                    EmptyState(strings.pairedNextUpEmptyText)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                        items(nextUpItems, key = { it.mediaItem.id }) { item ->
                            RemoteNextUpRow(
                                strings = strings,
                                item = item,
                                isPreparing = isPreparingPlayback,
                                onPrepareRemotePlayback = {
                                    prepareRemotePlayback(item.mediaItem, loadAfterPrepare = false)
                                },
                                onPlayRemotePlayback = {
                                    prepareRemotePlayback(item.mediaItem, loadAfterPrepare = true)
                                },
                            )
                        }
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(strings.pairedContinueWatchingTitle, fontWeight = FontWeight.Bold)
                if (continueWatchingItems.isEmpty()) {
                    EmptyState(strings.pairedContinueWatchingEmptyText)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                        items(continueWatchingItems, key = { it.mediaItem.id }) { item ->
                            RemoteContinueWatchingRow(
                                strings = strings,
                                item = item,
                                isPreparing = isPreparingPlayback,
                                onPlayRemotePlayback = {
                                    prepareRemotePlayback(item.mediaItem, loadAfterPrepare = true)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    OutlinedTextField(
        value = searchText,
        onValueChange = { searchText = it },
        label = { Text(strings.searchPairedEpisodesLabel) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { sort = LibraryCatalogSort.TITLE },
            enabled = sort != LibraryCatalogSort.TITLE,
        ) {
            Text(strings.sortTitleAction)
        }
        Button(
            onClick = { sort = LibraryCatalogSort.PATH },
            enabled = sort != LibraryCatalogSort.PATH,
        ) {
            Text(strings.sortPathAction)
        }
        Button(
            onClick = {
                subtitleFilter = if (subtitleFilter == LibrarySubtitleFilter.ANY) {
                    LibrarySubtitleFilter.WITH_SUBTITLES
                } else {
                    LibrarySubtitleFilter.ANY
                }
            },
        ) {
            Text(if (subtitleFilter == LibrarySubtitleFilter.ANY) strings.requireSubtitlesAction else strings.allEpisodesSliceLabel)
        }
    }
    MetadataRow(strings.showingLabel, strings.pairedEpisodesCountLabel(filteredItems.size, totalItems.size))
    when {
        catalog == null -> EmptyState(strings.pairedCatalogEmptyText)
        totalItems.isEmpty() -> EmptyState(strings.pairedServerEmptyText)
        filteredItems.isEmpty() -> EmptyState(
            text = strings.pairedFilterEmptyText,
            actionLabel = strings.resetFiltersAction,
            onAction = {
                searchText = ""
                sort = LibraryCatalogSort.TITLE
                subtitleFilter = LibrarySubtitleFilter.ANY
            },
        )
        else -> LazyColumn(modifier = Modifier.height(180.dp)) {
            items(filteredItems, key = { it.id }) { item ->
                RemoteEpisodeRow(
                    strings = strings,
                    item = item,
                    watchStatus = watchStatusById[item.id],
                    isPreparing = isPreparingPlayback,
                    onPrepareRemotePlayback = { prepareRemotePlayback(item, loadAfterPrepare = false) },
                    onPlayRemotePlayback = { prepareRemotePlayback(item, loadAfterPrepare = true) },
                )
            }
        }
    }
    selectedPlaybackPreparation?.let { preparation ->
        Text(strings.preparedDesktopPlaybackLabel(preparation.item.seriesTitle, preparation.item.episodeTitle))
        Text(strings.sourceValueLabel(preparation.source.url.redactToken()))
        Text(strings.resumeValueText(preparation.resumePositionMs?.let { "$it ms" } ?: strings.startFromBeginningLabel))
        Button(
            onClick = {
                onLoadPreparedPlayback(preparation)
            },
        ) {
            Text(strings.loadIntoDesktopControllerAction)
        }
    }
}

@Composable
internal fun RemoteNextUpRow(
    strings: DesktopStrings,
    item: LibraryNextUpItem,
    isPreparing: Boolean,
    onPrepareRemotePlayback: () -> Unit,
    onPlayRemotePlayback: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.mediaItem.seriesTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.mediaItem.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.nextUpLabel(strings), color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryActionButton(
                imageVector = Icons.Filled.Refresh,
                label = if (isPreparing) strings.preparingAction else strings.prepareAction,
                enabled = !isPreparing,
                onClick = onPrepareRemotePlayback,
            )
            LibraryActionButton(
                imageVector = Icons.Filled.PlayArrow,
                label = if (isPreparing) strings.loadingAction else item.nextUpActionLabel(strings),
                enabled = !isPreparing,
                onClick = onPlayRemotePlayback,
            )
        }
    }
}

@Composable
internal fun RemoteContinueWatchingRow(
    strings: DesktopStrings,
    item: LibraryPlaybackProgressItem,
    isPreparing: Boolean,
    onPlayRemotePlayback: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.mediaItem.seriesTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.mediaItem.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${strings.resumeAtLabel(item.progress.positionMs)} / " +
                    (item.progress.durationMs?.formatPlaybackTime() ?: strings.unknownDurationLabel),
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LibraryActionButton(
            imageVector = Icons.Filled.PlayArrow,
            label = if (isPreparing) strings.loadingAction else strings.resumeAction,
            enabled = !isPreparing,
            onClick = onPlayRemotePlayback,
        )
    }
}

@Composable
internal fun RemoteEpisodeRow(
    strings: DesktopStrings,
    item: LibraryMediaItem,
    watchStatus: LibraryWatchStatus?,
    isPreparing: Boolean,
    onPrepareRemotePlayback: () -> Unit,
    onPlayRemotePlayback: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.seriesTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(
                    watchStatus.statusLabel(),
                    item.mediaType.ifBlank { strings.unknownMediaLabel },
                    item.sizeBytes.formatLibrarySize(),
                    if (item.subtitles.isEmpty()) strings.noSubtitlesLabel else strings.subtitleCountLabel(item.subtitles.size),
                ).joinToString(separator = " - "),
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(item.relativePath, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryActionButton(
                imageVector = Icons.Filled.Refresh,
                label = if (isPreparing) strings.preparingAction else strings.prepareAction,
                enabled = !isPreparing,
                onClick = onPrepareRemotePlayback,
            )
            LibraryActionButton(
                imageVector = Icons.Filled.PlayArrow,
                label = if (isPreparing) strings.loadingAction else strings.playAction,
                enabled = !isPreparing,
                onClick = onPlayRemotePlayback,
            )
        }
    }
}

internal fun LibraryMediaItem.dandanplayStatusContext(
    settings: DandanplayProviderSettings,
): List<DandanplayPlaybackUiDetail> =
    listOf(
        DandanplayPlaybackUiDetail("Library episode", "$seriesTitle - $episodeTitle"),
        DandanplayPlaybackUiDetail("Media ID", id),
        DandanplayPlaybackUiDetail("Library file", relativePath),
        DandanplayPlaybackUiDetail("Provider", settings.statusText),
    )

internal fun Throwable.readableMessage(): String =
    message?.takeIf(String::isNotBlank) ?: javaClass.simpleName

internal fun String.isDandanplayWarningStatus(): Boolean {
    val normalized = lowercase()
    return listOf(
        "failed",
        "not configured",
        "not indexed",
        "no match",
        "no comments",
        "no result",
        "not checked",
        "cannot",
        "skipped",
    ).any(normalized::contains)
}

internal fun String.redactToken(): String =
    replace(Regex("([?&]token=)[^&]+"), "\$1...")

internal fun String.escapeHtml(): String =
    buildString(length) {
        this@escapeHtml.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }

internal fun DesktopDandanplayCommentCache.displayTitleForCacheManager(): String =
    listOfNotNull(
        animeTitle?.takeIf(String::isNotBlank),
        episodeTitle?.takeIf(String::isNotBlank),
    ).joinToString(" - ")
        .ifBlank { fileName }

internal fun DesktopDandanplayCommentCache.commentCountForCacheManager(): Int =
    runCatching { LocalDanmakuParser.parseNormalizedJson(commentsJson).size }
        .getOrDefault(0)

internal fun DesktopDandanplayCommentCache.isExpiredForCacheManager(cacheMaxAgeDays: Int): Boolean =
    System.currentTimeMillis() - fetchedAtEpochMs > cacheMaxAgeDays * 24L * 60L * 60L * 1_000L

internal fun DesktopLibraryRootProvenance.displayLabel(strings: DesktopStrings): String =
    when (this) {
        DesktopLibraryRootProvenance.USER_SELECTED -> strings.userSelectedFolderLabel
        DesktopLibraryRootProvenance.ANI_RSS_OUTPUT_FOLDER -> strings.aniRssOutputFolderLabel
    }

internal fun DesktopLibraryRootState.displayLabel(strings: DesktopStrings): String =
    when (this) {
        DesktopLibraryRootState.AVAILABLE -> strings.libraryRootAvailableLabel
        DesktopLibraryRootState.MISSING -> strings.libraryRootMissingLabel
    }

internal fun LibraryNextUpItem.nextUpLabel(strings: DesktopStrings): String =
    when (reason) {
        LibraryNextUpReason.RESUME -> progress?.positionMs?.let(strings.resumeAtLabel) ?: strings.resumeSavedPositionLabel
        LibraryNextUpReason.NEXT_EPISODE -> strings.nextAfterLabel(sourceProgress?.positionMs)
        LibraryNextUpReason.START -> strings.startWatchingLibraryLabel
    }

internal fun LibraryNextUpItem.nextUpActionLabel(strings: DesktopStrings): String =
    when (reason) {
        LibraryNextUpReason.RESUME -> strings.resumeAction
        LibraryNextUpReason.NEXT_EPISODE,
        LibraryNextUpReason.START -> strings.playAction
    }

internal fun AwtWindow.scaledRestoreBounds(bounds: Rectangle): Rectangle {
    val transform = graphicsConfiguration?.defaultTransform
    val scaleX = transform?.scaleX?.takeIf { it > 1.0 } ?: 1.0
    val scaleY = transform?.scaleY?.takeIf { it > 1.0 } ?: 1.0
    return Rectangle(
        bounds.x,
        bounds.y,
        (bounds.width * scaleX).roundToInt().coerceAtLeast(1),
        (bounds.height * scaleY).roundToInt().coerceAtLeast(1),
    )
}

