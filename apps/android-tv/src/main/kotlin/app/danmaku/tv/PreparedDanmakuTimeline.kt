package app.danmaku.tv

import app.danmaku.domain.DanmakuEvent
import app.danmaku.domain.DanmakuMode

/**
 * Time-bucketed danmaku index prepared once per track.
 *
 * Renderers query only the buckets intersecting the active time window instead
 * of filtering the full provider response for every frame.
 */
internal class PreparedDanmakuTimeline private constructor(
    val scrollingEvents: List<DanmakuEvent>,
    private val fixedBuckets: Map<Long, List<DanmakuEvent>>,
    private val bucketWidthMs: Long,
) {
    val eventCount: Int =
        scrollingEvents.size + fixedBuckets.values.sumOf(List<DanmakuEvent>::size)

    fun forEachActiveFixed(
        positionMs: Long,
        durationMs: Long = DEFAULT_FIXED_DURATION_MS,
        limit: Int = Int.MAX_VALUE,
        action: (DanmakuEvent) -> Unit,
    ): Int {
        require(positionMs >= 0)
        require(durationMs > 0)
        require(limit >= 0)
        if (limit == 0 || fixedBuckets.isEmpty()) return 0

        val firstBucket = (positionMs - durationMs).coerceAtLeast(0) / bucketWidthMs
        val lastBucket = positionMs / bucketWidthMs
        var emitted = 0
        for (bucket in firstBucket..lastBucket) {
            fixedBuckets[bucket].orEmpty().forEach { event ->
                if (
                    emitted < limit &&
                    positionMs >= event.timestampMs &&
                    positionMs - event.timestampMs < durationMs
                ) {
                    action(event)
                    emitted += 1
                }
            }
            if (emitted >= limit) break
        }
        return emitted
    }

    companion object {
        const val DEFAULT_FIXED_DURATION_MS = 4_500L
        private const val DEFAULT_BUCKET_WIDTH_MS = 500L

        val Empty = PreparedDanmakuTimeline(
            scrollingEvents = emptyList(),
            fixedBuckets = emptyMap(),
            bucketWidthMs = DEFAULT_BUCKET_WIDTH_MS,
        )

        fun prepare(
            events: List<DanmakuEvent>,
            bucketWidthMs: Long = DEFAULT_BUCKET_WIDTH_MS,
        ): PreparedDanmakuTimeline {
            require(bucketWidthMs > 0)
            if (events.isEmpty()) return Empty
            val sorted = events.sortedWith(
                compareBy<DanmakuEvent> { it.timestampMs }.thenBy { it.id },
            )
            return PreparedDanmakuTimeline(
                scrollingEvents = sorted.filter { it.style.mode == DanmakuMode.SCROLLING },
                fixedBuckets = sorted
                    .asSequence()
                    .filter { it.style.mode != DanmakuMode.SCROLLING }
                    .groupBy { it.timestampMs / bucketWidthMs },
                bucketWidthMs = bucketWidthMs,
            )
        }
    }
}
