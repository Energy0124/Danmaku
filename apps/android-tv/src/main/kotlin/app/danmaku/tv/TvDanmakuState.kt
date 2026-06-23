package app.danmaku.tv

import app.danmaku.domain.DanmakuEvent
import app.danmaku.domain.LanDanmakuLoadStatus
import app.danmaku.domain.LanDanmakuSource
import app.danmaku.domain.LanDanmakuTrack

internal enum class TvPlaybackStartupPhase {
    Idle,
    WaitingForDanmaku,
    Playing,
    Stopping,
}

internal enum class TvDanmakuPhase {
    Idle,
    Loading,
    Ready,
    NoMatch,
    Unavailable,
    Failed,
    TimedOut,
}

internal data class TvDanmakuState(
    val mediaId: String? = null,
    val phase: TvDanmakuPhase = TvDanmakuPhase.Idle,
    val source: LanDanmakuSource? = null,
    val events: List<DanmakuEvent> = emptyList(),
    val matchTitle: String? = null,
    val message: String? = null,
) {
    companion object {
        val Idle = TvDanmakuState()

        fun loading(mediaId: String): TvDanmakuState =
            TvDanmakuState(
                mediaId = mediaId,
                phase = TvDanmakuPhase.Loading,
                message = "Loading danmaku",
            )

        fun timedOut(mediaId: String): TvDanmakuState =
            TvDanmakuState(
                mediaId = mediaId,
                phase = TvDanmakuPhase.TimedOut,
                message = "Danmaku is still loading",
            )

        fun failed(
            mediaId: String,
            error: Throwable,
        ): TvDanmakuState =
            TvDanmakuState(
                mediaId = mediaId,
                phase = TvDanmakuPhase.Failed,
                message = error.message ?: "Danmaku load failed",
            )

        fun fromTrack(track: LanDanmakuTrack): TvDanmakuState =
            TvDanmakuState(
                mediaId = track.mediaId,
                phase = when (track.status) {
                    LanDanmakuLoadStatus.READY -> TvDanmakuPhase.Ready
                    LanDanmakuLoadStatus.NO_MATCH -> TvDanmakuPhase.NoMatch
                    LanDanmakuLoadStatus.UNAVAILABLE -> TvDanmakuPhase.Unavailable
                    LanDanmakuLoadStatus.FAILED -> TvDanmakuPhase.Failed
                },
                source = track.source,
                events = track.domainEvents,
                matchTitle = track.matchTitle,
                message = track.message,
            )
    }
}
