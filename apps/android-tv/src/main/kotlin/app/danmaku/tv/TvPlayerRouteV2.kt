package app.danmaku.tv

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.danmaku.domain.DanmakuEvent
import app.danmaku.domain.DanmakuMode
import app.danmaku.domain.DanmakuSize
import app.danmaku.domain.MeasuredDanmakuEvent
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackStatus
import app.danmaku.domain.PlaybackTrackKind
import app.danmaku.domain.ScrollingDanmakuLaneScheduler
import app.danmaku.domain.ScrollingDanmakuLayoutConfig
import app.danmaku.domain.seekTargetBy
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun TvPlayerRoute(
    playbackViewModel: TvPlaybackViewModel,
    navigation: TvNavigationState,
    navigator: TvNavigator,
) {
    val state by playbackViewModel.state.collectAsStateWithLifecycle()
    val route = navigation.route as? TvRoute.Player ?: return

    LaunchedEffect(state.controlsVisible, state.startupPhase, navigation.overlay) {
        if (
            state.controlsVisible &&
            state.startupPhase == TvPlaybackStartupPhase.Playing &&
            navigation.overlay == null
        ) {
            delay(4_500)
            playbackViewModel.hideControls()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.MediaPlayPause -> {
                        playbackViewModel.togglePlayPause()
                        true
                    }
                    Key.DirectionLeft -> {
                        playbackViewModel.dispatch(
                            PlaybackCommand.SeekTo(
                                state.snapshot.position.seekTargetBy(-10_000),
                            ),
                        )
                        true
                    }
                    Key.DirectionRight -> {
                        playbackViewModel.dispatch(
                            PlaybackCommand.SeekTo(
                                state.snapshot.position.seekTargetBy(10_000),
                            ),
                        )
                        true
                    }
                    else -> {
                        playbackViewModel.showControls()
                        false
                    }
                }
            }
            .testTag("screen-player"),
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setEnableComposeSurfaceSyncWorkaround(true)
                }
            },
            update = { it.player = playbackViewModel.androidPlayer() },
            modifier = Modifier.fillMaxSize(),
        )
        if (state.danmakuPreferences.enabled) {
            TvPreparedDanmakuOverlay(
                timeline = state.danmaku.timeline,
                snapshot = state.snapshot,
                preferences = state.danmakuPreferences,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (state.controlsVisible || state.startupPhase != TvPlaybackStartupPhase.Playing) {
            TvPlayerTitleBand(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(),
            )
            TvPlayerControls(
                route = route,
                navigation = navigation,
                navigator = navigator,
                state = state,
                onDispatch = playbackViewModel::dispatch,
                onTogglePlayPause = playbackViewModel::togglePlayPause,
                onShowOverlay = navigator::showOverlay,
                onStop = playbackViewModel::stopAndReturn,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(28.dp),
            )
        }
        when (navigation.overlay) {
            TvOverlay.AudioTracks,
            TvOverlay.SubtitleTracks,
            -> TvTrackOverlay(
                state = state,
                kind = if (navigation.overlay == TvOverlay.AudioTracks) {
                    PlaybackTrackKind.AUDIO
                } else {
                    PlaybackTrackKind.SUBTITLE
                },
                onDispatch = playbackViewModel::dispatch,
                onClose = navigator::closeOverlay,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(28.dp),
            )
            TvOverlay.DanmakuSettings ->
                TvDanmakuSettingsOverlay(
                    preferences = state.danmakuPreferences,
                    onUpdate = playbackViewModel::updateDanmakuPreferences,
                    onClose = navigator::closeOverlay,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(28.dp),
                )
            else -> Unit
        }
    }
}

@Composable
private fun TvPlayerTitleBand(
    state: TvPlaybackUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color(0xB3070B12))
            .padding(horizontal = 32.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.item?.displaySeriesTitle() ?: stringResource(R.string.nav_player),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            state.item?.episodeTitle?.let {
                Text(it, color = TvSecondaryContent)
            }
        }
        Text(
            text = when (state.danmaku.phase) {
                TvDanmakuPhase.Loading -> stringResource(R.string.danmaku_loading)
                TvDanmakuPhase.Ready -> stringResource(R.string.danmaku_ready)
                TvDanmakuPhase.NoMatch -> stringResource(R.string.danmaku_no_match)
                TvDanmakuPhase.Unavailable -> stringResource(R.string.danmaku_unavailable)
                TvDanmakuPhase.Failed -> stringResource(R.string.danmaku_failed)
                else -> ""
            },
            color = TvSecondaryContent,
        )
    }
}

@Composable
private fun TvPlayerControls(
    route: TvRoute.Player,
    navigation: TvNavigationState,
    navigator: TvNavigator,
    state: TvPlaybackUiState,
    onDispatch: (PlaybackCommand) -> Unit,
    onTogglePlayPause: () -> Unit,
    onShowOverlay: (TvOverlay) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xE6101722))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvPlaybackProgressBar(state.snapshot)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.player_position,
                    state.snapshot.position.positionMs.formatPlaybackTime(),
                    state.snapshot.position.durationMs?.formatPlaybackTime() ?: "--:--",
                ),
                color = TvSecondaryContent,
                modifier = Modifier.width(190.dp),
            )
            TvPlayerControlButton("-10s") {
                onDispatch(
                    PlaybackCommand.SeekTo(state.snapshot.position.seekTargetBy(-10_000)),
                )
            }
            TvPlayerControlButton(
                label = if (state.snapshot.status == PlaybackStatus.PLAYING) {
                    stringResource(R.string.action_pause)
                } else {
                    stringResource(R.string.action_play)
                },
                modifier = Modifier
                    .tvRouteFocus(
                        navigation,
                        navigator,
                        route,
                        "player-play-pause",
                        isDefault = true,
                    )
                    .testTag("player-play-pause"),
                selected = true,
                onClick = onTogglePlayPause,
            )
            TvPlayerControlButton("+10s") {
                onDispatch(
                    PlaybackCommand.SeekTo(state.snapshot.position.seekTargetBy(10_000)),
                )
            }
            TvPlayerControlButton(stringResource(R.string.audio_tracks_title)) {
                onShowOverlay(TvOverlay.AudioTracks)
            }
            TvPlayerControlButton(stringResource(R.string.subtitle_tracks_title)) {
                onShowOverlay(TvOverlay.SubtitleTracks)
            }
            TvPlayerControlButton(stringResource(R.string.danmaku_title)) {
                onShowOverlay(TvOverlay.DanmakuSettings)
            }
            TvPlayerControlButton(stringResource(R.string.action_stop), onClick = onStop)
        }
        state.nextItem?.let { next ->
            Text(
                text = stringResource(
                    R.string.player_next_item,
                    next.displaySeriesTitle(),
                    next.episodeTitle,
                ),
                color = TvSecondaryContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        state.error?.let { error ->
            Text(
                text = stringResource(
                    when (error) {
                        TvPlaybackError.ControllerConnecting ->
                            R.string.playback_controller_connecting
                        TvPlaybackError.ResumeLookupFailed ->
                            R.string.playback_resume_lookup_failed
                        TvPlaybackError.PreparationFailed ->
                            R.string.playback_preparation_failed
                    },
                ),
                color = TvError,
            )
        }
    }
}

@Composable
private fun TvPlayerControlButton(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.tvFocusHalo(RoundedCornerShape(16.dp)),
        colors = tvButtonColors(selected),
    ) {
        Text(label, maxLines = 1)
    }
}

@Composable
private fun TvPlaybackProgressBar(snapshot: PlaybackSnapshot) {
    val duration = snapshot.position.durationMs
    val progress = if (duration == null || duration <= 0) {
        0f
    } else {
        snapshot.position.positionMs.toFloat() / duration.toFloat()
    }.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF344155)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(6.dp)
                .background(TvAccent),
        )
    }
}

@Composable
private fun TvTrackOverlay(
    state: TvPlaybackUiState,
    kind: PlaybackTrackKind,
    onDispatch: (PlaybackCommand) -> Unit,
    onClose: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(390.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(TvSurfaceRaised)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (kind == PlaybackTrackKind.AUDIO) {
                stringResource(R.string.audio_tracks_title)
            } else {
                stringResource(R.string.subtitle_tracks_title)
            },
            style = MaterialTheme.typography.titleLarge,
        )
        if (kind == PlaybackTrackKind.SUBTITLE) {
            Button(
                onClick = { onDispatch(PlaybackCommand.SelectSubtitleTrack(null)) },
                colors = tvButtonColors(),
            ) {
                Text(stringResource(R.string.subtitle_off))
            }
        }
        state.snapshot.tracks.filter { it.kind == kind }.forEach { track ->
            Button(
                onClick = {
                    onDispatch(
                        if (kind == PlaybackTrackKind.AUDIO) {
                            PlaybackCommand.SelectAudioTrack(track.id)
                        } else {
                            PlaybackCommand.SelectSubtitleTrack(track.id)
                        },
                    )
                },
                colors = tvButtonColors(track.selected),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(track.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Button(onClick = { onClose() }, colors = tvButtonColors(selected = true)) {
            Text(stringResource(R.string.action_close))
        }
    }
}

@Composable
private fun TvDanmakuSettingsOverlay(
    preferences: TvDanmakuPreferences,
    onUpdate: ((TvDanmakuPreferences) -> TvDanmakuPreferences) -> Unit,
    onClose: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(390.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(TvSurfaceRaised)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.danmaku_settings_title), style = MaterialTheme.typography.titleLarge)
        Button(
            onClick = { onUpdate { it.copy(enabled = !it.enabled) } },
            colors = tvButtonColors(preferences.enabled),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (preferences.enabled) {
                    stringResource(R.string.danmaku_enabled)
                } else {
                    stringResource(R.string.danmaku_disabled)
                },
            )
        }
        TvPreferenceStepButton(
            label = stringResource(
                R.string.danmaku_opacity_value,
                (preferences.opacity * 100).toInt(),
            ),
            onClick = {
                onUpdate {
                    it.copy(opacity = (it.opacity + 0.1f).let { value ->
                        if (value > 1f) 0.3f else value
                    })
                }
            },
        )
        TvPreferenceStepButton(
            label = stringResource(
                R.string.danmaku_size_value,
                (preferences.fontScale * 100).toInt(),
            ),
            onClick = {
                onUpdate {
                    it.copy(fontScale = (it.fontScale + 0.1f).let { value ->
                        if (value > 1.5f) 0.8f else value
                    })
                }
            },
        )
        TvPreferenceStepButton(
            label = stringResource(
                R.string.danmaku_speed_value,
                preferences.speed,
            ),
            onClick = {
                onUpdate {
                    it.copy(speed = (it.speed + 0.25f).let { value ->
                        if (value > 2f) 0.5f else value
                    })
                }
            },
        )
        TvPreferenceStepButton(
            label = stringResource(
                R.string.danmaku_area_value,
                (preferences.maxScreenArea * 100).toInt(),
            ),
            onClick = {
                onUpdate {
                    it.copy(maxScreenArea = (it.maxScreenArea + 0.1f).let { value ->
                        if (value > 0.8f) 0.2f else value
                    })
                }
            },
        )
        Button(onClick = { onClose() }, colors = tvButtonColors(selected = true)) {
            Text(stringResource(R.string.action_close))
        }
    }
}

@Composable
private fun TvPreferenceStepButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = tvButtonColors(),
    ) {
        Text(label)
    }
}

@Composable
private fun TvPreparedDanmakuOverlay(
    timeline: PreparedDanmakuTimeline,
    snapshot: PlaybackSnapshot,
    preferences: TvDanmakuPreferences,
    modifier: Modifier = Modifier,
) {
    if (timeline.eventCount == 0) return
    val positionMs = rememberTvPlayerClock(snapshot)
    val density = LocalDensity.current
    val baseTextSizePx = with(density) { (26.sp * preferences.fontScale).toPx() }
    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val laneHeightPx = baseTextSizePx * 1.55f
        val laneCount = ((heightPx * preferences.maxScreenArea) / laneHeightPx)
            .toInt()
            .coerceAtLeast(1)
        val fillPaint = remember(baseTextSizePx, preferences.opacity) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.LEFT
                textSize = baseTextSizePx
                alpha = (preferences.opacity * 255).toInt()
            }
        }
        val strokePaint = remember(baseTextSizePx, preferences.opacity) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.LEFT
                textSize = baseTextSizePx
                style = Paint.Style.STROKE
                strokeWidth = 4f
                color = android.graphics.Color.BLACK
                alpha = (preferences.opacity * 255).toInt()
            }
        }
        val travelDuration = (8_000L / preferences.speed).toLong().coerceAtLeast(2_000L)
        val schedule = remember(
            timeline,
            widthPx,
            laneCount,
            baseTextSizePx,
            travelDuration,
        ) {
            val measured = timeline.scrollingEvents.map { event ->
                fillPaint.textSize = baseTextSizePx * event.style.size.tvScaleFactor()
                MeasuredDanmakuEvent(event, fillPaint.measureText(event.text))
            }
            ScrollingDanmakuLaneScheduler.schedule(
                measured,
                ScrollingDanmakuLayoutConfig(
                    viewportWidthPx = widthPx,
                    laneCount = laneCount,
                    travelDurationMs = travelDuration,
                    horizontalGapPx = baseTextSizePx,
                ),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val currentPosition = positionMs()
            val scrolling = schedule.visibleAt(currentPosition)
            drawIntoCanvas { canvas ->
                scrolling.take(laneCount).forEach { placement ->
                    drawDanmakuText(
                        event = placement.event,
                        x = placement.leftEdgeAt(currentPosition),
                        y = laneHeightPx * (placement.laneIndex + 1),
                        fillPaint = fillPaint,
                        strokePaint = strokePaint,
                        baseTextSizePx = baseTextSizePx,
                        canvas = canvas.nativeCanvas,
                    )
                }
                var topIndex = 0
                var bottomIndex = 0
                timeline.forEachActiveFixed(
                    positionMs = currentPosition,
                    limit = laneCount,
                ) { event ->
                    val y = if (event.style.mode == DanmakuMode.TOP) {
                        laneHeightPx * (++topIndex)
                    } else {
                        size.height - laneHeightPx * (++bottomIndex)
                    }
                    fillPaint.textSize = baseTextSizePx * event.style.size.tvScaleFactor()
                    val x = (size.width - fillPaint.measureText(event.text)) / 2f
                    drawDanmakuText(
                        event,
                        x,
                        y,
                        fillPaint,
                        strokePaint,
                        baseTextSizePx,
                        canvas.nativeCanvas,
                    )
                }
            }
        }
    }
}

private fun drawDanmakuText(
    event: DanmakuEvent,
    x: Float,
    y: Float,
    fillPaint: Paint,
    strokePaint: Paint,
    baseTextSizePx: Float,
    canvas: android.graphics.Canvas,
) {
    val textSize = baseTextSizePx * event.style.size.tvScaleFactor()
    fillPaint.textSize = textSize
    fillPaint.color = event.style.colorArgb.toInt()
    strokePaint.textSize = textSize
    canvas.drawText(event.text, x, y, strokePaint)
    canvas.drawText(event.text, x, y, fillPaint)
}

@Composable
private fun rememberTvPlayerClock(snapshot: PlaybackSnapshot): () -> Long {
    var frameTimeNanos by remember { mutableLongStateOf(0L) }
    val anchor = remember(snapshot.position.positionMs, snapshot.status, snapshot.playbackRate) {
        TvPlayerClockAnchor(
            positionMs = snapshot.position.positionMs,
            status = snapshot.status,
            playbackRate = snapshot.playbackRate,
            frameTimeNanos = frameTimeNanos,
        )
    }
    LaunchedEffect(snapshot.status, anchor) {
        if (snapshot.status != PlaybackStatus.PLAYING) return@LaunchedEffect
        while (isActive) {
            frameTimeNanos = withFrameNanos { it }
        }
    }
    return {
        if (anchor.status != PlaybackStatus.PLAYING || frameTimeNanos <= anchor.frameTimeNanos) {
            anchor.positionMs
        } else {
            anchor.positionMs +
                ((frameTimeNanos - anchor.frameTimeNanos) / 1_000_000.0 * anchor.playbackRate).toLong()
        }.coerceAtLeast(0)
    }
}

private data class TvPlayerClockAnchor(
    val positionMs: Long,
    val status: PlaybackStatus,
    val playbackRate: Float,
    val frameTimeNanos: Long,
)

private fun DanmakuSize.tvScaleFactor(): Float =
    when (this) {
        DanmakuSize.SMALL -> 0.85f
        DanmakuSize.NORMAL -> 1f
        DanmakuSize.LARGE -> 1.15f
    }
