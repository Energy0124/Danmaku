package app.danmaku.tv

import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Rule
import org.junit.Test
import androidx.tv.material3.Button
import androidx.tv.material3.Text

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
