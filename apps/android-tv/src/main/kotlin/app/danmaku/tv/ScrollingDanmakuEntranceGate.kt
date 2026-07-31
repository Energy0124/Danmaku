package app.danmaku.tv

import app.danmaku.domain.ScrollingDanmakuPlacement

/**
 * Prevents a scrolling comment from first appearing after it has already
 * crossed a noticeable part of the viewport.
 *
 * A comment is admitted only when its first observed frame is close to its
 * scheduled timestamp. Its first draw is pinned to the scheduled start so the
 * text begins fully beyond the right edge. Once admitted, subsequent frames
 * use the live playback position for the remainder of that pass.
 */
internal class ScrollingDanmakuEntranceGate(
    private val timelineAttachedAtMs: Long,
    private val maxFirstFrameLatenessMs: Long = MAX_FIRST_FRAME_LATENESS_MS,
) {
    private val admittedPlacements = mutableSetOf<ScrollingDanmakuPlacement>()

    init {
        require(timelineAttachedAtMs >= 0)
        require(maxFirstFrameLatenessMs >= 0)
    }

    fun drawPositionMs(
        placement: ScrollingDanmakuPlacement,
        currentPositionMs: Long,
    ): Long? {
        require(currentPositionMs >= 0)
        if (!placement.isVisibleAt(currentPositionMs)) return null

        if (placement in admittedPlacements) return currentPositionMs

        val firstFrameLatenessMs = currentPositionMs - placement.startsAtMs
        if (
            placement.startsAtMs < timelineAttachedAtMs ||
            firstFrameLatenessMs > maxFirstFrameLatenessMs
        ) {
            return null
        }

        admittedPlacements += placement
        return placement.startsAtMs
    }

    private companion object {
        const val MAX_FIRST_FRAME_LATENESS_MS = 250L
    }
}
