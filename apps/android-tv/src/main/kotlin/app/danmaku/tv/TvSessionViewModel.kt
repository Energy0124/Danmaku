package app.danmaku.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.library.android.LanLibraryDiscoveryException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted

internal class TvSessionViewModel(
    private val repository: TvLibraryRepository,
    private val navigator: TvNavigator,
    private val libraryDiscovery: TvLibraryDiscovery,
) : ViewModel() {
    private val discoveryError = MutableStateFlow<String?>(null)
    val state = combine(repository.state, discoveryError) { session, error ->
        if (error == null) session else session.copy(errorMessage = error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = repository.state.value,
    )

    init {
        viewModelScope.launch {
            if (repository.isQaFixtureInstalled) {
                navigator.reset(TvRoute.Home)
                return@launch
            }
            val hasCachedCatalog = repository.loadCachedCatalog()
            val current = repository.state.value
            navigator.reset(
                when {
                    hasCachedCatalog -> TvRoute.Home
                    current.savedConnections.isEmpty() && current.serverUrl.isBlank() ->
                        TvRoute.Onboarding
                    else -> TvRoute.Pc
                },
            )
            if (current.serverUrl.isNotBlank()) {
                refreshLibrary(navigateOnSuccess = !hasCachedCatalog)
            }
        }
    }

    fun updateServerUrl(value: String) = repository.updateServerUrl(value)

    fun updatePairingToken(value: String) = repository.updatePairingToken(value)

    fun refreshLibrary(navigateOnSuccess: Boolean = true) {
        viewModelScope.launch {
            discoveryError.value = null
            repository.refresh().onSuccess { outcome ->
                if (outcome == TvCatalogRefreshOutcome.Applied) repository.loadTracking()
                if (
                    navigateOnSuccess &&
                    outcome == TvCatalogRefreshOutcome.Applied
                ) navigator.reset(TvRoute.Home)
            }
        }
    }

    fun discoverPc() {
        viewModelScope.launch {
            discoveryError.value = null
            runCatching {
                withContext(Dispatchers.IO) {
                    libraryDiscovery.discover().firstOrNull()
                        ?: throw LanLibraryDiscoveryException(
                            "No Windows library server discovered",
                        )
                }
            }.onSuccess {
                repository.updateServerUrl(it.baseUrl)
                repository.refresh()
                    .onSuccess { outcome ->
                        if (outcome == TvCatalogRefreshOutcome.Applied) {
                            repository.loadTracking()
                            navigator.reset(TvRoute.Home)
                        }
                    }
                    .onFailure { navigator.reset(TvRoute.Pc) }
            }.onFailure {
                navigator.reset(TvRoute.Onboarding)
                discoveryError.value = it.message
            }
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
        viewModelScope.launch {
            repository.selectConnection(connection)
            val outcome = repository.refresh().getOrNull()
            if (outcome == TvCatalogRefreshOutcome.Stale) return@launch
            if (outcome == TvCatalogRefreshOutcome.Applied) repository.loadTracking()
            navigator.reset(
                if (repository.state.value.catalog == null) TvRoute.Pc else TvRoute.Home,
            )
        }
    }

    fun forgetConnection(connection: LanLibraryConnectionProfile) {
        viewModelScope.launch {
            repository.forgetConnection(connection)
        }
    }
}
