package app.danmaku.mobile

import android.graphics.Paint

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.danmaku.domain.DanmakuEvent
import app.danmaku.domain.DanmakuMode
import app.danmaku.domain.DanmakuSize
import app.danmaku.domain.MeasuredDanmakuEvent
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackStatus
import app.danmaku.domain.PlaybackTrack
import app.danmaku.domain.PlaybackTrackKind
import app.danmaku.domain.ScrollingDanmakuLaneScheduler
import app.danmaku.domain.ScrollingDanmakuLayoutConfig
import app.danmaku.domain.coerceSeekTarget
import app.danmaku.domain.seekTargetBy
import app.danmaku.player.android.Media3PlaybackController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun WatchPage(
    contentPadding: PaddingValues,
    controller: Media3PlaybackController?,
    snapshot: PlaybackSnapshot,
    nowPlaying: LibraryMediaItem?,
    playbackError: String?,
    isFullscreen: Boolean,
    danmakuState: MobileDanmakuState = MobileDanmakuState.Idle,
    playbackStartupPhase: MobilePlaybackStartupPhase = MobilePlaybackStartupPhase.Idle,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetVolume: (Int) -> Unit,
    onSelectAudio: (String) -> Unit,
    onSelectSubtitle: (String?) -> Unit,
    onBrowseLibrary: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    BackHandler(enabled = isFullscreen, onBack = onToggleFullscreen)

    if (isFullscreen) {
        PlayerStage(
            controller = controller,
            snapshot = snapshot,
            nowPlaying = nowPlaying,
            isFullscreen = true,
            danmakuState = danmakuState,
            playbackStartupPhase = playbackStartupPhase,
            onOpen = onOpen,
            onPlayPause = onPlayPause,
            onSeekTo = onSeekTo,
            onSetVolume = onSetVolume,
            onToggleFullscreen = onToggleFullscreen,
            modifier = Modifier
                .fillMaxSize()
                .testTag("watch-player-home"),
        )
        return
    }

    PageColumn(contentPadding) {
        item(key = "player") {
            Column(
                modifier = Modifier.testTag("watch-player-home"),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PlayerStage(
                    controller = controller,
                    snapshot = snapshot,
                    nowPlaying = nowPlaying,
                    isFullscreen = false,
                    danmakuState = danmakuState,
                    playbackStartupPhase = playbackStartupPhase,
                    onOpen = onOpen,
                    onPlayPause = onPlayPause,
                    onSeekTo = onSeekTo,
                    onSetVolume = onSetVolume,
                    onToggleFullscreen = onToggleFullscreen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .aspectRatio(16f / 9f),
                )
                NowPlayingPanel(
                    snapshot = snapshot,
                    nowPlaying = nowPlaying,
                    playbackError = playbackError,
                    onSelectAudio = onSelectAudio,
                    onSelectSubtitle = onSelectSubtitle,
                )
            }
        }
        item(key = "watch-actions") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("watch-library-actions"),
                shape = RoundedCornerShape(18.dp),
                color = PanelColor,
                border = BorderStroke(1.dp, Color(0xFF2B3239)),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.watch_library_playback_title),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.watch_library_playback_body),
                            color = SubtleText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(onClick = onBrowseLibrary) {
                        Text(stringResource(R.string.action_browse))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerStage(
    controller: Media3PlaybackController?,
    snapshot: PlaybackSnapshot,
    nowPlaying: LibraryMediaItem?,
    isFullscreen: Boolean,
    danmakuState: MobileDanmakuState,
    playbackStartupPhase: MobilePlaybackStartupPhase,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetVolume: (Int) -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var controlsVisible by remember(snapshot.source, isFullscreen) { mutableStateOf(true) }
    val hasSource = snapshot.source != null
    val player = controller?.player

    LaunchedEffect(controlsVisible, snapshot.status, hasSource, isFullscreen) {
        if (controlsVisible && hasSource && snapshot.status == PlaybackStatus.PLAYING) {
            delay(if (isFullscreen) 3_500 else 5_000)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .background(PlayerBlack)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { controlsVisible = !controlsVisible },
    ) {
        key(player, isFullscreen) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setEnableComposeSurfaceSyncWorkaround(true)
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("watch-video-surface"),
                onReset = { view ->
                    view.player = null
                    view.keepScreenOn = false
                },
                onRelease = { view ->
                    view.player = null
                    view.keepScreenOn = false
                },
                update = { view ->
                    if (view.player !== player) {
                        view.player = player
                    }
                    view.keepScreenOn = snapshot.status == PlaybackStatus.PLAYING
                    view.requestLayout()
                },
            )
        }

        MobileDanmakuOverlay(
            events = danmakuState.events,
            snapshot = snapshot,
            isFullscreen = isFullscreen,
            modifier = Modifier.fillMaxSize(),
        )

        if (!hasSource) {
            EmptyPlayerOverlay(
                onOpen = onOpen,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (isFullscreen) {
                PlayerChrome(
                    snapshot = snapshot,
                    nowPlaying = nowPlaying,
                    danmakuState = danmakuState,
                    playbackStartupPhase = playbackStartupPhase,
                    onOpen = onOpen,
                    onPlayPause = onPlayPause,
                    onSeekTo = onSeekTo,
                    onSetVolume = onSetVolume,
                    onToggleFullscreen = onToggleFullscreen,
                )
            } else {
                InlinePlayerChrome(
                    snapshot = snapshot,
                    nowPlaying = nowPlaying,
                    onOpen = onOpen,
                    onPlayPause = onPlayPause,
                    onSeekTo = onSeekTo,
                    onSetVolume = onSetVolume,
                    onToggleFullscreen = onToggleFullscreen,
                )
            }
        }
    }
}

@Composable
private fun PlayerChrome(
    snapshot: PlaybackSnapshot,
    nowPlaying: LibraryMediaItem?,
    danmakuState: MobileDanmakuState,
    playbackStartupPhase: MobilePlaybackStartupPhase,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetVolume: (Int) -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        PlayerTopChrome(
            snapshot = snapshot,
            nowPlaying = nowPlaying,
            isFullscreen = true,
            danmakuState = danmakuState,
            playbackStartupPhase = playbackStartupPhase,
            onToggleFullscreen = onToggleFullscreen,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        PlayerCenterControls(
            snapshot = snapshot,
            onPlayPause = onPlayPause,
            onSeekTo = onSeekTo,
            modifier = Modifier.align(Alignment.Center),
        )
        PlayerBottomChrome(
            snapshot = snapshot,
            nowPlaying = nowPlaying,
            isFullscreen = true,
            onOpen = onOpen,
            onSeekTo = onSeekTo,
            onSetVolume = onSetVolume,
            onToggleFullscreen = onToggleFullscreen,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
@Composable
private fun InlinePlayerChrome(
    snapshot: PlaybackSnapshot,
    nowPlaying: LibraryMediaItem?,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetVolume: (Int) -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.72f),
                        1f to Color.Transparent,
                    ),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    nowPlaying?.seriesTitle ?: "Danmaku",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    snapshot.sourceLabel(nowPlaying),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OverlayPill(label = snapshot.status.displayLabel())
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.84f),
                    ),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PlaybackSeekControls(snapshot = snapshot, onSeekTo = onSeekTo)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                InlinePlayerIconButton(
                    onClick = onOpen,
                    enabled = true,
                    icon = Icons.Filled.FolderOpen,
                    contentDescription = stringResource(R.string.action_open_video),
                    modifier = Modifier.testTag("watch-open-video-toolbar"),
                )
                InlinePlayerIconButton(
                    onClick = { onSeekTo(snapshot.position.seekTargetBy(-10_000)) },
                    enabled = snapshot.source != null,
                    icon = Icons.Filled.Replay10,
                    contentDescription = "-10s",
                    modifier = Modifier.testTag("watch-seek:-10s"),
                )
                InlinePlayerIconButton(
                    onClick = onPlayPause,
                    enabled = snapshot.source != null,
                    icon = if (snapshot.status == PlaybackStatus.PLAYING) {
                        Icons.Filled.Pause
                    } else {
                        Icons.Filled.PlayArrow
                    },
                    contentDescription = if (snapshot.status == PlaybackStatus.PLAYING) {
                        stringResource(R.string.action_pause)
                    } else {
                        stringResource(R.string.action_play)
                    },
                    modifier = Modifier.testTag("watch-play-pause"),
                    isPrimary = true,
                )
                InlinePlayerIconButton(
                    onClick = { onSeekTo(snapshot.position.seekTargetBy(10_000)) },
                    enabled = snapshot.source != null,
                    icon = Icons.Filled.Forward10,
                    contentDescription = "+10s",
                    modifier = Modifier.testTag("watch-seek:+10s"),
                )
                Spacer(modifier = Modifier.weight(1f))
                InlinePlayerIconButton(
                    onClick = { onSetVolume((snapshot.volumePercent - 10).coerceAtLeast(0)) },
                    enabled = snapshot.source != null && snapshot.volumePercent > 0,
                    icon = Icons.AutoMirrored.Filled.VolumeDown,
                    contentDescription = null,
                    modifier = Modifier.testTag("watch-volume-down"),
                )
                Text(
                    "${snapshot.volumePercent}%",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.widthIn(min = 32.dp),
                )
                InlinePlayerIconButton(
                    onClick = { onSetVolume((snapshot.volumePercent + 10).coerceAtMost(100)) },
                    enabled = snapshot.source != null && snapshot.volumePercent < 100,
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.testTag("watch-volume-up"),
                )
                InlinePlayerIconButton(
                    onClick = onToggleFullscreen,
                    enabled = true,
                    icon = Icons.Filled.Fullscreen,
                    contentDescription = stringResource(R.string.action_fullscreen),
                    modifier = Modifier.testTag("watch-fullscreen-toggle"),
                )
            }
        }
    }
}

@Composable
private fun PlayerTopChrome(
    snapshot: PlaybackSnapshot,
    nowPlaying: LibraryMediaItem?,
    isFullscreen: Boolean,
    danmakuState: MobileDanmakuState,
    playbackStartupPhase: MobilePlaybackStartupPhase,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.78f),
                    1f to Color.Transparent,
                ),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isFullscreen) {
            IconButton(onClick = onToggleFullscreen) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_exit_fullscreen),
                    tint = Color.White,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                nowPlaying?.seriesTitle ?: "Danmaku",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                snapshot.sourceLabel(nowPlaying),
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        mobileDanmakuStatusLabel(danmakuState, playbackStartupPhase)?.let {
            OverlayPill(
                label = it,
                icon = Icons.Filled.ClosedCaption,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        OverlayPill(label = snapshot.status.displayLabel())
    }
}

@Composable
private fun PlayerCenterControls(
    snapshot: PlaybackSnapshot,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerRoundButton(
            onClick = { onSeekTo(snapshot.position.seekTargetBy(-10_000)) },
            enabled = snapshot.source != null,
            icon = Icons.Filled.Replay10,
            contentDescription = "-10s",
            modifier = Modifier.testTag("watch-seek:-10s"),
        )
        Button(
            onClick = onPlayPause,
            enabled = snapshot.source != null,
            modifier = Modifier.testTag("watch-play-pause"),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentBlue,
                contentColor = PlayerBlack,
            ),
        ) {
            Icon(
                imageVector = if (snapshot.status == PlaybackStatus.PLAYING) {
                    Icons.Filled.Pause
                } else {
                    Icons.Filled.PlayArrow
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (snapshot.status == PlaybackStatus.PLAYING) {
                    stringResource(R.string.action_pause)
                } else {
                    stringResource(R.string.action_play)
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
        PlayerRoundButton(
            onClick = { onSeekTo(snapshot.position.seekTargetBy(10_000)) },
            enabled = snapshot.source != null,
            icon = Icons.Filled.Forward10,
            contentDescription = "+10s",
            modifier = Modifier.testTag("watch-seek:+10s"),
        )
    }
}

@Composable
private fun PlayerBottomChrome(
    snapshot: PlaybackSnapshot,
    nowPlaying: LibraryMediaItem?,
    isFullscreen: Boolean,
    onOpen: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetVolume: (Int) -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.86f),
                ),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                nowPlaying?.episodeTitle ?: stringResource(R.string.now_playing_empty_title),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                nowPlaying?.relativePath ?: stringResource(R.string.now_playing_empty_body),
                color = Color.White.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (isFullscreen) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PlaybackSeekControls(snapshot = snapshot, onSeekTo = onSeekTo)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = onOpen,
                modifier = Modifier.testTag("watch-open-video-toolbar"),
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = stringResource(R.string.action_open_video),
                    tint = Color.White,
                )
            }
            IconButton(
                onClick = { onSetVolume((snapshot.volumePercent - 10).coerceAtLeast(0)) },
                enabled = snapshot.source != null && snapshot.volumePercent > 0,
                modifier = Modifier.testTag("watch-volume-down"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
            Text(
                stringResource(R.string.volume_percent, snapshot.volumePercent),
                color = Color.White.copy(alpha = 0.76f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.widthIn(min = 78.dp),
            )
            IconButton(
                onClick = { onSetVolume((snapshot.volumePercent + 10).coerceAtMost(100)) },
                enabled = snapshot.source != null && snapshot.volumePercent < 100,
                modifier = Modifier.testTag("watch-volume-up"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = onToggleFullscreen,
                modifier = Modifier.testTag("watch-fullscreen-toggle"),
            ) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (isFullscreen) {
                        stringResource(R.string.action_exit_fullscreen)
                    } else {
                        stringResource(R.string.action_fullscreen)
                    },
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun EmptyPlayerOverlay(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.padding(24.dp),
    ) {
        Text(
            stringResource(R.string.player_ready_title),
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.player_ready_body),
            color = Color.White.copy(alpha = 0.68f),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(
            onClick = onOpen,
            modifier = Modifier.testTag("watch-open-video"),
        ) {
            Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_open_video))
        }
    }
}

@Composable
private fun MobileDanmakuOverlay(
    events: List<DanmakuEvent>,
    snapshot: PlaybackSnapshot,
    isFullscreen: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.testTag("watch-danmaku-overlay")) {
        if (events.isEmpty()) return@BoxWithConstraints

        val playbackClock = rememberMobileDanmakuPlaybackClock(snapshot)
        val density = LocalDensity.current
        val baseTextSizePx = with(density) {
            if (isFullscreen) 22.sp.toPx() else 13.sp.toPx()
        }
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val laneHeightPx = baseTextSizePx * 1.55f
        val laneCoverage = if (isFullscreen) 0.44f else 0.36f
        val maxLaneCount = if (isFullscreen) 8 else 3
        val laneCount = ((heightPx * laneCoverage) / laneHeightPx)
            .toInt()
            .coerceAtLeast(1)
            .coerceAtMost(maxLaneCount)
        val fillPaint = remember(baseTextSizePx) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.LEFT
                textSize = baseTextSizePx
                color = android.graphics.Color.WHITE
            }
        }
        val strokePaint = remember(baseTextSizePx, isFullscreen) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.LEFT
                textSize = baseTextSizePx
                style = Paint.Style.STROKE
                strokeWidth = if (isFullscreen) 3.5f else 2.5f
                color = android.graphics.Color.BLACK
            }
        }
        val schedule = remember(events, widthPx, laneCount, baseTextSizePx, isFullscreen) {
            val measuredEvents = events
                .filter { it.style.mode == DanmakuMode.SCROLLING }
                .map { event ->
                    fillPaint.textSize = baseTextSizePx * event.style.size.scaleFactor()
                    MeasuredDanmakuEvent(
                        event = event,
                        widthPx = fillPaint.measureText(event.text),
                    )
                }
            ScrollingDanmakuLaneScheduler.schedule(
                events = measuredEvents,
                config = ScrollingDanmakuLayoutConfig(
                    viewportWidthPx = widthPx,
                    laneCount = laneCount,
                    travelDurationMs = if (isFullscreen) 8_000 else 6_500,
                    horizontalGapPx = baseTextSizePx,
                ),
            )
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val positionMs = playbackClock.positionMs()
            val visibleScrolling = schedule.visibleAt(positionMs)
            val fixedEvents = events.filter { event ->
                event.style.mode != DanmakuMode.SCROLLING &&
                    positionMs in event.timestampMs until event.timestampMs + FIXED_DANMAKU_DURATION_MS
            }
            drawIntoCanvas { canvas ->
                visibleScrolling.forEach { placement ->
                    val event = placement.event
                    val textSize = baseTextSizePx * event.style.size.scaleFactor()
                    fillPaint.textSize = textSize
                    fillPaint.color = event.style.colorArgb.toInt()
                    strokePaint.textSize = textSize
                    val x = placement.leftEdgeAt(positionMs)
                    val y = laneHeightPx * (placement.laneIndex + 1)
                    canvas.nativeCanvas.drawText(event.text, x, y, strokePaint)
                    canvas.nativeCanvas.drawText(event.text, x, y, fillPaint)
                }
                fixedEvents.forEachIndexed { index, event ->
                    val textSize = baseTextSizePx * event.style.size.scaleFactor()
                    fillPaint.textSize = textSize
                    fillPaint.color = event.style.colorArgb.toInt()
                    strokePaint.textSize = textSize
                    val measuredWidth = fillPaint.measureText(event.text)
                    val x = (size.width - measuredWidth) / 2f
                    val y = when (event.style.mode) {
                        DanmakuMode.TOP -> laneHeightPx * (index + 1)
                        DanmakuMode.BOTTOM -> size.height - laneHeightPx * (index + 1)
                        DanmakuMode.SCROLLING -> 0f
                    }
                    canvas.nativeCanvas.drawText(event.text, x, y, strokePaint)
                    canvas.nativeCanvas.drawText(event.text, x, y, fillPaint)
                }
            }
        }
    }
}

@Composable
private fun rememberMobileDanmakuPlaybackClock(snapshot: PlaybackSnapshot): MobileDanmakuPlaybackClock {
    val clock = remember { MobileDanmakuPlaybackClock(snapshot) }

    LaunchedEffect(
        clock,
        snapshot.position.positionMs,
        snapshot.position.durationMs,
        snapshot.status,
        snapshot.playbackRate,
    ) {
        val frameNanos = withFrameNanos { it }
        clock.anchorTo(snapshot, frameNanos)
    }

    LaunchedEffect(clock, snapshot.status) {
        if (snapshot.status != PlaybackStatus.PLAYING) return@LaunchedEffect

        val firstFrameNanos = withFrameNanos { it }
        clock.anchorTo(snapshot, firstFrameNanos)

        while (isActive) {
            clock.frameTimeNanos = withInfiniteAnimationFrameNanos { it }
        }
    }

    return clock
}

private class MobileDanmakuPlaybackClock(snapshot: PlaybackSnapshot) {
    var frameTimeNanos by mutableLongStateOf(0L)
    private var anchor by mutableStateOf(MobileDanmakuClockAnchor.fromSnapshot(snapshot, frameTimeNanos))

    fun anchorTo(
        snapshot: PlaybackSnapshot,
        frameTimeNanos: Long,
    ) {
        anchor = MobileDanmakuClockAnchor.fromSnapshot(snapshot, frameTimeNanos)
        this.frameTimeNanos = frameTimeNanos
    }

    fun positionMs(): Long = anchor.positionAt(frameTimeNanos)
}

private data class MobileDanmakuClockAnchor(
    val positionMs: Long,
    val durationMs: Long?,
    val status: PlaybackStatus,
    val playbackRate: Float,
    val frameTimeNanos: Long,
) {
    fun positionAt(frameTimeNanos: Long): Long {
        if (status != PlaybackStatus.PLAYING || frameTimeNanos <= this.frameTimeNanos) {
            return positionMs.coercePlaybackPosition(durationMs)
        }

        val elapsedMs = ((frameTimeNanos - this.frameTimeNanos) / 1_000_000.0 * playbackRate)
            .toLong()
        val projectedPositionMs =
            if (elapsedMs > Long.MAX_VALUE - positionMs) {
                Long.MAX_VALUE
            } else {
                positionMs + elapsedMs
            }
        return projectedPositionMs.coercePlaybackPosition(durationMs)
    }

    companion object {
        fun fromSnapshot(
            snapshot: PlaybackSnapshot,
            frameTimeNanos: Long,
        ): MobileDanmakuClockAnchor =
            MobileDanmakuClockAnchor(
                positionMs = snapshot.position.positionMs,
                durationMs = snapshot.position.durationMs,
                status = snapshot.status,
                playbackRate = snapshot.playbackRate,
                frameTimeNanos = frameTimeNanos,
            )
    }
}

private fun Long.coercePlaybackPosition(durationMs: Long?): Long {
    val nonNegativePositionMs = coerceAtLeast(0)
    return durationMs
        ?.let { nonNegativePositionMs.coerceAtMost(it) }
        ?: nonNegativePositionMs
}

private fun DanmakuSize.scaleFactor(): Float =
    when (this) {
        DanmakuSize.SMALL -> 0.82f
        DanmakuSize.NORMAL -> 1f
        DanmakuSize.LARGE -> 1.18f
    }

private fun mobileDanmakuStatusLabel(
    state: MobileDanmakuState,
    startupPhase: MobilePlaybackStartupPhase,
): String? =
    when {
        startupPhase == MobilePlaybackStartupPhase.WaitingForDanmaku -> "Loading danmaku"
        state.phase == MobileDanmakuPhase.Ready && state.source != null -> "Danmaku ${state.source.name.lowercase()}"
        state.phase == MobileDanmakuPhase.TimedOut -> "Danmaku loading"
        state.phase == MobileDanmakuPhase.NoMatch -> "No danmaku"
        state.phase == MobileDanmakuPhase.Unavailable -> "Danmaku unavailable"
        state.phase == MobileDanmakuPhase.Failed -> "Danmaku failed"
        else -> null
    }

private const val FIXED_DANMAKU_DURATION_MS = 4_500L

@Composable
private fun NowPlayingPanel(
    snapshot: PlaybackSnapshot,
    nowPlaying: LibraryMediaItem?,
    playbackError: String?,
    onSelectAudio: (String) -> Unit,
    onSelectSubtitle: (String?) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("now-playing-panel"),
        shape = RoundedCornerShape(18.dp),
        color = PanelColor,
        border = BorderStroke(1.dp, Color(0xFF2B3239)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        nowPlaying?.seriesTitle ?: stringResource(R.string.now_playing_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        nowPlaying?.episodeTitle ?: stringResource(R.string.now_playing_empty_body),
                        color = SubtleText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusPill(snapshot.status.displayLabel())
            }
            TrackControls(
                snapshot = snapshot,
                onSelectAudio = onSelectAudio,
                onSelectSubtitle = onSelectSubtitle,
            )
            playbackError?.let {
                ErrorText(stringResource(R.string.playback_error_prefix, it))
            }
        }
    }
}

@Composable
private fun PlaybackSeekControls(
    snapshot: PlaybackSnapshot,
    onSeekTo: (Long) -> Unit,
) {
    val durationMs = snapshot.position.durationMs?.takeIf { it > 0 }
    val currentPositionMs = snapshot.position.coerceSeekTarget(snapshot.position.positionMs)
    var sliderPositionMs by remember(snapshot.source, durationMs) {
        mutableStateOf(currentPositionMs.toFloat())
    }
    var isDragging by remember(snapshot.source, durationMs) { mutableStateOf(false) }

    LaunchedEffect(currentPositionMs, durationMs, isDragging) {
        if (!isDragging) {
            sliderPositionMs = currentPositionMs.toFloat()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Slider(
            value = durationMs
                ?.let { sliderPositionMs.coerceIn(0f, it.toFloat()) }
                ?: 0f,
            onValueChange = {
                isDragging = true
                sliderPositionMs = it
            },
            onValueChangeFinished = {
                durationMs?.let {
                    onSeekTo(snapshot.position.coerceSeekTarget(sliderPositionMs.toLong()))
                }
                isDragging = false
            },
            valueRange = 0f..(durationMs ?: 1L).toFloat(),
            enabled = snapshot.source != null && durationMs != null,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                currentPositionMs.formatPlaybackTime(),
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                durationMs?.formatPlaybackTime() ?: "--:--",
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TrackControls(
    snapshot: PlaybackSnapshot,
    onSelectAudio: (String) -> Unit,
    onSelectSubtitle: (String?) -> Unit,
) {
    val audioTracks = snapshot.tracks.filter { it.kind == PlaybackTrackKind.AUDIO }
    val subtitleTracks = snapshot.tracks.filter { it.kind == PlaybackTrackKind.SUBTITLE }
    if (audioTracks.isNotEmpty() || subtitleTracks.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (audioTracks.isNotEmpty()) {
                Text(
                    stringResource(R.string.audio_tracks_title),
                    color = SubtleText,
                    style = MaterialTheme.typography.labelMedium,
                )
                TrackButtons(audioTracks, onSelectAudio)
            }
            if (subtitleTracks.isNotEmpty()) {
                Text(
                    stringResource(R.string.subtitle_tracks_title),
                    color = SubtleText,
                    style = MaterialTheme.typography.labelMedium,
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "subtitle-off") {
                        FilterChip(
                            selected = subtitleTracks.none(PlaybackTrack::selected),
                            onClick = { onSelectSubtitle(null) },
                            enabled = true,
                            modifier = Modifier.testTag("subtitle-off"),
                            label = { Text(stringResource(R.string.subtitle_off)) },
                        )
                    }
                    items(subtitleTracks, key = PlaybackTrack::id) { track ->
                        FilterChip(
                            selected = track.selected,
                            onClick = { onSelectSubtitle(track.id) },
                            enabled = track.supported && !track.selected,
                            modifier = Modifier.testTag("subtitle-track:${track.id}"),
                            label = { Text(track.buttonLabel()) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackButtons(
    tracks: List<PlaybackTrack>,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tracks, key = PlaybackTrack::id) { track ->
            FilterChip(
                selected = track.selected,
                onClick = { onSelect(track.id) },
                enabled = track.supported && !track.selected,
                modifier = Modifier.testTag("track:${track.id}"),
                label = { Text(track.buttonLabel()) },
            )
        }
    }
}

@Composable
private fun InlinePlayerIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
) {
    val containerColor = when {
        isPrimary && enabled -> AccentBlue
        enabled -> Color.Black.copy(alpha = 0.5f)
        else -> Color.Black.copy(alpha = 0.22f)
    }
    val contentColor = when {
        isPrimary && enabled -> PlayerBlack
        enabled -> Color.White
        else -> Color.White.copy(alpha = 0.38f)
    }
    Surface(
        modifier = Modifier.size(if (isPrimary) 42.dp else 36.dp),
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (enabled) 0.14f else 0.06f)),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(if (isPrimary) 24.dp else 20.dp),
            )
        }
    }
}

@Composable
private fun PlayerRoundButton(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = if (enabled) 0.56f else 0.24f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun OverlayPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                label,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
