package app.danmaku.desktop

import app.danmaku.domain.DanmakuEvent
import app.danmaku.domain.DanmakuDisplaySettings
import app.danmaku.domain.DanmakuMode
import app.danmaku.domain.DanmakuSize
import app.danmaku.domain.LocalDanmakuParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.absolutePathString

class DesktopDandanplayDanmakuResolver(
    private val loadConnection: () -> DandanplayConnection,
    private val cacheMaxAgeDays: () -> Int = { DEFAULT_CACHE_MAX_AGE_DAYS },
    private val danmakuSettings: () -> DanmakuDisplaySettings = { DanmakuDisplaySettings() },
    private val cacheStore: DandanplayCommentCacheStore? = null,
    private val fetchMatches: (DandanplayConnection, DandanplayMediaFingerprint) -> List<DandanplayMatch> = { _, _ ->
        directProviderAccessRemoved()
    },
    private val fetchComments: (DandanplayConnection, DandanplayMatch) -> List<DanmakuEvent> = { _, _ ->
        directProviderAccessRemoved()
    },
    private val cacheDirectory: Path = defaultCacheDirectory(),
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    fun resolve(
        mediaId: String,
        mediaPath: Path,
        forceRefresh: Boolean = false,
        preferredEpisodeId: Long? = null,
    ): DesktopDandanplayDanmakuResolution {
        require(mediaId.isNotBlank()) { "mediaId must not be blank" }
        require(preferredEpisodeId == null || preferredEpisodeId > 0) { "preferredEpisodeId must be positive" }
        val fingerprint = DandanplayMediaFingerprint.fromPath(mediaPath)
        if (!forceRefresh && preferredEpisodeId == null) {
            resolveCached(mediaId, mediaPath, fingerprint)?.let { return it }
        }

        val connection = loadConnection()
        val matches = fetchMatches(connection, fingerprint)
        val match = preferredEpisodeId
            ?.let { episodeId -> matches.firstOrNull { it.episodeId == episodeId } }
            ?: matches.firstOrNull()
        val track = match?.let { selectedMatch ->
            DandanplayCommentTrack(
                match = selectedMatch,
                events = fetchComments(connection, selectedMatch),
            )
        }
            ?: return DesktopDandanplayDanmakuResolution(
                fingerprint = fingerprint,
                match = null,
                matchCandidates = matches,
                eventCount = 0,
                subtitle = null,
                cachePath = null,
                source = DesktopDandanplayResolutionSource.NETWORK,
            )
        return renderResolution(
            mediaId = mediaId,
            fingerprint = fingerprint,
            track = track,
            matchCandidates = matches,
            source = DesktopDandanplayResolutionSource.NETWORK,
            shouldPersist = true,
        )
    }

    fun resolveCached(
        mediaId: String,
        mediaPath: Path,
    ): DesktopDandanplayDanmakuResolution? {
        require(mediaId.isNotBlank()) { "mediaId must not be blank" }
        return resolveCached(mediaId, mediaPath, DandanplayMediaFingerprint.fromPath(mediaPath))
    }

    fun clearCache(mediaId: String) {
        require(mediaId.isNotBlank()) { "mediaId must not be blank" }
        cacheStore?.deleteDandanplayCommentCache(mediaId)
    }

    fun cleanupExpiredCaches() {
        val maxAgeDays = cacheMaxAgeDays()
        require(maxAgeDays > 0) { "cache max age must be positive" }
        val cutoff = nowEpochMs() - maxAgeDays * MILLIS_PER_DAY
        cacheStore?.deleteDandanplayCommentCachesOlderThan(cutoff.coerceAtLeast(0))
    }

    private fun renderResolution(
        mediaId: String,
        fingerprint: DandanplayMediaFingerprint,
        track: DandanplayCommentTrack,
        matchCandidates: List<DandanplayMatch>,
        source: DesktopDandanplayResolutionSource,
        shouldPersist: Boolean,
    ): DesktopDandanplayDanmakuResolution {
        if (track.events.isEmpty()) {
            if (shouldPersist) {
                cacheStore?.saveDandanplayCommentCache(track.toCache(mediaId, fingerprint, null, nowEpochMs()))
            }
            return DesktopDandanplayDanmakuResolution(
                fingerprint = fingerprint,
                match = track.match,
                matchCandidates = matchCandidates,
                eventCount = 0,
                subtitle = null,
                cachePath = null,
                source = source,
            )
        }

        Files.createDirectories(cacheDirectory)
        val cachePath = cacheDirectory.resolve(cacheFileName(mediaId, fingerprint.normalizedFileHash))
        Files.writeString(
            cachePath,
            SyntheticDanmakuAssRenderer.render(track.events, danmakuSettings()),
            StandardCharsets.UTF_8,
        )
        val resolution = DesktopDandanplayDanmakuResolution(
            fingerprint = fingerprint,
            match = track.match,
            matchCandidates = matchCandidates,
            eventCount = track.events.size,
            subtitle = DesktopPlaybackSubtitle(
                source = cachePath.toAbsolutePath().normalize().absolutePathString(),
                label = "dandanplay: ${track.match.displayTitle}",
                isDanmakuOverlay = true,
            ),
            cachePath = cachePath,
            source = source,
            events = track.events,
        )
        if (shouldPersist) {
            cacheStore?.saveDandanplayCommentCache(track.toCache(mediaId, fingerprint, cachePath, nowEpochMs()))
        }
        return resolution
    }

    private fun resolveCached(
        mediaId: String,
        mediaPath: Path,
        fingerprint: DandanplayMediaFingerprint,
    ): DesktopDandanplayDanmakuResolution? {
        require(Files.isRegularFile(mediaPath)) { "mediaPath must be a regular file" }
        return cacheStore
            ?.loadDandanplayCommentCache(mediaId)
            ?.takeIf { it.fileHash.equals(fingerprint.normalizedFileHash, ignoreCase = true) }
            ?.takeIf { it.fileSizeBytes == fingerprint.fileSizeBytes }
            ?.takeUnless { it.isExpired(nowEpochMs(), cacheMaxAgeDays()) }
            ?.toCommentTrack()
            ?.let { cachedTrack ->
                renderResolution(
                    mediaId = mediaId,
                    fingerprint = fingerprint,
                    track = cachedTrack,
                    matchCandidates = listOf(cachedTrack.match),
                    source = DesktopDandanplayResolutionSource.CACHE,
                    shouldPersist = false,
                )
            }
    }

    private companion object {
        const val DEFAULT_CACHE_MAX_AGE_DAYS = 30
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L

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
    val matchCandidates: List<DandanplayMatch> = emptyList(),
    val eventCount: Int,
    val subtitle: DesktopPlaybackSubtitle?,
    val cachePath: Path?,
    val source: DesktopDandanplayResolutionSource,
    val events: List<DanmakuEvent> = emptyList(),
)

enum class DesktopDandanplayResolutionSource {
    CACHE,
    NETWORK,
}

interface DandanplayCommentCacheStore {
    fun loadDandanplayCommentCache(mediaId: String): DesktopDandanplayCommentCache?

    fun saveDandanplayCommentCache(cache: DesktopDandanplayCommentCache)

    fun deleteDandanplayCommentCache(mediaId: String)

    fun deleteDandanplayCommentCachesOlderThan(cutoffEpochMs: Long)
}

private fun DandanplayCommentTrack.toCache(
    mediaId: String,
    fingerprint: DandanplayMediaFingerprint,
    cachePath: Path?,
    fetchedAtEpochMs: Long,
): DesktopDandanplayCommentCache =
    DesktopDandanplayCommentCache(
        mediaId = mediaId,
        fileHash = fingerprint.normalizedFileHash,
        fileName = fingerprint.fileName,
        fileSizeBytes = fingerprint.fileSizeBytes,
        episodeId = match.episodeId,
        animeId = match.animeId,
        animeTitle = match.animeTitle,
        episodeTitle = match.episodeTitle,
        shiftSeconds = match.shiftSeconds,
        commentsJson = events.toNormalizedCommentsJson(),
        renderedAssPath = cachePath?.toAbsolutePath()?.normalize()?.absolutePathString(),
        fetchedAtEpochMs = fetchedAtEpochMs,
    )

private fun DesktopDandanplayCommentCache.toCommentTrack(): DandanplayCommentTrack? {
    val episodeId = episodeId ?: return null
    return DandanplayCommentTrack(
        match = DandanplayMatch(
            episodeId = episodeId,
            animeId = animeId,
            animeTitle = animeTitle,
            episodeTitle = episodeTitle,
            shiftSeconds = shiftSeconds,
        ),
        events = LocalDanmakuParser.parseNormalizedJson(commentsJson),
    )
}

private fun DesktopDandanplayCommentCache.isExpired(
    nowEpochMs: Long,
    maxAgeDays: Int,
): Boolean =
    nowEpochMs - fetchedAtEpochMs > maxAgeDays * 24L * 60L * 60L * 1_000L

private fun List<DanmakuEvent>.toNormalizedCommentsJson(): String =
    buildJsonObject {
        putJsonArray("events") {
            this@toNormalizedCommentsJson.forEach { event ->
                add(
                    buildJsonObject {
                        put("id", event.id)
                        put("timestampMs", event.timestampMs)
                        put("text", event.text)
                        putJsonObject("style") {
                            put("colorArgb", event.style.colorArgb.toString())
                            put("mode", event.style.mode.toNormalizedValue())
                            put("size", event.style.size.toNormalizedValue())
                        }
                    },
                )
            }
        }
    }.toString()

private fun DanmakuMode.toNormalizedValue(): String =
    name.lowercase()

private fun DanmakuSize.toNormalizedValue(): String =
    name.lowercase()
