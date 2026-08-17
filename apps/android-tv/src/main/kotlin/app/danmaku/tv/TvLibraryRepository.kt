package app.danmaku.tv

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackProgress
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.LanLibraryClientException
import app.danmaku.library.LanPlaybackTarget
import app.danmaku.library.android.AndroidLanLibraryConnectionStore
import app.danmaku.library.android.AndroidLibraryFavoriteStore
import app.danmaku.library.android.LanExternalTrackingClient
import app.danmaku.library.android.LanExternalTrackingException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

internal enum class TvCatalogRefreshOutcome {
    Applied,
    Stale,
}

internal class TvLibraryRepository(
    private val connectionSession: LanLibraryConnectionSession,
    private val connectionStore: AndroidLanLibraryConnectionStore,
    private val favoriteStore: AndroidLibraryFavoriteStore,
    private val catalogCache: TvCatalogCache,
    defaultServerUrl: String,
    defaultPairingToken: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val trackingClient: LanExternalTrackingClient = LanExternalTrackingClient(),
): TvPlaybackSession {
    private val refreshGeneration = AtomicLong()
    private val trackingGeneration = AtomicLong()
    private val folderRefreshGeneration = AtomicLong()
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
        invalidateTracking()
        invalidateFolderRefresh()
        mutableState.update {
            it.copy(
                serverUrl = serverUrl,
                isRefreshing = false,
                errorMessage = null,
                tracking = TvTrackingState(),
                folderRefresh = TvFolderRefreshState(),
            )
        }
    }

    fun updatePairingToken(pairingToken: String) {
        invalidateRefresh()
        invalidateTracking()
        invalidateFolderRefresh()
        mutableState.update {
            it.copy(
                pairingToken = pairingToken,
                isRefreshing = false,
                errorMessage = null,
                tracking = TvTrackingState(),
                folderRefresh = TvFolderRefreshState(),
            )
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

    suspend fun refresh(): Result<TvCatalogRefreshOutcome> {
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
            if (refreshGeneration.get() != generation) {
                return@map TvCatalogRefreshOutcome.Stale
            }
            catalogCache.save(
                serverUrl = request.serverUrl,
                catalog = snapshot.catalog,
                playbackProgresses = snapshot.playbackProgresses,
            )
            if (refreshGeneration.get() != generation) {
                return@map TvCatalogRefreshOutcome.Stale
            }
            withContext(ioDispatcher) {
                connectionStore.saveCurrentConnection(
                    baseUrl = request.serverUrl,
                    pairingToken = request.pairingToken,
                    displayName = snapshot.catalog.rootName,
                )
            }
            if (refreshGeneration.get() != generation) {
                return@map TvCatalogRefreshOutcome.Stale
            }
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
            TvCatalogRefreshOutcome.Applied
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

    suspend fun refreshFolder(path: List<String>): Result<TvCatalogRefreshOutcome> {
        val request = state.value
        if (request.serverUrl.isBlank() || request.pairingToken.isBlank()) {
            val error = IllegalArgumentException("PC access code is required")
            mutableState.update {
                it.copy(
                    folderRefresh = TvFolderRefreshState(
                        error = TvFolderRefreshError.ACCESS_CODE_REQUIRED,
                    ),
                )
            }
            return Result.failure(error)
        }
        val generation = folderRefreshGeneration.incrementAndGet()
        mutableState.update {
            it.copy(folderRefresh = TvFolderRefreshState(isBusy = true))
        }
        return runCatching {
            withContext(ioDispatcher) {
                connectionSession.requestFolderRescan(
                    request.serverUrl,
                    request.pairingToken,
                    path,
                )
            }
            while (true) {
                delay(FOLDER_SCAN_POLL_INTERVAL_MS)
                val status = withContext(ioDispatcher) {
                    connectionSession.validateServer(request.serverUrl)
                }
                if (!isCurrentFolderRefresh(request, generation)) {
                    return@runCatching null
                }
                mutableState.update {
                    it.copy(folderRefresh = it.folderRefresh.copy(filesSeen = status.scanFilesSeen))
                }
                if (!status.scanning) {
                    status.scanError?.let { throw TvFolderScanException(it) }
                    break
                }
            }
            withContext(ioDispatcher) {
                connectionSession.fetchCatalogWithProgress(
                    request.serverUrl,
                    request.pairingToken,
                )
            }
        }.map { snapshot ->
            if (snapshot == null || !isCurrentFolderRefresh(request, generation)) {
                return@map TvCatalogRefreshOutcome.Stale
            }
            catalogCache.save(
                serverUrl = request.serverUrl,
                catalog = snapshot.catalog,
                playbackProgresses = snapshot.playbackProgresses,
            )
            if (!isCurrentFolderRefresh(request, generation)) {
                return@map TvCatalogRefreshOutcome.Stale
            }
            mutableState.update {
                it.copy(
                    catalog = snapshot.catalog,
                    playbackProgresses = snapshot.playbackProgresses,
                    catalogSource = TvCatalogSource.Network,
                    isOffline = false,
                    folderRefresh = TvFolderRefreshState(),
                )
            }
            TvCatalogRefreshOutcome.Applied
        }.onFailure { error ->
            if (isCurrentFolderRefresh(request, generation)) {
                mutableState.update {
                    it.copy(
                        folderRefresh = TvFolderRefreshState(
                            error = when {
                                error is LanLibraryClientException && error.statusCode == 409 ->
                                    TvFolderRefreshError.ALREADY_RUNNING
                                error is TvFolderScanException -> TvFolderRefreshError.SCAN_FAILED
                                else -> TvFolderRefreshError.REQUEST_FAILED
                            },
                            errorDetail = error.message,
                        ),
                    )
                }
            }
        }
    }

    private fun isCurrentFolderRefresh(request: TvSessionUiState, generation: Long): Boolean =
        folderRefreshGeneration.get() == generation &&
            state.value.serverUrl == request.serverUrl &&
            state.value.pairingToken == request.pairingToken

    private fun invalidateFolderRefresh() {
        folderRefreshGeneration.incrementAndGet()
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

    suspend fun loadTracking(): Result<Unit> {
        val request = state.value
        if (request.serverUrl.isBlank() || request.pairingToken.isBlank()) return Result.success(Unit)
        val generation = trackingGeneration.incrementAndGet()
        mutableState.update { it.copy(tracking = it.tracking.copy(isBusy = true, error = null, errorDetail = null)) }
        return runCatching {
            withContext(ioDispatcher) {
                trackingClient.fetchAccounts(request.serverUrl, request.pairingToken) to
                    trackingClient.fetchTracking(request.serverUrl, request.pairingToken)
            }
        }.map { (accounts, document) ->
            if (trackingGeneration.get() == generation && state.value.matches(request)) {
                mutableState.update {
                    it.copy(tracking = TvTrackingState(accounts = accounts, document = document))
                }
            }
        }.onFailure { error ->
            if (trackingGeneration.get() == generation && state.value.matches(request)) {
                mutableState.update {
                    it.copy(
                        tracking = it.tracking.copy(
                            isBusy = false,
                            error = trackingError(error),
                            errorDetail = trackingErrorDetail(error),
                        ),
                    )
                }
            }
            refreshTrackingAccountsAfterFailure(request, generation)
        }
    }

    suspend fun readTracking(): Result<Unit> {
        val request = state.value
        val generation = trackingGeneration.incrementAndGet()
        mutableState.update {
            it.copy(
                tracking = it.tracking.copy(
                    isBusy = true,
                    hasFreshReadback = false,
                    error = null,
                    errorDetail = null,
                    lastOperation = null,
                    lastResponse = null,
                ),
            )
        }
        return runCatching {
            withContext(ioDispatcher) {
                trackingClient.refreshReadback(request.serverUrl, request.pairingToken)
            }
        }.map { response ->
            if (trackingGeneration.get() == generation && state.value.matches(request)) {
                mutableState.update {
                    it.copy(
                        tracking = it.tracking.copy(
                            document = response.document,
                            isBusy = false,
                            hasFreshReadback = response.errors.isEmpty(),
                            lastOperation = TvTrackingOperation.READBACK,
                            lastResponse = response,
                        ),
                    )
                }
            }
        }.onFailure { error ->
            if (trackingGeneration.get() == generation && state.value.matches(request)) {
                mutableState.update {
                    it.copy(
                        tracking = it.tracking.copy(
                            isBusy = false,
                            error = trackingError(error),
                            errorDetail = trackingErrorDetail(error),
                        ),
                    )
                }
            }
            refreshTrackingAccountsAfterFailure(request, generation)
        }
    }

    suspend fun syncTracking(): Result<Unit> {
        val request = state.value
        if (!request.tracking.hasFreshReadback) return Result.success(Unit)
        val updates = request.tracking.document?.plan?.updates?.map { it.update }.orEmpty()
        if (updates.isEmpty()) return Result.success(Unit)
        val generation = trackingGeneration.incrementAndGet()
        mutableState.update {
            it.copy(tracking = it.tracking.copy(isBusy = true, hasFreshReadback = false, error = null, errorDetail = null))
        }
        return runCatching {
            withContext(ioDispatcher) {
                trackingClient.sync(request.serverUrl, request.pairingToken, updates)
            }
        }.map { response ->
            if (trackingGeneration.get() == generation && state.value.matches(request)) {
                mutableState.update {
                    it.copy(
                        tracking = it.tracking.copy(
                            document = response.document,
                            isBusy = false,
                            lastOperation = TvTrackingOperation.SYNC,
                            lastResponse = response,
                        ),
                    )
                }
            }
        }.onFailure { error ->
            if (trackingGeneration.get() == generation && state.value.matches(request)) {
                mutableState.update {
                    it.copy(
                        tracking = it.tracking.copy(
                            isBusy = false,
                            error = if ((error as? LanExternalTrackingException)?.statusCode == 409) {
                                TvTrackingError.PREVIEW_CHANGED
                            } else {
                                trackingError(error)
                            },
                            errorDetail = trackingErrorDetail(error),
                        ),
                    )
                }
            }
            refreshTrackingAccountsAfterFailure(request, generation)
        }
    }

    suspend fun selectConnection(connection: LanLibraryConnectionProfile) {
        invalidateRefresh()
        invalidateTracking()
        invalidateFolderRefresh()
        mutableState.update {
            it.copy(
                serverUrl = connection.baseUrl,
                pairingToken = connection.pairingToken,
                catalog = null,
                playbackProgresses = emptyList(),
                catalogSource = TvCatalogSource.None,
                isOffline = false,
                errorMessage = null,
                tracking = TvTrackingState(),
                folderRefresh = TvFolderRefreshState(),
            )
        }
        loadCachedCatalog()
    }

    suspend fun forgetConnection(connection: LanLibraryConnectionProfile) {
        invalidateRefresh()
        invalidateTracking()
        invalidateFolderRefresh()
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
                    tracking = TvTrackingState(),
                    folderRefresh = TvFolderRefreshState(),
                )
            } else {
                current.copy(
                    savedConnections = saved,
                    isRefreshing = false,
                    folderRefresh = TvFolderRefreshState(),
                )
            }
        }
    }

    fun installQaFixture(fixture: TvQaFixture) {
        invalidateRefresh()
        invalidateFolderRefresh()
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

    override suspend fun updateProgresses(
        target: LanPlaybackTarget,
        progresses: List<PlaybackProgress>,
    ): Boolean {
        invalidateTracking()
        var applied = false
        var catalogToCache: LibraryCatalog? = null
        var serverUrlToCache: String? = null
        mutableState.update { current ->
            applied = current.matches(target)
            if (applied) {
                catalogToCache = current.catalog
                serverUrlToCache = current.serverUrl
                current.copy(
                    playbackProgresses = progresses,
                    tracking = current.tracking.copy(
                        isBusy = false,
                        hasFreshReadback = false,
                    ),
                )
            } else {
                current
            }
        }
        if (!applied) return false
        catalogToCache?.let { catalog ->
            catalogCache.save(requireNotNull(serverUrlToCache), catalog, progresses)
        }
        return true
    }

    private fun invalidateRefresh() {
        refreshGeneration.incrementAndGet()
    }

    private fun invalidateTracking() {
        trackingGeneration.incrementAndGet()
    }

    private fun TvSessionUiState.matches(other: TvSessionUiState): Boolean =
        serverUrl.trim().trimEnd('/') == other.serverUrl.trim().trimEnd('/') &&
            pairingToken == other.pairingToken

    private fun trackingError(error: Throwable): TvTrackingError =
        if ((error as? LanExternalTrackingException)?.statusCode == 401) {
            TvTrackingError.ACCESS_CODE_REJECTED
        } else {
            TvTrackingError.REQUEST_FAILED
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

    private suspend fun refreshTrackingAccountsAfterFailure(
        request: TvSessionUiState,
        generation: Long,
    ) {
        val accounts = runCatching {
            withContext(ioDispatcher) {
                trackingClient.fetchAccounts(request.serverUrl, request.pairingToken)
            }
        }.getOrNull() ?: return
        if (trackingGeneration.get() == generation && state.value.matches(request)) {
            mutableState.update { current ->
                current.copy(tracking = current.tracking.copy(accounts = accounts))
            }
        }
    }

    private fun TvSessionUiState.matches(target: LanPlaybackTarget): Boolean =
        serverUrl.trim().trimEnd('/') == target.baseUrl.trim().trimEnd('/') &&
            pairingToken == target.pairingToken
}

private class TvFolderScanException(message: String) : RuntimeException(message)
private const val FOLDER_SCAN_POLL_INTERVAL_MS = 750L
