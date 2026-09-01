package app.danmaku.tv

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.danmaku.domain.LanLibraryServerStatus
import app.danmaku.domain.PlaybackProgress
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.library.LanPlaybackTarget
import app.danmaku.library.android.AndroidLanLibraryConnectionStore
import app.danmaku.library.android.AndroidLibraryFavoriteStore
import app.danmaku.library.android.LanLibraryClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TvReviewRegressionInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    @After
    fun clearStores() {
        listOf(
            "danmaku_lan_library_connections",
            "danmaku_library_favorites",
            "danmaku_tv_catalog_cache",
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
    }

    @Test
    fun activityRecreationReusesApplicationContainer() {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(TV_QA_FIXTURE_EXTRA, true)

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            var originalContainer: TvApplicationContainer? = null
            var originalNavigator: TvNavigator? = null
            scenario.onActivity { activity ->
                originalContainer = activity.container
                originalNavigator = activity.container.navigator
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                assertSame(originalContainer, activity.container)
                assertSame(originalNavigator, activity.container.navigator)
            }
        }
    }

    @Test
    fun connectionEditInvalidatesInFlightRefresh() = runBlocking {
        val json = Json { encodeDefaults = true }
        val fixture = createTvQaFixture(seriesCount = 1, episodesPerSeries = 1)
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(json.encodeToString(LanLibraryServerStatus())),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(json.encodeToString(fixture.catalog))
                .setHeadersDelay(1, TimeUnit.SECONDS),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        server.start()
        try {
            val repository = TvLibraryRepository(
                connectionSession = LanLibraryConnectionSession(LanLibraryClient()),
                connectionStore = AndroidLanLibraryConnectionStore(context),
                favoriteStore = AndroidLibraryFavoriteStore(context),
                catalogCache = AndroidTvCatalogCache(context),
                defaultServerUrl = server.url("/").toString(),
                ioDispatcher = Dispatchers.IO,
            )

            val refresh = async(Dispatchers.Default) { repository.refresh() }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertTrue(repository.state.value.isRefreshing)

            val replacementUrl = "http://replacement.invalid:8686"
            repository.updateServerUrl(replacementUrl)
            assertFalse(repository.state.value.isRefreshing)

            assertEquals(
                TvCatalogRefreshOutcome.Stale,
                refresh.await().getOrThrow(),
            )
            assertEquals(replacementUrl, repository.state.value.serverUrl)
            assertFalse(repository.state.value.isRefreshing)
            assertNull(repository.state.value.errorMessage)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun connectionSelectionClearsInFlightFolderRefresh() = runBlocking {
        val json = Json { encodeDefaults = true }
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(202))
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(json.encodeToString(LanLibraryServerStatus(scanning = true)))
                    .setBodyDelay(1, TimeUnit.SECONDS),
            )
            val repository = trackingRepository(server)
            val refresh = async(Dispatchers.Default) {
                repository.refreshFolder(listOf("Example Show"))
            }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertTrue(repository.state.value.folderRefresh.isBusy)

            repository.selectConnection(
                LanLibraryConnectionProfile(
                    id = "replacement",
                    displayName = "Replacement PC",
                    baseUrl = "http://replacement.invalid:8686",
                ),
            )

            assertFalse(repository.state.value.folderRefresh.isBusy)
            assertNull(repository.state.value.folderRefresh.error)
            assertEquals(TvCatalogRefreshOutcome.Stale, refresh.await().getOrThrow())
        }
    }

    @Test
    fun progressFromPreviousConnectionIsRejected() = runBlocking {
        val cache = AndroidTvCatalogCache(context)
        val fixture = createTvQaFixture(seriesCount = 1, episodesPerSeries = 1)
        val firstUrl = "http://pc-a.invalid:8686"
        val secondUrl = "http://pc-b.invalid:8686"
        cache.save(firstUrl, fixture.catalog, emptyList())
        val repository = TvLibraryRepository(
            connectionSession = LanLibraryConnectionSession(LanLibraryClient()),
            connectionStore = AndroidLanLibraryConnectionStore(context),
            favoriteStore = AndroidLibraryFavoriteStore(context),
            catalogCache = cache,
            defaultServerUrl = firstUrl,
            ioDispatcher = Dispatchers.IO,
        )
        assertTrue(repository.loadCachedCatalog())

        repository.updateServerUrl(secondUrl)
        val staleProgress = PlaybackProgress(
            mediaId = fixture.catalog.items.single().id,
            positionMs = 42_000,
            durationMs = 120_000,
            updatedAtEpochMs = 123,
        )

        val applied = repository.updateProgresses(
            target = LanPlaybackTarget(firstUrl, staleProgress.mediaId),
            progresses = listOf(staleProgress),
        )

        assertFalse(applied)
        assertTrue(repository.state.value.playbackProgresses.isEmpty())
        assertNull(cache.load(secondUrl))
        assertTrue(cache.load(firstUrl)?.playbackProgresses?.isEmpty() == true)
    }

    @Test
    fun trackingResultFromPreviousConnectionIsRejected() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(ACCOUNTS_JSON)
                    .setBodyDelay(1, TimeUnit.SECONDS),
            )
            server.enqueue(jsonResponse(TRACKING_JSON))
            val repository = trackingRepository(server)

            val load = async(Dispatchers.Default) { repository.loadTracking() }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            repository.updateServerUrl("http://replacement.invalid:8686")
            load.await()

            assertNull(repository.state.value.tracking.accounts)
            assertNull(repository.state.value.tracking.document)
            assertFalse(repository.state.value.tracking.isBusy)
        }
    }

    @Test
    fun progressChangeInvalidatesFreshTrackingReadback() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(jsonResponse(OPERATION_JSON))
            val repository = trackingRepository(server)

            repository.readTracking().getOrThrow()
            assertTrue(repository.state.value.tracking.hasFreshReadback)
            assertEquals(1, repository.state.value.tracking.document?.plan?.updates?.size)

            assertTrue(
                repository.updateProgresses(
                    LanPlaybackTarget(server.url("/").toString(), "episode-id"),
                    emptyList(),
                ),
            )
            assertFalse(repository.state.value.tracking.hasFreshReadback)

            repository.syncTracking().getOrThrow()
            assertNull(server.takeRequest(250, TimeUnit.MILLISECONDS))
        }
    }

    private fun trackingRepository(server: MockWebServer): TvLibraryRepository =
        TvLibraryRepository(
            connectionSession = LanLibraryConnectionSession(LanLibraryClient()),
            connectionStore = AndroidLanLibraryConnectionStore(context),
            favoriteStore = AndroidLibraryFavoriteStore(context),
            catalogCache = AndroidTvCatalogCache(context),
            defaultServerUrl = server.url("/").toString(),
            ioDispatcher = Dispatchers.IO,
        )

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private companion object {
        const val ACCOUNTS_JSON = """
            {"myAnimeList":{"state":"CONNECTED","userId":"1","displayName":"MAL user"},"bangumi":{"state":"DISCONNECTED"},"bangumiTokenUrl":"https://next.bgm.tv/demo/access-token"}
        """
        const val TRACKING_JSON = """
            {"generatedAtEpochMs":1,"series":[],"mappings":[{"localSeriesId":"series","animeId":{"provider":"MY_ANIME_LIST","value":42},"source":"MANUAL","confidence":1.0,"mappedAtEpochMs":1}],"listEntries":[],"plan":{"summary":{"updateCount":1,"skippedCount":0,"conflictCount":0,"failureCount":0,"myAnimeListUpdateCount":1,"bangumiUpdateCount":0},"updates":[{"localSeriesId":"series","localSeriesIds":["series"],"seriesTitle":"Example","episodeCount":12,"mapping":{"localSeriesId":"series","animeId":{"provider":"MY_ANIME_LIST","value":42},"source":"MANUAL","confidence":1.0,"mappedAtEpochMs":1},"update":{"animeId":{"provider":"MY_ANIME_LIST","value":42},"status":"WATCHING","watchedEpisodes":3}}],"skipped":[],"conflicts":[],"mappingConflicts":[],"failures":[]}}
        """
        const val OPERATION_JSON = """
            {"document":$TRACKING_JSON,"successCount":1,"conflictCount":0,"missingCount":0,"errors":[]}
        """
    }
}
