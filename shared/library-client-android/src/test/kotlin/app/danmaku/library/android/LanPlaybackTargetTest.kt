package app.danmaku.library.android

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
            LanPlaybackTarget.fromStreamUrl(
                "http://192.168.1.20:8686/media/episode+id?token=12+34",
            ),
        )
    }

    @Test
    fun ignoresUrlsOutsideTheLanMediaContract() {
        assertNull(LanPlaybackTarget.fromStreamUrl("https://example.com/video.m3u8"))
        assertNull(LanPlaybackTarget.fromStreamUrl("http://192.168.1.20:8686/media/id"))
        assertNull(LanPlaybackTarget.fromStreamUrl("file:///sdcard/episode.mkv"))
    }
}
