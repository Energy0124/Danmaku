package app.danmaku.desktop

import kotlin.io.path.absolutePathString

data class DandanplayPlaybackUiStatus(
    val mediaId: String,
    val summary: String,
    val details: List<DandanplayPlaybackUiDetail> = emptyList(),
    val selectedEpisodeId: Long? = null,
    val matchCandidates: List<DandanplayMatch> = emptyList(),
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
            add(DandanplayPlaybackUiDetail("Anime ID", match.animeId.toString()))
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
        selectedEpisodeId = resolution.match?.episodeId,
        matchCandidates = resolution.matchCandidates,
    )
}

fun dandanplayStatusMessage(
    mediaId: String,
    summary: String,
    details: List<DandanplayPlaybackUiDetail> = emptyList(),
): DandanplayPlaybackUiStatus =
    DandanplayPlaybackUiStatus(
        mediaId = mediaId,
        summary = summary,
        details = details,
    )

private fun Int.toCommentCountLabel(): String =
    if (this == 1) {
        "1 comment"
    } else {
        "$this comments"
    }
