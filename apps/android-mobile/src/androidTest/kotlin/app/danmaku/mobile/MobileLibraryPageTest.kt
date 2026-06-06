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
import androidx.compose.ui.unit.dp
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogQuery
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.LibrarySubtitleTrack
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.PlaybackStatus
import app.danmaku.domain.filteredItems
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MobileLibraryPageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersEmptyLibraryState() {
        composeRule.setContent {
            MaterialTheme {
                LibraryPage(
                    contentPadding = PaddingValues(0.dp),
                    catalog = null,
                    playbackProgresses = emptyList(),
                    filteredItems = emptyList(),
                    totalCount = 0,
                    snapshot = PlaybackSnapshot(),
                    nowPlaying = null,
                    searchText = "",
                    onSearchTextChange = {},
                    sort = LibraryCatalogSort.TITLE,
                    onSortChange = {},
                    subtitleFilter = LibrarySubtitleFilter.ANY,
                    onSubtitleFilterChange = {},
                    onPlay = {},
                    onPlayPause = {},
                    onOpenPlayer = {},
                    onConnect = {},
                )
            }
        }

        composeRule.onNodeWithText("Library").assertExists()
        composeRule.onNodeWithText("Connect a Windows library to browse episodes").assertExists()
        composeRule.onNodeWithText("Connect library").assertExists()
    }

    @Test
    fun rendersConnectedCatalogSeriesDetailAndFiltersEpisodes() {
        composeRule.setContent {
            MaterialTheme {
                val catalog = seededCatalog()
                var searchText by remember { mutableStateOf("") }
                var sort by remember { mutableStateOf(LibraryCatalogSort.TITLE) }
                var subtitleFilter by remember { mutableStateOf(LibrarySubtitleFilter.ANY) }
                val filteredItems = catalog.filteredItems(
                    LibraryCatalogQuery(
                        searchText = searchText,
                        sort = sort,
                        subtitleFilter = subtitleFilter,
                    ),
                )

                LibraryPage(
                    contentPadding = PaddingValues(0.dp),
                    catalog = catalog,
                    playbackProgresses = emptyList(),
                    filteredItems = filteredItems,
                    totalCount = catalog.items.size,
                    snapshot = PlaybackSnapshot(),
                    nowPlaying = null,
                    searchText = searchText,
                    onSearchTextChange = { searchText = it },
                    sort = sort,
                    onSortChange = { sort = it },
                    subtitleFilter = subtitleFilter,
                    onSubtitleFilterChange = { subtitleFilter = it },
                    onPlay = {},
                    onPlayPause = {},
                    onOpenPlayer = {},
                    onConnect = {},
                )
            }
        }

        composeRule.onNodeWithText("3 of 3 episodes").assertExists()
        composeRule.onNodeWithTag("library-series-rail").assertExists()
        composeRule.onNodeWithTag("series:Example Show").performClick()

        composeRule.onNodeWithText("2 of 3 episodes").assertExists()
        composeRule.onNodeWithTag("series-detail:Example Show").assertExists()
        composeRule.onNodeWithText("2 episodes across 1 seasons").assertExists()
        composeRule.onNodeWithText("3 subtitles").assertExists()
        composeRule.onNodeWithTag("episode:example-1").assertExists()
        composeRule.onNodeWithTag("episode:example-2").assertExists()
        composeRule.onNodeWithTag("episode:other-1").assertDoesNotExist()
    }

    @Test
    fun rendersNextUpFromPlaybackProgressAndRoutesSelection() {
        var playedItemId: String? = null
        composeRule.setContent {
            MaterialTheme {
                val catalog = seededCatalog()
                LibraryPage(
                    contentPadding = PaddingValues(0.dp),
                    catalog = catalog,
                    playbackProgresses = listOf(
                        PlaybackProgress(
                            mediaId = "example-1",
                            positionMs = 1_190_000,
                            durationMs = 1_200_000,
                            updatedAtEpochMs = 456,
                        ),
                    ),
                    filteredItems = catalog.items,
                    totalCount = catalog.items.size,
                    snapshot = PlaybackSnapshot(),
                    nowPlaying = null,
                    searchText = "",
                    onSearchTextChange = {},
                    sort = LibraryCatalogSort.TITLE,
                    onSortChange = {},
                    subtitleFilter = LibrarySubtitleFilter.ANY,
                    onSubtitleFilterChange = {},
                    onPlay = { playedItemId = it.id },
                    onPlayPause = {},
                    onOpenPlayer = {},
                    onConnect = {},
                )
            }
        }

        composeRule.onNodeWithTag("library-next-up").assertExists()
        composeRule.onNodeWithText("Next Up").assertExists()
        composeRule.onNodeWithText("Next episode").assertExists()
        composeRule.onNodeWithTag("next-up:example-2").performClick()

        composeRule.runOnIdle {
            assertEquals("example-2", playedItemId)
        }
    }

    @Test
    fun rendersEpisodeWatchStatusFromPlaybackProgress() {
        composeRule.setContent {
            MaterialTheme {
                val catalog = seededCatalog()
                LibraryPage(
                    contentPadding = PaddingValues(0.dp),
                    catalog = catalog,
                    playbackProgresses = listOf(
                        PlaybackProgress(
                            mediaId = "example-1",
                            positionMs = 60_000,
                            durationMs = 1_200_000,
                            updatedAtEpochMs = 456,
                        ),
                        PlaybackProgress(
                            mediaId = "other-1",
                            positionMs = 1_190_000,
                            durationMs = 1_200_000,
                            updatedAtEpochMs = 457,
                        ),
                    ),
                    filteredItems = catalog.items,
                    totalCount = catalog.items.size,
                    snapshot = PlaybackSnapshot(),
                    nowPlaying = null,
                    searchText = "",
                    onSearchTextChange = {},
                    sort = LibraryCatalogSort.TITLE,
                    onSortChange = {},
                    subtitleFilter = LibrarySubtitleFilter.ANY,
                    onSubtitleFilterChange = {},
                    onPlay = {},
                    onPlayPause = {},
                    onOpenPlayer = {},
                    onConnect = {},
                )
            }
        }

        composeRule.onNodeWithText("In progress · 1:00 / 20:00", substring = true).assertExists()
        composeRule.onNodeWithText("Watched", substring = true).assertExists()
    }

    @Test
    fun activePlaybackShowsMiniPlayerAndRoutesEpisodeSelection() {
        var playedItemId: String? = null
        composeRule.setContent {
            MaterialTheme {
                val catalog = seededCatalog()
                LibraryPage(
                    contentPadding = PaddingValues(0.dp),
                    catalog = catalog,
                    playbackProgresses = emptyList(),
                    filteredItems = catalog.items,
                    totalCount = catalog.items.size,
                    snapshot = PlaybackSnapshot(
                        status = PlaybackStatus.PLAYING,
                        source = PlaybackSource.RemoteStream("http://example.test/media/example-1"),
                    ),
                    nowPlaying = catalog.items.first(),
                    searchText = "",
                    onSearchTextChange = {},
                    sort = LibraryCatalogSort.TITLE,
                    onSortChange = {},
                    subtitleFilter = LibrarySubtitleFilter.ANY,
                    onSubtitleFilterChange = {},
                    onPlay = { playedItemId = it.id },
                    onPlayPause = {},
                    onOpenPlayer = {},
                    onConnect = {},
                )
            }
        }

        composeRule.onNodeWithTag("mini-player").assertExists()
        composeRule.onNodeWithText("Example Show").assertExists()
        composeRule.onNodeWithText("Episode 01").assertExists()
        composeRule.onNodeWithTag("episode:example-2").performClick()

        composeRule.runOnIdle {
            assertEquals("example-2", playedItemId)
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
