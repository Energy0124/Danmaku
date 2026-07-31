package app.danmaku.tv

import app.danmaku.domain.DanmakuEvent
import app.danmaku.domain.MeasuredDanmakuEvent
import app.danmaku.domain.ScrollingDanmakuPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrollingDanmakuEntranceGateTest {
    @Test
    fun firstAcceptedFrameStartsOutsideRightEdgeThenTracksPlayback() {
        val gate = ScrollingDanmakuEntranceGate(timelineAttachedAtMs = 10_000)
        val placement = placement(id = "accepted", startsAtMs = 10_100)

        val firstDrawPosition = gate.drawPositionMs(placement, currentPositionMs = 10_300)

        assertEquals(10_100L, firstDrawPosition)
        assertEquals(1_920f, placement.leftEdgeAt(firstDrawPosition!!), 0f)
        assertEquals(10_316L, gate.drawPositionMs(placement, currentPositionMs = 10_316))
    }

    @Test
    fun firstFrameObservedTooLateIsNeverAdmittedMidScreen() {
        val gate = ScrollingDanmakuEntranceGate(timelineAttachedAtMs = 10_000)
        val placement = placement(id = "late", startsAtMs = 10_100)

        assertNull(gate.drawPositionMs(placement, currentPositionMs = 10_351))
        assertNull(gate.drawPositionMs(placement, currentPositionMs = 12_000))
    }

    @Test
    fun timelineAttachmentStillRejectsAlreadyInFlightComments() {
        val gate = ScrollingDanmakuEntranceGate(timelineAttachedAtMs = 10_000)
        val placement = placement(id = "old", startsAtMs = 9_900)

        assertNull(gate.drawPositionMs(placement, currentPositionMs = 10_000))
    }

    private fun placement(
        id: String,
        startsAtMs: Long,
    ): ScrollingDanmakuPlacement =
        ScrollingDanmakuPlacement(
            measuredEvent = MeasuredDanmakuEvent(
                event = DanmakuEvent(
                    id = id,
                    timestampMs = startsAtMs,
                    text = id,
                ),
                widthPx = 240f,
            ),
            laneIndex = 0,
            startsAtMs = startsAtMs,
            travelDurationMs = 8_000,
            viewportWidthPx = 1_920f,
        )
}
