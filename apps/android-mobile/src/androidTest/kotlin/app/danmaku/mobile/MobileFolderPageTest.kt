package app.danmaku.mobile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackProgress
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MobileFolderPageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun browsesNestedFoldersAndPlaysAFile() {
        val catalog = LibraryCatalog(
            rootName = "Merged",
            indexedAtEpochMs = 1,
            items = listOf(
                item("episode", "M:\\Anime", "Example/Season 1/Episode 1.mkv"),
                item("download", "D:\\Downloads", "Movie.mkv"),
            ),
        )
        var playedId: String? = null

        composeRule.setContent {
            MaterialTheme {
                var path by remember { mutableStateOf(emptyList<String>()) }
                FolderPage(
                    contentPadding = PaddingValues(0.dp),
                    catalog = catalog,
                    path = path,
                    onOpenFolder = { path = path + it },
                    onNavigateUp = { path = path.dropLast(1) },
                    onPlay = { playedId = it.id },
                    onConnect = {},
                )
            }
        }

        composeRule.onNodeWithTag("folder-entry:M:\\Anime").performClick()
        composeRule.onNodeWithTag("folder-entry:Example").performClick()
        composeRule.onNodeWithTag("folder-entry:Season 1").performClick()
        composeRule.onNodeWithText("Episode 1.mkv").assertExists()
        composeRule.onNodeWithTag("folder-file:episode").performClick()
        composeRule.runOnIdle { assertEquals("episode", playedId) }

        composeRule.onNodeWithTag("folder-up").performClick()
        composeRule.onNodeWithTag("folder-entry:Season 1").assertExists()
    }

    @Test
    fun disconnectedStateRoutesToConnect() {
        var connectCount = 0
        composeRule.setContent {
            MaterialTheme {
                FolderPage(
                    contentPadding = PaddingValues(0.dp),
                    catalog = null,
                    path = emptyList(),
                    onOpenFolder = {},
                    onNavigateUp = {},
                    onPlay = {},
                    onConnect = { connectCount += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Connect").performClick()
        composeRule.runOnIdle { assertEquals(1, connectCount) }
    }

    @Test
    fun filePreviewUpdatesFromUnwatchedThroughInProgressToWatched() {
        val file = item("episode", "M:\\Anime", "Example/01.mkv")
        var progresses by mutableStateOf(emptyList<PlaybackProgress>())
        composeRule.setContent {
            MaterialTheme {
                FolderPage(
                    contentPadding = PaddingValues(0.dp),
                    catalog = LibraryCatalog("Anime", 1, listOf(file)),
                    path = listOf("Example"),
                    playbackProgresses = progresses,
                    onOpenFolder = {},
                    onNavigateUp = {},
                    onPlay = {},
                    onConnect = {},
                )
            }
        }
        composeRule.onNodeWithTag("folder-file:episode").performScrollTo()
        composeRule.onNodeWithTag("folder-file:episode:watch-status", useUnmergedTree = true)
            .assertTextEquals("Unwatched")
        composeRule.onNodeWithTag("folder-file:episode:watch-progress", useUnmergedTree = true)
            .assertDoesNotExist()

        composeRule.runOnIdle {
            progresses = listOf(PlaybackProgress(file.id, 60_000, 240_000, 1))
        }
        composeRule.onNodeWithTag("folder-file:episode:watch-status", useUnmergedTree = true)
            .assertTextEquals("In progress · 1:00 / 4:00")
        composeRule.onNodeWithTag("folder-file:episode:watch-progress", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0.25f, 0f..1f),
            ))

        composeRule.runOnIdle {
            progresses = listOf(PlaybackProgress(file.id, 239_000, 240_000, 2))
        }
        composeRule.onNodeWithTag("folder-file:episode:watch-status", useUnmergedTree = true)
            .assertTextEquals("Watched")
        composeRule.onNodeWithTag("folder-file:episode:watch-progress", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(1f, 0f..1f),
            ))
    }

    @Test
    fun cachesOneFolderFileWithoutSelectingItsSiblings() {
        val first = item("first", "M:\\Anime", "Example/01.mkv")
        val second = item("second", "M:\\Anime", "Example/02.mkv")
        var selected: List<String> = emptyList()
        composeRule.setContent {
            MaterialTheme {
                FolderPage(
                    contentPadding = PaddingValues(0.dp),
                    catalog = LibraryCatalog("Anime", 1, listOf(first, second)),
                    path = listOf("Example"),
                    onOpenFolder = {},
                    onNavigateUp = {},
                    onPlay = {},
                    onConnect = {},
                    onCacheFile = { selected = listOf(it.id) },
                )
            }
        }

        composeRule.onNodeWithTag("folder-file-cache:first").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(listOf("first"), selected) }
    }

    @Test
    fun refreshUsesTheCurrentNestedFolderAndShowsBusyProgress() {
        val catalog = LibraryCatalog(
            rootName = "Anime",
            indexedAtEpochMs = 1,
            items = listOf(item("episode", "M:\\Anime", "Example/Episode 1.mkv")),
        )
        var refreshedPath: List<String>? = null
        composeRule.setContent {
            MaterialTheme {
                FolderPage(
                    contentPadding = PaddingValues(0.dp),
                    catalog = catalog,
                    path = listOf("Example"),
                    onOpenFolder = {},
                    onNavigateUp = {},
                    onPlay = {},
                    onConnect = {},
                    isRefreshing = false,
                    onRefresh = { refreshedPath = it },
                )
            }
        }

        composeRule.onNodeWithTag("folder-refresh").performClick()
        composeRule.runOnIdle { assertEquals(listOf("Example"), refreshedPath) }

        composeRule.setContent {
            MaterialTheme {
                FolderPage(
                    contentPadding = PaddingValues(0.dp),
                    catalog = catalog,
                    path = listOf("Example"),
                    onOpenFolder = {},
                    onNavigateUp = {},
                    onPlay = {},
                    onConnect = {},
                    isRefreshing = true,
                    refreshFilesSeen = 42,
                )
            }
        }
        composeRule.onNodeWithTag("folder-refresh").assertIsNotEnabled()
        composeRule.onNodeWithText("Scanning… 42 files found").assertExists()
    }

    private fun item(id: String, root: String, path: String) =
        LibraryMediaItem(
            id = id,
            seriesTitle = "Series $id",
            episodeTitle = "Episode $id",
            relativePath = path,
            rootLabel = root,
            sizeBytes = 1,
            mediaType = "video/mp4",
            streamPath = "/media/$id",
        )
}
