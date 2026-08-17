package app.danmaku.tv

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackPosition
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackStatus
import org.junit.Rule
import org.junit.Test

class TvFocusNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onboardingStartsOnDiscover() {
        val navigator = TvNavigator(TvRoute.Onboarding)
        composeRule.setContent {
            DanmakuTvTheme {
                TvOnboardingScreen(
                    navigation = navigator.state.value,
                    navigator = navigator,
                    isDiscovering = false,
                    errorMessage = null,
                    onDiscover = {},
                    onOpenPc = {},
                )
            }
        }

        composeRule.onNodeWithTag("onboarding-discover").assertIsFocused()
    }

    @Test
    fun homeLibraryFavoritesAndPcHaveDeterministicInitialFocus() {
        val fixture = createTvQaFixture()
        val session = fixture.session()
        val browse = TvBrowsePresenter().present(session, TvBrowseQuery())
        val navigator = TvNavigator(TvRoute.Home)
        var screen by mutableStateOf("home")

        composeRule.setContent {
            DanmakuTvTheme {
                when (screen) {
                    "home" -> TvHomeScreen(
                        navigation = navigator.state.value,
                        navigator = navigator,
                        session = session,
                        browse = browse,
                        onOpenSeries = {},
                        onPlay = {},
                        onOpenPc = {},
                    )
                    "library" -> TvLibraryGridScreen(
                        route = TvRoute.Library,
                        navigation = navigator.state.value,
                        navigator = navigator,
                        session = session,
                        browse = browse,
                        onOpenSeries = {},
                        onShowFilters = {},
                    )
                    else -> TvPcScreen(
                        navigation = navigator.state.value,
                        navigator = navigator,
                        session = session,
                        onServerUrlChange = {},
                        onPairingTokenChange = {},
                        onRefresh = {},
                        onDiscover = {},
                        onSave = {},
                        onSelectConnection = {},
                        onForgetConnection = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("home-hero").assertIsFocused().assertHasClickAction()

        composeRule.runOnIdle {
            navigator.reset(TvRoute.Library)
            screen = "library"
        }
        val firstSeries = browse.librarySeries.first()
        composeRule.onNodeWithTag("series-card:${firstSeries.id}")
            .assertIsFocused()
            .assertHasClickAction()

        composeRule.runOnIdle {
            navigator.reset(TvRoute.Pc)
            screen = "pc"
        }
        composeRule.onNodeWithTag("pc-discover").assertIsFocused()
    }

    @Test
    fun emptyFavoritesRetainsARealFocusTarget() {
        val navigator = TvNavigator(TvRoute.Favorites)
        composeRule.setContent {
            DanmakuTvTheme {
                TvFavoritesScreen(
                    navigation = navigator.state.value,
                    navigator = navigator,
                    session = TvSessionUiState(),
                    browse = TvBrowseUiState(),
                    onOpenSeries = {},
                )
            }
        }

        composeRule.onNodeWithTag("favorites-empty").assertIsFocused()
    }

    @Test
    fun searchAcceptsRemoteKeyboardInput() {
        val navigator = TvNavigator(TvRoute.Search)
        var query by mutableStateOf("")
        composeRule.setContent {
            DanmakuTvTheme {
                TvSearchScreen(
                    navigation = navigator.state.value,
                    navigator = navigator,
                    session = TvSessionUiState(),
                    browse = TvBrowseUiState(query = TvBrowseQuery(searchText = query)),
                    onSearch = { query = it },
                    onOpenSeries = {},
                )
            }
        }

        composeRule.onNodeWithTag("search-field")
            .assertIsFocused()
            .performTextInput("living room")
        composeRule.runOnIdle {
            check(query == "living room")
        }
    }

    @Test
    fun playerProgressBarReceivesFocusAndScrubsWithRemote() {
        var dispatched: PlaybackCommand? = null
        val state = TvPlaybackUiState(
            snapshot = PlaybackSnapshot(
                status = PlaybackStatus.PAUSED,
                position = PlaybackPosition(positionMs = 60_000, durationMs = 120_000),
            ),
            startupPhase = TvPlaybackStartupPhase.Playing,
            controlsVisible = true,
        )

        composeRule.setContent {
            DanmakuTvTheme {
                TvPlayerControls(
                    state = state,
                    onDispatch = { dispatched = it },
                    onTogglePlayPause = {},
                    onShowOverlay = {},
                    onStop = {},
                    modifier = Modifier,
                )
            }
        }

        composeRule.onNodeWithTag("player-play-pause").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("player-progress").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.runOnIdle {
            check(dispatched == PlaybackCommand.SeekTo(70_000))
        }
        composeRule.onNodeWithTag("player-progress")
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.runOnIdle {
            check(dispatched == PlaybackCommand.SeekTo(50_000))
        }
        composeRule.onNodeWithTag("player-progress").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("player-play-pause").assertIsFocused()
    }

    @Test
    fun seriesDetailUsesOneFocusTargetPerEpisode() {
        val fixture = createTvQaFixture(seriesCount = 2, episodesPerSeries = 3)
        val session = fixture.session()
        val browse = TvBrowsePresenter().present(session, TvBrowseQuery())
        val series = browse.librarySeries.first()
        val route = TvRoute.SeriesDetail(series.id)
        val navigator = TvNavigator(route)

        composeRule.setContent {
            DanmakuTvTheme {
                TvSeriesDetailScreen(
                    route = route,
                    navigation = navigator.state.value,
                    navigator = navigator,
                    session = session,
                    browse = browse,
                    onPlay = {},
                    onSetFavorite = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("series-play").assertIsFocused().assertHasClickAction()
        series.seasons.flatMap { it.items }.forEach { item ->
            composeRule.onNodeWithTag("episode-row:${item.id}").assertHasClickAction()
        }
        val firstEpisode = series.seasons.flatMap { it.items }.first()
        composeRule.onNodeWithTag("episode-row:${firstEpisode.id}")
            .performSemanticsAction(SemanticsActions.RequestFocus) { requestFocus ->
                requestFocus()
            }
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }

        composeRule.onNodeWithTag("series-play").assertIsFocused()
    }

    @Test
    fun leftFromRouteContentMovesFocusToNavigationRail() {
        val navigator = TvNavigator(TvRoute.Home)
        composeRule.setContent {
            val railRequester = remember { FocusRequester() }
            DanmakuTvTheme {
                Row {
                    Button(
                        onClick = {},
                        modifier = Modifier
                            .focusRequester(railRequester)
                            .testTag("test-rail"),
                    ) {
                        Text("Home")
                    }
                    CompositionLocalProvider(
                        LocalTvNavigationRailFocusRequester provides railRequester,
                    ) {
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .tvRouteFocus(
                                    navigator.state.value,
                                    navigator,
                                    TvRoute.Home,
                                    "test-content",
                                    isDefault = true,
                                )
                                .testTag("test-content"),
                        ) {
                            Text("Content")
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("test-content").assertIsFocused()
        composeRule.onNodeWithTag("test-content").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("test-rail").assertIsFocused()
    }

    @Test
    fun compactRailKeepsItsWidthAndLabelsOnlyTheFocusedDestination() {
        composeRule.setContent {
            val focusRequester = remember { FocusRequester() }
            DanmakuTvTheme {
                TvCompactNavigationRail(
                    currentRoute = TvRoute.Home,
                    session = TvSessionUiState(),
                    focusRequester = focusRequester,
                    onNavigate = {},
                )
            }
        }

        composeRule.onNodeWithTag("tv-route:Home")
            .performSemanticsAction(SemanticsActions.RequestFocus) { requestFocus ->
                requestFocus()
            }
            .assertIsFocused()
        composeRule.onNodeWithTag("tv-app-rail").assertWidthIsEqualTo(96.dp)
        composeRule.onNodeWithTag("tv-route-label:Home")
            .assertIsDisplayed()
            .assertWidthIsEqualTo(144.dp)

        composeRule.onNodeWithTag("tv-route:Home")
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("tv-route:Library").assertIsFocused()
        composeRule.onNodeWithTag("tv-app-rail").assertWidthIsEqualTo(96.dp)
        composeRule.onAllNodesWithTag("tv-route-label:Home").assertCountEquals(0)
        composeRule.onNodeWithTag("tv-route-label:Library")
            .assertIsDisplayed()
            .assertWidthIsEqualTo(144.dp)
    }

    @Test
    fun libraryFilterCapturesFocusAndNavigatesVertically() {
        var query by mutableStateOf(TvBrowseQuery())
        composeRule.setContent {
            DanmakuTvTheme {
                TvLibraryFiltersOverlay(
                    query = query,
                    availableReleaseYears = listOf(2025, 2024),
                    onSetSort = { query = query.copy(sort = it) },
                    onSetReleaseYear = { query = query.copy(releaseYear = it) },
                    onToggleSubtitles = {},
                    onReset = { query = TvBrowseQuery() },
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithTag("library-filter-sort").assertIsFocused()
        composeRule.onNodeWithTag("library-filter-sort")
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle {
            check(query.sort == TvLibrarySort.PATH)
        }

        composeRule.onNodeWithTag("library-filter-sort")
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("library-filter-season").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("library-filter-subtitles").assertIsFocused()
        composeRule.onNodeWithTag("library-filter-subtitles")
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("library-filter-season").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("library-filter-sort")
            .assertIsFocused()
    }


    @Test
    fun folderBrowserStartsOnFirstLibraryRoot() {
        val fixture = createTvQaFixture()
        val session = fixture.session()
        val browse = TvBrowsePresenter().present(session, TvBrowseQuery())
        val route = TvRoute.FolderBrowser()
        val navigator = TvNavigator(route)
        var openedFolder by mutableStateOf<String?>(null)

        composeRule.setContent {
            DanmakuTvTheme {
                TvFolderBrowserScreen(
                    route = route,
                    navigation = navigator.state.value,
                    navigator = navigator,
                    session = session,
                    browse = browse,
                    onOpenFolder = { openedFolder = it },
                    onOpenFile = {},
                    onNavigateUp = {},
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithTag("folder-entry:M:\\Anime")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle {
            check(openedFolder == "M:\\Anime")
        }
    }

    @Test
    fun folderBrowserFallsBackWhenSavedEntryDisappears() {
        val route = TvRoute.FolderBrowser()
        val navigator = TvNavigator(route)
        var browse by mutableStateOf(
            TvBrowseUiState(catalog = folderCatalog("alpha", "Alpha/Episode 1.mkv")),
        )

        composeRule.setContent {
            DanmakuTvTheme {
                TvFolderBrowserScreen(
                    route = route,
                    navigation = navigator.state.value,
                    navigator = navigator,
                    session = TvSessionUiState(catalog = browse.catalog),
                    browse = browse,
                    onOpenFolder = {},
                    onOpenFile = {},
                    onNavigateUp = {},
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithTag("folder-entry:Alpha").assertIsFocused()

        composeRule.runOnIdle {
            browse = TvBrowseUiState(catalog = folderCatalog("beta", "Beta/Episode 1.mkv"))
        }
        composeRule.onNodeWithTag("folder-entry:Beta").assertIsFocused()

        composeRule.runOnIdle {
            browse = TvBrowseUiState(
                catalog = LibraryCatalog(
                    rootName = "Anime",
                    indexedAtEpochMs = 3,
                    items = emptyList(),
                ),
            )
        }
        composeRule.onNodeWithTag("folder-empty").assertIsFocused()
    }

    private fun folderCatalog(id: String, relativePath: String) =
        LibraryCatalog(
            rootName = "Anime",
            indexedAtEpochMs = 2,
            items = listOf(
                LibraryMediaItem(
                    id = id,
                    seriesTitle = "Series $id",
                    episodeTitle = "Episode 1",
                    relativePath = relativePath,
                    sizeBytes = 1,
                    mediaType = "video/mp4",
                    streamPath = "/media/$id",
                ),
            ),
        )

    private fun TvQaFixture.session(): TvSessionUiState =
        TvSessionUiState(
            serverUrl = "http://10.0.2.2:18688",
            pairingToken = "qa",
            catalog = catalog,
            playbackProgresses = progresses,
            favoriteMediaIds = favorites,
            catalogSource = TvCatalogSource.Cache,
        )
}
