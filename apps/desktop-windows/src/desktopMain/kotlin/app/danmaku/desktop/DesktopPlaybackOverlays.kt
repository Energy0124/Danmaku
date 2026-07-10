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
internal fun PlayerEmptyOverlay(
    strings: DesktopStrings,
    canOpenMedia: Boolean,
    mpvRuntimeStatus: String,
    videoHostStatus: String,
    onOpenMediaFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(strings.noMediaLoadedLabel, color = Color.White, style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "$mpvRuntimeStatus ${strings.videoHostLabel}: $videoHostStatus",
            color = DanmakuColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOpenMediaFile, enabled = canOpenMedia) {
            Text(strings.openMediaFileAction)
        }
    }
}

@Composable
internal fun PlaybackWindowNavigationHeader(
    strings: DesktopStrings,
    title: String,
    status: String,
    overlayStatus: String,
    isFocusMode: Boolean,
    onShowHome: () -> Unit,
    onShowLibrary: () -> Unit,
    onToggleFocusMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color.Black)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlayerIconButton(Icons.Filled.Home, strings.homeAction, onClick = onShowHome)
        PlayerIconButton(Icons.AutoMirrored.Filled.LibraryBooks, strings.libraryAction, onClick = onShowLibrary)
        PlayerIconButton(
            imageVector = if (isFocusMode) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            contentDescription = if (isFocusMode) strings.showPlayerChromeAction else strings.hidePlayerChromeAction,
            active = isFocusMode,
            onClick = onToggleFocusMode,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = overlayStatus,
                color = DanmakuColors.TextMuted,
                style = MaterialTheme.typography.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = status,
            color = DanmakuColors.TextMuted,
            style = MaterialTheme.typography.caption,
            maxLines = 1,
        )
    }
}

@Composable
internal fun PlayerTopOverlay(
    strings: DesktopStrings,
    title: String,
    status: String,
    overlayStatus: String,
    isFullscreen: Boolean,
    isFocusMode: Boolean,
    onShowHome: () -> Unit,
    onShowLibrary: () -> Unit,
    onToggleFocusMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PlayerIconButton(Icons.Filled.Home, strings.homeAction, onClick = onShowHome)
        PlayerIconButton(Icons.AutoMirrored.Filled.LibraryBooks, strings.libraryAction, onClick = onShowLibrary)
        PlayerIconButton(
            imageVector = if (isFocusMode) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            contentDescription = if (isFocusMode) strings.showPlayerChromeAction else strings.hidePlayerChromeAction,
            active = isFocusMode,
            onClick = onToggleFocusMode,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isFullscreen) {
                Text(
                    text = overlayStatus,
                    color = DanmakuColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(status, color = DanmakuColors.TextMuted, maxLines = 1)
    }
}

@Composable
internal fun PlayerBottomOverlay(
    strings: DesktopStrings,
    playbackSnapshot: PlaybackSnapshot,
    isFullscreen: Boolean,
    videoAspectMode: DesktopVideoAspectMode,
    previousEpisodeLabel: String?,
    nextEpisodeLabel: String?,
    onPlayPreviousEpisode: (() -> Unit)?,
    onPlayNextEpisode: (() -> Unit)?,
    onOpenMediaFile: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekBackwardLarge: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekForwardLarge: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetPlaybackRate: (Float) -> Unit,
    onSetVolume: (Int) -> Unit,
    onSelectAudioTrack: (String) -> Unit,
    onSelectSubtitleTrack: (String?) -> Unit,
    onSetFullscreen: (Boolean) -> Unit,
    onSetVideoAspectMode: (DesktopVideoAspectMode) -> Unit,
    isFocusMode: Boolean,
    rightPanelVisible: Boolean,
    onToggleFocusMode: () -> Unit,
    onToggleRightPanel: () -> Unit,
    canOpenMedia: Boolean,
    modifier: Modifier = Modifier,
) {
    val hasMedia = playbackSnapshot.source != null
    val durationMs = playbackSnapshot.position.durationMs?.takeIf { it > 0 }
    val currentPositionMs = durationMs
        ?.let { playbackSnapshot.position.positionMs.coerceIn(0, it) }
        ?: playbackSnapshot.position.positionMs
    var sliderPositionMs by remember(playbackSnapshot.source, durationMs) {
        mutableStateOf(currentPositionMs.toFloat())
    }
    var isDragging by remember(playbackSnapshot.source) { mutableStateOf(false) }

    LaunchedEffect(currentPositionMs, durationMs, isDragging) {
        if (!isDragging) {
            sliderPositionMs = currentPositionMs.toFloat()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = currentPositionMs.formatPlaybackTime(),
                color = Color.White,
                modifier = Modifier.width(64.dp),
                maxLines = 1,
            )
            Slider(
                value = durationMs
                    ?.let { sliderPositionMs.coerceIn(0f, it.toFloat()) }
                    ?: 0f,
                onValueChange = {
                    isDragging = true
                    sliderPositionMs = it
                },
                onValueChangeFinished = {
                    isDragging = false
                    durationMs?.let {
                        onSeekTo(sliderPositionMs.toLong().coerceIn(0, it))
                    }
                },
                valueRange = 0f..(durationMs ?: 1L).toFloat(),
                enabled = durationMs != null,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = durationMs?.formatPlaybackTime() ?: "--:--",
                color = Color.White,
                modifier = Modifier.width(64.dp),
                maxLines = 1,
            )
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isCompact = maxWidth < 860.dp
            val isNarrow = maxWidth < 700.dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayerIconButton(Icons.Filled.FolderOpen, strings.openMediaFileAction, enabled = canOpenMedia, onClick = onOpenMediaFile)
                PlayerIconButton(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = previousEpisodeLabel?.let(strings.previousEpisodeWithTitle) ?: strings.previousEpisodeAction,
                    enabled = hasMedia && onPlayPreviousEpisode != null,
                    onClick = { onPlayPreviousEpisode?.invoke() },
                )
                if (!isNarrow) {
                    PlayerIconButton(Icons.Filled.FastRewind, strings.backThirtySecondsAction, enabled = hasMedia, onClick = onSeekBackwardLarge)
                }
                PlayerIconButton(Icons.Filled.Replay10, strings.backTenSecondsAction, enabled = hasMedia, onClick = onSeekBackward)
                PlayerIconButton(
                    imageVector = if (playbackSnapshot.status == PlaybackStatus.PLAYING) {
                        Icons.Filled.Pause
                    } else {
                        Icons.Filled.PlayArrow
                    },
                    contentDescription = if (playbackSnapshot.status == PlaybackStatus.PLAYING) strings.pauseAction else strings.playAction,
                    enabled = hasMedia,
                    onClick = if (playbackSnapshot.status == PlaybackStatus.PLAYING) onPause else onPlay,
                )
                PlayerIconButton(Icons.Filled.Forward10, strings.forwardTenSecondsAction, enabled = hasMedia, onClick = onSeekForward)
                if (!isNarrow) {
                    PlayerIconButton(Icons.Filled.FastForward, strings.forwardThirtySecondsAction, enabled = hasMedia, onClick = onSeekForwardLarge)
                }
                PlayerIconButton(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = nextEpisodeLabel?.let(strings.nextEpisodeWithTitle) ?: strings.nextEpisodeAction,
                    enabled = hasMedia && onPlayNextEpisode != null,
                    onClick = { onPlayNextEpisode?.invoke() },
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = strings.volumeLabel,
                    tint = DanmakuColors.TextMuted,
                )
                Text(
                    text = "${playbackSnapshot.volumePercent}%",
                    color = DanmakuColors.TextMuted,
                    modifier = Modifier.width(44.dp),
                    maxLines = 1,
                )
                Slider(
                    value = playbackSnapshot.volumePercent.toFloat(),
                    onValueChange = { onSetVolume(it.toInt().coerceIn(0, 100)) },
                    valueRange = 0f..100f,
                    enabled = hasMedia,
                    modifier = Modifier.width(if (isCompact) 72.dp else 120.dp),
                )
                PlayerOverlayButton(
                    text = "${playbackSnapshot.playbackRate}x",
                    enabled = hasMedia,
                    onClick = { onSetPlaybackRate(playbackSnapshot.playbackRate.nextPlaybackRate()) },
                )
                if (!isCompact) {
                    PlayerOverlayButton(
                        text = playbackSnapshot.selectedTrackButtonText(PlaybackTrackKind.AUDIO, strings.audioLabel),
                        enabled = hasMedia && playbackSnapshot.tracks.any { it.kind == PlaybackTrackKind.AUDIO },
                        modifier = Modifier.width(108.dp),
                        onClick = {
                            playbackSnapshot.nextTrackId(PlaybackTrackKind.AUDIO)?.let(onSelectAudioTrack)
                        },
                    )
                    PlayerOverlayButton(
                        text = playbackSnapshot.selectedTrackButtonText(PlaybackTrackKind.SUBTITLE, strings.subtitleShortLabel),
                        enabled = hasMedia && playbackSnapshot.tracks.any { it.kind == PlaybackTrackKind.SUBTITLE },
                        modifier = Modifier.width(108.dp),
                        onClick = {
                            onSelectSubtitleTrack(playbackSnapshot.nextSubtitleTrackId())
                        },
                    )
                    PlayerOverlayButton(
                        text = videoAspectMode.label,
                        enabled = hasMedia,
                        onClick = { onSetVideoAspectMode(videoAspectMode.nextAspectMode()) },
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                PlayerIconButton(
                    imageVector = Icons.Filled.Subtitles,
                    contentDescription = if (rightPanelVisible) {
                        strings.hideDanmakuPanelAction
                    } else {
                        strings.showDanmakuPanelAction
                    },
                    enabled = hasMedia,
                    active = rightPanelVisible,
                    onClick = onToggleRightPanel,
                )
                PlayerIconButton(
                    imageVector = if (isFocusMode) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (isFocusMode) strings.showPlayerChromeAction else strings.hidePlayerChromeAction,
                    enabled = true,
                    active = isFocusMode,
                    onClick = onToggleFocusMode,
                )
                PlayerIconButton(
                    imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (isFullscreen) strings.exitFullscreenAction else strings.enterFullscreenAction,
                    enabled = hasMedia,
                    onClick = { onSetFullscreen(!isFullscreen) },
                )
            }
        }
    }
}

@Composable
internal fun PlayerPreparationStatusOverlay(
    strings: DesktopStrings,
    isPreparingLocalPlayback: Boolean,
    playbackError: String?,
    libraryError: String?,
    dandanplayCacheStatus: DandanplayPlaybackUiStatus?,
    modifier: Modifier = Modifier,
) {
    val hasError = playbackError != null || libraryError != null
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = if (hasError) Icons.Filled.Warning else Icons.Filled.Refresh,
                contentDescription = null,
                tint = if (hasError) DanmakuColors.Warning else DanmakuColors.Info,
            )
            Text(
                text = if (hasError) strings.playbackNeedsAttentionTitle else strings.preparingPlaybackTitle,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isPreparingLocalPlayback) {
            PlayerPreparationStepRow(
                label = strings.libraryStepLabel,
                value = strings.preparingMediaStepText,
                color = DanmakuColors.Info,
                icon = Icons.Filled.Refresh,
            )
        }
        playbackError?.let { error ->
            PlayerPreparationStepRow(
                label = strings.playerRuntimeStepLabel,
                value = error,
                color = DanmakuColors.Warning,
                icon = Icons.Filled.Warning,
            )
        }
        libraryError?.let { error ->
            PlayerPreparationStepRow(
                label = strings.libraryPreparationStepLabel,
                value = error,
                color = DanmakuColors.Warning,
                icon = Icons.Filled.Warning,
            )
        }
        dandanplayCacheStatus?.let { status ->
            PlayerPreparationStepRow(
                label = strings.danmakuTitle,
                value = status.summary,
                color = if (status.summary.isDandanplayWarningStatus()) DanmakuColors.Warning else DanmakuColors.Good,
                icon = if (status.summary.isDandanplayWarningStatus()) Icons.Filled.Warning else Icons.Filled.CheckCircle,
            )
        }
    }
}

@Composable
internal fun PlayerPreparationStepRow(
    label: String,
    value: String,
    color: Color,
    icon: ImageVector,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(label, color = color, maxLines = 1, modifier = Modifier.width(118.dp))
        Text(
            value,
            color = DanmakuColors.TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}


