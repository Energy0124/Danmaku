package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeMappingSource
import app.danmaku.domain.ExternalAnimeMatchCandidate
import app.danmaku.domain.ExternalAnimeMatchQuery
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeSyncFailure
import app.danmaku.domain.ExternalAnimeTrackingPlan
import app.danmaku.domain.ExternalAnimeTrackingPlanUpdate
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.displayName
import app.danmaku.domain.externalAnimeSyncRetryAfterEpochMs
import app.danmaku.domain.groupedSeries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

internal class DesktopShellLibraryActions(
    private val scope: CoroutineScope,
    private val catalogStore: DesktopLibraryCatalogStore,
    private val selectionStore: LocalLibrarySelectionStore,
    private val rootRegistry: DesktopLibraryRootRegistry,
    private val rootScanner: DesktopLibraryRootScanner,
    private val animeMetadataResolver: DesktopAnimeMetadataResolver,
    private val dandanplayDanmakuResolver: DesktopDandanplayDanmakuResolver,
    private val externalAnimeCredentialStore: ExternalAnimeCredentialStore,
    private val posterCache: DesktopAnimePosterCache,
    private val settingsState: DesktopShellSettingsState,
    private val libraryState: DesktopShellLibraryState,
    private val publishLibrary: (IndexedLocalLibrary) -> Unit,
    private val appendDiagnostic: (String, String) -> Unit,
) {
    fun refreshMissingSeriesPosters(library: IndexedLocalLibrary) {
        if (libraryState.isRefreshingSeriesPosters) return
        if (!settingsState.dandanplaySettings.isFetchEnabled) {
            appendDiagnostic("metadata", "Skipping poster refresh; dandanplay provider is not configured")
            return
        }
        val missingSeries = library.catalog
            .groupedSeries()
            .filter { series ->
                findSeriesCoverImage(series, library) == null &&
                    animeMetadataResolver.cachedPosterForSeries(series) == null
            }
            .take(MAX_BACKGROUND_POSTER_REFRESH_SERIES)
        if (missingSeries.isEmpty()) return

        scope.launch {
            libraryState.isRefreshingSeriesPosters = true
            try {
                appendDiagnostic("metadata", "Refreshing posters for ${missingSeries.size} local series")
                val failures = mutableListOf<String>()
                val refreshedCount = withContext(Dispatchers.IO) {
                    missingSeries.count { series ->
                        val result = runCatching {
                            animeMetadataResolver.ensureDandanplayPosterForSeries(
                                series = series,
                                mediaPathById = library.filesById,
                            )
                        }
                        result.onFailure { error ->
                            failures += "${series.title}: ${error.message ?: error::class.simpleName}"
                        }
                        result.getOrNull() != null
                    }
                }
                libraryState.libraryMetadataVersion += 1
                failures.take(3).forEach { failure ->
                    appendDiagnostic("metadata", "Poster refresh failed for $failure")
                }
                val omittedFailureCount = failures.size - 3
                if (omittedFailureCount > 0) {
                    appendDiagnostic("metadata", "Poster refresh had $omittedFailureCount additional failures")
                }
                appendDiagnostic(
                    "metadata",
                    "Poster refresh complete: $refreshedCount/${missingSeries.size} series updated",
                )
            } finally {
                libraryState.isRefreshingSeriesPosters = false
            }
        }
    }

    fun syncExternalAnimePlan(plan: ExternalAnimeTrackingPlan) {
        if (libraryState.isExternalAnimeSyncing || plan.updates.isEmpty()) {
            return
        }
        libraryState.isExternalAnimeSyncing = true
        scope.launch {
            try {
                val previousFailures = libraryState.externalAnimeSyncFailures.associateBy(ExternalAnimeSyncFailure::animeId)
                val result = withContext(Dispatchers.IO) {
                    val clients = buildMap {
                        externalAnimeCredentialStore.loadMyAnimeListTrackingClient()
                            ?.let { put(it.provider, it) }
                        externalAnimeCredentialStore.loadBangumiTrackingClient()
                            ?.let { put(it.provider, it) }
                    }
                    var successCount = 0
                    val failures = mutableListOf<ExternalAnimeSyncFailure>()
                    plan.updates.forEach { update ->
                        val failedAtEpochMs = System.currentTimeMillis()
                        val client = clients[update.mapping.animeId.provider]
                        if (client == null) {
                            failures += externalAnimeSyncFailure(
                                update = update,
                                message = "${update.mapping.animeId.provider.displayName} access token is not configured",
                                previousFailures = previousFailures,
                                failedAtEpochMs = failedAtEpochMs,
                            )
                            return@forEach
                        }
                        runCatching {
                            client.updateListEntry(update.update)
                        }.onSuccess {
                            successCount += 1
                        }.onFailure { error ->
                            failures += externalAnimeSyncFailure(
                                update = update,
                                message = error.message ?: error.javaClass.simpleName.ifBlank { "External sync failed" },
                                previousFailures = previousFailures,
                                failedAtEpochMs = failedAtEpochMs,
                            )
                        }
                    }
                    successCount to failures
                }
                val (successCount, failures) = result
                libraryState.externalAnimeSyncFailures = failures
                appendDiagnostic(
                    "external-sync",
                    "External list sync complete: $successCount succeeded, ${failures.size} failed",
                )
            } finally {
                libraryState.isExternalAnimeSyncing = false
            }
        }
    }

    fun applyPublishedLibrary(library: IndexedLocalLibrary) {
        appendDiagnostic("library", "Publishing library: ${library.catalog.items.size} items")
        publishLibrary(library)
        libraryState.indexedLibrary = library
        libraryState.libraryMetadataVersion += 1
        libraryState.playbackProgresses = catalogStore.loadPlaybackProgress()
        libraryState.favoriteMediaIds = catalogStore.loadFavoriteMediaIds()
        libraryState.registeredRoots = rootRegistry.loadRoots()
        libraryState.selectedLocalPlaybackPreparation = null
        libraryState.libraryError = null
        refreshMissingSeriesPosters(library)
    }

    fun cleanupLegacySeriesAnimeMappings() {
        scope.launch {
            withContext(Dispatchers.IO) {
                catalogStore.deleteExternalAnimeMappings(
                    ExternalAnimeProvider.DANDANPLAY,
                    ExternalAnimeMappingSource.AUTO,
                )
            }
            appendDiagnostic("metadata", "Cleaned legacy automatic series anime mappings")
        }
    }

    fun refreshEpisodeAnimeMetadata(item: LibraryMediaItem, forceRefresh: Boolean = true) {
        val library = libraryState.indexedLibrary
        if (library == null) {
            appendDiagnostic("metadata", "Cannot refresh metadata; library is not indexed")
            return
        }
        if (!settingsState.dandanplaySettings.isFetchEnabled) {
            appendDiagnostic("metadata", "Cannot refresh metadata; dandanplay provider is not configured")
            return
        }
        if (item.id in libraryState.refreshingMetadataMediaIds) return

        scope.launch {
            libraryState.refreshingMetadataMediaIds = libraryState.refreshingMetadataMediaIds + item.id
            libraryState.dandanplayCacheStatus = dandanplayStatusMessage(
                mediaId = item.id,
                summary = "refreshing anime metadata and poster...",
                details = item.dandanplayStatusContext(settingsState.dandanplaySettings),
            )
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val animeId = animeMetadataResolver.cachedDandanplayAnimeIdForItem(item)
                        ?: run {
                            val mediaPath = library.requireMediaPath(item)
                            dandanplayDanmakuResolver
                                .resolve(mediaId = item.id, mediaPath = mediaPath, forceRefresh = false)
                                .match
                                ?.animeId
                        }
                        ?: throw DesktopUserActionException("dandanplay found no anime match for ${item.id}")
                    animeMetadataResolver.refreshDandanplayMetadataForItem(
                        item = item,
                        animeId = animeId,
                        forceRefresh = forceRefresh,
                    )
                }
            }
            result.onSuccess {
                libraryState.libraryMetadataVersion += 1
                appendDiagnostic("metadata", "Refreshed anime metadata/poster for ${item.episodeTitle}")
            }.onFailure { error ->
                appendDiagnostic(
                    "metadata",
                    "Anime metadata refresh failed for ${item.episodeTitle}: ${error.message ?: error::class.simpleName}",
                )
            }
            libraryState.refreshingMetadataMediaIds = libraryState.refreshingMetadataMediaIds - item.id
        }
    }

    fun refreshSeriesAnimeMetadata(series: LibrarySeries) {
        val library = libraryState.indexedLibrary
        if (library == null) {
            appendDiagnostic("metadata", "Cannot refresh series metadata; library is not indexed")
            return
        }
        if (!settingsState.dandanplaySettings.isFetchEnabled) {
            appendDiagnostic("metadata", "Cannot refresh series metadata; dandanplay provider is not configured")
            return
        }
        if (series.id in libraryState.refreshingMetadataSeriesIds) return

        val items = series.seasons.flatMap { it.items }
        scope.launch {
            libraryState.refreshingMetadataSeriesIds = libraryState.refreshingMetadataSeriesIds + series.id
            libraryState.refreshingMetadataMediaIds = libraryState.refreshingMetadataMediaIds + items.mapTo(mutableSetOf()) { it.id }
            appendDiagnostic("metadata", "Refreshing anime metadata/posters for ${series.title}")
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    items.count { item ->
                        val animeId = animeMetadataResolver.cachedDandanplayAnimeIdForItem(item)
                            ?: run {
                                val mediaPath = library.filesById[item.id] ?: return@count false
                                dandanplayDanmakuResolver
                                    .resolve(mediaId = item.id, mediaPath = mediaPath, forceRefresh = false)
                                    .match
                                    ?.animeId
                            }
                            ?: return@count false
                        animeMetadataResolver.refreshDandanplayMetadataForItem(
                            item = item,
                            animeId = animeId,
                            forceRefresh = true,
                        ) != null
                    }
                }
            }
            result.onSuccess { refreshedCount ->
                libraryState.libraryMetadataVersion += 1
                appendDiagnostic("metadata", "Series metadata refresh complete: $refreshedCount/${items.size} episodes updated")
            }.onFailure { error ->
                appendDiagnostic(
                    "metadata",
                    "Series metadata refresh failed for ${series.title}: ${error.message ?: error::class.simpleName}",
                )
            }
            libraryState.refreshingMetadataSeriesIds = libraryState.refreshingMetadataSeriesIds - series.id
            libraryState.refreshingMetadataMediaIds = libraryState.refreshingMetadataMediaIds - items.mapTo(mutableSetOf()) { it.id }
        }
    }

    fun saveManualExternalAnimeMapping(
        series: LibrarySeries,
        provider: ExternalAnimeProvider,
        animeIdText: String,
    ) {
        val animeId = animeIdText.trim().toLongOrNull()?.takeIf { it > 0 }
        if (animeId == null) {
            appendDiagnostic("metadata", "Cannot link ${series.title}; ${provider.displayName} ID must be positive")
            return
        }
        scope.launch {
            withContext(Dispatchers.IO) {
                catalogStore.saveExternalAnimeMapping(
                    ExternalAnimeMapping(
                        localSeriesId = series.id,
                        animeId = ExternalAnimeId(provider, animeId),
                        source = ExternalAnimeMappingSource.MANUAL,
                        confidence = 1.0,
                        mappedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
            }
            libraryState.libraryMetadataVersion += 1
            appendDiagnostic("metadata", "Linked ${series.title} to ${provider.displayName} #$animeId")
        }
    }

    fun deleteManualExternalAnimeMapping(series: LibrarySeries, provider: ExternalAnimeProvider) {
        scope.launch {
            withContext(Dispatchers.IO) {
                catalogStore.deleteExternalAnimeMapping(series.id, provider)
            }
            libraryState.libraryMetadataVersion += 1
            appendDiagnostic("metadata", "Removed ${provider.displayName} link for ${series.title}")
        }
    }

    fun saveManualExternalAnimeItemMapping(
        item: LibraryMediaItem,
        provider: ExternalAnimeProvider,
        animeIdText: String,
    ) {
        val animeId = animeIdText.trim().toLongOrNull()?.takeIf { it > 0 }
        if (animeId == null) {
            appendDiagnostic("metadata", "Cannot link ${item.episodeTitle}; ${provider.displayName} ID must be positive")
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    catalogStore.saveExternalAnimeItemMapping(
                        DesktopExternalAnimeItemMapping(
                            localMediaId = item.id,
                            animeId = ExternalAnimeId(provider, animeId),
                            source = ExternalAnimeMappingSource.MANUAL,
                            confidence = 1.0,
                            mappedAtEpochMs = System.currentTimeMillis(),
                        ),
                    )
                    if (provider == ExternalAnimeProvider.DANDANPLAY && settingsState.dandanplaySettings.isFetchEnabled) {
                        animeMetadataResolver.refreshDandanplayMetadataForAnime(
                            animeId = animeId,
                            forceRefresh = false,
                        )
                    }
                }
            }
            result.onSuccess {
                libraryState.libraryMetadataVersion += 1
                appendDiagnostic("metadata", "Linked ${item.episodeTitle} to ${provider.displayName} #$animeId")
            }.onFailure { error ->
                appendDiagnostic(
                    "metadata",
                    "Episode link failed for ${item.episodeTitle}: ${error.message ?: error::class.simpleName}",
                )
            }
        }
    }

    fun deleteManualExternalAnimeItemMapping(item: LibraryMediaItem, provider: ExternalAnimeProvider) {
        scope.launch {
            withContext(Dispatchers.IO) {
                catalogStore.deleteExternalAnimeItemMapping(item.id, provider)
            }
            libraryState.libraryMetadataVersion += 1
            appendDiagnostic("metadata", "Removed ${provider.displayName} episode link for ${item.episodeTitle}")
        }
    }

    suspend fun searchExternalAnimeMatches(
        query: ExternalAnimeMatchQuery,
        providers: Set<ExternalAnimeProvider>,
    ): Result<List<ExternalAnimeMatchCandidate>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val clients = buildList {
                    if (ExternalAnimeProvider.MY_ANIME_LIST in providers) {
                        externalAnimeCredentialStore.loadMyAnimeListSearchConnection()
                            ?.let { add(MyAnimeListAnimeSearchClient(it)) }
                    }
                    if (ExternalAnimeProvider.BANGUMI in providers) {
                        add(externalAnimeCredentialStore.loadBangumiSearchClient())
                    }
                }
                check(clients.isNotEmpty()) {
                    "No external anime search providers are configured. Add a MyAnimeList client ID or enable Bangumi settings."
                }
                ExternalAnimeSearchService(
                    clients = clients,
                    catalogStore = catalogStore,
                ).searchAndCache(
                    query = query,
                    providers = clients.mapTo(mutableSetOf(), ExternalAnimeSearchClient::provider),
                    limitPerProvider = 8,
                )
            }
        }

    suspend fun fetchMetadataMatchPoster(imageUrl: String?): Path? =
        withContext(Dispatchers.IO) {
            posterCache.fetch(imageUrl)
        }

    fun setFavorite(item: LibraryMediaItem, isFavorite: Boolean) {
        val updatedFavorites = if (isFavorite) {
            libraryState.favoriteMediaIds + item.id
        } else {
            libraryState.favoriteMediaIds - item.id
        }
        runCatching {
            catalogStore.saveFavoriteMediaIds(updatedFavorites)
            catalogStore.loadFavoriteMediaIds()
        }.onSuccess { loadedFavorites ->
            libraryState.favoriteMediaIds = loadedFavorites
            appendDiagnostic(
                "library",
                if (isFavorite) {
                    "Favorited ${item.id}"
                } else {
                    "Removed favorite ${item.id}"
                },
            )
        }.onFailure { error ->
            libraryState.libraryError = error.message
            appendDiagnostic("library", "Failed to update favorite ${item.id}: ${error.message}")
        }
    }

    fun registerAndScanUserRoot(root: Path) {
        scope.launch {
            appendDiagnostic("library", "Scanning user library root: $root")
            libraryState.isIndexing = true
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        rootRegistry.addUserSelectedRoot(root).let(rootScanner::scan).also {
                            selectionStore.save(root)
                        }
                    }
                }.onSuccess { result ->
                    libraryState.lastScanStats = result.indexedLibrary?.scanStats
                    applyPublishedLibrary(result.publishedLibrary)
                    appendDiagnostic(
                        "library",
                        "Scan complete: ${result.publishedLibrary.catalog.items.size} published items",
                    )
                }.onFailure { error ->
                    libraryState.libraryError = error.message
                    libraryState.registeredRoots = rootRegistry.loadRoots()
                    appendDiagnostic("library", "Scan failed: ${error.message}")
                }
            } finally {
                libraryState.isIndexing = false
            }
        }
    }

    fun importAndScanAniRssRoot(root: Path) {
        scope.launch {
            appendDiagnostic("downloads", "Importing ani-rss output root: $root")
            libraryState.isIndexing = true
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        rootScanner.importAniRssOutputRoot(root)
                    }
                }.onSuccess { result ->
                    libraryState.lastScanStats = result.indexedLibrary?.scanStats
                    applyPublishedLibrary(result.publishedLibrary)
                    appendDiagnostic(
                        "downloads",
                        "ani-rss root scan complete: ${result.publishedLibrary.catalog.items.size} published items",
                    )
                }.onFailure { error ->
                    libraryState.libraryError = error.message
                    libraryState.registeredRoots = rootRegistry.loadRoots()
                    appendDiagnostic("downloads", "ani-rss root scan failed: ${error.message}")
                }
            } finally {
                libraryState.isIndexing = false
            }
        }
    }

    fun rescanRegisteredRoots() {
        scope.launch {
            appendDiagnostic("library", "Rescanning ${libraryState.registeredRoots.size} registered folders")
            libraryState.isIndexing = true
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        rootScanner.scanAll()
                    }
                }.onSuccess { batch ->
                    libraryState.lastScanStats = LocalMediaLibraryScanStats(
                        reusedItemCount = batch.results.sumOf {
                            it.indexedLibrary?.scanStats?.reusedItemCount ?: 0
                        },
                        refreshedItemCount = batch.results.sumOf {
                            it.indexedLibrary?.scanStats?.refreshedItemCount ?: 0
                        },
                    )
                    applyPublishedLibrary(batch.publishedLibrary)
                    appendDiagnostic(
                        "library",
                        "Rescan complete: ${batch.publishedLibrary.catalog.items.size} published items",
                    )
                }.onFailure { error ->
                    libraryState.libraryError = error.message
                    libraryState.registeredRoots = rootRegistry.loadRoots()
                    appendDiagnostic("library", "Rescan failed: ${error.message}")
                }
            } finally {
                libraryState.isIndexing = false
            }
        }
    }

    fun removeRegisteredRoot(root: DesktopLibraryRoot) {
        scope.launch {
            appendDiagnostic("library", "Removing library root: ${root.normalizedPath}")
            libraryState.isIndexing = true
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        catalogStore.deleteLibraryRoot(root.id)
                        catalogStore.loadRegisteredLibrary()
                    }
                }.onSuccess { library ->
                    libraryState.registeredRoots = rootRegistry.loadRoots()
                    libraryState.lastScanStats = null
                    applyPublishedLibrary(library)
                    appendDiagnostic("library", "Removed library root ${root.displayName}")
                }.onFailure { error ->
                    libraryState.libraryError = error.message
                    libraryState.registeredRoots = rootRegistry.loadRoots()
                    appendDiagnostic("library", "Failed to remove library root ${root.displayName}: ${error.message}")
                }
            } finally {
                libraryState.isIndexing = false
            }
        }
    }

    private fun externalAnimeSyncFailure(
        update: ExternalAnimeTrackingPlanUpdate,
        message: String,
        previousFailures: Map<ExternalAnimeId, ExternalAnimeSyncFailure>,
        failedAtEpochMs: Long,
    ): ExternalAnimeSyncFailure {
        val attemptCount = (previousFailures[update.mapping.animeId]?.attemptCount ?: 0) + 1
        return ExternalAnimeSyncFailure(
            animeId = update.mapping.animeId,
            message = message,
            failedAtEpochMs = failedAtEpochMs,
            attemptCount = attemptCount,
            retryAfterEpochMs = externalAnimeSyncRetryAfterEpochMs(failedAtEpochMs, attemptCount),
        )
    }
}
