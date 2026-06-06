package app.danmaku.mobile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
                    onOpen = { openedVideo = true },
                    onPlayPause = {},
                    onSeekTo = {},
                    onSetVolume = {},
                    onSelectAudio = {},
                    onSelectSubtitle = {},
                    onBrowseLibrary = { openedLibrary = true },
                )
            }
        }

        composeRule.onNodeWithTag("watch-player-home").assertExists()
        composeRule.onNodeWithTag("watch-video-surface").assertExists()
        composeRule.onNodeWithTag("now-playing-panel").assertExists()
        composeRule.onNodeWithText("Ready to play").assertExists()
        composeRule.onNodeWithText("No episode selected").assertExists()
        composeRule.onNodeWithText("Select a video to start watching").assertExists()
        composeRule.onNodeWithTag("watch-play-pause").assertIsNotEnabled()

        composeRule.onNodeWithTag("watch-open-video").performClick()
        composeRule.onNodeWithText("Browse").performClick()

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
                        ),
                    ),
                    nowPlaying = seededItem(),
                    playbackError = null,
                    onOpen = {},
                    onPlayPause = {},
                    onSeekTo = {},
                    onSetVolume = {},
                    onSelectAudio = { selectedAudio = it },
                    onSelectSubtitle = { selectedSubtitle = it ?: "off" },
                    onBrowseLibrary = {},
                )
            }
        }

        composeRule.onNodeWithText("Example Show · Episode 01").assertExists()
        composeRule.onNodeWithText("Episode 01").assertExists()
        composeRule.onNodeWithText("Paused").assertExists()
        composeRule.onNodeWithText("1:00").assertExists()
        composeRule.onNodeWithText("20:00").assertExists()
        composeRule.onNodeWithText("Volume 40%").assertExists()
        composeRule.onNodeWithText("Audio").assertExists()
        composeRule.onNodeWithText("Subtitles").assertExists()

        composeRule.onNodeWithText("Japanese").performClick()
        composeRule.onNodeWithText("Off").performClick()

        composeRule.runOnIdle {
            assertEquals("audio-ja", selectedAudio)
            assertEquals("off", selectedSubtitle)
        }
    }

    @Test
    fun activePlaybackControlsRoutePlayPauseSeekAndVolume() {
        var playPauseCount = 0
        var seekTarget: Long? = null
        var volumeTarget: Int? = null

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
                    onOpen = {},
                    onPlayPause = { playPauseCount += 1 },
                    onSeekTo = { seekTarget = it },
                    onSetVolume = { volumeTarget = it },
                    onSelectAudio = {},
                    onSelectSubtitle = {},
                    onBrowseLibrary = {},
                )
            }
        }

        composeRule.onNodeWithText("Playing").assertExists()
        composeRule.onNodeWithText("Pause").assertExists()
        composeRule.onNodeWithText("Playback connection error: Transient test error").assertExists()
        composeRule.onNodeWithTag("watch-play-pause").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("watch-seek:+10s").performClick()
        composeRule.onNodeWithTag("watch-volume-up").performClick()

        composeRule.runOnIdle {
            assertEquals(1, playPauseCount)
            assertEquals(70_000, seekTarget)
            assertEquals(50, volumeTarget)
        }
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
