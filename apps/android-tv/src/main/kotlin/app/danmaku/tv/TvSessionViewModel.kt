package app.danmaku.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.library.lanLibraryConnectionProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class TvSessionViewModel(
    private val repository: TvSessionRepository,
    private val navigator: TvNavigator,
    private val libraryDiscovery: TvLibraryDiscovery,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val connection = MutableStateFlow(TvConnectionUiState())
    private var connectionJob: Job? = null
    val state = combine(repository.state, connection) { session, setup ->
        session.copy(connection = setup)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = repository.state.value,
    )

    fun onStart() {
        if (repository.isQaFixtureInstalled) {
            if (navigator.state.value.route == TvRoute.Onboarding) navigator.reset(TvRoute.Home)
            return
        }
        if (connectionJob?.isActive == true || connection.value.isManualEntry ||
            navigator.state.value.route is TvRoute.Player
        ) return
        connectionJob = viewModelScope.launch {
            val hasCatalog = repository.state.value.catalog != null || repository.loadCachedCatalog()
            currentCoroutineContext().ensureActive()
            if (hasCatalog && navigator.state.value.route == TvRoute.Onboarding) {
                navigator.reset(TvRoute.Home)
            }
            val current = repository.state.value
            if (current.serverUrl.isNotBlank() && refresh(navigateOnSuccess = !hasCatalog)) {
                return@launch
            }
            if (navigator.state.value.route is TvRoute.Player) return@launch
            if (!hasCatalog) navigator.reset(TvRoute.Onboarding)
            discover(automaticallyConnect = true)
        }
    }

    fun updateServerUrl(value: String) {
        stopConnecting()
        repository.updateServerUrl(value)
    }

    fun stopConnecting() {
        connectionJob?.cancel()
        connectionJob = null
        repository.updateServerUrl(repository.state.value.serverUrl)
        connection.value = connection.value.copy(isDiscovering = false, error = null)
    }

    fun setManualEntry(active: Boolean) {
        stopConnecting()
        connection.value = connection.value.copy(isManualEntry = active)
    }

    fun connectAddress(address: String) {
        val url = runCatching { normalizeTvServerAddress(address) }.getOrElse {
            connection.value = connection.value.copy(error = TvConnectionError.INVALID_ADDRESS)
            return
        }
        selectConnection(lanLibraryConnectionProfile(url))
    }

    fun refreshLibrary(navigateOnSuccess: Boolean = true) {
        stopConnecting()
        connectionJob = viewModelScope.launch { refresh(navigateOnSuccess) }
    }

    private suspend fun refresh(navigateOnSuccess: Boolean): Boolean {
        connection.value = connection.value.copy(error = null)
        val outcome = repository.refresh()
        currentCoroutineContext().ensureActive()
        if (outcome.getOrNull() != TvCatalogRefreshOutcome.Applied) {
            if (outcome.isFailure) {
                connection.value = connection.value.copy(error = TvConnectionError.CONNECTION_FAILED)
            }
            return false
        }
        if (navigateOnSuccess && navigator.state.value.route !is TvRoute.Player) {
            navigator.reset(TvRoute.Home)
        }
        connection.value = connection.value.copy(isManualEntry = false)
        loadTracking()
        return true
    }

    fun refreshFolder(path: List<String>) {
        viewModelScope.launch {
            repository.refreshFolder(path)
        }
    }

    fun discoverPc() {
        if (connectionJob?.isActive == true) return
        if (navigator.state.value.route != TvRoute.Onboarding) navigator.navigate(TvRoute.Onboarding)
        connectionJob = viewModelScope.launch { discover(automaticallyConnect = false) }
    }

    private suspend fun discover(automaticallyConnect: Boolean) {
        val previousError = connection.value.error
        connection.value = connection.value.copy(isDiscovering = true, error = null)
        try {
            val urls = withContext(ioDispatcher) {
                libraryDiscovery.discover().map { it.baseUrl }.distinct()
            }
            if (navigator.state.value.route == TvRoute.Onboarding) {
                val first = repository.state.value.savedConnections.firstOrNull()
                    ?: urls.firstOrNull()?.let { lanLibraryConnectionProfile(it) }
                if (first != null) navigator.saveFocus(TvRoute.Onboarding, "onboarding-pc:${first.id}")
            }
            connection.value = TvConnectionUiState(
                hasSearched = true,
                discoveredUrls = urls,
                error = previousError.takeIf { it == TvConnectionError.CONNECTION_FAILED },
            )
            val automaticUrl = if (automaticallyConnect && navigator.state.value.route !is TvRoute.Player) {
                automaticTvConnection(urls, repository.state.value.savedConnections)
            } else null
            if (automaticUrl != null) {
                repository.selectConnection(lanLibraryConnectionProfile(automaticUrl))
                currentCoroutineContext().ensureActive()
                refresh(navigateOnSuccess = navigator.state.value.route == TvRoute.Onboarding)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            connection.value = connection.value.copy(
                isDiscovering = false,
                hasSearched = true,
                error = TvConnectionError.DISCOVERY_FAILED,
            )
        }
    }

    fun saveConnection() {
        viewModelScope.launch {
            repository.saveConnection()
        }
    }

    fun loadTracking() {
        viewModelScope.launch { repository.loadTracking() }
    }

    fun readTracking() {
        viewModelScope.launch { repository.readTracking() }
    }

    fun syncTracking() {
        viewModelScope.launch { repository.syncTracking() }
    }

    fun selectConnection(connection: LanLibraryConnectionProfile) {
        stopConnecting()
        connectionJob = viewModelScope.launch {
            repository.selectConnection(connection)
            currentCoroutineContext().ensureActive()
            if (repository.state.value.catalog != null) navigator.reset(TvRoute.Home)
            refresh(navigateOnSuccess = true)
        }
    }

    fun forgetConnection(connection: LanLibraryConnectionProfile) {
        stopConnecting()
        viewModelScope.launch {
            repository.forgetConnection(connection)
        }
    }
}
