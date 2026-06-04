package app.danmaku.domain

data class ScrollingDanmakuLayoutConfig(
    val viewportWidthPx: Float,
    val laneCount: Int,
    val travelDurationMs: Long = 8_000,
    val horizontalGapPx: Float = 24f,
) {
    init {
        require(viewportWidthPx > 0) { "viewportWidthPx must be positive" }
        require(laneCount > 0) { "laneCount must be positive" }
        require(travelDurationMs > 0) { "travelDurationMs must be positive" }
        require(horizontalGapPx >= 0) { "horizontalGapPx must not be negative" }
    }
}

data class MeasuredDanmakuEvent(
    val event: DanmakuEvent,
    val widthPx: Float,
) {
    init {
        require(event.style.mode == DanmakuMode.SCROLLING) {
            "only scrolling danmaku events can be lane scheduled"
        }
        require(widthPx > 0) { "widthPx must be positive" }
    }
}

data class ScrollingDanmakuPlacement(
    val measuredEvent: MeasuredDanmakuEvent,
    val laneIndex: Int,
    val startsAtMs: Long,
    val travelDurationMs: Long,
    val viewportWidthPx: Float,
) {
    val event: DanmakuEvent
        get() = measuredEvent.event

    val widthPx: Float
        get() = measuredEvent.widthPx

    val endsAtMs: Long
        get() = startsAtMs + travelDurationMs

    init {
        require(laneIndex >= 0) { "laneIndex must not be negative" }
        require(startsAtMs >= 0) { "startsAtMs must not be negative" }
        require(travelDurationMs > 0) { "travelDurationMs must be positive" }
        require(startsAtMs <= Long.MAX_VALUE - travelDurationMs) {
            "placement end timestamp must fit in a Long"
        }
        require(viewportWidthPx > 0) { "viewportWidthPx must be positive" }
    }

    fun isVisibleAt(positionMs: Long): Boolean =
        positionMs in startsAtMs..<endsAtMs

    fun leftEdgeAt(positionMs: Long): Float {
        val elapsedMs = positionMs - startsAtMs
        val progress = elapsedMs.toFloat() / travelDurationMs
        return viewportWidthPx - progress * (viewportWidthPx + widthPx)
    }

    fun rightEdgeAt(positionMs: Long): Float =
        leftEdgeAt(positionMs) + widthPx
}

data class ScrollingDanmakuSchedule(
    val placements: List<ScrollingDanmakuPlacement>,
    val droppedEvents: List<DanmakuEvent>,
) {
    private val maxTravelDurationMs = placements.maxOfOrNull { it.travelDurationMs } ?: 0

    fun visibleAt(positionMs: Long): List<ScrollingDanmakuPlacement> {
        require(positionMs >= 0) { "positionMs must not be negative" }
        if (placements.isEmpty()) {
            return emptyList()
        }

        val earliestStartsAtMs =
            if (positionMs >= maxTravelDurationMs) {
                positionMs - maxTravelDurationMs + 1
            } else {
                0
            }
        val firstCandidateIndex = placements.lowerBound(earliestStartsAtMs)
        val afterLastCandidateIndex = placements.upperBound(positionMs)
        return placements
            .subList(firstCandidateIndex, afterLastCandidateIndex)
            .filter { it.isVisibleAt(positionMs) }
    }

    fun visibilityMetrics(sampleEveryMs: Long): ScrollingDanmakuVisibilityMetrics {
        require(sampleEveryMs > 0) { "sampleEveryMs must be positive" }
        if (placements.isEmpty()) {
            return ScrollingDanmakuVisibilityMetrics(
                placedEvents = 0,
                droppedEvents = droppedEvents.size,
                sampledPositions = 0,
                peakVisiblePlacements = 0,
                averageVisiblePlacements = 0f,
            )
        }

        val lastVisiblePositionMs = placements.maxOf { it.endsAtMs - 1 }
        var positionMs = 0L
        var sampledPositions = 0
        var totalVisiblePlacements = 0L
        var peakVisiblePlacements = 0

        while (positionMs <= lastVisiblePositionMs) {
            val visibleCount = visibleAt(positionMs).size
            sampledPositions += 1
            totalVisiblePlacements += visibleCount
            peakVisiblePlacements = maxOf(peakVisiblePlacements, visibleCount)
            if (Long.MAX_VALUE - positionMs < sampleEveryMs) {
                break
            }
            positionMs += sampleEveryMs
        }

        return ScrollingDanmakuVisibilityMetrics(
            placedEvents = placements.size,
            droppedEvents = droppedEvents.size,
            sampledPositions = sampledPositions,
            peakVisiblePlacements = peakVisiblePlacements,
            averageVisiblePlacements = totalVisiblePlacements.toFloat() / sampledPositions,
        )
    }
}

data class ScrollingDanmakuVisibilityMetrics(
    val placedEvents: Int,
    val droppedEvents: Int,
    val sampledPositions: Int,
    val peakVisiblePlacements: Int,
    val averageVisiblePlacements: Float,
) {
    init {
        require(placedEvents >= 0) { "placedEvents must not be negative" }
        require(droppedEvents >= 0) { "droppedEvents must not be negative" }
        require(sampledPositions >= 0) { "sampledPositions must not be negative" }
        require(peakVisiblePlacements >= 0) { "peakVisiblePlacements must not be negative" }
        require(averageVisiblePlacements >= 0f) {
            "averageVisiblePlacements must not be negative"
        }
    }
}

object ScrollingDanmakuLaneScheduler {
    fun schedule(
        events: List<MeasuredDanmakuEvent>,
        config: ScrollingDanmakuLayoutConfig,
    ): ScrollingDanmakuSchedule {
        val laneTails = MutableList<ScrollingDanmakuPlacement?>(config.laneCount) { null }
        val placements = mutableListOf<ScrollingDanmakuPlacement>()
        val droppedEvents = mutableListOf<DanmakuEvent>()

        events
            .withIndex()
            .sortedWith(compareBy({ it.value.event.timestampMs }, { it.index }))
            .forEach { indexedEvent ->
                val measuredEvent = indexedEvent.value
                val laneIndex = laneTails.indexOfFirst { previous ->
                    previous == null || canFollow(previous, measuredEvent, config)
                }

                if (laneIndex == -1) {
                    droppedEvents += measuredEvent.event
                    return@forEach
                }

                val placement = ScrollingDanmakuPlacement(
                    measuredEvent = measuredEvent,
                    laneIndex = laneIndex,
                    startsAtMs = measuredEvent.event.timestampMs,
                    travelDurationMs = config.travelDurationMs,
                    viewportWidthPx = config.viewportWidthPx,
                )
                laneTails[laneIndex] = placement
                placements += placement
            }

        return ScrollingDanmakuSchedule(
            placements = placements,
            droppedEvents = droppedEvents,
        )
    }

    private fun canFollow(
        previous: ScrollingDanmakuPlacement,
        next: MeasuredDanmakuEvent,
        config: ScrollingDanmakuLayoutConfig,
    ): Boolean {
        val nextStartsAtMs = next.event.timestampMs
        if (nextStartsAtMs >= previous.endsAtMs) {
            return true
        }

        val initialGapPx = config.viewportWidthPx - previous.rightEdgeAt(nextStartsAtMs)
        if (initialGapPx < config.horizontalGapPx) {
            return false
        }

        val nextPlacement = ScrollingDanmakuPlacement(
            measuredEvent = next,
            laneIndex = previous.laneIndex,
            startsAtMs = nextStartsAtMs,
            travelDurationMs = config.travelDurationMs,
            viewportWidthPx = config.viewportWidthPx,
        )
        val overlapEndsAtMs = minOf(previous.endsAtMs, nextPlacement.endsAtMs)
        val finalGapPx =
            nextPlacement.leftEdgeAt(overlapEndsAtMs) - previous.rightEdgeAt(overlapEndsAtMs)
        return finalGapPx >= config.horizontalGapPx
    }
}

private fun List<ScrollingDanmakuPlacement>.lowerBound(startsAtMs: Long): Int {
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

private fun List<ScrollingDanmakuPlacement>.upperBound(startsAtMs: Long): Int {
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
