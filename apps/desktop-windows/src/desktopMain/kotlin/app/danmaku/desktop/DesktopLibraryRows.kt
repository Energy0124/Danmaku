package app.danmaku.desktop

import app.danmaku.provider.dandanplay.DandanplayMatch
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
internal fun DesktopSeriesRow(
    strings: DesktopStrings,
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
            watchSummary.progressLabel(strings),
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
    strings: DesktopStrings,
    preparation: DesktopLocalPlaybackPreparation,
    status: DandanplayPlaybackUiStatus,
    isPreparing: Boolean,
    onSelectDandanplayMatch: (DesktopLocalPlaybackPreparation, DandanplayMatch) -> Unit,
) {
    val candidates = status.matchCandidates.distinctBy(DandanplayMatch::episodeId)
    if (candidates.size <= 1) return

    Spacer(modifier = Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DanmakuColors.AccentSoft.copy(alpha = 0.58f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Subtitles,
                contentDescription = strings.dandanplayMatchCandidatesTitle,
                tint = DanmakuColors.Accent,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    strings.dandanplayMatchCandidatesTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    strings.dandanplayMatchCandidatesDescription(candidates.size),
                    color = DanmakuColors.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusPill(candidates.size.toString(), active = true)
        }
        LazyColumn(
            modifier = Modifier.heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            items(candidates, key = { it.episodeId }) { match ->
                val isSelected = match.episodeId == status.selectedEpisodeId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (isSelected) DanmakuColors.SurfaceRaised else DanmakuColors.Surface.copy(alpha = 0.72f),
                        )
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            match.displayTitle,
                            color = Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            dandanplayMatchCandidateDetail(match, strings),
                            color = DanmakuColors.TextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isSelected) {
                        StatusPill(
                            strings.dandanplaySelectedMatchLabel,
                            icon = Icons.Filled.CheckCircle,
                            active = true,
                            color = DanmakuColors.Good,
                        )
                    } else {
                        Button(
                            onClick = { onSelectDandanplayMatch(preparation, match) },
                            enabled = !isPreparing,
                        ) {
                            Text(strings.dandanplayUseMatchAction)
                        }
                    }
                }
            }
        }
    }
}

internal fun dandanplayMatchCandidateDetail(
    match: DandanplayMatch,
    strings: DesktopStrings,
): String = buildList {
    add(strings.dandanplayEpisodeIdLabel(match.episodeId))
    match.animeId?.let { add(strings.dandanplayAnimeIdLabel(it)) }
    match.shiftSeconds?.let { add(strings.dandanplayShiftLabel(it.toString())) }
}.joinToString("  •  ")

internal fun LibrarySeriesWatchSummary?.progressLabel(strings: DesktopStrings): String =
    if (this == null) {
        strings.watchSummaryProgressLabel(0, 0, 0)
    } else {
        strings.watchSummaryProgressLabel(watchedCount, inProgressCount, newCount)
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
