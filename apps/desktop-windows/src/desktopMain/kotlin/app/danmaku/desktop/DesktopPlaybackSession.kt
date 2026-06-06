package app.danmaku.desktop

import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackController
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.library.LanPlaybackPreparation
import app.danmaku.library.LanPlaybackTarget
import java.nio.file.Path

data class DesktopPlaybackSubtitle(
    val source: String,
    val label: String,
    val isDanmakuOverlay: Boolean = false,
) {
    init {
        require(source.isNotBlank()) { "subtitle source must not be blank" }
        require(label.isNotBlank()) { "subtitle label must not be blank" }
    }
}

data class DesktopPlaybackRequest(
    val label: String,
    val source: PlaybackSource,
    val resumePositionMs: Long?,
    val subtitles: List<DesktopPlaybackSubtitle> = emptyList(),
    val progressMediaId: String? = null,
    val progressTarget: LanPlaybackTarget? = null,
) {
    init {
        require(label.isNotBlank()) { "label must not be blank" }
        require(resumePositionMs == null || resumePositionMs >= 0) {
            "resumePositionMs must not be negative"
        }
        require(progressMediaId == null || progressMediaId.isNotBlank()) {
            "progressMediaId must not be blank"
        }
    }
}

class DesktopPlaybackSession(
    private val controller: PlaybackController,
    private val afterLoad: (DesktopPlaybackRequest) -> Unit = {},
    private val attachSubtitle: (DesktopPlaybackSubtitle) -> Unit = {},
) {
    fun load(request: DesktopPlaybackRequest): PlaybackSnapshot {
        controller.loadRequestSource(request)
        request.subtitles.forEach(attachSubtitle)
        afterLoad(request)
        return controller.snapshot()
    }
}

private fun PlaybackController.loadRequestSource(request: DesktopPlaybackRequest) {
    if (this is DesktopMpvPlaybackController) {
        load(request.source, request.resumePositionMs)
        return
    }
    load(request.source)
    request.resumePositionMs?.let {
        dispatch(PlaybackCommand.SeekTo(it))
    }
}

fun DesktopLocalPlaybackPreparation.toPlaybackRequest(): DesktopPlaybackRequest =
    DesktopPlaybackRequest(
        label = "${item.seriesTitle} - ${item.episodeTitle}",
        source = source,
        resumePositionMs = resumePositionMs,
        subtitles = subtitles,
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
        subtitles = subtitles.map {
            DesktopPlaybackSubtitle(
                source = it.source.url,
                label = it.track.label,
            )
        },
        progressMediaId = target.mediaId,
        progressTarget = target,
    )
