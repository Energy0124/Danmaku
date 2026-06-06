package app.danmaku.desktop

import kotlin.io.path.absolutePathString

data class DandanplayPlaybackUiStatus(
    val mediaId: String,
    val summary: String,
    val details: List<DandanplayPlaybackUiDetail> = emptyList(),
)

data class DandanplayPlaybackUiDetail(
    val label: String,
    val value: String,
)

fun dandanplayStatusFromResolution(
    mediaId: String,
    resolution: DesktopDandanplayDanmakuResolution,
): DandanplayPlaybackUiStatus {
    val source = resolution.source.name.lowercase()
    val commentCount = resolution.eventCount.toCommentCountLabel()
    val summary = when {
        resolution.subtitle != null -> "dandanplay $source: attached $commentCount"
        resolution.match != null -> "dandanplay $source: matched, no comments"
        else -> "dandanplay network: no match"
    }
    val details = buildList {
        add(DandanplayPlaybackUiDetail("Provider source", source))
        resolution.match?.let { match ->
            add(DandanplayPlaybackUiDetail("Matched episode", match.displayTitle))
            add(DandanplayPlaybackUiDetail("Episode ID", match.episodeId.toString()))
        }
        add(DandanplayPlaybackUiDetail("Comments", commentCount))
        add(
            DandanplayPlaybackUiDetail(
                label = "ASS overlay",
                value = if (resolution.subtitle == null) {
                    "not attached"
                } else {
                    "attached"
                },
            ),
        )
        resolution.cachePath?.let { cachePath ->
            add(DandanplayPlaybackUiDetail("Cache file", cachePath.toAbsolutePath().normalize().absolutePathString()))
        }
    }
    return DandanplayPlaybackUiStatus(
        mediaId = mediaId,
        summary = summary,
        details = details,
    )
}

fun dandanplayStatusMessage(
    mediaId: String,
    summary: String,
): DandanplayPlaybackUiStatus =
    DandanplayPlaybackUiStatus(mediaId = mediaId, summary = summary)

private fun Int.toCommentCountLabel(): String =
    if (this == 1) {
        "1 comment"
    } else {
        "$this comments"
    }
