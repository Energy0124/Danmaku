package app.danmaku.mobile

import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackStatus
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.library.LanLibraryConnectionSnapshot
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.LanPlaybackTarget
import app.danmaku.library.android.AndroidLibraryFavoriteStore
import app.danmaku.library.android.AndroidLanLibraryConnectionStore
import app.danmaku.library.android.LanLibraryDiscoveryClient
import app.danmaku.library.android.LanLibraryDiscoveryException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class MobilePlayerActionHandler(
    private val state: MobilePlayerState,
    private val scope: CoroutineScope,
    private val libraryConnectionSession: LanLibraryConnectionSession,
    private val progressSync: LanPlaybackProgressSync,
    private val playbackPreparer: LanPlaybackPreparer,
    private val connectionStore: AndroidLanLibraryConnectionStore,
    private val favoriteStore: AndroidLibraryFavoriteStore,
    private val discoveryClient: LanLibraryDiscoveryClient,
    private val openVideoPicker: () -> Unit,
) {
    fun connectToInitialLibrary() {
        if (state.catalog != null) return
        connectToLibrary(
            requestedServerUrl = state.serverUrl,
            requestedPairingToken = state.pairingToken,
            discoverOnFailure = true,
        )
    }

    fun discoverPc() {
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
        state.nowPlaying = item
        state.selectedTab = MobileTab.Watch
        state.isPlayerFullscreen = true
        scope.launch {
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
            activeController.load(preparation)
            preparation.resumePositionMs?.let {
                activeController.dispatch(PlaybackCommand.SeekTo(it))
            }
            activeController.dispatch(PlaybackCommand.Play)
        }
    }

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
        state.serverUrl = connection.baseUrl
        state.pairingToken = connection.pairingToken
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
            },
            onOpenVideo = openVideoPicker,
            onSeekTo = { state.controller?.dispatch(PlaybackCommand.SeekTo(it)) },
            onSetVolume = { state.controller?.dispatch(PlaybackCommand.SetVolume(it)) },
            onSelectAudio = { state.controller?.dispatch(PlaybackCommand.SelectAudioTrack(it)) },
            onSelectSubtitle = { state.controller?.dispatch(PlaybackCommand.SelectSubtitleTrack(it)) },
            onSearchTextChange = { state.librarySearchText = it },
            onSortChange = { state.librarySort = it },
            onSubtitleFilterChange = { state.librarySubtitleFilter = it },
            onFavoriteFilterChange = { state.libraryFavoriteFilter = it },
            onSetFavorite = ::setFavorite,
            onServerUrlChange = { state.serverUrl = it },
            onPairingTokenChange = { state.pairingToken = it },
            onSelectConnection = ::selectConnection,
            onEditConnection = ::editConnection,
            onForgetConnection = ::forgetConnection,
            onSaveConnection = ::saveConnection,
            onDiscover = ::discoverPc,
            onRefresh = ::refreshLibrary,
            onTogglePlayerFullscreen = { state.isPlayerFullscreen = !state.isPlayerFullscreen },
        )
}
