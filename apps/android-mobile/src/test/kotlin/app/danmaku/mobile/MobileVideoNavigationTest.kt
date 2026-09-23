package app.danmaku.mobile

import app.danmaku.domain.DanmakuDisplaySettings
import app.danmaku.domain.AuthorizedDownloadPolicy
import app.danmaku.domain.DownloadAsset
import app.danmaku.domain.DownloadAssetKind
import app.danmaku.domain.DownloadAuthorization
import app.danmaku.domain.DownloadDrmPolicy
import app.danmaku.domain.DownloadManifest
import app.danmaku.domain.OfflineStoragePolicy
import app.danmaku.domain.LanDanmakuTrack
import app.danmaku.domain.LanDanmakuLoadStatus
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.PlaybackStatus
import app.danmaku.domain.PlaybackPosition
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.LibraryWatchState
import app.danmaku.domain.watchStatusByMediaId
import app.danmaku.library.LanPlaybackTarget
import app.danmaku.library.android.OfflineCacheEntry
import app.danmaku.library.android.OfflineCacheState
import app.danmaku.library.android.OfflinePlaybackPreparation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MobileVideoNavigationTest {
    private val first = item("1")
    private val second = item("2")
    private val third = item("3")

    @Test
    fun folderWatchStateUpdatesWithoutReloadingCatalog() {
        val state = state()
        state.nowPlaying = second
        state.playbackStartupPhase = MobilePlaybackStartupPhase.Playing
        val snapshot = PlaybackSnapshot(
            source = PlaybackSource.RemoteStream("http://pc:8686/stream/2"),
            status = PlaybackStatus.PLAYING,
            position = PlaybackPosition(45_000, 120_000),
        )
        state.recordActivePlaybackProgress(snapshot)
        assertEquals(LibraryWatchState.IN_PROGRESS, state.catalog!!.watchStatusByMediaId(state.playbackProgresses)[second.id]?.state)
        state.recordActivePlaybackProgress(snapshot.copy(position = PlaybackPosition(115_000, 120_000)))
        assertEquals(LibraryWatchState.WATCHED, state.catalog!!.watchStatusByMediaId(state.playbackProgresses)[second.id]?.state)
        assertEquals(LibraryWatchState.NEW, state.catalog!!.watchStatusByMediaId(state.playbackProgresses)[first.id]?.state)
        state.serverUrl = "http://another-pc:8686"
        state.recordActivePlaybackProgress(snapshot)
        assertEquals(115_000L, state.playbackProgresses.single().positionMs)
    }

    @Test
    fun followsCatalogOrderAndStopsAtBothEnds() {
        val state = state()
        state.nowPlaying = second
        assertEquals(first, state.adjacentVideo(-1))
        assertEquals(third, state.adjacentVideo(1))
        state.nowPlaying = first
        assertNull(state.adjacentVideo(-1))
        state.nowPlaying = third
        assertNull(state.adjacentVideo(1))
    }

    @Test
    fun doesNotNavigateAnotherServerOrAnUnlistedVideo() {
        val state = state()
        state.nowPlaying = item("missing")
        assertNull(state.adjacentVideo(1))
        state.nowPlaying = second
        state.serverUrl = "http://another-pc:8686"
        assertNull(state.adjacentVideo(-1))
        assertNull(state.adjacentVideo(1))
    }

    @Test
    fun localFileHasNoLibraryNavigation() {
        val state = state()
        state.activePlaybackTarget = null
        state.nowPlaying = null
        assertNull(state.adjacentVideo(-1))
        assertNull(state.adjacentVideo(1))
    }

    @Test
    fun navigationIsDisabledWhilePreparingTheNextVideo() {
        val state = state().apply {
            nowPlaying = second
            playbackStartupPhase = MobilePlaybackStartupPhase.WaitingForDanmaku
        }
        assertNull(state.adjacentVideo(-1))
        assertNull(state.adjacentVideo(1))
    }

    @Test
    fun cachedNavigationUsesOnlyReadyVideosFromTheSamePcInPathOrder() {
        val state = state().apply {
            nowPlaying = second
            activePlaybackTarget = null
            activeOfflineCacheKey = "cached-2"
            catalog = null
            cacheEntries = listOf(
                cached(third),
                cached(item("other"), server = "http://other-pc"),
                cached(first, cacheState = OfflineCacheState.DOWNLOADING),
                cached(second),
                cached(item("4"), cacheState = OfflineCacheState.FAILED),
            )
        }
        assertNull(state.adjacentVideo(-1))
        assertEquals(third, state.adjacentVideo(1))
        state.nowPlaying = third
        state.activeOfflineCacheKey = "cached-3"
        assertEquals(second, state.adjacentVideo(-1))
        assertNull(state.adjacentVideo(1))
    }

    @Test
    fun previewIgnoresStartupAndMismatchedMediaThenAcceptsCachedProgress() {
        val state = state().apply { nowPlaying = second }
        val snapshot = PlaybackSnapshot(
            source = PlaybackSource.LocalFile("file:///cache/2.mkv"),
            status = PlaybackStatus.PAUSED,
            position = PlaybackPosition(45_000, 120_000),
        )
        state.playbackStartupPhase = MobilePlaybackStartupPhase.WaitingForDanmaku
        state.recordActivePlaybackProgress(snapshot)
        assertEquals(emptyList<PlaybackProgress>(), state.playbackProgresses)
        state.playbackStartupPhase = MobilePlaybackStartupPhase.Playing
        state.activePlaybackTarget = LanPlaybackTarget(state.serverUrl, first.id)
        state.recordActivePlaybackProgress(snapshot)
        assertEquals(emptyList<PlaybackProgress>(), state.playbackProgresses)

        state.activePlaybackTarget = null
        state.cacheEntries = listOf(cached(second))
        state.activeOfflineCacheKey = "cached-2"
        state.recordActivePlaybackProgress(snapshot)
        assertEquals(45_000L, state.playbackProgresses.single().positionMs)
        state.serverUrl = "http://other-pc"
        state.recordActivePlaybackProgress(snapshot.copy(position = PlaybackPosition(60_000, 120_000)))
        assertEquals(45_000L, state.playbackProgresses.single().positionMs)
    }

    @Test
    fun cachedResumeFallbackUsesOnlyResumableProgressFromItsPc() {
        val state = state()
        val preparation = OfflinePlaybackPreparation(
            cacheKey = "cached-2",
            serverUrl = state.serverUrl,
            item = second,
            source = PlaybackSource.LocalFile("file:///cache/2.mkv"),
            subtitles = emptyList(),
            danmaku = LanDanmakuTrack(second.id, LanDanmakuLoadStatus.NO_MATCH),
            resumePositionMs = null,
        )
        val progress = PlaybackProgress(second.id, 45_000, 120_000, 1)
        state.playbackProgresses = listOf(progress)
        assertEquals(45_000L, state.cachedResumePositionMs(preparation))
        state.playbackProgresses = listOf(progress.copy(positionMs = 119_000))
        assertNull(state.cachedResumePositionMs(preparation))
        state.playbackProgresses = listOf(progress.copy(positionMs = 5_000))
        assertNull(state.cachedResumePositionMs(preparation))
        state.playbackProgresses = listOf(progress)
        state.serverUrl = "http://other-pc"
        assertNull(state.cachedResumePositionMs(preparation))
        assertEquals(60_000L, state.cachedResumePositionMs(preparation.copy(resumePositionMs = 60_000)))
    }

    private fun state() = MobilePlayerState(emptyList(), emptySet(), DanmakuDisplaySettings()).apply {
        serverUrl = "http://pc:8686"
        catalog = LibraryCatalog("Library", 0, listOf(first, second, third))
        activePlaybackTarget = LanPlaybackTarget(serverUrl, second.id)
        playbackStartupPhase = MobilePlaybackStartupPhase.Playing
    }

    private fun cached(
        item: LibraryMediaItem,
        server: String = "http://pc:8686",
        cacheState: OfflineCacheState = OfflineCacheState.READY,
    ) = OfflineCacheEntry(
        key = "cached-${item.id}",
        serverUrl = server,
        item = item,
        state = cacheState,
        manifest = DownloadManifest(
            id = item.id,
            sourceId = server,
            title = item.episodeTitle,
            assets = listOf(
                DownloadAsset(item.id, DownloadAssetKind.MEDIA, server + item.streamPath, "video.mkv", item.mediaType),
            ),
            policy = AuthorizedDownloadPolicy(
                OfflineStoragePolicy.ALLOWED_WITHOUT_EXPIRY,
                DownloadAuthorization.USER_OWNED_LOCAL_FILE,
                DownloadDrmPolicy.DRM_FREE,
            ),
            requestedAtEpochMs = 0,
        ),
    )

    private fun item(id: String) = LibraryMediaItem(
        id = id,
        seriesTitle = "Series",
        episodeTitle = "Episode $id",
        relativePath = "$id.mkv",
        sizeBytes = 1,
        mediaType = "video/x-matroska",
        streamPath = "/stream/$id",
    )
}
