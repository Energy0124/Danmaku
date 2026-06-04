package app.danmaku.library

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.resumePositionMs
import app.danmaku.domain.toPlaybackProgress

interface LanLibraryClient {
    fun fetchCatalog(
        baseUrl: String,
        pairingToken: String,
    ): LibraryCatalog

    fun streamUrl(
        baseUrl: String,
        item: LibraryMediaItem,
        pairingToken: String,
    ): String

    fun subtitleUrl(
        baseUrl: String,
        subtitle: LibrarySubtitleTrack,
        pairingToken: String,
    ): String

    fun fetchProgress(
        baseUrl: String,
        mediaId: String,
        pairingToken: String,
    ): PlaybackProgress?

    fun saveProgress(
        baseUrl: String,
        pairingToken: String,
        progress: PlaybackProgress,
    )
}

data class LanPlaybackTarget(
    val baseUrl: String,
    val pairingToken: String,
    val mediaId: String,
) {
    init {
        require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
        require(mediaId.isNotBlank()) { "mediaId must not be blank" }
    }
}

data class LanPlaybackPreparation(
    val item: LibraryMediaItem,
    val target: LanPlaybackTarget,
    val source: PlaybackSource.RemoteStream,
    val resumePositionMs: Long?,
)

class LanPlaybackPreparer(
    private val libraryClient: LanLibraryClient,
) {
    fun prepare(
        baseUrl: String,
        pairingToken: String,
        item: LibraryMediaItem,
    ): LanPlaybackPreparation {
        val target = LanPlaybackTarget(
            baseUrl = baseUrl,
            pairingToken = pairingToken,
            mediaId = item.id,
        )
        return LanPlaybackPreparation(
            item = item,
            target = target,
            source = PlaybackSource.RemoteStream(
                libraryClient.streamUrl(baseUrl, item, pairingToken),
            ),
            resumePositionMs = libraryClient
                .fetchProgress(baseUrl, item.id, pairingToken)
                ?.resumePositionMs(),
        )
    }
}

class LanPlaybackProgressSync(
    private val libraryClient: LanLibraryClient,
    private val currentTimeMillis: () -> Long,
) {
    fun fetchResumePositionMs(target: LanPlaybackTarget): Long? =
        libraryClient
            .fetchProgress(target.baseUrl, target.mediaId, target.pairingToken)
            ?.resumePositionMs()

    fun saveProgress(
        target: LanPlaybackTarget,
        snapshot: PlaybackSnapshot,
    ) {
        snapshot
            .toPlaybackProgress(target.mediaId, currentTimeMillis())
            ?.let {
                libraryClient.saveProgress(target.baseUrl, target.pairingToken, it)
            }
    }
}
