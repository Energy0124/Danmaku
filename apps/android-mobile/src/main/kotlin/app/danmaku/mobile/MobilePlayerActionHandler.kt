package app.danmaku.mobile

import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackStatus
import app.danmaku.library.LanDanmakuLoader
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.library.LanLibraryConnectionSnapshot
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.LanLibraryClientException
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.LanPlaybackTarget
import app.danmaku.library.android.AndroidLibraryFavoriteStore
import app.danmaku.library.android.AndroidLanLibraryConnectionStore
import app.danmaku.library.android.LanLibraryDiscoveryClient
import app.danmaku.library.android.LanLibraryDiscoveryException
import app.danmaku.library.android.LanExternalTrackingClient
import app.danmaku.library.android.LanExternalTrackingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

internal class MobilePlayerActionHandler(
    private val state: MobilePlayerState,
    private val scope: CoroutineScope,
    private val libraryConnectionSession: LanLibraryConnectionSession,
    private val progressSync: LanPlaybackProgressSync,
    private val playbackPreparer: LanPlaybackPreparer,
    private val danmakuLoader: LanDanmakuLoader,
    private val connectionStore: AndroidLanLibraryConnectionStore,
    private val favoriteStore: AndroidLibraryFavoriteStore,
    private val danmakuSettingsStore: MobileDanmakuSettingsPersistence,
    private val discoveryClient: LanLibraryDiscoveryClient,
    private val trackingClient: LanExternalTrackingClient,
    private val openVideoPicker: () -> Unit,
) {
    private val trackingGeneration = AtomicLong()
    private val folderRefreshGeneration = AtomicLong()
    fun connectToInitialLibrary() {
        if (state.catalog != null) return
        connectToLibrary(
            requestedServerUrl = state.serverUrl,
            requestedPairingToken = state.pairingToken,
            discoverOnFailure = true,
        )
    }

    fun discoverPc() {
        resetFolderRefresh()
        scope.launch {
            runCatching { discoverFirstServerUrl() }
                .onSuccess { connectToLibrary(it, "") }
                .onFailure { state.libraryError = it.message }
        }
    }

    fun connectToLibrary(
        requestedServerUrl: String,
        requestedPairingToken: String,
        fallbackDisplayName: String? = null,
        discoverOnFailure: Boolean = false,
    ) {
        resetFolderRefresh()
        scope.launch {
            runCatching {
                fetchCatalogWithProgress(requestedServerUrl, requestedPairingToken)
            }.onSuccess {
                applyLibraryConnection(
                    requestedServerUrl = requestedServerUrl,
                    requestedPairingToken = requestedPairingToken,
                    fallbackDisplayName = fallbackDisplayName,
                    snapshot = it,
                )
            }.onFailure { failure ->
                if (discoverOnFailure) {
                    connectToDiscoveredLibrary(failure)
                } else {
                    state.libraryError = failure.message
                }
            }
        }
    }

    fun refreshLibrary() {
        connectToLibrary(state.serverUrl, state.pairingToken)
    }

    fun refreshFolder(path: List<String>) {
        val baseUrl = state.serverUrl.trim().trimEnd('/')
        val token = state.pairingToken
        if (baseUrl.isBlank()) {
            state.folderRefreshError = MobileFolderRefreshError.REQUEST_FAILED
            state.folderRefreshErrorDetail = null
            return
        }
        val generation = folderRefreshGeneration.incrementAndGet()
        state.folderRefreshInProgress = true
        state.folderRefreshFilesSeen = null
        state.folderRefreshError = null
        state.folderRefreshErrorDetail = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    libraryConnectionSession.requestFolderRescan(baseUrl, path)
                }
                while (true) {
                    delay(FOLDER_SCAN_POLL_INTERVAL_MS)
                    val status = withContext(Dispatchers.IO) {
                        libraryConnectionSession.validateServer(baseUrl)
                    }
                    if (!isCurrentFolderRefresh(baseUrl, token, generation)) return@launch
                    state.folderRefreshFilesSeen = status.scanFilesSeen
                    if (!status.scanning) {
                        status.scanError?.let { throw FolderScanException(it) }
                        break
                    }
                }
                fetchCatalogWithProgress(baseUrl, token)
            }.onSuccess { snapshot ->
                if (!isCurrentFolderRefresh(baseUrl, token, generation)) return@onSuccess
                state.catalog = snapshot.catalog
                state.playbackProgresses = snapshot.playbackProgresses
                state.libraryError = null
                state.folderRefreshInProgress = false
                state.folderRefreshFilesSeen = null
            }.onFailure { error ->
                if (!isCurrentFolderRefresh(baseUrl, token, generation)) return@onFailure
                state.folderRefreshInProgress = false
                state.folderRefreshError = when {
                    error is LanLibraryClientException && error.statusCode == 409 ->
                        MobileFolderRefreshError.ALREADY_RUNNING
                    error is FolderScanException -> MobileFolderRefreshError.SCAN_FAILED
                    else -> MobileFolderRefreshError.REQUEST_FAILED
                }
                state.folderRefreshErrorDetail = error.message
            }
        }
    }

    private fun isCurrentFolderRefresh(baseUrl: String, token: String, generation: Long): Boolean =
        folderRefreshGeneration.get() == generation &&
            state.serverUrl.trim().trimEnd('/') == baseUrl &&
            state.pairingToken == token

    private fun resetFolderRefresh() {
        folderRefreshGeneration.incrementAndGet()
        state.folderRefreshInProgress = false
        state.folderRefreshFilesSeen = null
        state.folderRefreshError = null
        state.folderRefreshErrorDetail = null
    }

    fun loadTracking() {
        val baseUrl = state.serverUrl.trim().trimEnd('/')
        val token = state.pairingToken
        if (baseUrl.isBlank() || token.isBlank()) {
            state.tracking = MobileTrackingState(
                error = MobileTrackingError.ACCESS_CODE_REQUIRED,
            )
            return
        }
        val generation = trackingGeneration.incrementAndGet()
        state.tracking = state.tracking.copy(isBusy = true, error = null, errorDetail = null)
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    trackingClient.fetchAccounts(baseUrl, token) to
                        trackingClient.fetchTracking(baseUrl, token)
                }
            }.onSuccess { (accounts, document) ->
                if (!isCurrentTrackingRequest(baseUrl, token, generation)) return@onSuccess
                state.tracking = MobileTrackingState(accounts = accounts, document = document)
            }.onFailure { error ->
                if (!isCurrentTrackingRequest(baseUrl, token, generation)) return@onFailure
                state.tracking = state.tracking.copy(
                    isBusy = false,
                    error = trackingError(error),
                    errorDetail = trackingErrorDetail(error),
                )
            }
        }
    }

    fun readTracking() {
        val baseUrl = state.serverUrl.trim().trimEnd('/')
        val token = state.pairingToken
        if (baseUrl.isBlank() || token.isBlank()) return
        val generation = trackingGeneration.incrementAndGet()
        state.tracking = state.tracking.copy(
            isBusy = true,
            hasFreshReadback = false,
            error = null,
            errorDetail = null,
            lastOperation = null,
            lastResponse = null,
        )
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { trackingClient.refreshReadback(baseUrl, token) } }
                .onSuccess { response ->
                    if (!isCurrentTrackingRequest(baseUrl, token, generation)) return@onSuccess
                    state.tracking = state.tracking.copy(
                        document = response.document,
                        isBusy = false,
                        hasFreshReadback = response.errors.isEmpty(),
                        lastOperation = MobileTrackingOperation.READBACK,
                        lastResponse = response,
                    )
                }
                .onFailure { error ->
                    if (!isCurrentTrackingRequest(baseUrl, token, generation)) return@onFailure
                    state.tracking = state.tracking.copy(
                        isBusy = false,
                        error = trackingError(error),
                        errorDetail = trackingErrorDetail(error),
                    )
                    refreshTrackingAccountsAfterFailure(baseUrl, token, generation)
                }
        }
    }

    fun syncTracking() {
        val current = state.tracking
        if (!current.hasFreshReadback) return
        val expectedUpdates = current.document?.plan?.updates?.map { it.update }.orEmpty()
        if (expectedUpdates.isEmpty()) return
        val baseUrl = state.serverUrl.trim().trimEnd('/')
        val token = state.pairingToken
        val generation = trackingGeneration.incrementAndGet()
        state.tracking = current.copy(isBusy = true, hasFreshReadback = false, error = null, errorDetail = null)
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { trackingClient.sync(baseUrl, token, expectedUpdates) } }
                .onSuccess { response ->
                    if (!isCurrentTrackingRequest(baseUrl, token, generation)) return@onSuccess
                    state.tracking = state.tracking.copy(
                        document = response.document,
                        isBusy = false,
                        lastOperation = MobileTrackingOperation.SYNC,
                        lastResponse = response,
                    )
                }
                .onFailure { error ->
                    if (!isCurrentTrackingRequest(baseUrl, token, generation)) return@onFailure
                    state.tracking = state.tracking.copy(
                        isBusy = false,
                        error = if ((error as? LanExternalTrackingException)?.statusCode == 409) {
                            MobileTrackingError.PREVIEW_CHANGED
                        } else {
                            trackingError(error)
                        },
                        errorDetail = trackingErrorDetail(error),
                    )
                    refreshTrackingAccountsAfterFailure(baseUrl, token, generation)
                }
        }
    }

    private suspend fun refreshTrackingAccountsAfterFailure(
        baseUrl: String,
        token: String,
        generation: Long,
    ) {
        val accounts = runCatching {
            withContext(Dispatchers.IO) { trackingClient.fetchAccounts(baseUrl, token) }
        }.getOrNull() ?: return
        if (isCurrentTrackingRequest(baseUrl, token, generation)) {
            state.tracking = state.tracking.copy(accounts = accounts)
        }
    }

    fun invalidateTrackingPreview() {
        trackingGeneration.incrementAndGet()
        state.tracking = state.tracking.copy(
            isBusy = false,
            hasFreshReadback = false,
        )
    }

    private fun isCurrentTrackingRequest(baseUrl: String, token: String, generation: Long): Boolean =
        trackingGeneration.get() == generation &&
        state.serverUrl.trim().trimEnd('/') == baseUrl && state.pairingToken == token

    private fun trackingError(error: Throwable): MobileTrackingError =
        if ((error as? LanExternalTrackingException)?.statusCode == 401) {
            MobileTrackingError.ACCESS_CODE_REJECTED
        } else {
            MobileTrackingError.REQUEST_FAILED
        }

    private fun trackingErrorDetail(error: Throwable): String? {
        val providerError = error as? LanExternalTrackingException ?: return null
        if (providerError.statusCode in setOf(401, 409)) return null
        return providerError.message
            ?.trim()
            ?.lineSequence()
            ?.firstOrNull()
            ?.take(240)
            ?.takeIf(String::isNotBlank)
    }

    private suspend fun connectToDiscoveredLibrary(originalFailure: Throwable) {
        runCatching {
            val discoveredServerUrl = discoverFirstServerUrl()
            val snapshot = fetchCatalogWithProgress(discoveredServerUrl, "")
            discoveredServerUrl to snapshot
        }.onSuccess { (discoveredServerUrl, snapshot) ->
            applyLibraryConnection(
                requestedServerUrl = discoveredServerUrl,
                requestedPairingToken = "",
                fallbackDisplayName = null,
                snapshot = snapshot,
            )
        }.onFailure { discoveryFailure ->
            state.libraryError = listOfNotNull(
                originalFailure.message,
                discoveryFailure.message?.let { "Discovery failed: $it" },
            ).joinToString("; ").ifBlank { "Unable to connect to Windows library server" }
        }
    }

    private suspend fun discoverFirstServerUrl(): String =
        withContext(Dispatchers.IO) {
            discoveryClient.discover().firstOrNull()?.baseUrl
                ?: throw LanLibraryDiscoveryException("No Windows library server discovered")
        }

    private suspend fun fetchCatalogWithProgress(
        baseUrl: String,
        pairingToken: String,
    ): LanLibraryConnectionSnapshot =
        withContext(Dispatchers.IO) {
            libraryConnectionSession.fetchCatalogWithProgress(
                baseUrl = baseUrl,
                pairingToken = pairingToken,
            )
        }

    private fun applyLibraryConnection(
        requestedServerUrl: String,
        requestedPairingToken: String,
        fallbackDisplayName: String?,
        snapshot: LanLibraryConnectionSnapshot,
    ) {
        state.serverUrl = requestedServerUrl.trim().trimEnd('/')
        state.pairingToken = requestedPairingToken
        state.catalog = snapshot.catalog
        state.playbackProgresses = snapshot.playbackProgresses
        connectionStore.saveCurrentConnection(
            baseUrl = requestedServerUrl,
            pairingToken = requestedPairingToken,
            displayName = snapshot.catalog.rootName.ifBlank { fallbackDisplayName },
        )
        state.savedConnections = connectionStore.loadProfiles()
        state.libraryError = null
        state.selectedTab = MobileTab.Library
        loadTracking()
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

    fun playEpisode(item: LibraryMediaItem) {
        val activeController = state.controller
        if (activeController == null) {
            state.playbackError = "Player service is not connected yet."
            state.isPlayerFullscreen = false
            return
        }

        val target = LanPlaybackTarget(state.serverUrl, state.pairingToken, item.id)
        val previousTarget = state.activePlaybackTarget
        if (previousTarget != null && previousTarget != target) {
            val previousSnapshot = activeController.snapshot()
            invalidateTrackingPreview()
            scope.launch(Dispatchers.IO) {
                runCatching { progressSync.saveProgress(previousTarget, previousSnapshot) }
            }
        }
        state.nowPlaying = item
        state.activePlaybackTarget = target
        state.selectedTab = MobileTab.Watch
        state.isPlayerFullscreen = true
        state.playbackStartupPhase = MobilePlaybackStartupPhase.WaitingForDanmaku
        state.danmakuState = MobileDanmakuState.loading(item.id)
        state.libraryError = null

        val danmakuDeferred = scope.async(Dispatchers.IO) {
            runCatching {
                MobileDanmakuState.fromTrack(danmakuLoader.fetchDanmaku(target))
            }.recover { error ->
                MobileDanmakuState.failed(target.mediaId, error)
            }
        }

        scope.launch {
            runCatching {
                val resumePosition = runCatching {
                    withContext(Dispatchers.IO) {
                        progressSync.fetchResumePositionMs(target)
                    }
                }.onFailure {
                    state.libraryError = "Resume lookup failed: ${it.message}"
                }.getOrNull()
                val preparation = playbackPreparer.prepare(
                    baseUrl = target.baseUrl,
                    pairingToken = target.pairingToken,
                    item = item,
                    resumePositionMs = resumePosition,
                )
                if (!state.isCurrentPlayback(target)) return@launch

                activeController.load(preparation)
                preparation.resumePositionMs?.let {
                    activeController.dispatch(PlaybackCommand.SeekTo(it))
                }

                val danmakuResult = withTimeoutOrNull(DANMAKU_PLAYBACK_WAIT_TIMEOUT_MS) {
                    danmakuDeferred.await()
                }
                if (!state.isCurrentPlayback(target)) return@launch

                if (danmakuResult == null) {
                    state.danmakuState = MobileDanmakuState.timedOut(target.mediaId)
                    state.playbackStartupPhase = MobilePlaybackStartupPhase.Playing
                    activeController.dispatch(PlaybackCommand.Play)
                    awaitDanmakuAfterTimeout(target, danmakuDeferred)
                } else {
                    state.danmakuState = danmakuResult.getOrElse { error ->
                        MobileDanmakuState.failed(target.mediaId, error)
                    }
                    state.playbackStartupPhase = MobilePlaybackStartupPhase.Playing
                    activeController.dispatch(PlaybackCommand.Play)
                }
            }.onFailure { error ->
                if (state.isCurrentPlayback(target)) {
                    state.playbackError = error.message
                    state.playbackStartupPhase = MobilePlaybackStartupPhase.Idle
                    state.danmakuState = MobileDanmakuState.Idle
                    state.activePlaybackTarget = null
                    state.isPlayerFullscreen = false
                }
            }
        }
    }

    private fun awaitDanmakuAfterTimeout(
        target: LanPlaybackTarget,
        danmakuDeferred: Deferred<Result<MobileDanmakuState>>,
    ) {
        scope.launch {
            val resolvedState = danmakuDeferred.await().getOrElse { error ->
                MobileDanmakuState.failed(target.mediaId, error)
            }
            if (state.isCurrentPlayback(target)) {
                state.danmakuState = resolvedState
            }
        }
    }

    private fun MobilePlayerState.isCurrentPlayback(target: LanPlaybackTarget): Boolean =
        activePlaybackTarget == target
    fun togglePlayback() {
        if (state.snapshot.status == PlaybackStatus.PLAYING) {
            state.controller?.dispatch(PlaybackCommand.Pause)
        } else {
            state.controller?.dispatch(PlaybackCommand.Play)
        }
    }

    fun showLibraryItem(item: LibraryMediaItem) {
        state.librarySearchText = item.seriesTitle
        state.isPlayerFullscreen = false
        state.selectedTab = MobileTab.Library
    }

    fun selectConnection(connection: LanLibraryConnectionProfile) {
        connectToLibrary(
            requestedServerUrl = connection.baseUrl,
            requestedPairingToken = connection.pairingToken,
            fallbackDisplayName = connection.displayName,
        )
    }

    fun editConnection(connection: LanLibraryConnectionProfile) {
        resetFolderRefresh()
        state.serverUrl = connection.baseUrl
        state.pairingToken = connection.pairingToken
        state.tracking = MobileTrackingState()
    }

    fun forgetConnection(connection: LanLibraryConnectionProfile) {
        connectionStore.forgetProfile(connection.id)
        state.savedConnections = connectionStore.loadProfiles()
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

    fun toAppActions(): MobileAppActions =
        MobileAppActions(
            onTabSelected = {
                state.isPlayerFullscreen = false
                state.selectedTab = it
            },
            onPlay = ::playEpisode,
            onPlayPause = ::togglePlayback,
            onOpenPlayer = { state.selectedTab = MobileTab.Watch },
            onOpenLibrary = {
                state.isPlayerFullscreen = false
                state.selectedTab = MobileTab.Library
            },
            onShowLibraryItem = ::showLibraryItem,
            onConnect = {
                state.isPlayerFullscreen = false
                state.selectedTab = MobileTab.Connect
                loadTracking()
            },
            onOpenVideo = openVideoPicker,
            onSeekTo = { state.controller?.dispatch(PlaybackCommand.SeekTo(it)) },
            onSetVolume = { state.controller?.dispatch(PlaybackCommand.SetVolume(it)) },
            onSetPlaybackRate = { state.controller?.dispatch(PlaybackCommand.SetPlaybackRate(it)) },
            onUpdateDanmakuDisplaySettings = {
                state.danmakuDisplaySettings = it
                danmakuSettingsStore.save(it)
            },
            onSelectAudio = { state.controller?.dispatch(PlaybackCommand.SelectAudioTrack(it)) },
            onSelectSubtitle = { state.controller?.dispatch(PlaybackCommand.SelectSubtitleTrack(it)) },
            onSearchTextChange = { state.librarySearchText = it },
            onSortChange = { state.librarySort = it },
            onSubtitleFilterChange = { state.librarySubtitleFilter = it },
            onFavoriteFilterChange = { state.libraryFavoriteFilter = it },
            onSetFavorite = ::setFavorite,
            onServerUrlChange = {
                resetFolderRefresh()
                state.serverUrl = it
                state.tracking = MobileTrackingState()
            },
            onPairingTokenChange = {
                resetFolderRefresh()
                state.pairingToken = it
                state.tracking = MobileTrackingState()
            },
            onSelectConnection = ::selectConnection,
            onEditConnection = ::editConnection,
            onForgetConnection = ::forgetConnection,
            onSaveConnection = ::saveConnection,
            onDiscover = ::discoverPc,
            onRefresh = ::refreshLibrary,
            onRefreshFolder = ::refreshFolder,
            onTogglePlayerFullscreen = { state.isPlayerFullscreen = !state.isPlayerFullscreen },
            onLoadTracking = ::loadTracking,
            onReadTracking = ::readTracking,
            onSyncTracking = ::syncTracking,
        )

    private companion object {
        const val DANMAKU_PLAYBACK_WAIT_TIMEOUT_MS = 15_000L
        const val FOLDER_SCAN_POLL_INTERVAL_MS = 750L
    }
}

private class FolderScanException(message: String) : RuntimeException(message)
