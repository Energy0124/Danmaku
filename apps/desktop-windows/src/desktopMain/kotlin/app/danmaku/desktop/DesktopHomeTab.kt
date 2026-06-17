package app.danmaku.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeSyncFailure
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.continueWatchingItems
import app.danmaku.domain.externalAnimeTrackingPlan
import app.danmaku.domain.groupedSeries
import app.danmaku.domain.nextUpItems
import app.danmaku.domain.recentlyWatchedItems
import app.danmaku.domain.seriesWatchSummaryById
import java.nio.file.Path

@Composable
internal fun HomeTab(
    strings: DesktopStrings,
    playbackSnapshot: PlaybackSnapshot,
    registeredRoots: List<DesktopLibraryRoot>,
    indexedLibrary: IndexedLocalLibrary?,
    seriesPosterById: Map<String, Path?>,
    playbackProgresses: List<PlaybackProgress>,
    favoriteMediaIds: Set<String>,
    externalAnimeMappings: List<ExternalAnimeMapping>,
    externalAnimeSyncFailures: List<ExternalAnimeSyncFailure>,
    isIndexing: Boolean,
    isPreparingLocalPlayback: Boolean,
    isRefreshingSeriesPosters: Boolean,
    refreshingMetadataMediaIds: Set<String>,
    refreshingMetadataSeriesIds: Set<String>,
    dandanplayCacheStatus: DandanplayPlaybackUiStatus?,
    episodeCount: Int,
    networkUrls: List<String>,
    pairingToken: String,
    overlayStatus: String,
    libraryError: String?,
    lastScanStats: LocalMediaLibraryScanStats?,
    diagnosticLog: List<DesktopDiagnosticLogEntry>,
    onOpenLibrary: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTracking: () -> Unit,
    onRefreshMetadata: () -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    val catalog = indexedLibrary?.catalog
    val series = remember(catalog) { catalog?.groupedSeries().orEmpty() }
    val seriesByMediaId = remember(series) {
        series
            .flatMap { librarySeries -> librarySeries.seasons.flatMap { season -> season.items.map { it.id to librarySeries } } }
            .toMap()
    }
    val seriesWatchSummaryById = remember(catalog, playbackProgresses) {
        catalog?.seriesWatchSummaryById(playbackProgresses).orEmpty()
    }
    val continueWatchingItems = remember(catalog, playbackProgresses) {
        catalog?.continueWatchingItems(playbackProgresses).orEmpty()
    }
    val nextUpItems = remember(catalog, playbackProgresses) {
        catalog?.nextUpItems(playbackProgresses).orEmpty()
    }
    val recentlyWatchedItems = remember(catalog, playbackProgresses) {
        catalog?.recentlyWatchedItems(playbackProgresses).orEmpty()
    }
    val recentlyAddedItems = remember(catalog) {
        catalog
            ?.items
            .orEmpty()
            .sortedWith(
                compareByDescending<LibraryMediaItem> { it.indexedAtEpochMs }
                    .thenByDescending { it.relativePath },
            )
    }
    val externalTrackingPlan = remember(catalog, externalAnimeMappings, playbackProgresses, externalAnimeSyncFailures) {
        catalog?.externalAnimeTrackingPlan(
            mappings = externalAnimeMappings,
            progresses = playbackProgresses,
            failures = externalAnimeSyncFailures,
        )
    }
    val metadataRefreshingCount = refreshingMetadataMediaIds.size + refreshingMetadataSeriesIds.size
    val posterReadyCount = seriesPosterById.values.count { it != null }

    TabScaffold {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxWidth < 1120.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    HomeMainColumn(
                        strings = strings,
                        playbackSnapshot = playbackSnapshot,
                        continueWatchingItems = continueWatchingItems,
                        nextUpItems = nextUpItems,
                        recentlyWatchedItems = recentlyWatchedItems,
                        recentlyAddedItems = recentlyAddedItems,
                        series = series,
                        seriesByMediaId = seriesByMediaId,
                        seriesPosterById = seriesPosterById,
                        seriesWatchSummaryById = seriesWatchSummaryById,
                        favoriteMediaIds = favoriteMediaIds,
                        isPreparingLocalPlayback = isPreparingLocalPlayback,
                        onOpenLibrary = onOpenLibrary,
                        onPlayLocalPlayback = onPlayLocalPlayback,
                    )
                    HomeStatusColumn(
                        strings = strings,
                        registeredRoots = registeredRoots,
                        episodeCount = episodeCount,
                        seriesCount = series.size,
                        networkUrls = networkUrls,
                        pairingToken = pairingToken,
                        overlayStatus = overlayStatus,
                        libraryError = libraryError,
                        lastScanStats = lastScanStats,
                        isIndexing = isIndexing,
                        isRefreshingSeriesPosters = isRefreshingSeriesPosters,
                        metadataRefreshingCount = metadataRefreshingCount,
                        posterReadyCount = posterReadyCount,
                        externalTrackingPlan = externalTrackingPlan,
                        dandanplayCacheStatus = dandanplayCacheStatus,
                        diagnosticLog = diagnosticLog,
                        onOpenLibrary = onOpenLibrary,
                        onOpenDownloads = onOpenDownloads,
                        onOpenSettings = onOpenSettings,
                        onOpenTracking = onOpenTracking,
                        onRefreshMetadata = onRefreshMetadata,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HomeMainColumn(
                        strings = strings,
                        playbackSnapshot = playbackSnapshot,
                        continueWatchingItems = continueWatchingItems,
                        nextUpItems = nextUpItems,
                        recentlyWatchedItems = recentlyWatchedItems,
                        recentlyAddedItems = recentlyAddedItems,
                        series = series,
                        seriesByMediaId = seriesByMediaId,
                        seriesPosterById = seriesPosterById,
                        seriesWatchSummaryById = seriesWatchSummaryById,
                        favoriteMediaIds = favoriteMediaIds,
                        isPreparingLocalPlayback = isPreparingLocalPlayback,
                        onOpenLibrary = onOpenLibrary,
                        onPlayLocalPlayback = onPlayLocalPlayback,
                        modifier = Modifier.weight(1f),
                    )
                    HomeStatusColumn(
                        strings = strings,
                        registeredRoots = registeredRoots,
                        episodeCount = episodeCount,
                        seriesCount = series.size,
                        networkUrls = networkUrls,
                        pairingToken = pairingToken,
                        overlayStatus = overlayStatus,
                        libraryError = libraryError,
                        lastScanStats = lastScanStats,
                        isIndexing = isIndexing,
                        isRefreshingSeriesPosters = isRefreshingSeriesPosters,
                        metadataRefreshingCount = metadataRefreshingCount,
                        posterReadyCount = posterReadyCount,
                        externalTrackingPlan = externalTrackingPlan,
                        dandanplayCacheStatus = dandanplayCacheStatus,
                        diagnosticLog = diagnosticLog,
                        onOpenLibrary = onOpenLibrary,
                        onOpenDownloads = onOpenDownloads,
                        onOpenSettings = onOpenSettings,
                        onOpenTracking = onOpenTracking,
                        onRefreshMetadata = onRefreshMetadata,
                        modifier = Modifier.width(332.dp),
                    )
                }
            }
        }
    }
}
