package app.danmaku.library

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LanDanmakuLoadStatus
import app.danmaku.domain.LanDanmakuTrack
import app.danmaku.domain.LanLibraryServerStatus
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
import app.danmaku.domain.PlaybackProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LanLibraryConnectionSessionTest {
    @Test
    fun fetchesCompatibleCatalogAndCatalogWideProgress() {
        val progress = PlaybackProgress(
            mediaId = "episode-id",
            positionMs = 12_345,
            durationMs = 98_765,
            updatedAtEpochMs = 456,
        )
        val client = RecordingLanLibraryClient(progresses = listOf(progress))

        val snapshot = LanLibraryConnectionSession(client).fetchCatalogWithProgress(
            baseUrl = "http://192.168.1.20:8686",
            pairingToken = "123456",
        )

        assertEquals(LanLibraryServerStatus(), snapshot.status)
        assertEquals(client.catalog, snapshot.catalog)
        assertEquals(listOf(progress), snapshot.playbackProgresses)
        assertEquals(1, client.statusFetches)
        assertEquals(1, client.catalogFetches)
        assertEquals(1, client.progressFetches)
    }

    @Test
    fun stopsBeforeCatalogFetchWhenServerIsIncompatible() {
        val client = RecordingLanLibraryClient(
            status = LanLibraryServerStatus(apiVersion = LanLibraryServerStatus.CURRENT_API_VERSION + 1),
        )

        val error = assertFailsWith<LanLibraryClientException> {
            LanLibraryConnectionSession(client).fetchCatalogWithProgress(
                baseUrl = "http://192.168.1.20:8686",
                pairingToken = "123456",
            )
        }

        assertEquals(
            "This Windows library server requires a newer Danmaku app. " +
                "Server API 2 is newer than supported API 1.",
            error.message,
        )
        assertEquals(1, client.statusFetches)
        assertEquals(0, client.catalogFetches)
        assertEquals(0, client.progressFetches)
    }

    @Test
    fun catalogOnlyFetchStillPreflightsServerCompatibility() {
        val client = RecordingLanLibraryClient()

        assertEquals(
            client.catalog,
            LanLibraryConnectionSession(client).fetchCatalog(
                baseUrl = "http://192.168.1.20:8686",
                pairingToken = "123456",
            ),
        )
        assertEquals(1, client.statusFetches)
        assertEquals(1, client.catalogFetches)
        assertEquals(0, client.progressFetches)
    }

    private class RecordingLanLibraryClient(
        private val status: LanLibraryServerStatus = LanLibraryServerStatus(),
        val catalog: LibraryCatalog = LibraryCatalog(
            rootName = "Example Library",
            indexedAtEpochMs = 123,
            items = listOf(
                LibraryMediaItem(
                    id = "episode-id",
                    seriesTitle = "Example Show",
                    episodeTitle = "Episode 01",
                    relativePath = "Example Show/Episode 01.mkv",
                    sizeBytes = 123,
                    mediaType = "video/x-matroska",
                    streamPath = "/media/episode-id",
                ),
            ),
        ),
        private val progresses: List<PlaybackProgress> = emptyList(),
    ) : LanLibraryClient {
        var statusFetches = 0
            private set
        var catalogFetches = 0
            private set
        var progressFetches = 0
            private set

        override fun fetchServerStatus(baseUrl: String): LanLibraryServerStatus {
            statusFetches += 1
            return status
        }

        override fun fetchCatalog(baseUrl: String, pairingToken: String): LibraryCatalog {
            catalogFetches += 1
            return catalog
        }

        override fun streamUrl(
            baseUrl: String,
            item: LibraryMediaItem,
            pairingToken: String,
        ): String =
            "$baseUrl${item.streamPath}"

        override fun subtitleUrl(
            baseUrl: String,
            subtitle: LibrarySubtitleTrack,
            pairingToken: String,
        ): String =
            "$baseUrl${subtitle.streamPath}"

        override fun fetchProgress(
            baseUrl: String,
            mediaId: String,
            pairingToken: String,
        ): PlaybackProgress? =
            progresses.firstOrNull { it.mediaId == mediaId }

        override fun fetchAllProgress(
            baseUrl: String,
            pairingToken: String,
        ): List<PlaybackProgress> {
            progressFetches += 1
            return progresses
        }

        override fun fetchDanmaku(
            baseUrl: String,
            mediaId: String,
            pairingToken: String,
            forceRefresh: Boolean,
        ): LanDanmakuTrack =
            LanDanmakuTrack(mediaId = mediaId, status = LanDanmakuLoadStatus.UNAVAILABLE)

        override fun saveProgress(
            baseUrl: String,
            pairingToken: String,
            progress: PlaybackProgress,
        ) = Unit
    }
}
