package app.danmaku.tv

import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.library.android.DiscoveredLanLibraryServer
import app.danmaku.library.lanLibraryConnectionProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvSessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val url = "http://pc:8686"

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun firstLaunchDiscoversConnectsAndOpensHomeWithoutWaitingForTracking() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.trackingGate = CompletableDeferred()
        val navigator = TvNavigator()
        val viewModel = viewModel(repository, navigator, listOf(url))

        viewModel.onStart()
        runCurrent()

        assertEquals(listOf(url), repository.refreshedUrls)
        assertEquals(TvRoute.Home, navigator.state.value.route)
        assertEquals(1, repository.trackingLoads)
        assertFalse(repository.trackingGate!!.isCompleted)
        repository.trackingGate!!.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun everyForegroundReconnectsSavedPcWithoutChangingBrowseRoute() = runTest(dispatcher) {
        val repository = FakeRepository(savedUrl = url, cached = true)
        val navigator = TvNavigator()
        val viewModel = viewModel(repository, navigator)

        viewModel.onStart()
        advanceUntilIdle()
        assertEquals(TvRoute.Home, navigator.state.value.route)
        navigator.navigate(TvRoute.Library)
        viewModel.onStart()
        advanceUntilIdle()

        assertEquals(listOf(url, url), repository.refreshedUrls)
        assertEquals(TvRoute.Library, navigator.state.value.route)
    }

    @Test
    fun cacheOpensBeforeReconnectCompletes() = runTest(dispatcher) {
        val repository = FakeRepository(savedUrl = url, cached = true)
        repository.refreshGate = CompletableDeferred()
        val navigator = TvNavigator()
        val viewModel = viewModel(repository, navigator)

        viewModel.onStart()
        runCurrent()
        assertEquals(TvRoute.Home, navigator.state.value.route)
        assertTrue(viewModel.state.value.isRefreshing)
        viewModel.onStart()
        runCurrent()
        assertEquals(1, repository.refreshedUrls.size)

        repository.refreshGate!!.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun failedSavedPcRetriesAfterDiscoveryAndKeepsCachedHome() = runTest(dispatcher) {
        val repository = FakeRepository(savedUrl = url, cached = true)
        repository.failuresRemaining = 1
        val navigator = TvNavigator()
        val viewModel = viewModel(repository, navigator, listOf(url))

        viewModel.onStart()
        advanceUntilIdle()

        assertEquals(listOf(url, url), repository.refreshedUrls)
        assertEquals(TvRoute.Home, navigator.state.value.route)
        assertFalse(viewModel.state.value.isOffline)
    }

    @Test
    fun unreachableSavedPcDoesNotSwitchToAnUnknownLibrary() = runTest(dispatcher) {
        val repository = FakeRepository(savedUrl = url, cached = true)
        repository.failuresRemaining = 1
        val navigator = TvNavigator()
        val replacement = "http://other-pc:8686"
        val viewModel = viewModel(repository, navigator, listOf(replacement))

        viewModel.onStart()
        advanceUntilIdle()

        assertEquals(listOf(url), repository.refreshedUrls)
        assertEquals(url, viewModel.state.value.serverUrl)
        assertEquals(listOf(replacement), viewModel.state.value.connection.discoveredUrls)
        assertTrue(viewModel.state.value.isOffline)
        assertEquals(TvRoute.Home, navigator.state.value.route)
    }

    @Test
    fun multipleDiscoveredPcsWaitForSelection() = runTest(dispatcher) {
        val repository = FakeRepository()
        val navigator = TvNavigator()
        val urls = listOf(url, "http://other-pc:8686")
        val viewModel = viewModel(repository, navigator, urls)

        viewModel.onStart()
        advanceUntilIdle()
        assertEquals(TvRoute.Onboarding, navigator.state.value.route)
        assertEquals(urls, viewModel.state.value.connection.discoveredUrls)
        assertEquals("onboarding-pc:$url", navigator.savedFocus(TvRoute.Onboarding))
        assertTrue(repository.refreshedUrls.isEmpty())

        viewModel.selectConnection(lanLibraryConnectionProfile(urls.last()))
        advanceUntilIdle()
        assertEquals(listOf(urls.last()), repository.refreshedUrls)
        assertEquals(TvRoute.Home, navigator.state.value.route)
    }

    @Test
    fun unavailableSavedPcWithoutCacheShowsConnectionRecovery() = runTest(dispatcher) {
        val repository = FakeRepository(savedUrl = url)
        repository.failuresRemaining = 1
        val navigator = TvNavigator()
        val viewModel = viewModel(repository, navigator)

        viewModel.onStart()
        advanceUntilIdle()

        assertEquals(TvRoute.Onboarding, navigator.state.value.route)
        assertEquals(TvConnectionError.CONNECTION_FAILED, viewModel.state.value.connection.error)
        assertTrue(viewModel.state.value.connection.hasSearched)
        assertFalse(viewModel.state.value.connection.isDiscovering)
        assertEquals(1, viewModel.state.value.savedConnections.size)
    }

    @Test
    fun explicitDiscoveryShowsSinglePcForSelectionAndIgnoresDuplicateSearches() = runTest(dispatcher) {
        val repository = FakeRepository()
        val navigator = TvNavigator(TvRoute.Pc)
        var discoveries = 0
        val viewModel = TvSessionViewModel(repository, navigator, TvLibraryDiscovery {
            discoveries++
            listOf(DiscoveredLanLibraryServer(url))
        }, dispatcher)

        viewModel.discoverPc()
        viewModel.discoverPc()
        advanceUntilIdle()

        assertEquals(1, discoveries)
        assertEquals(TvRoute.Onboarding, navigator.state.value.route)
        assertEquals(listOf(url), viewModel.state.value.connection.discoveredUrls)
        assertTrue(repository.refreshedUrls.isEmpty())
    }

    @Test
    fun failedReconnectDoesNotStartDiscoveryAfterUserStartsPlayback() = runTest(dispatcher) {
        val repository = FakeRepository(savedUrl = url, cached = true)
        repository.failuresRemaining = 1
        repository.refreshGate = CompletableDeferred()
        val navigator = TvNavigator()
        var discoveries = 0
        val viewModel = TvSessionViewModel(repository, navigator, TvLibraryDiscovery {
            discoveries++
            listOf(DiscoveredLanLibraryServer(url))
        }, dispatcher)

        viewModel.onStart()
        runCurrent()
        navigator.navigate(TvRoute.Player("episode"))
        repository.refreshGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(0, discoveries)
        assertEquals(TvRoute.Player("episode"), navigator.state.value.route)
    }

    @Test
    fun openingManualEntryCancelsDiscoveryAndDoesNotRestartOnForeground() = runTest(dispatcher) {
        val repository = FakeRepository()
        val navigator = TvNavigator()
        var discoveries = 0
        val viewModel = TvSessionViewModel(repository, navigator, TvLibraryDiscovery {
            discoveries++
            listOf(DiscoveredLanLibraryServer(url))
        }, dispatcher)

        viewModel.onStart()
        viewModel.setManualEntry(true)
        viewModel.onStart()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.connection.isManualEntry)
        assertEquals(0, discoveries)
        assertTrue(repository.refreshedUrls.isEmpty())
        viewModel.connectAddress("pc")
        advanceUntilIdle()
        assertEquals(listOf(url), repository.refreshedUrls)
        assertFalse(viewModel.state.value.connection.isManualEntry)
        assertEquals(TvRoute.Home, navigator.state.value.route)
    }

    @Test
    fun discoveryFailureLeavesLocalizedRetryState() = runTest(dispatcher) {
        val repository = FakeRepository()
        val navigator = TvNavigator()
        val viewModel = TvSessionViewModel(repository, navigator, TvLibraryDiscovery {
            error("socket failure")
        }, dispatcher)

        viewModel.onStart()
        advanceUntilIdle()

        assertEquals(TvConnectionError.DISCOVERY_FAILED, viewModel.state.value.connection.error)
        assertFalse(viewModel.state.value.connection.isDiscovering)
        assertEquals(TvRoute.Onboarding, navigator.state.value.route)
    }

    @Test
    fun foregroundDoesNotReconnectDuringPlayback() = runTest(dispatcher) {
        val repository = FakeRepository(savedUrl = url)
        val navigator = TvNavigator(TvRoute.Player("episode"))
        val viewModel = viewModel(repository, navigator)

        viewModel.onStart()
        advanceUntilIdle()

        assertTrue(repository.refreshedUrls.isEmpty())
        assertEquals(TvRoute.Player("episode"), navigator.state.value.route)
    }

    private fun viewModel(
        repository: FakeRepository,
        navigator: TvNavigator,
        discovered: List<String> = emptyList(),
    ) = TvSessionViewModel(repository, navigator, TvLibraryDiscovery {
        discovered.map { DiscoveredLanLibraryServer(it) }
    }, dispatcher)

    private class FakeRepository(savedUrl: String = "", val cached: Boolean = false) : TvSessionRepository {
        private val catalog = createTvQaFixture(seriesCount = 1, episodesPerSeries = 1).catalog
        override val isQaFixtureInstalled = false
        override val state = MutableStateFlow(TvSessionUiState(
            serverUrl = savedUrl,
            savedConnections = if (savedUrl.isBlank()) emptyList() else listOf(lanLibraryConnectionProfile(savedUrl)),
        ))
        val refreshedUrls = mutableListOf<String>()
        var failuresRemaining = 0
        var trackingLoads = 0
        var refreshGate: CompletableDeferred<Unit>? = null
        var trackingGate: CompletableDeferred<Unit>? = null

        override fun updateServerUrl(serverUrl: String) {
            state.value = state.value.copy(serverUrl = serverUrl, isRefreshing = false, errorMessage = null)
        }

        override suspend fun loadCachedCatalog(): Boolean {
            if (cached) state.value = state.value.copy(catalog = catalog, catalogSource = TvCatalogSource.Cache)
            return cached
        }

        override suspend fun refresh(): Result<TvCatalogRefreshOutcome> {
            refreshedUrls += state.value.serverUrl
            state.value = state.value.copy(isRefreshing = true)
            refreshGate?.await()
            state.value = state.value.copy(isRefreshing = false)
            if (failuresRemaining > 0) {
                failuresRemaining--
                state.value = state.value.copy(isOffline = state.value.catalog != null)
                return Result.failure(IllegalStateException("offline"))
            }
            state.value = state.value.copy(catalog = catalog, isOffline = false, catalogSource = TvCatalogSource.Network)
            return Result.success(TvCatalogRefreshOutcome.Applied)
        }

        override suspend fun selectConnection(connection: LanLibraryConnectionProfile) {
            state.value = state.value.copy(serverUrl = connection.baseUrl, catalog = null)
            loadCachedCatalog()
        }

        override suspend fun loadTracking(): Result<Unit> {
            trackingLoads++
            trackingGate?.await()
            return Result.success(Unit)
        }

        override suspend fun refreshFolder(path: List<String>) = Result.success(TvCatalogRefreshOutcome.Applied)
        override suspend fun saveConnection() = Result.success(Unit)
        override suspend fun forgetConnection(connection: LanLibraryConnectionProfile) = Unit
        override suspend fun readTracking() = Result.success(Unit)
        override suspend fun syncTracking() = Result.success(Unit)
    }
}
