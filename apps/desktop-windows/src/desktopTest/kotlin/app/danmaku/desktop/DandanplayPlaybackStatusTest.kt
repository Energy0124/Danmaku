package app.danmaku.desktop

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class DandanplayPlaybackStatusTest {
    @Test
    fun describesAttachedNetworkTrackWithMatchDetails() {
        val status = dandanplayStatusFromResolution(
            mediaId = "media-1",
            resolution = DesktopDandanplayDanmakuResolution(
                fingerprint = DandanplayMediaFingerprint(
                    fileName = "Episode 01.mkv",
                    fileHash = "5d41402abc4b2a76b9719d911017c592",
                    fileSizeBytes = 5,
                ),
                match = DandanplayMatch(
                    episodeId = 123,
                    animeId = 456,
                    animeTitle = "Example Anime",
                    episodeTitle = "Episode 01",
                    shiftSeconds = 0.0,
                ),
                matchCandidates = listOf(
                    DandanplayMatch(
                        episodeId = 123,
                        animeId = 456,
                        animeTitle = "Example Anime",
                        episodeTitle = "Episode 01",
                        shiftSeconds = 0.0,
                    ),
                    DandanplayMatch(
                        episodeId = 124,
                        animeId = 456,
                        animeTitle = "Example Anime",
                        episodeTitle = "Episode 02",
                        shiftSeconds = null,
                    ),
                ),
                eventCount = 2,
                subtitle = DesktopPlaybackSubtitle(
                    source = "overlay.ass",
                    label = "dandanplay: Example Anime - Episode 01",
                    isDanmakuOverlay = true,
                ),
                cachePath = Path.of("cache", "overlay.ass"),
                source = DesktopDandanplayResolutionSource.NETWORK,
            ),
        )

        assertEquals("media-1", status.mediaId)
        assertEquals("dandanplay network: attached 2 comments", status.summary)
        assertContains(status.details, DandanplayPlaybackUiDetail("Provider source", "network"))
        assertContains(status.details, DandanplayPlaybackUiDetail("Matched episode", "Example Anime - Episode 01"))
        assertContains(status.details, DandanplayPlaybackUiDetail("Anime ID", "456"))
        assertContains(status.details, DandanplayPlaybackUiDetail("Episode ID", "123"))
        assertContains(status.details, DandanplayPlaybackUiDetail("Comments", "2 comments"))
        assertContains(status.details, DandanplayPlaybackUiDetail("ASS overlay", "attached"))
        assertEquals(123, status.selectedEpisodeId)
        assertEquals(listOf(123L, 124L), status.matchCandidates.map(DandanplayMatch::episodeId))
    }

    @Test
    fun describesNoMatchWithoutPretendingOverlayAttached() {
        val status = dandanplayStatusFromResolution(
            mediaId = "media-1",
            resolution = DesktopDandanplayDanmakuResolution(
                fingerprint = DandanplayMediaFingerprint(
                    fileName = "Episode 01.mkv",
                    fileHash = "5d41402abc4b2a76b9719d911017c592",
                    fileSizeBytes = 5,
                ),
                match = null,
                eventCount = 0,
                subtitle = null,
                cachePath = null,
                source = DesktopDandanplayResolutionSource.NETWORK,
            ),
        )

        assertEquals("dandanplay network: no match", status.summary)
        assertContains(status.details, DandanplayPlaybackUiDetail("Comments", "0 comments"))
        assertContains(status.details, DandanplayPlaybackUiDetail("ASS overlay", "not attached"))
    }

    @Test
    fun wrapsSimpleOperatorStatusMessages() {
        val status = dandanplayStatusMessage(
            mediaId = "media-1",
            summary = "dandanplay cache cleared",
            details = listOf(DandanplayPlaybackUiDetail("Library episode", "Example Anime - Episode 01")),
        )

        assertEquals("media-1", status.mediaId)
        assertEquals("dandanplay cache cleared", status.summary)
        assertEquals(listOf(DandanplayPlaybackUiDetail("Library episode", "Example Anime - Episode 01")), status.details)
    }
}
