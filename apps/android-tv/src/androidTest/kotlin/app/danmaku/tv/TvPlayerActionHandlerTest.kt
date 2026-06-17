package app.danmaku.tv

import android.content.Context
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
import app.danmaku.domain.LanLibraryServerStatus
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.library.LanLibraryClient
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.LanPlaybackPreparation
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.android.AndroidLanLibraryConnectionStore
import app.danmaku.library.android.AndroidLibraryFavoriteStore
import app.danmaku.library.android.DiscoveredLanLibraryServer
import app.danmaku.library.android.LanLibraryDiscoveryException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvPlayerActionHandlerTest {
    private lateinit var context: Context
    private lateinit var connectionStore: AndroidLanLibraryConnectionStore
    private lateinit var favoriteStore: AndroidLibraryFavoriteStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        connectionStore = AndroidLanLibraryConnectionStore(context)
        favoriteStore = AndroidLibraryFavoriteStore(context)
        clearStores()
    }

    @After
    fun tearDown() {
        clearStores()
    }

    @Test
    fun refreshLibraryLoadsCatalogProgressAndSavesConnection() = runBlocking {
        val progress = PlaybackProgress(
            mediaId = "episode-1",
            positionMs = 42_000,
            durationMs = 1_200_000,
            updatedAtEpochMs = 2_000,
        )
        val client = RecordingLanLibraryClient(progresses = listOf(progress))
        val state = TvPlayerState(emptyList(), emptySet()).apply {
            serverUrl = " http://tv.test:8686/ "
            pairingToken = "pairing-token"
        }
        val handler = actionHandler(state, client, this)

        handler.refreshLibrary()
        waitForLaunchedActions()

        assertEquals(client.catalog, state.catalog)
        assertEquals(listOf(progress), state.playbackProgresses)
        assertEquals(TvDestination.Library, state.selectedDestination)
        assertNull(state.libraryError)
        assertEquals(1, client.statusFetches)
        assertEquals(1, client.catalogFetches)
        assertEquals(1, client.progressFetches)

        val savedConnection = state.savedConnections.single()
        assertEquals("http://tv.test:8686", savedConnection.baseUrl)
        assertEquals("pairing-token", savedConnection.pairingToken)
        assertEquals(client.catalog.rootName, savedConnection.displayName)
        assertEquals(state.savedConnections, connectionStore.loadProfiles())
    }

    @Test
    fun refreshLibraryStoresCatalogFailure() = runBlocking {
        val state = TvPlayerState(emptyList(), emptySet()).apply {
            serverUrl = "http://tv.test:8686"
        }
        val handler = actionHandler(
            state = state,
            client = RecordingLanLibraryClient(catalogError = IllegalStateException("catalog unavailable")),
            scope = this,
        )

        handler.refreshLibrary()
        waitForLaunchedActions()

        assertNull(state.catalog)
        assertEquals("catalog unavailable", state.libraryError)
        assertEquals(TvDestination.Pc, state.selectedDestination)
        assertTrue(state.savedConnections.isEmpty())
    }

    @Test
    fun discoverPcStoresFirstDiscoveredServerUrl() = runBlocking {
        val discovery = RecordingTvLibraryDiscovery(
            result = listOf(
                DiscoveredLanLibraryServer(baseUrl = "http://first.test:8686"),
                DiscoveredLanLibraryServer(baseUrl = "http://second.test:8686"),
            ),
        )
        val state = TvPlayerState(emptyList(), emptySet()).apply {
            serverUrl = "http://old.test:8686"
            libraryError = "previous error"
        }
        val handler = actionHandler(
            state = state,
            client = RecordingLanLibraryClient(),
            scope = this,
            libraryDiscovery = discovery,
        )

        handler.discoverPc()
        waitForLaunchedActions()

        assertEquals("http://first.test:8686", state.serverUrl)
        assertNull(state.libraryError)
        assertEquals(1, discovery.discoveryRequests)
    }

    @Test
    fun discoverPcStoresNoServerFailure() = runBlocking {
        val state = TvPlayerState(emptyList(), emptySet()).apply {
            serverUrl = "http://old.test:8686"
        }
        val handler = actionHandler(
            state = state,
            client = RecordingLanLibraryClient(),
            scope = this,
            libraryDiscovery = RecordingTvLibraryDiscovery(result = emptyList()),
        )

        handler.discoverPc()
        waitForLaunchedActions()

        assertEquals("http://old.test:8686", state.serverUrl)
        assertEquals("No Windows library server discovered", state.libraryError)
    }

    @Test
    fun discoverPcStoresDiscoveryFailure() = runBlocking {
        val state = TvPlayerState(emptyList(), emptySet()).apply {
            serverUrl = "http://old.test:8686"
        }
        val handler = actionHandler(
            state = state,
            client = RecordingLanLibraryClient(),
            scope = this,
            libraryDiscovery = RecordingTvLibraryDiscovery(
                failure = LanLibraryDiscoveryException("Discovery socket unavailable"),
            ),
        )

        handler.discoverPc()
        waitForLaunchedActions()

        assertEquals("http://old.test:8686", state.serverUrl)
        assertEquals("Discovery socket unavailable", state.libraryError)
    }

    @Test
    fun connectionAndFavoriteActionsUpdateStateAndStores() = runBlocking {
        val item = LibraryMediaItem(
            id = "episode-1",
            seriesTitle = "Example Show",
            episodeTitle = "Episode 01",
            relativePath = "Example Show/Episode 01.mkv",
            sizeBytes = 123,
            mediaType = "video/x-matroska",
            streamPath = "/media/episode-1",
        )
        val state = TvPlayerState(emptyList(), emptySet()).apply {
            serverUrl = "http://tv.test:8686/"
            pairingToken = "pairing-token"
            catalog = LibraryCatalog(
                rootName = "Saved PC",
                indexedAtEpochMs = 1_000,
                items = listOf(item),
            )
        }
        val handler = actionHandler(state, RecordingLanLibraryClient(), this)

        handler.setFavorite(item, isFavorite = true)

        assertEquals(setOf(item.id), state.favoriteMediaIds)
        assertEquals(setOf(item.id), favoriteStore.loadFavoriteMediaIds())
        assertNull(state.libraryError)

        handler.setFavorite(item, isFavorite = false)

        assertFalse(item.id in state.favoriteMediaIds)
        assertFalse(item.id in favoriteStore.loadFavoriteMediaIds())

        handler.saveConnection()

        val savedConnection = state.savedConnections.single()
        assertEquals("http://tv.test:8686", savedConnection.baseUrl)
        assertEquals("Saved PC", savedConnection.displayName)
        assertEquals("pairing-token", savedConnection.pairingToken)
        assertNull(state.libraryError)

        handler.selectConnection(
            LanLibraryConnectionProfile(
                id = "http://other.test:8686",
                displayName = "Other PC",
                baseUrl = "http://other.test:8686",
                pairingToken = "other-token",
            ),
        )

        assertEquals("http://other.test:8686", state.serverUrl)
        assertEquals("other-token", state.pairingToken)

        handler.forgetConnection(savedConnection)

        assertTrue(state.savedConnections.isEmpty())
        assertTrue(connectionStore.loadProfiles().isEmpty())
    }

    @Test
    fun playItemPreparesRemotePlaybackWithResumeAndDispatchesCommands() = runBlocking {
        val item = libraryMediaItem("episode-1")
        val progress = PlaybackProgress(
            mediaId = item.id,
            positionMs = 42_000,
            durationMs = 1_200_000,
            updatedAtEpochMs = 2_000,
        )
        val client = RecordingLanLibraryClient(progresses = listOf(progress))
        val controller = RecordingTvPlaybackController()
        val state = TvPlayerState(emptyList(), emptySet()).apply {
            serverUrl = "http://tv.test:8686"
            pairingToken = "pairing-token"
            this.controller = controller
        }
        val handler = actionHandler(state, client, this)

        handler.playItem(item)
        waitForLaunchedActions()

        val loadedPreparation = controller.loadedPreparations.single()
        assertEquals(item, loadedPreparation.item)
        assertEquals("http://tv.test:8686", loadedPreparation.target.baseUrl)
        assertEquals("pairing-token", loadedPreparation.target.pairingToken)
        assertEquals(item.id, loadedPreparation.target.mediaId)
        assertEquals(42_000, loadedPreparation.resumePositionMs)
        assertEquals(
            PlaybackSource.RemoteStream("http://tv.test:8686/media/episode-1"),
            loadedPreparation.source,
        )
        assertEquals(
            listOf(PlaybackCommand.SeekTo(42_000), PlaybackCommand.Play),
            controller.commands,
        )
        assertNull(state.libraryError)
    }

    @Test
    fun playItemStoresResumeLookupFailureAndStillStartsPlayback() = runBlocking {
        val item = libraryMediaItem("episode-1")
        val client = RecordingLanLibraryClient(
            progressFailure = IllegalStateException("progress unavailable"),
        )
        val controller = RecordingTvPlaybackController()
        val state = TvPlayerState(emptyList(), emptySet()).apply {
            serverUrl = "http://tv.test:8686"
            pairingToken = "pairing-token"
            this.controller = controller
        }
        val handler = actionHandler(state, client, this)

        handler.playItem(item)
        waitForLaunchedActions()

        assertNull(controller.loadedPreparations.single().resumePositionMs)
        assertEquals(listOf(PlaybackCommand.Play), controller.commands)
        assertEquals("Resume lookup failed: progress unavailable", state.libraryError)
    }

    private fun actionHandler(
        state: TvPlayerState,
        client: LanLibraryClient,
        scope: CoroutineScope,
        libraryDiscovery: TvLibraryDiscovery = RecordingTvLibraryDiscovery(result = emptyList()),
    ): TvPlayerActionHandler =
        TvPlayerActionHandler(
            state = state,
            scope = scope,
            libraryConnectionSession = LanLibraryConnectionSession(client),
            progressSync = LanPlaybackProgressSync(client) { 2_000 },
            playbackPreparer = LanPlaybackPreparer(client),
            connectionStore = connectionStore,
            favoriteStore = favoriteStore,
            libraryDiscovery = libraryDiscovery,
        )

    private fun clearStores() {
        connectionStore.loadProfiles().forEach {
            connectionStore.forgetProfile(it.id)
        }
        favoriteStore.saveFavoriteMediaIds(emptySet())
    }

    private suspend fun waitForLaunchedActions() {
        currentCoroutineContext()[Job]?.children?.toList()?.joinAll()
    }

    private fun libraryMediaItem(id: String): LibraryMediaItem =
        LibraryMediaItem(
            id = id,
            seriesTitle = "Example Show",
            episodeTitle = "Episode 01",
            relativePath = "Example Show/Episode 01.mkv",
            sizeBytes = 123,
            mediaType = "video/x-matroska",
            streamPath = "/media/$id",
        )

    private class RecordingTvPlaybackController : TvPlaybackController {
        override val androidPlayer: Player? = null
        val loadedPreparations = mutableListOf<LanPlaybackPreparation>()
        val commands = mutableListOf<PlaybackCommand>()

        override fun load(preparation: LanPlaybackPreparation) {
            loadedPreparations += preparation
        }

        override fun dispatch(command: PlaybackCommand) {
            commands += command
        }

        override fun snapshot(): PlaybackSnapshot =
            PlaybackSnapshot()
    }

    private class RecordingTvLibraryDiscovery(
        private val result: List<DiscoveredLanLibraryServer> = emptyList(),
        private val failure: Throwable? = null,
    ) : TvLibraryDiscovery {
        var discoveryRequests = 0
            private set

        override fun discover(): List<DiscoveredLanLibraryServer> {
            discoveryRequests += 1
            failure?.let { throw it }
            return result
        }
    }

    private class RecordingLanLibraryClient(
        private val status: LanLibraryServerStatus = LanLibraryServerStatus(),
        val catalog: LibraryCatalog = LibraryCatalog(
            rootName = "Action PC",
            indexedAtEpochMs = 1_000,
            items = listOf(
                LibraryMediaItem(
                    id = "episode-1",
                    seriesTitle = "Example Show",
                    episodeTitle = "Episode 01",
                    relativePath = "Example Show/Episode 01.mkv",
                    sizeBytes = 123,
                    mediaType = "video/x-matroska",
                    streamPath = "/media/episode-1",
                ),
            ),
        ),
        private val progresses: List<PlaybackProgress> = emptyList(),
        private val catalogError: Throwable? = null,
        private val progressFailure: Throwable? = null,
    ) : LanLibraryClient {
        var statusFetches = 0
            private set
        var catalogFetches = 0
            private set
        var progressFetches = 0
            private set

        override fun fetchServerStatus(baseUrl: String): LanLibraryServerStatus {
            statusFetches += 1
            return status
        }

        override fun fetchCatalog(
            baseUrl: String,
            pairingToken: String,
        ): LibraryCatalog {
            catalogFetches += 1
            catalogError?.let { throw it }
            return catalog
        }

        override fun streamUrl(
            baseUrl: String,
            item: LibraryMediaItem,
            pairingToken: String,
        ): String =
            "$baseUrl${item.streamPath}"

        override fun subtitleUrl(
            baseUrl: String,
            subtitle: LibrarySubtitleTrack,
            pairingToken: String,
        ): String =
            "$baseUrl${subtitle.streamPath}"

        override fun fetchProgress(
            baseUrl: String,
            mediaId: String,
            pairingToken: String,
        ): PlaybackProgress? {
            progressFailure?.let { throw it }
            return progresses.firstOrNull { it.mediaId == mediaId }
        }

        override fun fetchAllProgress(
            baseUrl: String,
            pairingToken: String,
        ): List<PlaybackProgress> {
            progressFetches += 1
            return progresses
        }

        override fun saveProgress(
            baseUrl: String,
            pairingToken: String,
            progress: PlaybackProgress,
        ) = Unit
    }
}
