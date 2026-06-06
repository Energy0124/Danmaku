package app.danmaku.tv

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.tv.material3.MaterialTheme
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TvLibraryItemsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersCatalogSummarySeriesRailAndEpisodeMetadata() {
        composeRule.setContent {
            MaterialTheme {
                LibraryItems(catalog = seededCatalog(), onPlay = {})
            }
        }

        composeRule.onNodeWithText("PC Library").assertExists()
        composeRule.onNodeWithText("Seeded PC").assertExists()
        composeRule.onNodeWithText("3 / 3 episodes").assertExists()
        composeRule.onNodeWithTag("series:Example Show").assertExists()
        composeRule.onNodeWithText("2 episodes").assertExists()
        composeRule.onNodeWithTag("series:Other Show").assertExists()
        composeRule.onNodeWithText("1 episode").assertExists()
        composeRule.onNodeWithTag("episode:example-1").assertExists()
        composeRule.onNodeWithTag("episode:example-2").assertExists()
        composeRule.onNodeWithTag("episode:other-1").assertExists()
    }

    @Test
    fun seriesButtonNarrowsAndClearsEpisodeList() {
        composeRule.setContent {
            MaterialTheme {
                LibraryItems(catalog = seededCatalog(), onPlay = {})
            }
        }

        composeRule.onNodeWithTag("series:Example Show").performClick()

        composeRule.onNodeWithText("2 / 3 episodes").assertExists()
        composeRule.onNodeWithTag("episode:example-1").assertExists()
        composeRule.onNodeWithTag("episode:example-2").assertExists()
        composeRule.onNodeWithTag("episode:other-1").assertDoesNotExist()

        composeRule.onNodeWithTag("series:all").performClick()

        composeRule.onNodeWithText("3 / 3 episodes").assertExists()
        composeRule.onNodeWithTag("episode:other-1").assertExists()
    }

    @Test
    fun episodeButtonRoutesSelectedItemToPlayCallback() {
        var playedItemId: String? = null
        composeRule.setContent {
            MaterialTheme {
                LibraryItems(
                    catalog = seededCatalog(),
                    onPlay = { playedItemId = it.id },
                )
            }
        }

        composeRule.onNodeWithTag("episode:example-1").performClick()

        composeRule.runOnIdle {
            assertEquals("example-1", playedItemId)
        }
    }

    private fun seededCatalog(): LibraryCatalog =
        LibraryCatalog(
            rootName = "Seeded PC",
            indexedAtEpochMs = 1_700_000_000_000,
            items = listOf(
                mediaItem(
                    id = "example-1",
                    seriesTitle = "Example Show",
                    episodeTitle = "Episode 01",
                    subtitleCount = 2,
                ),
                mediaItem(
                    id = "example-2",
                    seriesTitle = "Example Show",
                    episodeTitle = "Episode 02",
                    subtitleCount = 1,
                ),
                mediaItem(
                    id = "other-1",
                    seriesTitle = "Other Show",
                    episodeTitle = "Pilot",
                    subtitleCount = 0,
                ),
            ),
        )

    private fun mediaItem(
        id: String,
        seriesTitle: String,
        episodeTitle: String,
        subtitleCount: Int,
    ): LibraryMediaItem =
        LibraryMediaItem(
            id = id,
            seriesTitle = seriesTitle,
            episodeTitle = episodeTitle,
            relativePath = "$seriesTitle/$episodeTitle.mkv",
            sizeBytes = 1_024L * 1_024L * 700L,
            mediaType = "video/x-matroska",
            streamPath = "/media/$id",
            subtitles = (1..subtitleCount).map { index ->
                LibrarySubtitleTrack(
                    id = "$id-subtitle-$index",
                    label = "Subtitle $index",
                    relativePath = "$seriesTitle/$episodeTitle.$index.ass",
                    mediaType = "text/x-ssa",
                    streamPath = "/subtitles/$id-$index",
                )
            },
        )
}
