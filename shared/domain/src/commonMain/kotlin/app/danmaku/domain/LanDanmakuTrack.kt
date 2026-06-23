package app.danmaku.domain

import kotlinx.serialization.Serializable

@Serializable
data class LanDanmakuTrack(
    val mediaId: String,
    val status: LanDanmakuLoadStatus,
    val source: LanDanmakuSource? = null,
    val comments: List<LanDanmakuComment> = emptyList(),
    val matchTitle: String? = null,
    val episodeId: Long? = null,
    val fetchedAtEpochMs: Long? = null,
    val message: String? = null,
) {
    init {
        require(mediaId.isNotBlank()) { "mediaId must not be blank" }
        require(matchTitle == null || matchTitle.isNotBlank()) { "matchTitle must not be blank" }
        require(episodeId == null || episodeId > 0) { "episodeId must be positive" }
        require(fetchedAtEpochMs == null || fetchedAtEpochMs >= 0) {
            "fetchedAtEpochMs must not be negative"
        }
        require(message == null || message.isNotBlank()) { "message must not be blank" }
    }

    val domainEvents: List<DanmakuEvent>
        get() = comments.map(LanDanmakuComment::toDanmakuEvent)
}

@Serializable
enum class LanDanmakuLoadStatus {
    READY,
    NO_MATCH,
    UNAVAILABLE,
    FAILED,
}

@Serializable
enum class LanDanmakuSource {
    CACHE,
    NETWORK,
}

@Serializable
data class LanDanmakuComment(
    val id: String,
    val timestampMs: Long,
    val text: String,
    val style: LanDanmakuCommentStyle = LanDanmakuCommentStyle(),
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(timestampMs >= 0) { "timestampMs must not be negative" }
        require(text.isNotBlank()) { "text must not be blank" }
    }

    fun toDanmakuEvent(): DanmakuEvent =
        DanmakuEvent(
            id = id,
            timestampMs = timestampMs,
            text = text,
            style = style.toDanmakuStyle(),
        )
}

@Serializable
data class LanDanmakuCommentStyle(
    val colorArgb: Long = DEFAULT_COLOR_ARGB,
    val mode: DanmakuMode = DanmakuMode.SCROLLING,
    val size: DanmakuSize = DanmakuSize.NORMAL,
) {
    init {
        require(colorArgb in 0..MAX_COLOR_ARGB) {
            "colorArgb must fit in an unsigned 32-bit color"
        }
    }

    fun toDanmakuStyle(): DanmakuStyle =
        DanmakuStyle(
            colorArgb = colorArgb.toUInt(),
            mode = mode,
            size = size,
        )

    private companion object {
        const val DEFAULT_COLOR_ARGB = 0xFFFFFFFFL
        const val MAX_COLOR_ARGB = 0xFFFFFFFFL
    }
}

fun DanmakuEvent.toLanDanmakuComment(): LanDanmakuComment =
    LanDanmakuComment(
        id = id,
        timestampMs = timestampMs,
        text = text,
        style = LanDanmakuCommentStyle(
            colorArgb = style.colorArgb.toLong(),
            mode = style.mode,
            size = style.size,
        ),
    )
