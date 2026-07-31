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

    @Test
    fun resetAfterRewindAllowsCommentsFromTheNewPass() {
        val placement = placement(id = "rewound", startsAtMs = 5_100)
        val originalGate = ScrollingDanmakuEntranceGate(timelineAttachedAtMs = 10_000)
        assertNull(originalGate.drawPositionMs(placement, currentPositionMs = 5_200))

        val resetGate = ScrollingDanmakuEntranceGate(timelineAttachedAtMs = 5_000)

        assertEquals(
            5_100L,
            resetGate.drawPositionMs(placement, currentPositionMs = 5_200),
        )
    }

    @Test
    fun resetAfterForwardSeekRejectsPreviouslyAdmittedComments() {
        val placement = placement(id = "forward", startsAtMs = 5_100)
        val originalGate = ScrollingDanmakuEntranceGate(timelineAttachedAtMs = 5_000)
        assertEquals(
            5_100L,
            originalGate.drawPositionMs(placement, currentPositionMs = 5_200),
        )

        val resetGate = ScrollingDanmakuEntranceGate(timelineAttachedAtMs = 7_000)

        assertNull(resetGate.drawPositionMs(placement, currentPositionMs = 7_000))
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
