package app.danmaku.desktop

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.absolutePathString

class DesktopDandanplayDanmakuResolver(
    private val loadConnection: () -> DandanplayConnection,
    private val fetchTrack: (DandanplayConnection, DandanplayMediaFingerprint) -> DandanplayCommentTrack? = { connection, fingerprint ->
        DandanplayDanmakuClient(connection).fetchBestMatchComments(fingerprint)
    },
    private val cacheDirectory: Path = defaultCacheDirectory(),
) {
    fun resolve(
        mediaId: String,
        mediaPath: Path,
    ): DesktopDandanplayDanmakuResolution {
        require(mediaId.isNotBlank()) { "mediaId must not be blank" }
        val fingerprint = DandanplayMediaFingerprint.fromPath(mediaPath)
        val track = fetchTrack(loadConnection(), fingerprint)
            ?: return DesktopDandanplayDanmakuResolution(
                fingerprint = fingerprint,
                match = null,
                eventCount = 0,
                subtitle = null,
                cachePath = null,
            )
        if (track.events.isEmpty()) {
            return DesktopDandanplayDanmakuResolution(
                fingerprint = fingerprint,
                match = track.match,
                eventCount = 0,
                subtitle = null,
                cachePath = null,
            )
        }

        Files.createDirectories(cacheDirectory)
        val cachePath = cacheDirectory.resolve(cacheFileName(mediaId, fingerprint.normalizedFileHash))
        Files.writeString(
            cachePath,
            SyntheticDanmakuAssRenderer.render(track.events),
            StandardCharsets.UTF_8,
        )
        return DesktopDandanplayDanmakuResolution(
            fingerprint = fingerprint,
            match = track.match,
            eventCount = track.events.size,
            subtitle = DesktopPlaybackSubtitle(
                source = cachePath.toAbsolutePath().normalize().absolutePathString(),
                label = "dandanplay: ${track.match.displayTitle}",
                isDanmakuOverlay = true,
            ),
            cachePath = cachePath,
        )
    }

    private companion object {
        fun defaultCacheDirectory(): Path {
            val localAppData = System.getenv("LOCALAPPDATA")
            if (!localAppData.isNullOrBlank()) {
                return Path.of(localAppData).resolve("Danmaku").resolve("danmaku-cache").resolve("dandanplay")
            }
            return Path.of(System.getProperty("java.io.tmpdir")).resolve("Danmaku").resolve("danmaku-cache").resolve("dandanplay")
        }

        fun cacheFileName(
            mediaId: String,
            fileHash: String,
        ): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$mediaId:$fileHash".toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
            return "$digest.ass"
        }
    }
}

data class DesktopDandanplayDanmakuResolution(
    val fingerprint: DandanplayMediaFingerprint,
    val match: DandanplayMatch?,
    val eventCount: Int,
    val subtitle: DesktopPlaybackSubtitle?,
    val cachePath: Path?,
)
