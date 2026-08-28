package app.danmaku.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackStatus
import app.danmaku.domain.nextItem
import app.danmaku.library.LanPlaybackTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class TvPlaybackViewModel(
    private val repository: TvPlaybackSession,
    private val navigator: TvNavigator,
    private val gateway: TvPlaybackGateway,
    private val preferencesStore: TvDanmakuPreferencesPersistence,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        TvPlaybackUiState(danmakuPreferences = preferencesStore.load()),
    )
    val state: StateFlow<TvPlaybackUiState> = mutableState.asStateFlow()

    private var controller: TvPlaybackController? = null
    private var pendingPlayItem: LibraryMediaItem? = null
    private var playbackGeneration = 0L
    private var preparationJob: Job? = null
    private var danmakuJob: Job? = null
    private var positionJob: Job? = null
    private var playerVisible = false
    private val playerListener = object : Player.Listener {
        override fun onEvents(
            player: Player,
            events: Player.Events,
        ) {
            updateSnapshot()
        }
    }

    fun attachController(value: TvPlaybackController) {
        if (controller === value) return
        controller?.androidPlayer?.removeListener(playerListener)
        controller = value
        value.androidPlayer?.addListener(playerListener)
        mutableState.update {
            it.copy(
                controllerReady = true,
                snapshot = value.snapshot(),
                error = null,
            )
        }
        val pendingItem = pendingPlayItem
        pendingPlayItem = null
        if (pendingItem == null) {
            updatePositionSampling()
        } else {
            play(pendingItem)
        }
    }

    fun detachController() {
        controller?.androidPlayer?.removeListener(playerListener)
        controller = null
        positionJob?.cancel()
        positionJob = null
        mutableState.update { it.copy(controllerReady = false) }
    }

    fun androidPlayer(): Player? = controller?.androidPlayer

    fun setPlayerVisible(visible: Boolean) {
        playerVisible = visible
        updatePositionSampling()
    }

    fun play(item: LibraryMediaItem) {
        val activeController = controller
        if (activeController == null) {
            pendingPlayItem = item
            mutableState.update { it.copy(error = TvPlaybackError.ControllerConnecting) }
            return
        }
        pendingPlayItem = null
        val previousTarget = mutableState.value.target
        if (previousTarget != null) {
            val previousSnapshot = activeController.snapshot()
            viewModelScope.launch {
                runCatching { gateway.saveProgress(previousTarget, previousSnapshot) }
            }
        }
        val session = repository.state.value
        val target = LanPlaybackTarget(session.serverUrl, item.id)
        playbackGeneration += 1
        val generation = playbackGeneration
        preparationJob?.cancel()
        danmakuJob?.cancel()
        mutableState.value = TvPlaybackUiState(
            controllerReady = true,
            snapshot = activeController.snapshot(),
            item = item,
            target = target,
            startupPhase = TvPlaybackStartupPhase.PreparingMedia,
            controlsVisible = true,
            danmaku = TvDanmakuState.loading(item.id),
            danmakuPreferences = mutableState.value.danmakuPreferences,
            nextItem = session.catalog?.nextItem(item.id),
        )
        navigator.navigate(TvRoute.Player(item.id))

        danmakuJob = viewModelScope.launch {
            val resolved = gateway.loadDanmaku(target)
            if (generation == playbackGeneration && mutableState.value.target == target) {
                mutableState.update { it.copy(danmaku = resolved) }
            }
            if (
                resolved.phase == TvDanmakuPhase.NoMatch &&
                generation == playbackGeneration &&
                mutableState.value.target == target
            ) {
                mutableState.update { it.copy(danmaku = TvDanmakuState.loading(item.id)) }
                val refreshed = gateway.loadDanmaku(target, forceRefresh = true)
                if (generation == playbackGeneration && mutableState.value.target == target) {
                    mutableState.update { it.copy(danmaku = refreshed) }
                }
            }
        }

        preparationJob = viewModelScope.launch {
            runCatching {
                gateway.prepare(
                    target = target,
                    item = item,
                    onResumeLookupFailure = {
                        if (generation == playbackGeneration) {
                            mutableState.update { it.copy(error = TvPlaybackError.ResumeLookupFailed) }
                        }
                    },
                )
            }.onSuccess { preparation ->
                if (generation != playbackGeneration || mutableState.value.target != target) {
                    return@onSuccess
                }
                loadPreparedTvLibraryItem(activeController, preparation)
                startLoadedTvLibraryItem(activeController)
                mutableState.update {
                    it.copy(
                        startupPhase = TvPlaybackStartupPhase.Playing,
                        snapshot = activeController.snapshot(),
                    )
                }
                updatePositionSampling()
            }.onFailure { error ->
                if (generation == playbackGeneration) {
                    mutableState.update {
                        it.copy(
                            startupPhase = TvPlaybackStartupPhase.Idle,
                            error = TvPlaybackError.PreparationFailed,
                        )
                    }
                }
            }
        }
    }

    fun dispatch(command: PlaybackCommand) {
        val activeController = controller ?: return
        activeController.dispatch(command)
        mutableState.update {
            it.copy(
                snapshot = activeController.snapshot(),
                controlsVisible = true,
                discontinuityGeneration = it.discontinuityGeneration +
                    if (command is PlaybackCommand.SeekTo) 1L else 0L,
            )
        }
    }

    fun togglePlayPause() {
        val command = if (mutableState.value.snapshot.status == PlaybackStatus.PLAYING) {
            PlaybackCommand.Pause
        } else {
            PlaybackCommand.Play
        }
        dispatch(command)
    }

    fun showControls() {
        mutableState.update { it.copy(controlsVisible = true) }
    }

    fun hideControls() {
        if (mutableState.value.startupPhase == TvPlaybackStartupPhase.Playing) {
            mutableState.update { it.copy(controlsVisible = false) }
        }
    }

    fun updateDanmakuPreferences(transform: (TvDanmakuPreferences) -> TvDanmakuPreferences) {
        val updated = transform(mutableState.value.danmakuPreferences)
        preferencesStore.save(updated)
        mutableState.update { it.copy(danmakuPreferences = updated) }
    }

    fun stopAndReturn() {
        val activeController = controller ?: return
        val current = mutableState.value
        val target = current.target
        val snapshot = activeController.snapshot()
        playbackGeneration += 1
        preparationJob?.cancel()
        danmakuJob?.cancel()
        activeController.stop()
        mutableState.value = TvPlaybackUiState(
            controllerReady = true,
            snapshot = activeController.snapshot(),
            danmakuPreferences = current.danmakuPreferences,
        )
        navigator.back()

        if (target != null) {
            viewModelScope.launch {
                val updatedProgresses = runCatching {
                    gateway.saveProgressAndRefresh(target, snapshot)
                }.getOrNull()
                if (updatedProgresses != null) {
                    repository.updateProgresses(target, updatedProgresses)
                }
            }
        }
    }

    fun handleBack(): Boolean {
        if (!mutableState.value.isActive) return false
        if (mutableState.value.controlsVisible) {
            mutableState.update { it.copy(controlsVisible = false) }
        } else {
            stopAndReturn()
        }
        return true
    }

    private fun updateSnapshot() {
        val activeController = controller ?: return
        mutableState.update { it.copy(snapshot = activeController.snapshot()) }
    }

    private fun updatePositionSampling() {
        positionJob?.cancel()
        positionJob = null
        if (!playerVisible || controller == null) return
        positionJob = viewModelScope.launch {
            while (isActive && playerVisible) {
                updateSnapshot()
                delay(POSITION_SAMPLE_INTERVAL_MS)
            }
        }
    }

    override fun onCleared() {
        detachController()
    }

    private companion object {
        const val POSITION_SAMPLE_INTERVAL_MS = 250L
    }
}
