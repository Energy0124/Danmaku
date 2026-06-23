package app.danmaku.tv

import android.graphics.Paint
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import app.danmaku.domain.DanmakuEvent
import app.danmaku.domain.DanmakuMode
import app.danmaku.domain.DanmakuSize
import app.danmaku.domain.MeasuredDanmakuEvent
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.ScrollingDanmakuLaneScheduler
import app.danmaku.domain.ScrollingDanmakuLayoutConfig
import app.danmaku.domain.seekTargetBy
import kotlinx.coroutines.delay

@Composable
internal fun TvFullscreenPlaybackScreen(
    state: TvPlayerState,
    actionHandler: TvPlayerActionHandler,
) {
    BackHandler(enabled = state.isFullscreenPlayback) {
        actionHandler.handlePlaybackBack()
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(state.playbackControlsVisible, state.playbackStartupPhase) {
        if (state.playbackControlsVisible && state.playbackStartupPhase == TvPlaybackStartupPhase.Playing) {
            delay(4_000)
            actionHandler.hidePlaybackControls()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                        actionHandler.togglePlayPause()
                        true
                    }
                    Key.DirectionLeft -> {
                        state.controller?.dispatch(
                            PlaybackCommand.SeekTo(state.snapshot.position.seekTargetBy(-10_000)),
                        )
                        actionHandler.showPlaybackControls()
                        true
                    }
                    Key.DirectionRight -> {
                        state.controller?.dispatch(
                            PlaybackCommand.SeekTo(state.snapshot.position.seekTargetBy(10_000)),
                        )
                        actionHandler.showPlaybackControls()
                        true
                    }
                    else -> {
                        actionHandler.showPlaybackControls()
                        false
                    }
                }
            },
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setEnableComposeSurfaceSyncWorkaround(true)
                }
            },
            update = { playerView ->
                playerView.player = state.controller?.androidPlayer
            },
            modifier = Modifier.fillMaxSize(),
        )
        TvDanmakuOverlay(
            events = state.danmakuState.events,
            snapshot = state.snapshot,
            modifier = Modifier.fillMaxSize(),
        )
        TvDanmakuStatus(
            state = state.danmakuState,
            startupPhase = state.playbackStartupPhase,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(32.dp),
        )
        if (
            state.playbackControlsVisible ||
            state.playbackStartupPhase == TvPlaybackStartupPhase.WaitingForDanmaku ||
            state.playbackStartupPhase == TvPlaybackStartupPhase.Stopping
        ) {
            TvFullscreenPlaybackControls(
                itemTitle = state.activePlaybackItem?.let { "${it.seriesTitle} / ${it.episodeTitle}" }
                    ?: stringResource(R.string.nav_player),
                snapshot = state.snapshot,
                startupPhase = state.playbackStartupPhase,
                onSeekTo = { state.controller?.dispatch(PlaybackCommand.SeekTo(it)) },
                onPlay = {
                    state.controller?.dispatch(PlaybackCommand.Play)
                    actionHandler.showPlaybackControls()
                },
                onPause = {
                    state.controller?.dispatch(PlaybackCommand.Pause)
                    actionHandler.showPlaybackControls()
                },
                onStop = actionHandler::stopPlaybackAndReturn,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp),
            )
        }
    }
}

@Composable
private fun TvDanmakuOverlay(
    events: List<DanmakuEvent>,
    snapshot: PlaybackSnapshot,
    modifier: Modifier = Modifier,
) {
    if (events.isEmpty()) return
    val positionMs = snapshot.position.positionMs
    val density = LocalDensity.current
    val baseTextSizePx = with(density) { 28.sp.toPx() }
    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val laneHeightPx = baseTextSizePx * 1.55f
        val laneCount = ((heightPx * 0.45f) / laneHeightPx).toInt().coerceAtLeast(1)
        val fillPaint = remember(baseTextSizePx) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.LEFT
                textSize = baseTextSizePx
                color = android.graphics.Color.WHITE
            }
        }
        val strokePaint = remember(baseTextSizePx) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.LEFT
                textSize = baseTextSizePx
                style = Paint.Style.STROKE
                strokeWidth = 4f
                color = android.graphics.Color.BLACK
            }
        }
        val schedule = remember(events, widthPx, laneCount, baseTextSizePx) {
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
                    travelDurationMs = 8_000,
                    horizontalGapPx = baseTextSizePx,
                ),
            )
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
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
private fun TvDanmakuStatus(
    state: TvDanmakuState,
    startupPhase: TvPlaybackStartupPhase,
    modifier: Modifier = Modifier,
) {
    val text = when {
        startupPhase == TvPlaybackStartupPhase.WaitingForDanmaku -> "Loading danmaku"
        startupPhase == TvPlaybackStartupPhase.Stopping -> "Saving progress"
        state.phase == TvDanmakuPhase.Ready && state.source != null -> "Danmaku ${state.source.name.lowercase()}"
        state.phase == TvDanmakuPhase.TimedOut -> "Danmaku still loading"
        state.phase == TvDanmakuPhase.NoMatch -> "No danmaku match"
        state.phase == TvDanmakuPhase.Unavailable -> "Danmaku unavailable"
        state.phase == TvDanmakuPhase.Failed -> "Danmaku failed"
        else -> null
    } ?: return
    Text(
        text = text,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x99000000))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun TvFullscreenPlaybackControls(
    itemTitle: String,
    snapshot: PlaybackSnapshot,
    startupPhase: TvPlaybackStartupPhase,
    onSeekTo: (Long) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xCC020617))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(itemTitle, color = Color.White)
        Text(
            text = if (startupPhase == TvPlaybackStartupPhase.WaitingForDanmaku) {
                "Preparing danmaku before playback"
            } else {
                stringResource(
                    R.string.player_position,
                    snapshot.position.positionMs.formatPlaybackTime(),
                    snapshot.position.durationMs?.formatPlaybackTime() ?: "--:--",
                )
            },
            color = Color(0xFFE0F2FE),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onSeekTo(snapshot.position.seekTargetBy(-30_000)) },
                enabled = snapshot.source != null,
                colors = tvButtonColors(),
            ) {
                Text("-30s")
            }
            Button(
                onClick = { onSeekTo(snapshot.position.seekTargetBy(-10_000)) },
                enabled = snapshot.source != null,
                colors = tvButtonColors(),
            ) {
                Text("-10s")
            }
            Button(
                onClick = onPlay,
                enabled = snapshot.source != null,
                colors = tvButtonColors(),
            ) {
                Text(stringResource(R.string.action_play))
            }
            Button(
                onClick = onPause,
                enabled = snapshot.source != null,
                colors = tvButtonColors(),
            ) {
                Text(stringResource(R.string.action_pause))
            }
            Button(
                onClick = { onSeekTo(snapshot.position.seekTargetBy(10_000)) },
                enabled = snapshot.source != null,
                colors = tvButtonColors(),
            ) {
                Text("+10s")
            }
            Button(
                onClick = { onSeekTo(snapshot.position.seekTargetBy(30_000)) },
                enabled = snapshot.source != null,
                colors = tvButtonColors(),
            ) {
                Text("+30s")
            }
            Button(
                onClick = onStop,
                enabled = startupPhase != TvPlaybackStartupPhase.Stopping,
                colors = tvButtonColors(),
            ) {
                Text(stringResource(R.string.action_stop))
            }
        }
    }
}

private fun DanmakuSize.scaleFactor(): Float =
    when (this) {
        DanmakuSize.SMALL -> 0.85f
        DanmakuSize.NORMAL -> 1f
        DanmakuSize.LARGE -> 1.15f
    }

private const val FIXED_DANMAKU_DURATION_MS = 4_500L
