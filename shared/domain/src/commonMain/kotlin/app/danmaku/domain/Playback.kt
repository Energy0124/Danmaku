package app.danmaku.domain

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

