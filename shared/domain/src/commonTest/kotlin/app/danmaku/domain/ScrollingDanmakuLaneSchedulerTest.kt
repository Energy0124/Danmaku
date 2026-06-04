package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScrollingDanmakuLaneSchedulerTest {
    private val config = ScrollingDanmakuLayoutConfig(
        viewportWidthPx = 100f,
        laneCount = 2,
        travelDurationMs = 1_000,
        horizontalGapPx = 10f,
    )

    @Test
    fun reusesLaneWhenThePreviousCommentHasEnoughSpace() {
        val schedule = schedule(
            event(id = "first", timestampMs = 0, widthPx = 20f),
            event(id = "second", timestampMs = 500, widthPx = 20f),
        )

        assertEquals(listOf(0, 0), schedule.placements.map { it.laneIndex })
        assertTrue(schedule.droppedEvents.isEmpty())
    }

    @Test
    fun usesAnotherLaneWhenCommentsWouldOverlap() {
        val schedule = schedule(
            event(id = "first", timestampMs = 0, widthPx = 60f),
            event(id = "second", timestampMs = 100, widthPx = 60f),
        )

        assertEquals(listOf(0, 1), schedule.placements.map { it.laneIndex })
    }

    @Test
    fun dropsCommentWhenEveryLaneWouldOverlap() {
        val schedule = schedule(
            event(id = "first", timestampMs = 0, widthPx = 60f),
            event(id = "second", timestampMs = 100, widthPx = 60f),
            event(id = "third", timestampMs = 200, widthPx = 60f),
        )

        assertEquals(listOf("first", "second"), schedule.placements.map { it.event.id })
        assertEquals(listOf("third"), schedule.droppedEvents.map { it.id })
    }

    @Test
    fun rejectsAFollowingCommentThatWouldCatchThePreviousComment() {
        val singleLaneConfig = config.copy(
            laneCount = 1,
            horizontalGapPx = 0f,
        )

        val schedule = schedule(
            event(id = "short", timestampMs = 0, widthPx = 10f),
            event(id = "wide", timestampMs = 600, widthPx = 300f),
            config = singleLaneConfig,
        )

        assertEquals(listOf("short"), schedule.placements.map { it.event.id })
        assertEquals(listOf("wide"), schedule.droppedEvents.map { it.id })
    }

    @Test
    fun reportsVisiblePlacementsAndInterpolatedPosition() {
        val schedule = schedule(event(id = "first", timestampMs = 100, widthPx = 20f))
        val placement = schedule.placements.single()

        assertFalse(placement.isVisibleAt(99))
        assertTrue(placement.isVisibleAt(100))
        assertEquals(100f, placement.leftEdgeAt(100))
        assertEquals(40f, placement.leftEdgeAt(600))
        assertFalse(placement.isVisibleAt(1_100))
        assertEquals(listOf(placement), schedule.visibleAt(600))
    }

    @Test
    fun supportsRepeatedQueriesAfterSeekingBackward() {
        val schedule = schedule(
            event(id = "early", timestampMs = 0, widthPx = 20f),
            event(id = "late", timestampMs = 1_000, widthPx = 20f),
        )

        assertEquals(listOf("late"), schedule.visibleAt(1_600).map { it.event.id })
        assertEquals(listOf("early"), schedule.visibleAt(600).map { it.event.id })
    }

    @Test
    fun queriesABoundedWindowFromALargeGeneratedTrack() {
        val generatedTrack = List(10_000) { index ->
            event(
                id = index.toString(),
                timestampMs = index * 250L,
                widthPx = 80f,
            )
        }
        val schedule = ScrollingDanmakuLaneScheduler.schedule(
            events = generatedTrack,
            config = ScrollingDanmakuLayoutConfig(
                viewportWidthPx = 1_920f,
                laneCount = 24,
                travelDurationMs = 6_000,
            ),
        )

        assertEquals(10_000, schedule.placements.size)
        assertTrue(schedule.visibleAt(1_000_000).size <= 24)
    }

    @Test
    fun measuresVisibilityDensityFromALargeGeneratedTrack() {
        val generatedTrack = List(10_000) { index ->
            event(
                id = index.toString(),
                timestampMs = index * 250L,
                widthPx = 80f,
            )
        }
        val schedule = ScrollingDanmakuLaneScheduler.schedule(
            events = generatedTrack,
            config = ScrollingDanmakuLayoutConfig(
                viewportWidthPx = 1_920f,
                laneCount = 24,
                travelDurationMs = 6_000,
            ),
        )

        val metrics = schedule.visibilityMetrics(sampleEveryMs = 1_000)

        assertEquals(10_000, metrics.placedEvents)
        assertEquals(0, metrics.droppedEvents)
        assertTrue(metrics.sampledPositions > 2_000)
        assertTrue(metrics.peakVisiblePlacements <= 24)
        assertTrue(metrics.averageVisiblePlacements > 0f)
    }

    @Test
    fun rejectsNonPositiveVisibilityMetricIntervals() {
        assertFailsWith<IllegalArgumentException> {
            schedule(event(id = "first", timestampMs = 0, widthPx = 20f))
                .visibilityMetrics(sampleEveryMs = 0)
        }
    }

    private fun schedule(
        vararg events: MeasuredDanmakuEvent,
        config: ScrollingDanmakuLayoutConfig = this.config,
    ): ScrollingDanmakuSchedule =
        ScrollingDanmakuLaneScheduler.schedule(events.toList(), config)

    private fun event(
        id: String,
        timestampMs: Long,
        widthPx: Float,
    ): MeasuredDanmakuEvent =
        MeasuredDanmakuEvent(
            event = DanmakuEvent(
                id = id,
                timestampMs = timestampMs,
                text = id,
            ),
            widthPx = widthPx,
        )
}
