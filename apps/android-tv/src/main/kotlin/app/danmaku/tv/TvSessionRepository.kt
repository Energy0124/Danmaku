package app.danmaku.tv

import app.danmaku.library.LanLibraryConnectionProfile
import kotlinx.coroutines.flow.StateFlow

internal interface TvSessionRepository {
    val state: StateFlow<TvSessionUiState>
    val isQaFixtureInstalled: Boolean
    fun updateServerUrl(serverUrl: String)
    suspend fun loadCachedCatalog(): Boolean
    suspend fun refresh(): Result<TvCatalogRefreshOutcome>
    suspend fun refreshFolder(path: List<String>): Result<TvCatalogRefreshOutcome>
    suspend fun selectConnection(connection: LanLibraryConnectionProfile)
    suspend fun saveConnection(): Result<Unit>
    suspend fun forgetConnection(connection: LanLibraryConnectionProfile)
    suspend fun loadTracking(): Result<Unit>
    suspend fun readTracking(): Result<Unit>
    suspend fun syncTracking(): Result<Unit>
}
