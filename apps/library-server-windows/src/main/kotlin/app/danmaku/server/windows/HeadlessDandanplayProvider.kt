package app.danmaku.server.windows

import app.danmaku.domain.DanmakuEvent
import app.danmaku.domain.LanDanmakuLoadStatus
import app.danmaku.domain.LanDanmakuSource
import app.danmaku.domain.LanDanmakuTrack
import app.danmaku.domain.toLanDanmakuComment
import app.danmaku.provider.dandanplay.DandanplayCommentTrack
import app.danmaku.provider.dandanplay.DandanplayConnection
import app.danmaku.provider.dandanplay.DandanplayDanmakuClient
import app.danmaku.provider.dandanplay.DandanplayMatch
import app.danmaku.provider.dandanplay.DandanplayMediaFingerprint
import app.danmaku.provider.dandanplay.DandanplayAuthenticationMode as ProviderDandanplayAuthenticationMode
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.nio.file.Path

internal interface HeadlessDandanplayProviderService {
    fun resolve(
        mediaPath: Path,
        preferredEpisodeId: Long?,
        withRelated: Boolean,
    ): HeadlessDandanplayResolveResult
}

internal data class HeadlessDandanplayResolveResult(
    val fingerprint: DandanplayMediaFingerprint,
    val matchCandidates: List<DandanplayMatch>,
    val selectedTrack: DandanplayCommentTrack?,
)

internal class DefaultHeadlessDandanplayProviderService(
    private val client: DandanplayDanmakuClient,
) : HeadlessDandanplayProviderService {
    override fun resolve(
        mediaPath: Path,
        preferredEpisodeId: Long?,
        withRelated: Boolean,
    ): HeadlessDandanplayResolveResult {
        val fingerprint = DandanplayMediaFingerprint.fromPath(mediaPath)
        val matches = client.match(fingerprint)
        val selectedMatch = preferredEpisodeId
            ?.let { episodeId -> matches.firstOrNull { match -> match.episodeId == episodeId } }
            ?: matches.firstOrNull()
        val track = selectedMatch?.let { match ->
            DandanplayCommentTrack(
                match = match,
                events = client.fetchComments(match.episodeId, withRelated),
            )
        }
        return HeadlessDandanplayResolveResult(
            fingerprint = fingerprint,
            matchCandidates = matches,
            selectedTrack = track,
        )
    }
}

internal fun HeadlessServerSettings.toDandanplayProviderService(): HeadlessDandanplayProviderService =
    DefaultHeadlessDandanplayProviderService(
        DandanplayDanmakuClient(dandanplay.toDandanplayConnection()),
    )

internal fun HeadlessDandanplayResolveResult.toJsonObject(mediaId: String) =
    buildJsonObject {
        put("mediaId", mediaId)
        putJsonObject("fingerprint") {
            put("fileName", fingerprint.fileName)
            put("fileHash", fingerprint.normalizedFileHash)
            put("fileSizeBytes", fingerprint.fileSizeBytes)
            fingerprint.videoDurationSeconds?.let { duration -> put("videoDurationSeconds", duration) }
        }
        put(
            "matches",
            buildJsonArray {
                matchCandidates.forEach { match -> add(match.toJsonObject()) }
            },
        )
        put("selectedMatch", selectedTrack?.match?.toJsonObject() ?: JsonNull)
        put("commentCount", selectedTrack?.events?.size ?: 0)
        put(
            "comments",
            buildJsonArray {
                selectedTrack?.events.orEmpty().forEach { event -> add(event.toJsonObject()) }
            },
        )
    }

internal fun HeadlessDandanplayResolveResult.toLanDanmakuTrack(mediaId: String): LanDanmakuTrack {
    val track = selectedTrack
    val events = track?.events.orEmpty()
    return LanDanmakuTrack(
        mediaId = mediaId,
        status = when {
            events.isNotEmpty() -> LanDanmakuLoadStatus.READY
            track == null -> LanDanmakuLoadStatus.NO_MATCH
            else -> LanDanmakuLoadStatus.NO_MATCH
        },
        source = LanDanmakuSource.NETWORK,
        comments = events.map { it.toLanDanmakuComment() },
        matchTitle = track?.match?.displayTitle,
        episodeId = track?.match?.episodeId,
        fetchedAtEpochMs = System.currentTimeMillis(),
        message = when {
            events.isNotEmpty() -> null
            track == null -> "No Dandanplay match found."
            else -> "Dandanplay match has no comments."
        },
    )
}

private fun HeadlessDandanplayProviderSettings.toDandanplayConnection(): DandanplayConnection =
    if (appId != null && appSecret != null) {
        DandanplayConnection(
            baseUrl = baseUrl,
            appId = appId,
            appSecret = appSecret,
            authenticationMode = authenticationMode.toProviderMode(),
        )
    } else {
        DandanplayConnection(baseUrl)
    }

private fun HeadlessDandanplayAuthenticationMode.toProviderMode(): ProviderDandanplayAuthenticationMode =
    when (this) {
        HeadlessDandanplayAuthenticationMode.SIGNED -> ProviderDandanplayAuthenticationMode.SIGNED
        HeadlessDandanplayAuthenticationMode.CREDENTIAL -> ProviderDandanplayAuthenticationMode.CREDENTIAL
    }

private fun DandanplayMatch.toJsonObject() =
    buildJsonObject {
        put("episodeId", episodeId)
        animeId?.let { put("animeId", it) }
        animeTitle?.let { put("animeTitle", it) }
        episodeTitle?.let { put("episodeTitle", it) }
        shiftSeconds?.let { put("shiftSeconds", it) }
        put("displayTitle", displayTitle)
    }

private fun DanmakuEvent.toJsonObject() =
    buildJsonObject {
        put("id", id)
        put("timestampMs", timestampMs)
        put("text", text)
        putJsonObject("style") {
            put("colorArgb", style.colorArgb.toString())
            put("mode", style.mode.name)
            put("size", style.size.name)
        }
    }
