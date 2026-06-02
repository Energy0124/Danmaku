package app.danmaku.domain

import kotlinx.serialization.Serializable

data class PlaybackPosition(
    val positionMs: Long,
    val durationMs: Long?,
) {
    init {
        require(positionMs >= 0) { "positionMs must not be negative" }
        require(durationMs == null || durationMs >= 0) {
            "durationMs must not be negative"
        }
    }
}

@Serializable
data class PlaybackProgress(
    val mediaId: String,
    val positionMs: Long,
    val durationMs: Long?,
    val updatedAtEpochMs: Long,
) {
    init {
        require(mediaId.isNotBlank()) { "mediaId must not be blank" }
        require(positionMs >= 0) { "positionMs must not be negative" }
        require(durationMs == null || durationMs >= 0) {
            "durationMs must not be negative"
        }
        require(updatedAtEpochMs >= 0) { "updatedAtEpochMs must not be negative" }
    }
}

sealed interface PlaybackCommand {
    data object Play : PlaybackCommand
    data object Pause : PlaybackCommand
    data class SeekTo(val positionMs: Long) : PlaybackCommand {
        init {
            require(positionMs >= 0) { "positionMs must not be negative" }
        }
    }

    data class SetPlaybackRate(val rate: Float) : PlaybackCommand {
        init {
            require(rate > 0) { "rate must be positive" }
        }
    }
}
