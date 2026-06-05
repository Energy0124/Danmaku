package app.danmaku.desktop

import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackController
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.library.LanPlaybackPreparation
import app.danmaku.library.LanPlaybackTarget
import java.nio.file.Path

data class DesktopPlaybackRequest(
    val label: String,
    val source: PlaybackSource,
    val resumePositionMs: Long?,
    val progressMediaId: String? = null,
    val progressTarget: LanPlaybackTarget? = null,
) {
    init {
        require(label.isNotBlank()) { "label must not be blank" }
        require(progressMediaId == null || progressMediaId.isNotBlank()) {
            "progressMediaId must not be blank"
        }
    }
}

class DesktopPlaybackSession(
    private val controller: PlaybackController,
    private val afterLoad: (DesktopPlaybackRequest) -> Unit = {},
) {
    fun load(request: DesktopPlaybackRequest): PlaybackSnapshot {
        controller.load(request.source)
        request.resumePositionMs?.let {
            controller.dispatch(PlaybackCommand.SeekTo(it))
        }
        afterLoad(request)
        return controller.snapshot()
    }
}

fun DesktopLocalPlaybackPreparation.toPlaybackRequest(): DesktopPlaybackRequest =
    DesktopPlaybackRequest(
        label = "${item.seriesTitle} - ${item.episodeTitle}",
        source = source,
        resumePositionMs = resumePositionMs,
        progressMediaId = item.id,
    )

fun Path.toDirectLocalPlaybackRequest(): DesktopPlaybackRequest {
    val normalizedPath = toAbsolutePath().normalize()
    return DesktopPlaybackRequest(
        label = normalizedPath.fileName?.toString() ?: normalizedPath.toString(),
        source = PlaybackSource.LocalFile(normalizedPath.toString()),
        resumePositionMs = null,
    )
}

fun LanPlaybackPreparation.toDesktopPlaybackRequest(): DesktopPlaybackRequest =
    DesktopPlaybackRequest(
        label = "${item.seriesTitle} - ${item.episodeTitle}",
        source = source,
        resumePositionMs = resumePositionMs,
        progressMediaId = target.mediaId,
        progressTarget = target,
    )
