package app.danmaku.desktop

import app.danmaku.domain.LanDanmakuLoadStatus
import app.danmaku.domain.LanDanmakuSource
import app.danmaku.domain.LanDanmakuTrack
import app.danmaku.domain.toLanDanmakuComment
import app.danmaku.server.LanDanmakuResolver
import java.nio.file.Path

internal class DesktopLanDanmakuResolver(
    private val resolver: DesktopDandanplayDanmakuResolver,
) : LanDanmakuResolver {
    override fun resolve(
        mediaId: String,
        mediaPath: Path,
        forceRefresh: Boolean,
    ): LanDanmakuTrack {
        val resolution = resolver.resolve(
            mediaId = mediaId,
            mediaPath = mediaPath,
            forceRefresh = forceRefresh,
        )
        val match = resolution.match
        return LanDanmakuTrack(
            mediaId = mediaId,
            status = when {
                resolution.events.isNotEmpty() -> LanDanmakuLoadStatus.READY
                match == null -> LanDanmakuLoadStatus.NO_MATCH
                else -> LanDanmakuLoadStatus.NO_MATCH
            },
            source = resolution.source.toLanDanmakuSource(),
            comments = resolution.events.map { it.toLanDanmakuComment() },
            matchTitle = match?.displayTitle,
            episodeId = match?.episodeId,
            message = when {
                resolution.events.isNotEmpty() -> null
                match == null -> "No Dandanplay match found."
                else -> "Dandanplay match has no comments."
            },
        )
    }
}

private fun DesktopDandanplayResolutionSource.toLanDanmakuSource(): LanDanmakuSource =
    when (this) {
        DesktopDandanplayResolutionSource.CACHE -> LanDanmakuSource.CACHE
        DesktopDandanplayResolutionSource.NETWORK -> LanDanmakuSource.NETWORK
    }
