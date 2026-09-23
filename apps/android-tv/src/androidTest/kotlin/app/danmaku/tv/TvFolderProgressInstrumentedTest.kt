package app.danmaku.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackProgress
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TvFolderProgressInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun folderRowsShowProgressUpdatesAndDpadPlaysTheSelectedFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = item("first", "01.mkv")
        val second = item("second", "02.mkv")
        val progress = PlaybackProgress(second.id, 60_000, 240_000, 1)
        var session by mutableStateOf(
            TvSessionUiState(
                catalog = LibraryCatalog("Library", 1, listOf(first, second)),
                playbackProgresses = listOf(progress),
            ),
        )
        val presenter = TvBrowsePresenter()
        val route = TvRoute.FolderBrowser(listOf("Series"))
        val navigator = TvNavigator(route)
        var played: LibraryMediaItem? = null
        composeRule.setContent {
            DanmakuTvTheme {
                TvFolderBrowserScreen(
                    route = route,
                    navigation = navigator.state.value,
                    navigator = navigator,
                    session = session,
                    browse = presenter.present(session, TvBrowseQuery()),
                    onOpenFolder = {},
                    onPlay = { played = it },
                    onNavigateUp = {},
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithTag("folder-file:first:watch-status", useUnmergedTree = true)
            .assertTextEquals(context.getString(R.string.folder_watch_unwatched))
        composeRule.onNodeWithTag("folder-file:first:watch-progress", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag("folder-file:second:watch-status", useUnmergedTree = true)
            .assertTextEquals(
                context.getString(
                    R.string.folder_watch_position_duration,
                    context.getString(R.string.folder_watch_in_progress), "1:00", "4:00",
                ),
            )
        assertProgress(0.25f)
        composeRule.onNodeWithTag("folder-file:first")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("folder-file:second")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle {
            assertEquals(second, played)
            session = session.copy(
                playbackProgresses = listOf(progress.copy(positionMs = 239_000, updatedAtEpochMs = 2)),
            )
        }
        composeRule.onNodeWithTag("folder-file:second:watch-status", useUnmergedTree = true)
            .assertTextEquals(context.getString(R.string.folder_watch_watched))
        assertProgress(1f)
        composeRule.onNodeWithTag("folder-file:second").assertIsFocused()

        composeRule.runOnIdle {
            session = session.copy(
                playbackProgresses = listOf(progress.copy(durationMs = null, updatedAtEpochMs = 3)),
            )
        }
        composeRule.onNodeWithTag("folder-file:second:watch-status", useUnmergedTree = true)
            .assertTextEquals(
                context.getString(
                    R.string.folder_watch_position,
                    context.getString(R.string.folder_watch_in_progress), "1:00",
                ),
            )
        composeRule.onNodeWithTag("folder-file:second:watch-progress", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    private fun assertProgress(fraction: Float) {
        composeRule.onNodeWithTag("folder-file:second:watch-progress", useUnmergedTree = true)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(fraction, 0f..1f),
                ),
            )
    }

    private fun item(id: String, fileName: String) = LibraryMediaItem(
        id = id,
        seriesTitle = "Series",
        episodeTitle = id,
        relativePath = "Series/$fileName",
        sizeBytes = 1,
        mediaType = "video/mp4",
        streamPath = "/media/$id",
    )
}
