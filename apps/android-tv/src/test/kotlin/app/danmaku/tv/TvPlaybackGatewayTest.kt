package app.danmaku.tv

import app.danmaku.domain.LanDanmakuTrack
import app.danmaku.domain.LanLibraryServerStatus
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
import app.danmaku.domain.PlaybackProgress
import app.danmaku.library.LanDanmakuLoader
import app.danmaku.library.LanLibraryClient
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.LanPlaybackTarget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TvPlaybackGatewayTest {
    private val item = LibraryMediaItem(
        id = "second",
        seriesTitle = "Series",
        episodeTitle = "Episode 2",
        relativePath = "Series/02.mkv",
        sizeBytes = 1,
        mediaType = "video/x-matroska",
        streamPath = "/media/second",
    )
    private val target = LanPlaybackTarget("http://pc", item.id)

    @Test
    fun preparationUsesTheSelectedFilesServerProgressAndSharedResumePolicy() = runBlocking {
        val client = ProgressClient()
        val gateway = gateway(client)
        val cases = listOf(
            null to null,
            progress(5_000) to null,
            progress(42_000) to 42_000L,
            progress(115_000) to null,
            progress(42_000).copy(durationMs = null) to 42_000L,
        )
        for ((saved, expected) in cases) {
            client.progress = saved
            var lookupFailed = false
            val preparation = gateway.prepare(target, item) { lookupFailed = true }

            assertEquals(target, client.requestedTarget)
            assertEquals(target, preparation.target)
            assertEquals(item, preparation.item)
            assertEquals(expected, preparation.resumePositionMs)
            assertEquals("http://pc/media/second", preparation.source.url)
            assertFalse(lookupFailed)
        }
    }

    @Test
    fun failedResumeLookupReportsTheFailureAndStillPreparesTheSelectedFile() = runBlocking {
        val failure = IllegalStateException("Unavailable")
        val client = ProgressClient().apply { lookupFailure = failure }
        var reported: Throwable? = null

        val preparation = gateway(client).prepare(target, item) { reported = it }

        assertEquals(failure.javaClass, reported?.javaClass)
        assertEquals(failure.message, reported?.message)
        assertEquals(item, preparation.item)
        assertEquals(null, preparation.resumePositionMs)
    }

    private fun progress(positionMs: Long) = PlaybackProgress(item.id, positionMs, 120_000, 1)

    private fun gateway(client: LanLibraryClient) = LanTvPlaybackGateway(
        progressSync = LanPlaybackProgressSync(client) { 1 },
        playbackPreparer = LanPlaybackPreparer(client),
        danmakuLoader = LanDanmakuLoader(client),
    )

    private class ProgressClient : LanLibraryClient {
        var progress: PlaybackProgress? = null
        var lookupFailure: Throwable? = null
        var requestedTarget: LanPlaybackTarget? = null

        override fun fetchProgress(baseUrl: String, mediaId: String): PlaybackProgress? {
            requestedTarget = LanPlaybackTarget(baseUrl, mediaId)
            lookupFailure?.let { throw it }
            return progress
        }

        override fun streamUrl(baseUrl: String, item: LibraryMediaItem) = baseUrl + item.streamPath
        override fun subtitleUrl(baseUrl: String, subtitle: LibrarySubtitleTrack) = baseUrl + subtitle.streamPath
        override fun fetchServerStatus(baseUrl: String): LanLibraryServerStatus = error("Unused")
        override fun fetchCatalog(baseUrl: String): LibraryCatalog = error("Unused")
        override fun requestFolderRescan(baseUrl: String, path: List<String>): Unit = error("Unused")
        override fun fetchAllProgress(baseUrl: String): List<PlaybackProgress> = error("Unused")
        override fun fetchDanmaku(baseUrl: String, mediaId: String, forceRefresh: Boolean): LanDanmakuTrack =
            error("Unused")
        override fun saveProgress(baseUrl: String, progress: PlaybackProgress): Unit = error("Unused")
    }
}
