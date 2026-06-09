package app.danmaku.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.MaterialTheme
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryItemMetadataStatus
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
import app.danmaku.domain.PlaybackProgress
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
                LibraryItems(catalog = seededCatalog(), playbackProgresses = emptyList(), onPlay = {})
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
                LibraryItems(catalog = seededCatalog(), playbackProgresses = emptyList(), onPlay = {})
            }
        }

        composeRule.onNodeWithTag("series:Example Show").performClick()

        composeRule.onNodeWithText("2 / 3 episodes").assertExists()
        composeRule.onNodeWithTag("series-detail:Example Show").assertExists()
        composeRule.onNodeWithText("3 subtitle tracks").assertExists()
        composeRule.onNodeWithText("0 watched, 0 watching, 2 new").assertExists()
        composeRule.onNodeWithTag("episode-detail:example-1").assertExists()
        composeRule.onNodeWithTag("series-season:Season unknown").assertExists()
        composeRule.onNodeWithTag("episode:example-1").assertExists()
        composeRule.onNodeWithTag("episode:example-2").assertExists()
        composeRule.onNodeWithTag("episode:other-1").assertDoesNotExist()

        composeRule.onNodeWithTag("series:all").performClick()

        composeRule.onNodeWithText("3 / 3 episodes").assertExists()
        composeRule.onNodeWithTag("series-detail:Example Show").assertDoesNotExist()
        composeRule.onNodeWithTag("episode:other-1").assertExists()
    }

    @Test
    fun favoritesFilterNarrowsEpisodesAndRoutesFavoriteToggle() {
        var toggledFavorite: Pair<String, Boolean>? = null
        composeRule.setContent {
            MaterialTheme {
                LibraryItems(
                    catalog = seededCatalog(),
                    playbackProgresses = emptyList(),
                    favoriteMediaIds = setOf("example-2"),
                    onSetFavorite = { item, isFavorite ->
                        toggledFavorite = item.id to isFavorite
                    },
                    onPlay = {},
                )
            }
        }

        composeRule.onNodeWithText("1 favorites").assertExists()
        composeRule.onNodeWithTag("library-favorites-filter").performClick()

        composeRule.onNodeWithText("1 / 3 episodes").assertExists()
        composeRule.onNodeWithTag("episode:example-2").assertExists()
        composeRule.onNodeWithTag("episode:example-1").assertDoesNotExist()

        composeRule.onNodeWithTag("episode-favorite:example-2").performClick()

        composeRule.runOnIdle {
            assertEquals("example-2" to false, toggledFavorite)
        }
    }

    @Test
    fun emptyResultsStateCanResetLibraryFilters() {
        composeRule.setContent {
            MaterialTheme {
                LibraryItems(
                    catalog = seededCatalog(),
                    playbackProgresses = emptyList(),
                    favoriteMediaIds = emptySet(),
                    onPlay = {},
                )
            }
        }

        composeRule.onNodeWithTag("library-favorites-filter").performClick()

        composeRule.onNodeWithText("No matching episodes").assertExists()
        composeRule.onNodeWithTag("library-reset-filters").performClick()

        composeRule.onNodeWithText("3 / 3 episodes").assertExists()
        composeRule.onNodeWithTag("episode:example-1").assertExists()
        composeRule.onNodeWithTag("episode:other-1").assertExists()
    }

    @Test
    fun episodeDetailShowsContextAndNavigatesNeighborEpisodes() {
        composeRule.setContent {
            MaterialTheme {
                LibraryItems(catalog = seededCatalog(), playbackProgresses = emptyList(), onPlay = {})
            }
        }

        composeRule.onNodeWithTag("episode:example-2").performClick()
        composeRule.onNodeWithTag("episode-detail:example-2").assertExists()
        composeRule.onNodeWithText("Example Show / Season unknown / New").assertExists()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithTag("episode-detail:other-1").assertExists()
    }

    @Test
    fun rendersMetadataLoadingStateInEpisodeRowsAndDetails() {
        composeRule.setContent {
            MaterialTheme {
                LibraryItems(
                    catalog = LibraryCatalog(
                        rootName = "Seeded PC",
                        indexedAtEpochMs = 1_700_000_000_000,
                        items = listOf(
                            mediaItem(
                                id = "example-loading",
                                seriesTitle = "Loading Show",
                                episodeTitle = "Episode 01",
                                subtitleCount = 1,
                                metadataStatus = LibraryItemMetadataStatus.LOADING,
                            ),
                        ),
                    ),
                    playbackProgresses = emptyList(),
                    onPlay = {},
                )
            }
        }

        composeRule.onNodeWithTag("episode:example-loading").performClick()

        composeRule.onNodeWithTag("episode-detail:example-loading").assertExists()
        composeRule.onAllNodesWithText("Poster/metadata loading", substring = true).assertCountEquals(2)
    }

    @Test
    fun episodeCardShowsDetailsAndQuickActionRoutesPlayCallback() {
        var playedItemId: String? = null
        composeRule.setContent {
            MaterialTheme {
                LibraryItems(
                    catalog = seededCatalog(),
                    playbackProgresses = emptyList(),
                    onPlay = { playedItemId = it.id },
                )
            }
        }

        composeRule.onNodeWithTag("episode:example-1").performClick()
        composeRule.onNodeWithTag("episode-detail:example-1").assertExists()
        composeRule.onNodeWithTag("episode-play:example-1").performClick()

        composeRule.runOnIdle {
            assertEquals("example-1", playedItemId)
        }
    }

    @Test
    fun dpadCenterSelectsEpisodeCardAndQuickPlayAction() {
        var playedItemId: String? = null
        composeRule.setContent {
            MaterialTheme {
                LibraryItems(
                    catalog = seededCatalog(),
                    playbackProgresses = emptyList(),
                    onPlay = { playedItemId = it.id },
                )
            }
        }

        composeRule.onNodeWithTag("episode:example-1").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeRule.onNodeWithTag("episode-detail:example-1").assertExists()
        composeRule.onNodeWithTag("episode-play:example-1").performKeyInput {
            pressKey(Key.DirectionCenter)
        }

        composeRule.runOnIdle {
            assertEquals("example-1", playedItemId)
        }
    }

    @Test
    fun seriesDetailEpisodeButtonRoutesSelectedItemToPlayCallback() {
        var playedItemId: String? = null
        composeRule.setContent {
            MaterialTheme {
                LibraryItems(
                    catalog = seededCatalog(),
                    playbackProgresses = emptyList(),
                    onPlay = { playedItemId = it.id },
                )
            }
        }

        composeRule.onNodeWithTag("series:Example Show").performClick()
        composeRule.onNodeWithTag("series-detail-episode:example-2").performClick()

        composeRule.runOnIdle {
            assertEquals("example-2", playedItemId)
        }
    }

    @Test
    fun nextUpPromotesFollowingEpisodeAndRoutesSelectedItemToPlayCallback() {
        var playedItemId: String? = null
        composeRule.setContent {
            MaterialTheme {
                LibraryItems(
                    catalog = seededCatalog(),
                    playbackProgresses = listOf(
                        PlaybackProgress(
                            mediaId = "example-1",
                            positionMs = 1_190_000,
                            durationMs = 1_200_000,
                            updatedAtEpochMs = 1_700_000_000_100,
                        ),
                    ),
                    onPlay = { playedItemId = it.id },
                )
            }
        }

        composeRule.onNodeWithTag("library-next-up").assertExists()
        composeRule.onNodeWithText("Next Up").assertExists()
        composeRule.onNodeWithText("Next episode").assertExists()
        composeRule.onNodeWithTag("next-up-details:example-2").performClick()
        composeRule.onNodeWithTag("episode-detail:example-2").assertExists()
        composeRule.onNodeWithTag("next-up:example-2").performClick()

        composeRule.runOnIdle {
            assertEquals("example-2", playedItemId)
        }
    }

    @Test
    fun rendersEpisodeWatchStatusFromPlaybackProgress() {
        composeRule.setContent {
            MaterialTheme {
                LibraryItems(
                    catalog = seededCatalog(),
                    playbackProgresses = listOf(
                        PlaybackProgress(
                            mediaId = "example-1",
                            positionMs = 60_000,
                            durationMs = 1_200_000,
                            updatedAtEpochMs = 1_700_000_000_100,
                        ),
                        PlaybackProgress(
                            mediaId = "other-1",
                            positionMs = 1_190_000,
                            durationMs = 1_200_000,
                            updatedAtEpochMs = 1_700_000_000_200,
                        ),
                    ),
                    onPlay = {},
                )
            }
        }

        composeRule.onNodeWithText("In progress 1:00 / 20:00").assertExists()
        composeRule.onNodeWithText("Watched").assertExists()
    }

    @Test
    fun rendersProgressRailsAndRoutesSelection() {
        var playedItemId: String? = null
        composeRule.setContent {
            MaterialTheme {
                LibraryItems(
                    catalog = seededCatalog(),
                    playbackProgresses = listOf(
                        PlaybackProgress(
                            mediaId = "example-1",
                            positionMs = 60_000,
                            durationMs = 1_200_000,
                            updatedAtEpochMs = 1_700_000_000_100,
                        ),
                        PlaybackProgress(
                            mediaId = "other-1",
                            positionMs = 1_190_000,
                            durationMs = 1_200_000,
                            updatedAtEpochMs = 1_700_000_000_200,
                        ),
                    ),
                    onPlay = { playedItemId = it.id },
                )
            }
        }

        composeRule.onNodeWithTag("library-continue-watching").assertExists()
        composeRule.onNodeWithTag("library-recently-watched").assertExists()
        composeRule.onNodeWithTag("recently-watched-details:other-1").performClick()
        composeRule.onNodeWithTag("episode-detail:other-1").assertExists()
        composeRule.onNodeWithTag("continue-watching:example-1").performClick()

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
        metadataStatus: LibraryItemMetadataStatus = LibraryItemMetadataStatus.NOT_AVAILABLE,
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
            metadataStatus = metadataStatus,
        )
}
