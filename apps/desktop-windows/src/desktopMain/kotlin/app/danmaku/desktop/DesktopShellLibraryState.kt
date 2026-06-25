package app.danmaku.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.danmaku.domain.ExternalAnimeListEntry
import app.danmaku.domain.ExternalAnimeSyncFailure
import app.danmaku.domain.LocalAnimeListEntry
import app.danmaku.domain.PlaybackProgress

internal data class DesktopShellLibrarySnapshot(
    val registeredRoots: List<DesktopLibraryRoot>,
    val indexedLibrary: IndexedLocalLibrary?,
    val playbackProgresses: List<PlaybackProgress>,
    val favoriteMediaIds: Set<String>,
    val localAnimeListEntries: List<LocalAnimeListEntry>,
    val libraryQualityIssueDecisions: List<DesktopLibraryQualityIssueDecision>,
    val downloadQueueItems: List<DesktopDownloadQueueItem>,
    val externalAnimeSyncFailures: List<ExternalAnimeSyncFailure>,
    val externalAnimeListEntries: List<ExternalAnimeListEntry>,
)

internal class DesktopShellLibraryState {
    var registeredRoots by mutableStateOf<List<DesktopLibraryRoot>>(emptyList())
    var indexedLibrary by mutableStateOf<IndexedLocalLibrary?>(null)
    var libraryMetadataVersion by mutableStateOf(0)
    var playbackProgresses by mutableStateOf<List<PlaybackProgress>>(emptyList())
    var favoriteMediaIds by mutableStateOf<Set<String>>(emptySet())
    var localAnimeListEntries by mutableStateOf<List<LocalAnimeListEntry>>(emptyList())
    var libraryQualityIssueDecisions by mutableStateOf<List<DesktopLibraryQualityIssueDecision>>(emptyList())
    var downloadQueueItems by mutableStateOf<List<DesktopDownloadQueueItem>>(emptyList())
    var selectedLocalPlaybackPreparation by mutableStateOf<DesktopLocalPlaybackPreparation?>(null)
    var libraryError by mutableStateOf<String?>(null)
    var isIndexing by mutableStateOf(false)
    var isPreparingLocalPlayback by mutableStateOf(false)
    var lastScanStats by mutableStateOf<LocalMediaLibraryScanStats?>(null)
    var dandanplayCacheStatus by mutableStateOf<DandanplayPlaybackUiStatus?>(null)
    var isRefreshingSeriesPosters by mutableStateOf(false)
    var refreshingMetadataMediaIds by mutableStateOf<Set<String>>(emptySet())
    var refreshingMetadataSeriesIds by mutableStateOf<Set<String>>(emptySet())
    var externalAnimeSyncFailures by mutableStateOf<List<ExternalAnimeSyncFailure>>(emptyList())
    var externalAnimeListEntries by mutableStateOf<List<ExternalAnimeListEntry>>(emptyList())
    var isExternalAnimeSyncing by mutableStateOf(false)
    var isExternalAnimeReadbackRefreshing by mutableStateOf(false)
    var isExternalAnimeProgressImporting by mutableStateOf(false)
    var isExternalAnimeMappingSuggesting by mutableStateOf(false)

    fun applySnapshot(snapshot: DesktopShellLibrarySnapshot) {
        registeredRoots = snapshot.registeredRoots
        indexedLibrary = snapshot.indexedLibrary
        playbackProgresses = snapshot.playbackProgresses
        favoriteMediaIds = snapshot.favoriteMediaIds
        localAnimeListEntries = snapshot.localAnimeListEntries
        libraryQualityIssueDecisions = snapshot.libraryQualityIssueDecisions
        downloadQueueItems = snapshot.downloadQueueItems
        externalAnimeSyncFailures = snapshot.externalAnimeSyncFailures
        externalAnimeListEntries = snapshot.externalAnimeListEntries
        libraryError = null
    }
}

@Composable
internal fun rememberDesktopShellLibraryState(): DesktopShellLibraryState =
    remember { DesktopShellLibraryState() }