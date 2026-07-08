package app.danmaku.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import app.danmaku.domain.DanmakuEvent
import app.danmaku.domain.DanmakuMode
import app.danmaku.domain.DanmakuSize
import app.danmaku.domain.MeasuredDanmakuEvent
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackStatus
import app.danmaku.domain.ScrollingDanmakuLaneScheduler
import app.danmaku.domain.ScrollingDanmakuLayoutConfig
import app.danmaku.domain.ScrollingDanmakuPlacement

internal class MobileDanmakuOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        color = android.graphics.Color.WHITE
        isSubpixelText = true
        isLinearText = true
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        style = Paint.Style.STROKE
        color = android.graphics.Color.BLACK
        isSubpixelText = true
        isLinearText = true
    }

    private var events: List<DanmakuEvent> = emptyList()
    private var fixedEvents: List<DanmakuEvent> = emptyList()
    private var placements: List<ScrollingDanmakuPlacement> = emptyList()
    private var snapshot: PlaybackSnapshot = PlaybackSnapshot()
    private var isFullscreen: Boolean = false
    private var baseTextSizePx: Float = 0f
    private var laneHeightPx: Float = 0f
    private var maxTravelDurationMs: Long = 0L
    private var clockAnchor = MobileOverlayClockAnchor.fromSnapshot(snapshot, System.nanoTime())

    init {
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun update(
        events: List<DanmakuEvent>,
        snapshot: PlaybackSnapshot,
        isFullscreen: Boolean,
    ) {
        val now = System.nanoTime()
        val statusChanged = this.snapshot.status != snapshot.status
        val durationChanged = this.snapshot.position.durationMs != snapshot.position.durationMs
        val rateChanged = this.snapshot.playbackRate != snapshot.playbackRate
        val projectedPositionMs = clockAnchor.positionAt(now)
        val positionDriftMs = absoluteDifference(
            snapshot.position.positionMs.toDouble(),
            projectedPositionMs,
        )
        val shouldAnchor = statusChanged ||
            durationChanged ||
            rateChanged ||
            snapshot.status != PlaybackStatus.PLAYING ||
            positionDriftMs > POSITION_REANCHOR_THRESHOLD_MS
        val layoutChanged = this.events !== events || this.isFullscreen != isFullscreen

        this.snapshot = snapshot
        if (shouldAnchor) {
            clockAnchor = MobileOverlayClockAnchor.fromSnapshot(snapshot, now)
        }
        if (layoutChanged) {
            this.events = events
            this.isFullscreen = isFullscreen
            rebuildSchedule()
        }
        if (snapshot.status == PlaybackStatus.PLAYING) {
            postInvalidateOnAnimation()
        } else {
            invalidate()
        }
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int,
    ) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width != oldWidth || height != oldHeight) {
            rebuildSchedule()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (events.isEmpty() || width <= 0 || height <= 0 || baseTextSizePx <= 0f) {
            return
        }

        val positionMs = clockAnchor.positionAt(System.nanoTime())
        drawScrolling(canvas, positionMs)
        drawFixed(canvas, positionMs)

        if (snapshot.status == PlaybackStatus.PLAYING && isAttachedToWindow) {
            postInvalidateOnAnimation()
        }
    }

    private fun rebuildSchedule() {
        if (width <= 0 || height <= 0 || events.isEmpty()) {
            fixedEvents = emptyList()
            placements = emptyList()
            maxTravelDurationMs = 0L
            return
        }

        baseTextSizePx = if (isFullscreen) {
            22f.spToPx()
        } else {
            13f.spToPx()
        }
        laneHeightPx = baseTextSizePx * 1.55f
        strokePaint.strokeWidth = if (isFullscreen) 3.5f else 2.5f
        val laneCoverage = if (isFullscreen) 0.44f else 0.36f
        val maxLaneCount = if (isFullscreen) 8 else 3
        val laneCount = ((height * laneCoverage) / laneHeightPx)
            .toInt()
            .coerceAtLeast(1)
            .coerceAtMost(maxLaneCount)
        val travelDurationMs = if (isFullscreen) 8_000L else 6_500L

        val measuredEvents = events
            .filter { it.style.mode == DanmakuMode.SCROLLING }
            .map { event ->
                fillPaint.textSize = baseTextSizePx * event.style.size.scaleFactor()
                MeasuredDanmakuEvent(
                    event = event,
                    widthPx = fillPaint.measureText(event.text),
                )
            }
        fixedEvents = events
            .filter { it.style.mode != DanmakuMode.SCROLLING }
            .sortedBy { it.timestampMs }
        placements = ScrollingDanmakuLaneScheduler.schedule(
            events = measuredEvents,
            config = ScrollingDanmakuLayoutConfig(
                viewportWidthPx = width.toFloat(),
                laneCount = laneCount,
                travelDurationMs = travelDurationMs,
                horizontalGapPx = baseTextSizePx,
            ),
        ).placements
        maxTravelDurationMs = placements.maxOfOrNull { it.travelDurationMs } ?: 0L
        invalidate()
    }

    private fun drawScrolling(
        canvas: Canvas,
        positionMs: Double,
    ) {
        if (placements.isEmpty()) return
        val positionMsForBounds = positionMs.toLong().coerceAtLeast(0L)
        val earliestStartsAtMs = if (positionMsForBounds >= maxTravelDurationMs) {
            positionMsForBounds - maxTravelDurationMs + 1
        } else {
            0L
        }
        val firstCandidateIndex = placements.lowerBoundPlacementStart(earliestStartsAtMs)
        val afterLastCandidateIndex = placements.upperBoundPlacementStart(positionMsForBounds)
        for (index in firstCandidateIndex until afterLastCandidateIndex) {
            val placement = placements[index]
            if (!placement.isVisibleAt(positionMs)) continue
            val event = placement.event
            val textSize = baseTextSizePx * event.style.size.scaleFactor()
            fillPaint.textSize = textSize
            fillPaint.color = event.style.colorArgb.toInt()
            strokePaint.textSize = textSize
            val x = placement.leftEdgeAt(positionMs)
            val y = laneHeightPx * (placement.laneIndex + 1)
            canvas.drawText(event.text, x, y, strokePaint)
            canvas.drawText(event.text, x, y, fillPaint)
        }
    }

    private fun drawFixed(
        canvas: Canvas,
        positionMs: Double,
    ) {
        if (fixedEvents.isEmpty()) return
        val positionMsForBounds = positionMs.toLong().coerceAtLeast(0L)
        val earliestTimestampMs = if (positionMsForBounds >= FIXED_DANMAKU_DURATION_MS) {
            positionMsForBounds - FIXED_DANMAKU_DURATION_MS + 1
        } else {
            0L
        }
        val firstCandidateIndex = fixedEvents.lowerBoundEventTimestamp(earliestTimestampMs)
        val afterLastCandidateIndex = fixedEvents.upperBoundEventTimestamp(positionMsForBounds)
        var visibleIndex = 0
        for (index in firstCandidateIndex until afterLastCandidateIndex) {
            val event = fixedEvents[index]
            if (positionMs < event.timestampMs || positionMs >= event.timestampMs + FIXED_DANMAKU_DURATION_MS) {
                continue
            }
            val textSize = baseTextSizePx * event.style.size.scaleFactor()
            fillPaint.textSize = textSize
            fillPaint.color = event.style.colorArgb.toInt()
            strokePaint.textSize = textSize
            val measuredWidth = fillPaint.measureText(event.text)
            val x = (width - measuredWidth) / 2f
            val y = when (event.style.mode) {
                DanmakuMode.TOP -> laneHeightPx * (visibleIndex + 1)
                DanmakuMode.BOTTOM -> height - laneHeightPx * (visibleIndex + 1)
                DanmakuMode.SCROLLING -> 0f
            }
            canvas.drawText(event.text, x, y, strokePaint)
            canvas.drawText(event.text, x, y, fillPaint)
            visibleIndex += 1
        }
    }

    private fun Float.spToPx(): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            this,
            resources.displayMetrics,
        )
}

private data class MobileOverlayClockAnchor(
    val positionMs: Long,
    val durationMs: Long?,
    val status: PlaybackStatus,
    val playbackRate: Float,
    val frameTimeNanos: Long,
) {
    fun positionAt(frameTimeNanos: Long): Double {
        if (status != PlaybackStatus.PLAYING || frameTimeNanos <= this.frameTimeNanos) {
            return positionMs.toDouble().coercePlaybackPosition(durationMs)
        }

        val elapsedMs = (frameTimeNanos - this.frameTimeNanos) / 1_000_000.0 * playbackRate
        val projectedPositionMs = positionMs + elapsedMs
        return projectedPositionMs.coercePlaybackPosition(durationMs)
    }

    companion object {
        fun fromSnapshot(
            snapshot: PlaybackSnapshot,
            frameTimeNanos: Long,
        ): MobileOverlayClockAnchor =
            MobileOverlayClockAnchor(
                positionMs = snapshot.position.positionMs,
                durationMs = snapshot.position.durationMs,
                status = snapshot.status,
                playbackRate = snapshot.playbackRate,
                frameTimeNanos = frameTimeNanos,
            )
    }
}

private fun ScrollingDanmakuPlacement.isVisibleAt(positionMs: Double): Boolean =
    positionMs >= startsAtMs && positionMs < endsAtMs

private fun ScrollingDanmakuPlacement.leftEdgeAt(positionMs: Double): Float {
    val elapsedMs = positionMs - startsAtMs
    val progress = elapsedMs / travelDurationMs
    return (viewportWidthPx - progress * (viewportWidthPx + widthPx)).toFloat()
}

private fun List<ScrollingDanmakuPlacement>.lowerBoundPlacementStart(startsAtMs: Long): Int {
    var low = 0
    var high = size
    while (low < high) {
        val middle = (low + high) / 2
        if (this[middle].startsAtMs < startsAtMs) {
            low = middle + 1
        } else {
            high = middle
        }
    }
    return low
}

private fun List<ScrollingDanmakuPlacement>.upperBoundPlacementStart(startsAtMs: Long): Int {
    var low = 0
    var high = size
    while (low < high) {
        val middle = (low + high) / 2
        if (this[middle].startsAtMs <= startsAtMs) {
            low = middle + 1
        } else {
            high = middle
        }
    }
    return low
}

private fun List<DanmakuEvent>.lowerBoundEventTimestamp(timestampMs: Long): Int {
    var low = 0
    var high = size
    while (low < high) {
        val middle = (low + high) / 2
        if (this[middle].timestampMs < timestampMs) {
            low = middle + 1
        } else {
            high = middle
        }
    }
    return low
}

private fun List<DanmakuEvent>.upperBoundEventTimestamp(timestampMs: Long): Int {
    var low = 0
    var high = size
    while (low < high) {
        val middle = (low + high) / 2
        if (this[middle].timestampMs <= timestampMs) {
            low = middle + 1
        } else {
            high = middle
        }
    }
    return low
}

private fun Double.coercePlaybackPosition(durationMs: Long?): Double {
    val nonNegativePositionMs = coerceAtLeast(0.0)
    return durationMs
        ?.let { nonNegativePositionMs.coerceAtMost(it.toDouble()) }
        ?: nonNegativePositionMs
}

private fun DanmakuSize.scaleFactor(): Float =
    when (this) {
        DanmakuSize.SMALL -> 0.82f
        DanmakuSize.NORMAL -> 1f
        DanmakuSize.LARGE -> 1.18f
    }

private fun absoluteDifference(
    first: Double,
    second: Double,
): Double =
    if (first >= second) {
        first - second
    } else {
        second - first
    }

private const val FIXED_DANMAKU_DURATION_MS = 4_500L
private const val POSITION_REANCHOR_THRESHOLD_MS = 650.0