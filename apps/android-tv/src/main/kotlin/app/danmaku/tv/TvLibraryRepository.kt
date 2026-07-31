package app.danmaku.tv

import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackProgress
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.android.AndroidLanLibraryConnectionStore
import app.danmaku.library.android.AndroidLibraryFavoriteStore
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

internal class TvLibraryRepository(
    private val connectionSession: LanLibraryConnectionSession,
    private val connectionStore: AndroidLanLibraryConnectionStore,
    private val favoriteStore: AndroidLibraryFavoriteStore,
    private val catalogCache: TvCatalogCache,
    defaultServerUrl: String,
    defaultPairingToken: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): TvPlaybackSession {
    private val refreshGeneration = AtomicLong()
    private val initialConnections = connectionStore.loadProfiles()
    private val mutableState = MutableStateFlow(
        TvSessionUiState(
            savedConnections = initialConnections,
            serverUrl = initialConnections.firstOrNull()?.baseUrl ?: defaultServerUrl.trim(),
            pairingToken = initialConnections.firstOrNull()?.pairingToken ?: defaultPairingToken.trim(),
            favoriteMediaIds = favoriteStore.loadFavoriteMediaIds(),
        ),
    )
    override val state: StateFlow<TvSessionUiState> = mutableState.asStateFlow()
    var isQaFixtureInstalled: Boolean = false
        private set

    fun updateServerUrl(serverUrl: String) {
        invalidateRefresh()
        mutableState.update {
            it.copy(serverUrl = serverUrl, isRefreshing = false, errorMessage = null)
        }
    }

    fun updatePairingToken(pairingToken: String) {
        invalidateRefresh()
        mutableState.update {
            it.copy(pairingToken = pairingToken, isRefreshing = false, errorMessage = null)
        }
    }

    suspend fun loadCachedCatalog(): Boolean {
        val serverUrl = state.value.serverUrl
        if (serverUrl.isBlank()) return false
        val cached = catalogCache.load(serverUrl) ?: return false
        if (state.value.serverUrl != serverUrl) return false
        mutableState.update {
            it.copy(
                catalog = cached.catalog,
                playbackProgresses = cached.playbackProgresses,
                catalogSource = TvCatalogSource.Cache,
                isOffline = false,
                errorMessage = null,
            )
        }
        return true
    }

    suspend fun refresh(): Result<Unit> {
        val request = state.value
        if (request.serverUrl.isBlank()) {
            val error = IllegalArgumentException("PC server URL is required")
            mutableState.update { it.copy(errorMessage = error.message) }
            return Result.failure(error)
        }
        val generation = refreshGeneration.incrementAndGet()
        mutableState.update { it.copy(isRefreshing = true, errorMessage = null) }
        return runCatching {
            withContext(ioDispatcher) {
                connectionSession.fetchCatalogWithProgress(
                    baseUrl = request.serverUrl,
                    pairingToken = request.pairingToken,
                )
            }
        }.map { snapshot ->
            if (refreshGeneration.get() == generation) {
                catalogCache.save(
                    serverUrl = request.serverUrl,
                    catalog = snapshot.catalog,
                    playbackProgresses = snapshot.playbackProgresses,
                )
                if (refreshGeneration.get() != generation) return@map
                withContext(ioDispatcher) {
                    connectionStore.saveCurrentConnection(
                        baseUrl = request.serverUrl,
                        pairingToken = request.pairingToken,
                        displayName = snapshot.catalog.rootName,
                    )
                }
                if (refreshGeneration.get() == generation) {
                    mutableState.update {
                        it.copy(
                            savedConnections = connectionStore.loadProfiles(),
                            catalog = snapshot.catalog,
                            playbackProgresses = snapshot.playbackProgresses,
                            catalogSource = TvCatalogSource.Network,
                            isRefreshing = false,
                            isOffline = false,
                            errorMessage = null,
                        )
                    }
                }
            }
        }.onFailure { error ->
            if (refreshGeneration.get() == generation) {
                mutableState.update {
                    it.copy(
                        isRefreshing = false,
                        isOffline = it.catalog != null,
                        errorMessage = error.message,
                    )
                }
            }
        }
    }

    suspend fun saveConnection(): Result<Unit> =
        runCatching {
            val current = state.value
            withContext(ioDispatcher) {
                connectionStore.saveCurrentConnection(
                    baseUrl = current.serverUrl,
                    pairingToken = current.pairingToken,
                    displayName = current.catalog?.rootName,
                )
            }
            mutableState.update {
                it.copy(
                    savedConnections = connectionStore.loadProfiles(),
                    errorMessage = null,
                )
            }
        }.onFailure { error ->
            mutableState.update { it.copy(errorMessage = error.message) }
        }

    suspend fun selectConnection(connection: LanLibraryConnectionProfile) {
        invalidateRefresh()
        mutableState.update {
            it.copy(
                serverUrl = connection.baseUrl,
                pairingToken = connection.pairingToken,
                catalog = null,
                playbackProgresses = emptyList(),
                catalogSource = TvCatalogSource.None,
                isOffline = false,
                errorMessage = null,
            )
        }
        loadCachedCatalog()
    }

    suspend fun forgetConnection(connection: LanLibraryConnectionProfile) {
        invalidateRefresh()
        withContext(ioDispatcher) {
            connectionStore.forgetProfile(connection.id)
            catalogCache.clear(connection.baseUrl)
        }
        mutableState.update { current ->
            val saved = connectionStore.loadProfiles()
            if (current.serverUrl.trim().trimEnd('/') == connection.normalizedBaseUrl) {
                current.copy(
                    savedConnections = saved,
                    serverUrl = saved.firstOrNull()?.baseUrl.orEmpty(),
                    pairingToken = saved.firstOrNull()?.pairingToken.orEmpty(),
                    catalog = null,
                    playbackProgresses = emptyList(),
                    catalogSource = TvCatalogSource.None,
                    isRefreshing = false,
                )
            } else {
                current.copy(savedConnections = saved, isRefreshing = false)
            }
        }
    }

    fun installQaFixture(fixture: TvQaFixture) {
        invalidateRefresh()
        isQaFixtureInstalled = true
        mutableState.value = TvSessionUiState(
            serverUrl = "http://10.0.2.2:18688",
            pairingToken = "qa-fixture",
            catalog = fixture.catalog,
            playbackProgresses = fixture.progresses,
            favoriteMediaIds = fixture.favorites,
            catalogSource = TvCatalogSource.Cache,
            errorMessage = null,
        )
    }

    fun setFavorite(
        item: LibraryMediaItem,
        isFavorite: Boolean,
    ) {
        runCatching {
            favoriteStore.setFavoriteMediaId(item.id, isFavorite)
        }.onSuccess { favorites ->
            mutableState.update {
                it.copy(favoriteMediaIds = favorites, errorMessage = null)
            }
        }.onFailure { error ->
            mutableState.update { it.copy(errorMessage = error.message) }
        }
    }

    override suspend fun updateProgresses(progresses: List<PlaybackProgress>) {
        val current = state.value
        mutableState.update { it.copy(playbackProgresses = progresses) }
        current.catalog?.let { catalog ->
            catalogCache.save(current.serverUrl, catalog, progresses)
        }
    }

    private fun invalidateRefresh() {
        refreshGeneration.incrementAndGet()
    }
}
