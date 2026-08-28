package app.danmaku.library

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LanDanmakuTrack
import app.danmaku.domain.LanLibraryServerStatus
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.compatibilityErrorMessage
import app.danmaku.domain.resumePositionMs
import app.danmaku.domain.toPlaybackProgress

interface LanLibraryClient {
    fun fetchServerStatus(baseUrl: String): LanLibraryServerStatus

    fun fetchCatalog(baseUrl: String): LibraryCatalog

    fun requestFolderRescan(
        baseUrl: String,
        path: List<String>,
    )

    fun streamUrl(baseUrl: String, item: LibraryMediaItem): String

    fun subtitleUrl(baseUrl: String, subtitle: LibrarySubtitleTrack): String

    fun fetchProgress(
        baseUrl: String,
        mediaId: String,
    ): PlaybackProgress?

    fun fetchAllProgress(baseUrl: String): List<PlaybackProgress>

    fun fetchDanmaku(
        baseUrl: String,
        mediaId: String,
        forceRefresh: Boolean = false,
    ): LanDanmakuTrack

    fun saveProgress(
        baseUrl: String,
        progress: PlaybackProgress,
    )
}

class LanLibraryClientException(
    message: String,
    cause: Throwable? = null,
    val statusCode: Int? = null,
) : RuntimeException(message, cause)

data class LanPlaybackTarget(
    val baseUrl: String,
    val mediaId: String,
) {
    init {
        require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
        require(mediaId.isNotBlank()) { "mediaId must not be blank" }
    }
}

data class LanLibraryConnectionProfile(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val lastConnectedAtEpochMs: Long? = null,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
        lastConnectedAtEpochMs?.let {
            require(it >= 0) { "lastConnectedAtEpochMs must not be negative" }
        }
    }

    val normalizedBaseUrl: String =
        baseUrl.trim().trimEnd('/')
}

fun lanLibraryConnectionProfile(
    baseUrl: String,
    displayName: String? = null,
    lastConnectedAtEpochMs: Long? = null,
): LanLibraryConnectionProfile {
    val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
    require(normalizedBaseUrl.isNotBlank()) { "baseUrl must not be blank" }
    val normalizedName = displayName
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: normalizedBaseUrl.removePrefix("http://")
            .removePrefix("https://")
            .ifBlank { "Windows PC" }
    return LanLibraryConnectionProfile(
        id = normalizedBaseUrl,
        displayName = normalizedName,
        baseUrl = normalizedBaseUrl,
        lastConnectedAtEpochMs = lastConnectedAtEpochMs,
    )
}

data class LanPlaybackPreparation(
    val item: LibraryMediaItem,
    val target: LanPlaybackTarget,
    val source: PlaybackSource.RemoteStream,
    val resumePositionMs: Long?,
    val subtitles: List<LanSubtitlePreparation> = emptyList(),
)

data class LanSubtitlePreparation(
    val track: LibrarySubtitleTrack,
    val source: PlaybackSource.RemoteStream,
)

class LanPlaybackPreparer(
    private val libraryClient: LanLibraryClient,
) {
    fun prepare(
        baseUrl: String,
        item: LibraryMediaItem,
    ): LanPlaybackPreparation =
        prepare(
            baseUrl = baseUrl,
            item = item,
            resumePositionMs = libraryClient
                .fetchProgress(baseUrl, item.id)
                ?.resumePositionMs(),
        )

    fun prepare(
        baseUrl: String,
        item: LibraryMediaItem,
        resumePositionMs: Long?,
    ): LanPlaybackPreparation {
        val target = LanPlaybackTarget(
            baseUrl = baseUrl,
            mediaId = item.id,
        )
        return LanPlaybackPreparation(
            item = item,
            target = target,
            source = PlaybackSource.RemoteStream(
                libraryClient.streamUrl(baseUrl, item),
            ),
            subtitles = item.subtitles.map { subtitle ->
                LanSubtitlePreparation(
                    track = subtitle,
                    source = PlaybackSource.RemoteStream(
                        libraryClient.subtitleUrl(baseUrl, subtitle),
                    ),
                )
            },
            resumePositionMs = resumePositionMs,
        )
    }
}

class LanDanmakuLoader(
    private val libraryClient: LanLibraryClient,
) {
    fun fetchDanmaku(
        target: LanPlaybackTarget,
        forceRefresh: Boolean = false,
    ): LanDanmakuTrack =
        libraryClient.fetchDanmaku(
            baseUrl = target.baseUrl,
            mediaId = target.mediaId,
            forceRefresh = forceRefresh,
        )
}

class LanPlaybackProgressSync(
    private val libraryClient: LanLibraryClient,
    private val currentTimeMillis: () -> Long,
) {
    fun fetchResumePositionMs(target: LanPlaybackTarget): Long? =
        libraryClient
            .fetchProgress(target.baseUrl, target.mediaId)
            ?.resumePositionMs()

    fun fetchAllProgress(baseUrl: String): List<PlaybackProgress> =
        libraryClient.fetchAllProgress(baseUrl)

    fun saveProgress(
        target: LanPlaybackTarget,
        snapshot: PlaybackSnapshot,
    ): PlaybackProgress? =
        snapshot
            .toPlaybackProgress(target.mediaId, currentTimeMillis())
            ?.takeIf { it.positionMs > 0 }
            ?.also {
                libraryClient.saveProgress(target.baseUrl, it)
            }
}

data class LanLibraryConnectionSnapshot(
    val status: LanLibraryServerStatus,
    val catalog: LibraryCatalog,
    val playbackProgresses: List<PlaybackProgress> = emptyList(),
)

class LanLibraryConnectionSession(
    private val libraryClient: LanLibraryClient,
) {
    fun fetchCatalog(baseUrl: String): LibraryCatalog {
        validateServer(baseUrl)
        return libraryClient.fetchCatalog(baseUrl)
    }

    fun requestFolderRescan(
        baseUrl: String,
        path: List<String>,
    ) {
        libraryClient.requestFolderRescan(baseUrl, path)
    }

    fun fetchCatalogWithProgress(baseUrl: String): LanLibraryConnectionSnapshot {
        val status = validateServer(baseUrl)
        val catalog = libraryClient.fetchCatalog(baseUrl)
        val progress = runCatching {
            libraryClient.fetchAllProgress(baseUrl)
        }.getOrDefault(emptyList())
        return LanLibraryConnectionSnapshot(
            status = status,
            catalog = catalog,
            playbackProgresses = progress,
        )
    }

    fun validateServer(baseUrl: String): LanLibraryServerStatus {
        val status = libraryClient.fetchServerStatus(baseUrl)
        status.compatibilityErrorMessage()?.let {
            throw LanLibraryClientException(it)
        }
        return status
    }
}
