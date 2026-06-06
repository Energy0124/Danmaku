package app.danmaku.desktop

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DesktopLaunchOptionsTest {
    @Test
    fun parsesSmokePlaybackMediaEqualsSyntax() {
        val options = DesktopLaunchOptions.parse(
            listOf(
                "--smoke-playback-media=S:/Anime/Episode 01.mkv",
                "--smoke-playback-seconds=3",
            ),
        )

        assertEquals(Path.of("S:/Anime/Episode 01.mkv"), options.smokePlayback?.mediaPath)
        assertEquals(3.seconds, options.smokePlayback?.playbackDuration)
        assertTrue(options.smokePlayback?.autoExit == true)
    }

    @Test
    fun parsesSmokeVideoAliasAndKeepsWindowOpenWhenRequested() {
        val options = DesktopLaunchOptions.parse(
            listOf(
                "--smoke-video",
                "S:/Anime/Episode 01.mkv",
                "--smoke-keep-open",
            ),
        )

        assertEquals(Path.of("S:/Anime/Episode 01.mkv"), options.smokePlayback?.mediaPath)
        assertEquals(DEFAULT_SMOKE_PLAYBACK_DURATION, options.smokePlayback?.playbackDuration)
        assertFalse(options.smokePlayback?.autoExit ?: true)
    }

    @Test
    fun ignoresSmokeDurationWithoutMedia() {
        val options = DesktopLaunchOptions.parse(listOf("--smoke-playback-seconds=2"))

        assertNull(options.smokePlayback)
    }

    @Test
    fun parsesSmokeMediaFromEnvironment() {
        val options = DesktopLaunchOptions.parse(
            args = listOf("--smoke-playback-media-env"),
            environment = mapOf(
                DesktopLaunchOptions.SMOKE_PLAYBACK_MEDIA_ENV to "D:/AniRss/Anime/黄泉使者/S01E01.mkv",
                DesktopLaunchOptions.SMOKE_PLAYBACK_SECONDS_ENV to "4",
            ),
        )

        assertEquals(Path.of("D:/AniRss/Anime/黄泉使者/S01E01.mkv"), options.smokePlayback?.mediaPath)
        assertEquals(4.seconds, options.smokePlayback?.playbackDuration)
    }

    @Test
    fun clampsSmokeDuration() {
        val options = DesktopLaunchOptions.parse(
            listOf(
                "--smoke-video=S:/Anime/Episode 01.mkv",
                "--smoke-playback-seconds=600",
            ),
        )

        assertEquals(60.seconds, options.smokePlayback?.playbackDuration)
    }

    @Test
    fun rejectsMissingSmokeMediaValue() {
        assertFailsWith<IllegalStateException> {
            DesktopLaunchOptions.parse(listOf("--smoke-playback-media"))
        }
    }

    @Test
    fun rejectsMissingSmokeMediaEnvironmentValue() {
        assertFailsWith<IllegalStateException> {
            DesktopLaunchOptions.parse(listOf("--smoke-playback-media-env"))
        }
    }
}
