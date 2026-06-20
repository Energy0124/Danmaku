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
    fun parsesLocalizationQaLaunchOverrides() {
        val options = DesktopLaunchOptions.parse(
            listOf(
                "--ui-language=zh-TW",
                "--initial-tab=library",
                "--server-port=0",
                "--web-assets-dir=apps/web-ui/dist",
            ),
        )

        assertEquals(DesktopUiLanguage.ZH_TW, options.initialLanguage)
        assertEquals(DesktopShellTab.MEDIA_LIBRARY, options.initialTab)
        assertEquals(0, options.serverPort)
        assertEquals(Path.of("apps/web-ui/dist"), options.webAssetsRoot)
    }

    @Test
    fun parsesAppLevelQaScreenshotOptions() {
        val options = DesktopLaunchOptions.parse(
            listOf(
                "--qa-screenshot-dir=S:/Projects/Danmaku/build/qa/desktop-localization",
                "--qa-screenshot-name",
                "desktop-localization-en-home",
                "--qa-screenshot-delay-seconds=2",
                "--qa-screenshot-keep-open",
            ),
        )

        assertEquals(Path.of("S:/Projects/Danmaku/build/qa/desktop-localization"), options.qaScreenshot?.outputDirectory)
        assertEquals("desktop-localization-en-home", options.qaScreenshot?.fileName)
        assertEquals(2.seconds, options.qaScreenshot?.delay)
        assertFalse(options.qaScreenshot?.autoExit ?: true)
    }

    @Test
    fun parsesLaunchOverrideAliases() {
        val options = DesktopLaunchOptions.parse(
            listOf(
                "--language",
                "en",
                "--tab",
                "settings",
            ),
        )

        assertEquals(DesktopUiLanguage.ENGLISH, options.initialLanguage)
        assertEquals(DesktopShellTab.PROFILE, options.initialTab)
    }

    @Test
    fun parsesSmokeMediaFromEnvironment() {
        val options = DesktopLaunchOptions.parse(
            args = listOf("--smoke-playback-media-env"),
            environment = mapOf(
                DesktopLaunchOptions.SMOKE_PLAYBACK_MEDIA_ENV to "D:/AniRss/Anime/黄泉使者/S01E01.mkv",
                DesktopLaunchOptions.SMOKE_PLAYBACK_SECONDS_ENV to "4",
                DesktopLaunchOptions.WEB_UI_DIST_ENV to "apps/web-ui/dist",
            ),
        )

        assertEquals(Path.of("D:/AniRss/Anime/黄泉使者/S01E01.mkv"), options.smokePlayback?.mediaPath)
        assertEquals(4.seconds, options.smokePlayback?.playbackDuration)
        assertEquals(Path.of("apps/web-ui/dist"), options.webAssetsRoot)
    }

    @Test
    fun parsesRemoteClientLaunchOptions() {
        val options = DesktopLaunchOptions.parse(
            listOf(
                "--remote-server-url=http://192.168.1.20:8686/",
                "--remote-pairing-token",
                "123456",
            ),
        )

        assertEquals(DesktopShellTab.MEDIA_LIBRARY, options.initialTab)
        assertEquals("http://192.168.1.20:8686", options.remoteClient?.normalizedServerUrl)
        assertEquals("123456", options.remoteClient?.pairingToken)
        assertTrue(options.remoteClient?.autoLoad == true)
    }

    @Test
    fun parsesRemoteClientEnvironmentAndCanSkipAutoLoad() {
        val options = DesktopLaunchOptions.parse(
            args = listOf("--remote-no-auto-load"),
            environment = mapOf(
                DesktopLaunchOptions.REMOTE_SERVER_URL_ENV to "http://127.0.0.1:8686",
                DesktopLaunchOptions.REMOTE_PAIRING_TOKEN_ENV to "654321",
            ),
        )

        assertEquals(DesktopShellTab.MEDIA_LIBRARY, options.initialTab)
        assertEquals("http://127.0.0.1:8686", options.remoteClient?.normalizedServerUrl)
        assertEquals("654321", options.remoteClient?.pairingToken)
        assertFalse(options.remoteClient?.autoLoad ?: true)
    }

    @Test
    fun remoteClientWithoutTokenDoesNotAutoLoad() {
        val options = DesktopLaunchOptions.parse(listOf("--remote-url=http://127.0.0.1:8686"))

        assertEquals(DesktopShellTab.MEDIA_LIBRARY, options.initialTab)
        assertEquals("http://127.0.0.1:8686", options.remoteClient?.normalizedServerUrl)
        assertEquals("", options.remoteClient?.pairingToken)
        assertFalse(options.remoteClient?.autoLoad ?: true)
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
    fun rejectsInvalidServerPort() {
        assertFailsWith<IllegalStateException> {
            DesktopLaunchOptions.parse(listOf("--server-port=65536"))
        }
    }

    @Test
    fun rejectsMissingSmokeMediaEnvironmentValue() {
        assertFailsWith<IllegalStateException> {
            DesktopLaunchOptions.parse(listOf("--smoke-playback-media-env"))
        }
    }
}
