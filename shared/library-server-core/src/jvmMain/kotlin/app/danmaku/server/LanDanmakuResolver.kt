package app.danmaku.server

import app.danmaku.domain.LanDanmakuTrack
import java.nio.file.Path

fun interface LanDanmakuResolver {
    fun resolve(
        mediaId: String,
        mediaPath: Path,
        forceRefresh: Boolean,
    ): LanDanmakuTrack
}
