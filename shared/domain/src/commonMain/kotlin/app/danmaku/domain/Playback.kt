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

fun PlaybackProgress.resumePositionMs(
    minimumPositionMs: Long = 10_000,
    minimumRemainingMs: Long = 30_000,
): Long? {
    require(minimumPositionMs >= 0) { "minimumPositionMs must not be negative" }
    require(minimumRemainingMs >= 0) { "minimumRemainingMs must not be negative" }
    return positionMs.takeIf {
        it >= minimumPositionMs &&
            (durationMs == null || durationMs - it >= minimumRemainingMs)
    }
}

fun PlaybackSnapshot.toPlaybackProgress(
    mediaId: String,
    updatedAtEpochMs: Long,
): PlaybackProgress? =
    source?.let {
        PlaybackProgress(
            mediaId = mediaId,
            positionMs = position.positionMs,
            durationMs = position.durationMs,
            updatedAtEpochMs = updatedAtEpochMs,
        )
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

    data class SetVolume(val volumePercent: Int) : PlaybackCommand {
        init {
            require(volumePercent in 0..100) { "volumePercent must be between 0 and 100" }
        }
    }

    data class SelectAudioTrack(val trackId: String) : PlaybackCommand {
        init {
            require(trackId.isNotBlank()) { "trackId must not be blank" }
        }
    }

    data class SelectSubtitleTrack(val trackId: String?) : PlaybackCommand {
        init {
            require(trackId == null || trackId.isNotBlank()) { "trackId must not be blank" }
        }
    }
}
