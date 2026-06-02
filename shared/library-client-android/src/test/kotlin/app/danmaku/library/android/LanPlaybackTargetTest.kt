package app.danmaku.library.android

import app.danmaku.library.LanPlaybackTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanPlaybackTargetTest {
    @Test
    fun parsesPairedMediaStreamUrls() {
        assertEquals(
            LanPlaybackTarget(
                baseUrl = "http://192.168.1.20:8686",
                pairingToken = "12 34",
                mediaId = "episode id",
            ),
            lanPlaybackTargetFromStreamUrl(
                "http://192.168.1.20:8686/media/episode+id?token=12+34",
            ),
        )
    }

    @Test
    fun ignoresUrlsOutsideTheLanMediaContract() {
        assertNull(lanPlaybackTargetFromStreamUrl("https://example.com/video.m3u8"))
        assertNull(lanPlaybackTargetFromStreamUrl("http://192.168.1.20:8686/media/id"))
        assertNull(lanPlaybackTargetFromStreamUrl("file:///sdcard/episode.mkv"))
    }
}
