package app.danmaku.domain

/**
 * A provider-independent danmaku comment.
 *
 * Positioning and animation are renderer concerns. The domain keeps only the
 * normalized content needed by schedulers and user filters.
 */
data class DanmakuEvent(
    val id: String,
    val timestampMs: Long,
    val text: String,
    val style: DanmakuStyle = DanmakuStyle(),
) {
    init {
        require(timestampMs >= 0) { "timestampMs must not be negative" }
        require(text.isNotBlank()) { "text must not be blank" }
    }
}

data class DanmakuStyle(
    val colorArgb: UInt = 0xFFFFFFFFu,
    val mode: DanmakuMode = DanmakuMode.SCROLLING,
    val size: DanmakuSize = DanmakuSize.NORMAL,
)

enum class DanmakuMode {
    SCROLLING,
    TOP,
    BOTTOM,
}

enum class DanmakuSize {
    SMALL,
    NORMAL,
    LARGE,
}

