package app.danmaku.tv

import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackStatus
import app.danmaku.library.LanDanmakuLoader
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.LanPlaybackTarget
import app.danmaku.library.android.AndroidLanLibraryConnectionStore
import app.danmaku.library.android.AndroidLibraryFavoriteStore
import app.danmaku.library.android.LanLibraryDiscoveryException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal class TvPlayerActionHandler(
    private val state: TvPlayerState,
    private val scope: CoroutineScope,
    private val libraryConnectionSession: LanLibraryConnectionSession,
    private val progressSync: LanPlaybackProgressSync,
    private val playbackPreparer: LanPlaybackPreparer,
    private val danmakuLoader: LanDanmakuLoader,
    private val connectionStore: AndroidLanLibraryConnectionStore,
    private val favoriteStore: AndroidLibraryFavoriteStore,
    private val libraryDiscovery: TvLibraryDiscovery,
    private val danmakuPlaybackWaitTimeoutMs: Long = DANMAKU_PLAYBACK_WAIT_TIMEOUT_MS,
) {
    fun refreshLibrary() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    libraryConnectionSession.fetchCatalogWithProgress(
                        baseUrl = state.serverUrl,
                        pairingToken = state.pairingToken,
                    )
                }
            }.onSuccess {
                state.catalog = it.catalog
                state.playbackProgresses = it.playbackProgresses
                connectionStore.saveCurrentConnection(
                    baseUrl = state.serverUrl,
                    pairingToken = state.pairingToken,
                    displayName = it.catalog.rootName,
                )
                state.savedConnections = connectionStore.loadProfiles()
                state.libraryError = null
                state.selectedDestination = TvDestination.Library
            }.onFailure {
                state.libraryError = it.message
            }
        }
    }

    fun discoverPc() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    libraryDiscovery.discover().firstOrNull()
                        ?: throw LanLibraryDiscoveryException("No Windows library server discovered")
                }
            }.onSuccess {
                state.serverUrl = it.baseUrl
                state.libraryError = null
            }.onFailure {
                state.libraryError = it.message
            }
        }
    }

    fun saveConnection() {
        runCatching {
            connectionStore.saveCurrentConnection(
                baseUrl = state.serverUrl,
                pairingToken = state.pairingToken,
                displayName = state.catalog?.rootName,
            )
        }.onSuccess {
            state.savedConnections = connectionStore.loadProfiles()
            state.libraryError = null
        }.onFailure {
            state.libraryError = it.message
        }
    }

    fun selectConnection(connection: LanLibraryConnectionProfile) {
        state.serverUrl = connection.baseUrl
        state.pairingToken = connection.pairingToken
    }

    fun forgetConnection(connection: LanLibraryConnectionProfile) {
        connectionStore.forgetProfile(connection.id)
        state.savedConnections = connectionStore.loadProfiles()
    }

    fun setFavorite(item: LibraryMediaItem, isFavorite: Boolean) {
        runCatching {
            favoriteStore.setFavoriteMediaId(item.id, isFavorite)
        }.onSuccess {
            state.favoriteMediaIds = it
            state.libraryError = null
        }.onFailure {
            state.libraryError = it.message
        }
    }

    fun playItem(item: LibraryMediaItem) {
        val activeController = state.controller ?: return
        val target = LanPlaybackTarget(state.serverUrl, state.pairingToken, item.id)
        state.activePlaybackItem = item
        state.activePlaybackTarget = target
        state.isFullscreenPlayback = true
        state.playbackControlsVisible = false
        state.playbackStartupPhase = TvPlaybackStartupPhase.WaitingForDanmaku
        state.danmakuState = TvDanmakuState.loading(item.id)
        state.libraryError = null

        val danmakuDeferred = scope.async(Dispatchers.IO) {
            runCatching {
                TvDanmakuState.fromTrack(danmakuLoader.fetchDanmaku(target))
            }.recover { error ->
                TvDanmakuState.failed(target.mediaId, error)
            }
        }

        scope.launch {
            runCatching {
                val preparation = prepareTvLibraryItem(
                    progressSync = progressSync,
                    playbackPreparer = playbackPreparer,
                    target = target,
                    item = item,
                    onResumeLookupFailure = {
                        state.libraryError = "Resume lookup failed: ${it.message}"
                    },
                )
                if (!state.isCurrentPlayback(target)) return@launch

                loadPreparedTvLibraryItem(activeController, preparation)
                val danmakuResult = withTimeoutOrNull(danmakuPlaybackWaitTimeoutMs) {
                    danmakuDeferred.await()
                }
                if (!state.isCurrentPlayback(target)) return@launch

                if (danmakuResult == null) {
                    state.danmakuState = TvDanmakuState.timedOut(target.mediaId)
                    state.playbackStartupPhase = TvPlaybackStartupPhase.Playing
                    startLoadedTvLibraryItem(activeController)
                    awaitDanmakuAfterTimeout(target, danmakuDeferred)
                } else {
                    state.danmakuState = danmakuResult.getOrElse { error ->
                        TvDanmakuState.failed(target.mediaId, error)
                    }
                    state.playbackStartupPhase = TvPlaybackStartupPhase.Playing
                    startLoadedTvLibraryItem(activeController)
                }
            }.onFailure { error ->
                if (state.isCurrentPlayback(target)) {
                    state.libraryError = error.message
                    state.playbackStartupPhase = TvPlaybackStartupPhase.Idle
                    state.isFullscreenPlayback = false
                    state.activePlaybackItem = null
                    state.activePlaybackTarget = null
                    state.danmakuState = TvDanmakuState.Idle
                }
            }
        }
    }

    fun showPlaybackControls() {
        if (state.isFullscreenPlayback && state.playbackStartupPhase != TvPlaybackStartupPhase.Stopping) {
            state.playbackControlsVisible = true
        }
    }

    fun hidePlaybackControls() {
        if (state.playbackStartupPhase == TvPlaybackStartupPhase.Playing) {
            state.playbackControlsVisible = false
        }
    }

    fun togglePlayPause() {
        val controller = state.controller ?: return
        val isPlaying = state.snapshot.status == PlaybackStatus.PLAYING
        controller.dispatch(if (isPlaying) PlaybackCommand.Pause else PlaybackCommand.Play)
        showPlaybackControls()
    }

    fun handlePlaybackBack() {
        if (!state.isFullscreenPlayback) return
        if (!state.playbackControlsVisible) {
            showPlaybackControls()
            return
        }
        stopPlaybackAndReturn()
    }

    fun stopPlaybackAndReturn() {
        val controller = state.controller ?: return
        val target = state.activePlaybackTarget
        val snapshot = controller.snapshot()
        state.playbackStartupPhase = TvPlaybackStartupPhase.Stopping
        scope.launch {
            if (target != null) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        progressSync.saveProgress(target, snapshot)
                        progressSync.fetchAllProgress(target.baseUrl, target.pairingToken)
                    }
                }.onSuccess {
                    state.playbackProgresses = it
                    state.libraryError = null
                }.onFailure {
                    state.libraryError = "Progress save failed: ${it.message}"
                }
            }
            controller.stop()
            state.snapshot = controller.snapshot()
            state.activePlaybackItem = null
            state.activePlaybackTarget = null
            state.isFullscreenPlayback = false
            state.playbackControlsVisible = false
            state.playbackStartupPhase = TvPlaybackStartupPhase.Idle
            state.danmakuState = TvDanmakuState.Idle
        }
    }

    private fun awaitDanmakuAfterTimeout(
        target: LanPlaybackTarget,
        danmakuDeferred: Deferred<Result<TvDanmakuState>>,
    ) {
        scope.launch {
            val resolvedState = danmakuDeferred.await().getOrElse { error ->
                TvDanmakuState.failed(target.mediaId, error)
            }
            if (state.isCurrentPlayback(target)) {
                state.danmakuState = resolvedState
            }
        }
    }

    private fun TvPlayerState.isCurrentPlayback(target: LanPlaybackTarget): Boolean =
        activePlaybackTarget == target && isFullscreenPlayback

    private companion object {
        const val DANMAKU_PLAYBACK_WAIT_TIMEOUT_MS = 15_000L
    }
}
