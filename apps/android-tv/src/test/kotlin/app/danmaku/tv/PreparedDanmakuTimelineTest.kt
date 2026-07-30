package app.danmaku.tv

import app.danmaku.domain.DanmakuEvent
import app.danmaku.domain.DanmakuMode
import app.danmaku.domain.DanmakuStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparedDanmakuTimelineTest {
    @Test
    fun indexesTenThousandEventsAndBoundsFixedWindow() {
        val events = (0 until 10_000).map { index ->
            DanmakuEvent(
                id = "event-$index",
                timestampMs = (index % 20) * 250L,
                text = "Comment $index",
                style = DanmakuStyle(
                    mode = if (index < 500) DanmakuMode.TOP else DanmakuMode.SCROLLING,
                ),
            )
        }

        val timeline = PreparedDanmakuTimeline.prepare(events)
        val emitted = mutableListOf<DanmakuEvent>()
        val count = timeline.forEachActiveFixed(
            positionMs = 4_750,
            durationMs = 4_500,
            limit = 40,
            action = emitted::add,
        )

        assertEquals(10_000, timeline.eventCount)
        assertEquals(9_500, timeline.scrollingEvents.size)
        assertEquals(40, count)
        assertEquals(40, emitted.size)
        assertTrue(emitted.all { it.timestampMs in 250L..4_750L })
    }

    @Test
    fun seekingQueriesOnlyTheRequestedTimeWindow() {
        val timeline = PreparedDanmakuTimeline.prepare(
            listOf(
                fixed("early", 1_000),
                fixed("middle", 5_000),
                fixed("late", 20_000),
            ),
        )
        val visible = mutableListOf<String>()

        timeline.forEachActiveFixed(positionMs = 5_500, durationMs = 1_000) {
            visible += it.id
        }

        assertEquals(listOf("middle"), visible)
    }

    @Test
    fun lateTimelineSkipsScrollingDanmakuAlreadyInFlight() {
        assertFalse(shouldRenderScrollingDanmaku(startsAtMs = 9_000, timelineAttachedAtMs = 10_000))
        assertTrue(shouldRenderScrollingDanmaku(startsAtMs = 10_000, timelineAttachedAtMs = 10_000))
        assertTrue(shouldRenderScrollingDanmaku(startsAtMs = 10_001, timelineAttachedAtMs = 10_000))
    }

    private fun fixed(
        id: String,
        timestampMs: Long,
    ): DanmakuEvent =
        DanmakuEvent(
            id = id,
            timestampMs = timestampMs,
            text = id,
            style = DanmakuStyle(mode = DanmakuMode.TOP),
        )
}
