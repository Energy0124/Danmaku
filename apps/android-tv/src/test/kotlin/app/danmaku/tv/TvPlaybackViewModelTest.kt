package app.danmaku.tv

import androidx.media3.common.Player
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.PlaybackStatus
import app.danmaku.library.LanPlaybackPreparation
import app.danmaku.library.LanPlaybackTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvPlaybackViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun videoStartsWhileDanmakuIsStillPendingThenAcceptsFailure() = runTest(dispatcher) {
        val item = item("one")
        val session = FakeSession(item)
        val gateway = FakeGateway()
        val controller = RecordingController()
        val viewModel = viewModel(session, gateway, controller)

        viewModel.play(item)
        runCurrent()

        assertTrue(PlaybackCommand.Play in controller.commands)
        assertEquals(TvPlaybackStartupPhase.Playing, viewModel.state.value.startupPhase)
        assertEquals(TvDanmakuPhase.Loading, viewModel.state.value.danmaku.phase)

        gateway.danmaku(item.id).complete(TvDanmakuState.failed(item.id, IllegalStateException("offline")))
        advanceUntilIdle()

        assertEquals(TvDanmakuPhase.Failed, viewModel.state.value.danmaku.phase)
    @Test
    fun missingDanmakuForcesRefreshAndAttachesWhenReady() = runTest(dispatcher) {
        val item = item("missing")
        val session = FakeSession(item)
        val gateway = FakeGateway()
        val controller = RecordingController()
        val viewModel = viewModel(session, gateway, controller)

        viewModel.play(item)
        runCurrent()
        gateway.danmaku(item.id).complete(
            TvDanmakuState(mediaId = item.id, phase = TvDanmakuPhase.NoMatch),
        )
        runCurrent()

        assertEquals(
            listOf(item.id to false, item.id to true),
            gateway.danmakuRequests,
        )
        assertEquals(TvDanmakuPhase.Loading, viewModel.state.value.danmaku.phase)

        gateway.danmaku(item.id, forceRefresh = true).complete(
            TvDanmakuState(mediaId = item.id, phase = TvDanmakuPhase.Ready),
        )
        advanceUntilIdle()

        assertEquals(TvDanmakuPhase.Ready, viewModel.state.value.danmaku.phase)
    }

        assertEquals(1, controller.loaded.size)
    }

    @Test
    fun lateDanmakuAndPreparationFromPreviousItemAreRejected() = runTest(dispatcher) {
        val first = item("first")
        val second = item("second")
        val session = FakeSession(first, second)
        val gateway = FakeGateway(deferPreparation = true)
        val controller = RecordingController()
        val viewModel = viewModel(session, gateway, controller)

        viewModel.play(first)
        runCurrent()
        viewModel.play(second)
        runCurrent()

        gateway.preparation(first.id).complete(first.preparation())
        gateway.danmaku(first.id).complete(TvDanmakuState.failed(first.id, IllegalStateException("late")))
        gateway.preparation(second.id).complete(second.preparation())
        gateway.danmaku(second.id).complete(TvDanmakuState.loading(second.id))
        advanceUntilIdle()

        assertEquals(listOf(second.id), controller.loaded.map { it.item.id })
        assertEquals(second.id, viewModel.state.value.item?.id)
        assertEquals(second.id, viewModel.state.value.danmaku.mediaId)
        assertEquals(null, viewModel.state.value.error)
    }

    @Test
    fun stopPersistsProgressAndDanmakuPreferences() = runTest(dispatcher) {
        val item = item("persisted")
        val expectedProgress = PlaybackProgress(
            mediaId = item.id,
            positionMs = 42_000,
            durationMs = 120_000,
            updatedAtEpochMs = 123,
        )
        val session = FakeSession(item)
        val gateway = FakeGateway(savedProgresses = listOf(expectedProgress))
        val controller = RecordingController()
        val preferences = InMemoryPreferences()
        val viewModel = TvPlaybackViewModel(
            repository = session,
            navigator = TvNavigator(TvRoute.Home),
            gateway = gateway,
            preferencesStore = preferences,
        ).also { it.attachController(controller) }

        viewModel.play(item)
        runCurrent()
        viewModel.updateDanmakuPreferences { it.copy(opacity = 0.7f) }
        viewModel.stopAndReturn()
        advanceUntilIdle()

        assertEquals(item.id, gateway.savedTarget?.mediaId)
        assertEquals(PlaybackStatus.PLAYING, gateway.savedSnapshot?.status)
        assertEquals(listOf(expectedProgress), session.savedProgresses)
        assertEquals(0.7f, preferences.load().opacity)
    }

    private fun viewModel(
        session: FakeSession,
        gateway: FakeGateway,
        controller: RecordingController,
    ): TvPlaybackViewModel =
        TvPlaybackViewModel(
            repository = session,
            navigator = TvNavigator(TvRoute.Home),
            gateway = gateway,
            preferencesStore = InMemoryPreferences(),
        ).also { it.attachController(controller) }

    private fun item(id: String): LibraryMediaItem =
        LibraryMediaItem(
            id = id,
            seriesTitle = "Series",
            episodeTitle = "Episode $id",
            relativePath = "Series/$id.mkv",
            sizeBytes = 1,
            mediaType = "video/x-matroska",
            streamPath = "/media/$id",
        )

    private fun LibraryMediaItem.preparation(): LanPlaybackPreparation {
        val target = LanPlaybackTarget("http://pc", "token", id)
        return LanPlaybackPreparation(
            item = this,
            target = target,
            source = PlaybackSource.RemoteStream("http://pc/media/$id"),
            resumePositionMs = null,
        )
    }

    private class FakeSession(vararg items: LibraryMediaItem) : TvPlaybackSession {
        override val state: StateFlow<TvSessionUiState> = MutableStateFlow(
            TvSessionUiState(
                serverUrl = "http://pc",
                pairingToken = "token",
                catalog = LibraryCatalog("PC", 1, items.toList()),
            ),
        )
        var savedProgresses: List<PlaybackProgress> = emptyList()

        override suspend fun updateProgresses(progresses: List<PlaybackProgress>) {
            savedProgresses = progresses
        }
    }

    private inner class FakeGateway(
        private val deferPreparation: Boolean = false,
        private val savedProgresses: List<PlaybackProgress> = emptyList(),
    ) : TvPlaybackGateway {
        private val preparations = mutableMapOf<String, CompletableDeferred<LanPlaybackPreparation>>()
        private val danmaku = mutableMapOf<String, CompletableDeferred<TvDanmakuState>>()
        var savedTarget: LanPlaybackTarget? = null
        val danmakuRequests = mutableListOf<Pair<String, Boolean>>()
        var savedSnapshot: PlaybackSnapshot? = null

        fun preparation(id: String) =
            preparations.getOrPut(id) { CompletableDeferred() }

        fun danmaku(
            id: String,
            forceRefresh: Boolean = false,
        ) = danmaku.getOrPut("$id:$forceRefresh") { CompletableDeferred() }

        override suspend fun prepare(
            target: LanPlaybackTarget,
            item: LibraryMediaItem,
            onResumeLookupFailure: (Throwable) -> Unit,
        ): LanPlaybackPreparation =
            if (deferPreparation) preparation(item.id).await() else item.preparation()

        override suspend fun loadDanmaku(
            target: LanPlaybackTarget,
            forceRefresh: Boolean,
        ): TvDanmakuState {
            danmakuRequests += target.mediaId to forceRefresh
            return danmaku(target.mediaId, forceRefresh).await()
        }

        override suspend fun saveProgressAndRefresh(
            target: LanPlaybackTarget,
            snapshot: PlaybackSnapshot,
        ): List<PlaybackProgress> {
            savedTarget = target
            savedSnapshot = snapshot
            return savedProgresses
        }
    }

    private class RecordingController : TvPlaybackController {
        override val androidPlayer: Player? = null
        val commands = mutableListOf<PlaybackCommand>()
        val loaded = mutableListOf<LanPlaybackPreparation>()
        private var snapshot = PlaybackSnapshot()

        override fun load(preparation: LanPlaybackPreparation) {
            loaded += preparation
            snapshot = snapshot.copy(
                source = preparation.source,
                status = PlaybackStatus.READY,
            )
        }

        override fun dispatch(command: PlaybackCommand) {
            commands += command
            snapshot = when (command) {
                PlaybackCommand.Play -> snapshot.copy(status = PlaybackStatus.PLAYING)
                PlaybackCommand.Pause -> snapshot.copy(status = PlaybackStatus.PAUSED)
                else -> snapshot
            }
        }

        override fun stop() {
            snapshot = PlaybackSnapshot()
        }

        override fun snapshot(): PlaybackSnapshot = snapshot
    }

    private class InMemoryPreferences : TvDanmakuPreferencesPersistence {
        private var value = TvDanmakuPreferences()

        override fun load(): TvDanmakuPreferences = value

        override fun save(value: TvDanmakuPreferences) {
            this.value = value
        }
    }
}
