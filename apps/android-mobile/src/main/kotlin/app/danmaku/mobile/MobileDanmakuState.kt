package app.danmaku.mobile

import app.danmaku.domain.DanmakuEvent
import app.danmaku.domain.LanDanmakuLoadStatus
import app.danmaku.domain.LanDanmakuSource
import app.danmaku.domain.LanDanmakuTrack

internal enum class MobilePlaybackStartupPhase {
    Idle,
    WaitingForDanmaku,
    Playing,
}

internal enum class MobileDanmakuPhase {
    Idle,
    Loading,
    Ready,
    NoMatch,
    Unavailable,
    Failed,
    TimedOut,
}

internal data class MobileDanmakuState(
    val mediaId: String? = null,
    val phase: MobileDanmakuPhase = MobileDanmakuPhase.Idle,
    val source: LanDanmakuSource? = null,
    val events: List<DanmakuEvent> = emptyList(),
    val matchTitle: String? = null,
    val message: String? = null,
) {
    companion object {
        val Idle = MobileDanmakuState()

        fun loading(mediaId: String): MobileDanmakuState =
            MobileDanmakuState(
                mediaId = mediaId,
                phase = MobileDanmakuPhase.Loading,
                message = "Loading danmaku",
            )

        fun timedOut(mediaId: String): MobileDanmakuState =
            MobileDanmakuState(
                mediaId = mediaId,
                phase = MobileDanmakuPhase.TimedOut,
                message = "Danmaku is still loading",
            )

        fun failed(
            mediaId: String,
            error: Throwable,
        ): MobileDanmakuState =
            MobileDanmakuState(
                mediaId = mediaId,
                phase = MobileDanmakuPhase.Failed,
                message = error.message ?: "Danmaku load failed",
            )

        fun fromTrack(track: LanDanmakuTrack): MobileDanmakuState =
            MobileDanmakuState(
                mediaId = track.mediaId,
                phase = when (track.status) {
                    LanDanmakuLoadStatus.READY -> MobileDanmakuPhase.Ready
                    LanDanmakuLoadStatus.NO_MATCH -> MobileDanmakuPhase.NoMatch
                    LanDanmakuLoadStatus.UNAVAILABLE -> MobileDanmakuPhase.Unavailable
                    LanDanmakuLoadStatus.FAILED -> MobileDanmakuPhase.Failed
                },
                source = track.source,
                events = track.domainEvents,
                matchTitle = track.matchTitle,
                message = track.message,
            )
    }
}