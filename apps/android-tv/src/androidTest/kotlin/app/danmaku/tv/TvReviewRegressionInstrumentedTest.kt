package app.danmaku.tv

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.android.AndroidLanLibraryConnectionStore
import app.danmaku.library.android.AndroidLibraryFavoriteStore
import app.danmaku.library.android.LanLibraryClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
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
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeadersDelay(1, TimeUnit.SECONDS),
        )
        server.start()
        try {
            val repository = TvLibraryRepository(
                connectionSession = LanLibraryConnectionSession(LanLibraryClient()),
                connectionStore = AndroidLanLibraryConnectionStore(context),
                favoriteStore = AndroidLibraryFavoriteStore(context),
                catalogCache = AndroidTvCatalogCache(context),
                defaultServerUrl = server.url("/").toString(),
                defaultPairingToken = "test-token",
                ioDispatcher = Dispatchers.IO,
            )

            val refresh = async(Dispatchers.Default) { repository.refresh() }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertTrue(repository.state.value.isRefreshing)

            val replacementUrl = "http://replacement.invalid:8686"
            repository.updateServerUrl(replacementUrl)
            assertFalse(repository.state.value.isRefreshing)

            assertTrue(refresh.await().isFailure)
            assertEquals(replacementUrl, repository.state.value.serverUrl)
            assertFalse(repository.state.value.isRefreshing)
            assertNull(repository.state.value.errorMessage)
        } finally {
            server.shutdown()
        }
    }
}
