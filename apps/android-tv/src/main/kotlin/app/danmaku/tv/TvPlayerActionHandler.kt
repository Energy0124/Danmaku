package app.danmaku.tv

import app.danmaku.domain.LibraryMediaItem
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.android.AndroidLibraryFavoriteStore
import app.danmaku.library.android.AndroidLanLibraryConnectionStore
import app.danmaku.library.android.DiscoveredLanLibraryServer
import app.danmaku.library.android.LanLibraryDiscoveryException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun interface TvLibraryDiscovery {
    fun discover(): List<DiscoveredLanLibraryServer>
}

internal class TvPlayerActionHandler(
    private val state: TvPlayerState,
    private val scope: CoroutineScope,
    private val libraryConnectionSession: LanLibraryConnectionSession,
    private val progressSync: LanPlaybackProgressSync,
    private val playbackPreparer: LanPlaybackPreparer,
    private val connectionStore: AndroidLanLibraryConnectionStore,
    private val favoriteStore: AndroidLibraryFavoriteStore,
    private val libraryDiscovery: TvLibraryDiscovery,
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
        scope.launch {
            playTvLibraryItem(
                controller = activeController,
                progressSync = progressSync,
                playbackPreparer = playbackPreparer,
                baseUrl = state.serverUrl,
                pairingToken = state.pairingToken,
                item = item,
                onResumeLookupFailure = {
                    state.libraryError = "Resume lookup failed: ${it.message}"
                },
            )
        }
    }
}
