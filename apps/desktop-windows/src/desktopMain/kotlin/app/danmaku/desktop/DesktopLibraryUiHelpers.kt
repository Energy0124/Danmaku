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
    localAnimeListEntry: LocalAnimeListEntry?,
    isSelected: Boolean,
    isPreparing: Boolean,
    isRefreshingMetadata: Boolean,
    onSelect: () -> Unit,
    onSaveLocalAnimeListEntry: (LocalAnimeListStatus, Int?, String?) -> Unit,
    onDeleteLocalAnimeListEntry: () -> Unit,
    onRefreshMetadata: () -> Unit,
    onPlay: () -> Unit,
) {
    var watchListMenuExpanded by remember(series.id) { mutableStateOf(false) }
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
            localAnimeListEntry?.let { entry ->
                Text(
                    text = entry.status.localizedLabel(strings),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(DanmakuColors.Accent.copy(alpha = 0.90f), RoundedCornerShape(bottomStart = 4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = series.title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(
                localAnimeListEntry?.status?.localizedLabel(strings),
                watchSummary.progressLabel(strings),
                metadataReadiness.label,
            ).joinToString(separator = " - "),
            color = DanmakuColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.weight(0.28f)) {
                Button(
                    onClick = { watchListMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = strings.localWatchListTitle, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(
                    expanded = watchListMenuExpanded,
                    onDismissRequest = { watchListMenuExpanded = false },
                ) {
                    LocalAnimeListStatus.entries.forEach { status ->
                        DropdownMenuItem(
                            onClick = {
                                onSaveLocalAnimeListEntry(
                                    status,
                                    localAnimeListEntry?.score,
                                    localAnimeListEntry?.notes,
                                )
                                watchListMenuExpanded = false
                            },
                        ) {
                            Text(
                                status.localizedLabel(strings),
                                fontWeight = if (status == localAnimeListEntry?.status) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                    if (localAnimeListEntry != null) {
                        Divider(color = DanmakuColors.SurfaceRaised)
                        DropdownMenuItem(
                            onClick = {
                                onDeleteLocalAnimeListEntry()
                                watchListMenuExpanded = false
                            },
                        ) {
                            Text(strings.clearLocalWatchListAction)
                        }
                    }
                }
            }
            Button(
                onClick = onRefreshMetadata,
                enabled = !isPreparing && !isRefreshingMetadata,
                modifier = Modifier.weight(0.28f),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = strings.refreshSeriesMetadataAction, modifier = Modifier.size(16.dp))
            }
            LibraryActionButton(
                imageVector = Icons.Filled.PlayArrow,
                label = if (isPreparing) strings.loadingAction else strings.playAction,
                modifier = Modifier.weight(0.44f),
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

