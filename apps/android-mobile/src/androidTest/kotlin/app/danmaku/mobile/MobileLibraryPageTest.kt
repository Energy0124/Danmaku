package app.danmaku.mobile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.LibraryAnimeMetadata
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogQuery
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryItemMetadataStatus
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.LibrarySubtitleTrack
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.PlaybackStatus
import app.danmaku.domain.filteredItems
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.library.lanLibraryConnectionProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        composeRule.onAllNodesWithText("Connect a Windows library to browse episodes").assertCountEquals(2)
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

        composeRule.onAllNodesWithText("3 of 3 episodes").assertCountEquals(2)
        scrollLibraryFeedTo(2)
        composeRule.onNodeWithTag("library-series-rail").assertExists()
        selectSeries("Example Show")

        scrollLibraryFeedTo(3)
        composeRule.onNodeWithTag("series-detail:Example Show").assertExists()
        composeRule.onNodeWithText("2 episodes across 1 seasons").assertExists()
        composeRule.onAllNodesWithText("0 watched · 0 watching · 2 new", substring = true).assertCountEquals(2)
        composeRule.onNodeWithText("3 subtitles").assertExists()
        scrollLibraryFeedTo(4)
        composeRule.onNodeWithTag("episode:example-1").assertExists()
        composeRule.onNodeWithTag("episode:example-2").assertExists()
        composeRule.onNodeWithTag("episode:other-1").assertDoesNotExist()
    }

    @Test
    fun rendersPosterMetadataActiveFiltersAndMatchedSeriesSelection() {
        composeRule.setContent {
            MaterialTheme {
                val catalog = LibraryCatalog(
                    rootName = "Seeded PC",
                    indexedAtEpochMs = 1_700_000_000_000,
                    items = listOf(
                        mediaItem(
                            id = "example-1",
                            seriesTitle = "Folder Title",
                            episodeTitle = "Episode 01",
                            subtitleCount = 2,
                            posterPath = "/posters/example-1",
                            animeMetadata = matchedMetadata(),
                            metadataStatus = LibraryItemMetadataStatus.LOADING,
                        ),
                        mediaItem(
                            id = "example-2",
                            seriesTitle = "Folder Title",
                            episodeTitle = "Episode 02",
                            subtitleCount = 0,
                        ),
                        mediaItem(
                            id = "other-1",
                            seriesTitle = "Other Show",
                            episodeTitle = "Pilot",
                            subtitleCount = 1,
                        ),
                    ),
                )
                var searchText by remember { mutableStateOf("") }
                val sort = LibraryCatalogSort.PATH
                val subtitleFilter = LibrarySubtitleFilter.WITH_SUBTITLES
                val favoriteFilter = LibraryFavoriteFilter.FAVORITES_ONLY
                val favoriteIds = setOf("example-1")
                val filteredItems = catalog.filteredItems(
                    LibraryCatalogQuery(
                        searchText = searchText,
                        sort = sort,
                        subtitleFilter = subtitleFilter,
                        favoriteFilter = favoriteFilter,
                        favoriteMediaIds = favoriteIds,
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
                    onSortChange = {},
                    subtitleFilter = subtitleFilter,
                    onSubtitleFilterChange = {},
                    favoriteMediaIds = favoriteIds,
                    favoriteFilter = favoriteFilter,
                    onFavoriteFilterChange = {},
                    onPlay = {},
                    onPlayPause = {},
                    onOpenPlayer = {},
                    onConnect = {},
                )
            }
        }

        composeRule.onNodeWithText("Favorites only").assertExists()
        composeRule.onNodeWithText("Subtitles only").assertExists()
        composeRule.onNodeWithText("Path sort").assertExists()
        scrollLibraryFeedTo(2)
        composeRule.onNodeWithTag("library-series-rail").assertExists()
        selectSeries("Matched Anime")

        scrollLibraryFeedTo(3)
        composeRule.onNodeWithTag("series-detail:Matched Anime").assertExists()
        scrollLibraryFeedTo(4)
        composeRule.onNodeWithTag("episode:example-1").performSemanticsAction(SemanticsActions.OnClick)
        scrollLibraryFeedTo(5)
        composeRule.onNodeWithTag("episode-detail:example-1").assertExists()
        composeRule.onNodeWithText("Matched anime: Matched Anime").assertExists()
        composeRule.onNodeWithText("Poster ready").assertExists()
        composeRule.onAllNodesWithText("Poster/metadata loading", substring = true).assertCountEquals(2)
        scrollLibraryFeedTo(4)
        composeRule.onNodeWithTag("episode:example-1").assertExists()
        composeRule.onNodeWithTag("episode:example-2").assertDoesNotExist()
    }

    @Test
    fun favoritesFilterNarrowsEpisodesAndRoutesFavoriteToggle() {
        var toggledFavorite: Pair<String, Boolean>? = null
        composeRule.setContent {
            MaterialTheme {
                val catalog = seededCatalog()
                var favoriteIds by remember { mutableStateOf(setOf("example-2")) }
                var favoriteFilter by remember { mutableStateOf(LibraryFavoriteFilter.ANY) }
                val filteredItems = catalog.filteredItems(
                    LibraryCatalogQuery(
                        favoriteFilter = favoriteFilter,
                        favoriteMediaIds = favoriteIds,
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
                    searchText = "",
                    onSearchTextChange = {},
                    sort = LibraryCatalogSort.TITLE,
                    onSortChange = {},
                    subtitleFilter = LibrarySubtitleFilter.ANY,
                    onSubtitleFilterChange = {},
                    favoriteMediaIds = favoriteIds,
                    favoriteFilter = favoriteFilter,
                    onFavoriteFilterChange = { favoriteFilter = it },
                    onSetFavorite = { item, isFavorite ->
                        toggledFavorite = item.id to isFavorite
                        favoriteIds = if (isFavorite) {
                            favoriteIds + item.id
                        } else {
                            favoriteIds - item.id
                        }
                    },
                    onPlay = {},
                    onPlayPause = {},
                    onOpenPlayer = {},
                    onConnect = {},
                )
            }
        }

        composeRule.onNodeWithText("1 favorites").assertExists()
        composeRule.onNodeWithTag("library-favorites-filter").performClick()

        composeRule.onAllNodesWithText("1 of 3 episodes").assertCountEquals(2)
        scrollLibraryFeedTo(3)
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
                val catalog = seededCatalog()
                var favoriteFilter by remember { mutableStateOf(LibraryFavoriteFilter.FAVORITES_ONLY) }
                var searchText by remember { mutableStateOf("missing") }
                var sort by remember { mutableStateOf(LibraryCatalogSort.PATH) }
                var subtitleFilter by remember { mutableStateOf(LibrarySubtitleFilter.WITH_SUBTITLES) }
                val filteredItems = catalog.filteredItems(
                    LibraryCatalogQuery(
                        searchText = searchText,
                        sort = sort,
                        subtitleFilter = subtitleFilter,
                        favoriteFilter = favoriteFilter,
                        favoriteMediaIds = emptySet(),
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
                    favoriteMediaIds = emptySet(),
                    favoriteFilter = favoriteFilter,
                    onFavoriteFilterChange = { favoriteFilter = it },
                    onPlay = {},
                    onPlayPause = {},
                    onOpenPlayer = {},
                    onConnect = {},
                )
            }
        }

        scrollLibraryFeedTo(3)
        composeRule.onNodeWithTag("library-empty-results").assertExists()
        composeRule.onNodeWithText("No matching episodes").assertExists()
        composeRule.onNodeWithText("Reset filters").performClick()

        scrollLibraryFeedTo(1)
        assertTextVisible("3 of 3 episodes")
        scrollLibraryFeedTo(3)
        composeRule.onNodeWithTag("episode:example-1").assertExists()
        composeRule.onNodeWithTag("episode:other-1").assertExists()
    }

    @Test
    fun episodeDetailShowsContextAndNavigatesNeighborEpisodes() {
        composeRule.setContent {
            MaterialTheme {
                val catalog = seededCatalog()
                LibraryPage(
                    contentPadding = PaddingValues(0.dp),
                    catalog = catalog,
                    playbackProgresses = emptyList(),
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

        scrollLibraryFeedTo(4)
        composeRule.onNodeWithTag("episode:example-2").performSemanticsAction(SemanticsActions.OnClick)
        scrollLibraryFeedTo(6)
        composeRule.onNodeWithTag("episode-detail:example-2").assertExists()
        composeRule.onNodeWithText("Example Show · Season unknown · New").assertExists()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithTag("episode-detail:other-1").assertExists()
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

        scrollLibraryFeedTo(2)
        composeRule.onNodeWithTag("library-next-up").assertExists()
        composeRule.onNodeWithText("Next Up").assertExists()
        composeRule.onNodeWithText("Next episode").assertExists()
        composeRule.onNodeWithTag("next-up-details:example-2").performClick()
        scrollLibraryFeedTo(7)
        composeRule.onNodeWithTag("episode-detail:example-2").assertExists()
        scrollLibraryFeedTo(2)
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

        scrollLibraryFeedTo(6)
        composeRule.onNodeWithText("In progress · 1:00 / 20:00", substring = true).assertExists()
        scrollLibraryFeedTo(8)
        composeRule.onNodeWithText("Watched · 700.0 MB", substring = true).assertExists()
    }

    @Test
    fun rendersProgressRailsAndRoutesSelection() {
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
                    onPlay = { playedItemId = it.id },
                    onPlayPause = {},
                    onOpenPlayer = {},
                    onConnect = {},
                )
            }
        }

        scrollLibraryFeedTo(3)
        composeRule.onNodeWithTag("library-continue-watching").assertExists()
        scrollLibraryFeedTo(4)
        composeRule.onNodeWithTag("library-recently-watched").assertExists()
        composeRule.onNodeWithTag("recently-watched-details:other-1").performClick()
        scrollLibraryFeedTo(9)
        composeRule.onNodeWithTag("episode-detail:other-1").assertExists()
        scrollLibraryFeedTo(3)
        composeRule.onNodeWithTag("continue-watching:example-1").performClick()

        composeRule.runOnIdle {
            assertEquals("example-1", playedItemId)
        }
    }

    @Test
    fun activePlaybackShowsMiniPlayerAndRoutesExplicitEpisodePlay() {
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
        composeRule.onAllNodesWithText("Example Show").assertCountEquals(2)
        composeRule.onAllNodesWithText("Episode 01").assertCountEquals(2)
        scrollLibraryFeedTo(5)
        composeRule.onNodeWithTag("episode:example-2").performSemanticsAction(SemanticsActions.OnClick)
        scrollLibraryFeedTo(7)
        composeRule.onNodeWithTag("episode-detail:example-2").assertExists()

        composeRule.runOnIdle {
            assertEquals(null, playedItemId)
        }

        composeRule.onNodeWithTag("episode-play:example-2").performClick()

        composeRule.runOnIdle {
            assertEquals("example-2", playedItemId)
        }
    }

    @Test
    fun connectionPanelPrioritizesSavedPcPickerAndRevealsManualFieldsForEditing() {
        val profile = lanLibraryConnectionProfile(
            baseUrl = "http://living-room-pc:8686",
            pairingToken = "123456",
            displayName = "Living Room PC",
        )
        var selectedConnection: LanLibraryConnectionProfile? = null
        var editedServerUrl = ""
        var editedPairingToken = ""

        composeRule.setContent {
            MaterialTheme {
                ConnectionPanel(
                    catalog = null,
                    serverUrl = "http://10.0.2.2:8686",
                    pairingToken = "",
                    savedConnections = listOf(profile),
                    libraryError = null,
                    onServerUrlChange = { editedServerUrl = it },
                    onPairingTokenChange = { editedPairingToken = it },
                    onSelectConnection = { selectedConnection = it },
                    onEditConnection = {
                        editedServerUrl = it.baseUrl
                        editedPairingToken = it.pairingToken
                    },
                    onForgetConnection = {},
                    onSaveConnection = {},
                    onDiscover = {},
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithText("Saved PCs").assertExists()
        composeRule.onNodeWithText("Living Room PC").assertExists()
        composeRule.onNodeWithText("Server URL").assertDoesNotExist()
        composeRule.onNodeWithTag("saved-connection:${profile.id}").performClick()

        composeRule.runOnIdle {
            assertEquals(profile, selectedConnection)
        }

        composeRule.onNodeWithTag("saved-connection-edit:${profile.id}").performClick()
        composeRule.onNodeWithText("Server URL").assertExists()
        composeRule.onNodeWithText("Pairing code").assertExists()
        composeRule.runOnIdle {
            assertEquals(profile.baseUrl, editedServerUrl)
            assertEquals(profile.pairingToken, editedPairingToken)
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
        posterPath: String? = null,
        animeMetadata: LibraryAnimeMetadata? = null,
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
            posterPath = posterPath,
            animeMetadata = animeMetadata,
            metadataStatus = metadataStatus,
        )

    private fun matchedMetadata(): LibraryAnimeMetadata =
        LibraryAnimeMetadata(
            animeId = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, 101),
            displayTitle = "Matched Anime",
            primaryTitle = "Matched Anime",
            englishTitle = "Matched Anime",
            episodeCount = 2,
            startYear = 2026,
        )

    private fun scrollLibraryFeedTo(index: Int) {
        val isPhoneFeed = composeRule.onAllNodesWithTag("library-feed").fetchSemanticsNodes().isNotEmpty()
        val feedTag = if (isPhoneFeed) {
            "library-feed"
        } else {
            "library-master-pane"
        }
        val targetIndex = if (isPhoneFeed) index else minOf(index, 8)
        composeRule.onNodeWithTag(feedTag).performScrollToIndex(targetIndex)
    }

    private fun selectSeries(title: String) {
        composeRule.onNodeWithTag("series:$title")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun assertTextVisible(text: String) {
        assertTrue(composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty())
    }
}
