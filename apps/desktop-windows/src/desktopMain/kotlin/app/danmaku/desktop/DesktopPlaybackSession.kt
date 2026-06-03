package app.danmaku.desktop

import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackController
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.library.LanPlaybackPreparation

data class DesktopPlaybackRequest(
    val label: String,
    val source: PlaybackSource,
    val resumePositionMs: Long?,
) {
    init {
        require(label.isNotBlank()) { "label must not be blank" }
    }
}

class DesktopPlaybackSession(
    private val controller: PlaybackController,
) {
    fun load(request: DesktopPlaybackRequest): PlaybackSnapshot {
        controller.load(request.source)
        request.resumePositionMs?.let {
            controller.dispatch(PlaybackCommand.SeekTo(it))
        }
        return controller.snapshot()
    }
}

fun DesktopLocalPlaybackPreparation.toPlaybackRequest(): DesktopPlaybackRequest =
    DesktopPlaybackRequest(
        label = "${item.seriesTitle} - ${item.episodeTitle}",
        source = source,
        resumePositionMs = resumePositionMs,
    )

fun LanPlaybackPreparation.toDesktopPlaybackRequest(): DesktopPlaybackRequest =
    DesktopPlaybackRequest(
        label = "${item.seriesTitle} - ${item.episodeTitle}",
        source = source,
        resumePositionMs = resumePositionMs,
    )
