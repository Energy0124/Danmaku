package app.danmaku.mobile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import app.danmaku.domain.DanmakuDisplaySettings
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackPosition
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.PlaybackStatus
import app.danmaku.domain.PlaybackTrack
import app.danmaku.domain.PlaybackTrackKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MobileWatchPageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyPlayerLayoutRoutesOpenAndBrowseActions() {
        var openedVideo = false
        var openedLibrary = false

        composeRule.setContent {
            MaterialTheme {
                WatchPage(
                    contentPadding = PaddingValues(0.dp),
                    controller = null,
                    snapshot = PlaybackSnapshot(),
                    nowPlaying = null,
                    playbackError = null,
                    isFullscreen = false,
                    onOpen = { openedVideo = true },
                    onPlayPause = {},
                    onSeekTo = {},
                    onSetVolume = {},
                    onSelectAudio = {},
                    onSelectSubtitle = {},
                    onBrowseLibrary = { openedLibrary = true },
                    onToggleFullscreen = {},
                )
            }
        }

        composeRule.onNodeWithTag("watch-player-home").assertExists()
        composeRule.onNodeWithTag("watch-video-surface").assertExists()
        composeRule.onNodeWithTag("now-playing-panel").assertExists()
        composeRule.onNodeWithText("Ready to play").assertExists()
        composeRule.onAllNodesWithText("No episode selected").assertCountEquals(1)
        composeRule.onNodeWithText("Select a video to start watching").assertExists()
        composeRule.onNodeWithTag("watch-play-pause").assertIsNotEnabled()

        composeRule.onNodeWithTag("watch-open-video").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("page-column").performScrollToIndex(1)
        composeRule.onNodeWithTag("watch-library-actions").assertExists()
        composeRule.onNodeWithText("Browse").performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertTrue(openedVideo)
            assertTrue(openedLibrary)
        }
    }

    @Test
    fun connectedPlayerLayoutShowsEpisodeProgressAndTracks() {
        var selectedAudio: String? = null
        var selectedSubtitle = "unchanged"

        composeRule.setContent {
            MaterialTheme {
                WatchPage(
                    contentPadding = PaddingValues(0.dp),
                    controller = null,
                    snapshot = PlaybackSnapshot(
                        status = PlaybackStatus.PAUSED,
                        source = PlaybackSource.RemoteStream("http://pc.local/media/example-1"),
                        position = PlaybackPosition(positionMs = 60_000, durationMs = 1_200_000),
                        volumePercent = 40,
                        tracks = listOf(
                            PlaybackTrack(
                                id = "audio-en",
                                kind = PlaybackTrackKind.AUDIO,
                                label = "English",
                                selected = true,
                            ),
                            PlaybackTrack(
                                id = "audio-ja",
                                kind = PlaybackTrackKind.AUDIO,
                                label = "Japanese",
                            ),
                            PlaybackTrack(
                                id = "subtitle-en",
                                kind = PlaybackTrackKind.SUBTITLE,
                                label = "English subs",
                                selected = true,
                            ),
                            PlaybackTrack(
                                id = "subtitle-ja",
                                kind = PlaybackTrackKind.SUBTITLE,
                                label = "Japanese subs",
                            ),
                        ),
                    ),
                    nowPlaying = seededItem(),
                    playbackError = null,
                    isFullscreen = false,
                    onOpen = {},
                    onPlayPause = {},
                    onSeekTo = {},
                    onSetVolume = {},
                    onSelectAudio = { selectedAudio = it },
                    onSelectSubtitle = { selectedSubtitle = it ?: "off" },
                    onBrowseLibrary = {},
                    onToggleFullscreen = {},
                )
            }
        }

        composeRule.onNodeWithText("Example Show · Episode 01").assertExists()
        composeRule.onAllNodesWithText("Episode 01").assertCountEquals(1)
        composeRule.onAllNodesWithText("Paused").assertCountEquals(2)
        composeRule.onNodeWithText("1:00").assertExists()
        composeRule.onNodeWithText("20:00").assertExists()
        composeRule.onNodeWithText("40%").assertExists()
        composeRule.onNodeWithText("Audio").assertExists()
        composeRule.onNodeWithText("Subtitles").assertExists()

        composeRule.onNodeWithTag("track:audio-ja")
            .performScrollTo()
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("subtitle-off").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("subtitle-track:subtitle-ja")
            .performScrollTo()
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertEquals("audio-ja", selectedAudio)
            assertEquals("subtitle-ja", selectedSubtitle)
        }
    }

    @Test
    fun activePlaybackControlsRoutePlayPauseSeekAndVolume() {
        var playPauseCount = 0
        var seekTarget: Long? = null
        var volumeTarget: Int? = null
        var fullscreenToggleCount = 0

        composeRule.setContent {
            MaterialTheme {
                WatchPage(
                    contentPadding = PaddingValues(0.dp),
                    controller = null,
                    snapshot = PlaybackSnapshot(
                        status = PlaybackStatus.PLAYING,
                        source = PlaybackSource.RemoteStream("http://pc.local/media/example-1"),
                        position = PlaybackPosition(positionMs = 60_000, durationMs = 1_200_000),
                        volumePercent = 40,
                    ),
                    nowPlaying = seededItem(),
                    playbackError = "Transient test error",
                    isFullscreen = false,
                    onOpen = {},
                    onPlayPause = { playPauseCount += 1 },
                    onSeekTo = { seekTarget = it },
                    onSetVolume = { volumeTarget = it },
                    onSelectAudio = {},
                    onSelectSubtitle = {},
                    onBrowseLibrary = {},
                    onToggleFullscreen = { fullscreenToggleCount += 1 },
                )
            }
        }

        composeRule.onAllNodesWithText("Playing").assertCountEquals(2)
        composeRule.onNodeWithTag("watch-play-pause").assertExists()
        composeRule.onNodeWithText("Playback connection error: Transient test error").assertExists()
        composeRule.onNodeWithTag("watch-play-pause")
            .performScrollTo()
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("watch-seek:+10s", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("watch-volume-up")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("watch-fullscreen-toggle")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertEquals(1, playPauseCount)
            assertEquals(70_000L, seekTarget)
            assertEquals(50, volumeTarget)
            assertEquals(1, fullscreenToggleCount)
        }
    }

    @Test
    fun fullscreenPlayerUsesStandaloneVideoStage() {
        var openedVideo = false
        var fullscreenToggleCount = 0

        composeRule.setContent {
            MaterialTheme {
                WatchPage(
                    contentPadding = PaddingValues(0.dp),
                    controller = null,
                    snapshot = PlaybackSnapshot(
                        status = PlaybackStatus.PLAYING,
                        source = PlaybackSource.RemoteStream("http://pc.local/media/example-1"),
                        position = PlaybackPosition(positionMs = 60_000, durationMs = 1_200_000),
                        volumePercent = 40,
                    ),
                    nowPlaying = seededItem(),
                    playbackError = null,
                    isFullscreen = true,
                    onOpen = { openedVideo = true },
                    onPlayPause = {},
                    onSeekTo = {},
                    onSetVolume = {},
                    onSelectAudio = {},
                    onSelectSubtitle = {},
                    onBrowseLibrary = {},
                    onToggleFullscreen = { fullscreenToggleCount += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("watch-player-home").assertExists()
        composeRule.onNodeWithTag("watch-video-surface").assertExists()
        composeRule.onNodeWithTag("watch-danmaku-overlay", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithTag("now-playing-panel").assertCountEquals(0)
        composeRule.onAllNodesWithTag("watch-library-actions").assertCountEquals(0)
        val bottomChromeBounds = composeRule.onNodeWithTag("watch-fullscreen-bottom-chrome")
            .getUnclippedBoundsInRoot()
        val bottomChromeHeight = bottomChromeBounds.bottom - bottomChromeBounds.top
        assertTrue("Fullscreen bottom chrome is $bottomChromeHeight tall", bottomChromeHeight <= 96.dp)
        composeRule.onNodeWithText("Episode 01").assertExists()
        composeRule.onNodeWithText("1:00").assertExists()
        composeRule.onNodeWithText("20:00").assertExists()
        composeRule.onNodeWithTag("watch-seek-slider").assertExists()
        composeRule.onAllNodesWithText("Example Show/Episode 01.mkv").assertCountEquals(0)
        composeRule.onAllNodesWithTag("watch-volume-down").assertCountEquals(0)
        composeRule.onAllNodesWithTag("watch-volume-up").assertCountEquals(0)
        composeRule.onAllNodesWithText("40%").assertCountEquals(0)
        composeRule.onNodeWithTag("watch-open-video-toolbar")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("watch-fullscreen-toggle")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertTrue(openedVideo)
            assertEquals(1, fullscreenToggleCount)
        }
    }

    @Test
    fun playbackOptionsRouteSupportedMediaAndDanmakuChanges() {
        var settings by mutableStateOf(
            DanmakuDisplaySettings(
                opacityPercent = 90,
                displayAreaPercent = 50,
            ),
        )
        var playbackRate: Float? = null
        var selectedAudio: String? = null
        var selectedSubtitle = "unchanged"

        composeRule.setContent {
            MaterialTheme {
                WatchPage(
                    contentPadding = PaddingValues(0.dp),
                    controller = null,
                    snapshot = PlaybackSnapshot(
                        status = PlaybackStatus.PAUSED,
                        source = PlaybackSource.RemoteStream("http://pc.local/media/example-1"),
                        position = PlaybackPosition(positionMs = 60_000, durationMs = 1_200_000),
                        playbackRate = 1f,
                        tracks = listOf(
                            PlaybackTrack(
                                id = "audio-ja",
                                kind = PlaybackTrackKind.AUDIO,
                                label = "Japanese",
                                selected = true,
                            ),
                            PlaybackTrack(
                                id = "audio-en",
                                kind = PlaybackTrackKind.AUDIO,
                                label = "English",
                            ),
                            PlaybackTrack(
                                id = "subtitle-en",
                                kind = PlaybackTrackKind.SUBTITLE,
                                label = "English subs",
                                selected = true,
                            ),
                        ),
                    ),
                    nowPlaying = seededItem(),
                    playbackError = null,
                    isFullscreen = true,
                    danmakuDisplaySettings = settings,
                    onOpen = {},
                    onPlayPause = {},
                    onSeekTo = {},
                    onSetVolume = {},
                    onSetPlaybackRate = { playbackRate = it },
                    onUpdateDanmakuDisplaySettings = { settings = it },
                    onSelectAudio = { selectedAudio = it },
                    onSelectSubtitle = { selectedSubtitle = it ?: "off" },
                    onBrowseLibrary = {},
                    onToggleFullscreen = {},
                )
            }
        }

        composeRule.onNodeWithTag("watch-playback-options").performClick()
        composeRule.onNodeWithTag("playback-options-panel").assertExists()
        composeRule.onNodeWithText("Playback options").assertExists()
        composeRule.onNodeWithText("Video").assertExists()
        composeRule.onNodeWithText("Danmaku").assertExists()

        composeRule.onNodeWithTag("playback-rate:1.5").performClick()
        composeRule.onNodeWithTag("options-audio-track:audio-en").performClick()
        composeRule.onNodeWithTag("options-subtitle-off").performClick()
        composeRule.onNodeWithTag("danmaku-type-bottom").performScrollTo().performClick()
        composeRule.onNodeWithTag("danmaku-opacity-slider")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(70f) }
        composeRule.onNodeWithTag("danmaku-speed-slider")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(300f) }
        composeRule.onNodeWithTag("danmaku-visible-toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("danmaku-offset-step:30000").performScrollTo().performClick()
        composeRule.onNodeWithTag("danmaku-offset-plus").performScrollTo().performClick()
        val offsetInput = composeRule.onNodeWithTag("danmaku-offset-input")
        offsetInput.performScrollTo()
        offsetInput.performTextClearance()
        offsetInput.performTextInput("-02:30.500")
        composeRule.onNodeWithTag("danmaku-offset-apply").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(1.5f, playbackRate)
            assertEquals("audio-en", selectedAudio)
            assertEquals("off", selectedSubtitle)
            assertEquals(false, settings.visible)
            assertEquals(false, settings.showBottom)
            assertEquals(70, settings.opacityPercent)
            assertEquals(300, settings.speedPercent)
            assertEquals(-150_500L, settings.offsetMs)
        }

        composeRule.onNodeWithTag("playback-options-close").performScrollTo().performClick()
        composeRule.onAllNodesWithTag("playback-options-panel").assertCountEquals(0)
    }

    private fun seededItem(): LibraryMediaItem =
        LibraryMediaItem(
            id = "example-1",
            seriesTitle = "Example Show",
            episodeTitle = "Episode 01",
            relativePath = "Example Show/Episode 01.mkv",
            sizeBytes = 1_024L * 1_024L * 700L,
            mediaType = "video/x-matroska",
            streamPath = "/media/example-1",
        )
}
