package app.danmaku.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.danmaku.domain.ExternalAnimeListEntry
import app.danmaku.domain.ExternalAnimeSyncFailure
import java.nio.file.Path

internal class DesktopShellLibraryState(
    rootRegistry: DesktopLibraryRootRegistry,
    catalogStore: DesktopLibraryCatalogStore,
    legacySelectedLibraryRoot: Path?,
) {
    var registeredRoots by mutableStateOf(rootRegistry.loadRoots())
    var indexedLibrary by mutableStateOf(
        if (registeredRoots.isNotEmpty()) {
            catalogStore.loadRegisteredLibrary()
        } else {
            legacySelectedLibraryRoot?.let(catalogStore::load)
        },
    )
    var libraryMetadataVersion by mutableStateOf(0)
    var playbackProgresses by mutableStateOf(catalogStore.loadPlaybackProgress())
    var favoriteMediaIds by mutableStateOf(catalogStore.loadFavoriteMediaIds())
    var downloadQueueItems by mutableStateOf(catalogStore.loadDownloads())
    var selectedLocalPlaybackPreparation by mutableStateOf<DesktopLocalPlaybackPreparation?>(null)
    var libraryError by mutableStateOf<String?>(null)
    var isIndexing by mutableStateOf(false)
    var isPreparingLocalPlayback by mutableStateOf(false)
    var lastScanStats by mutableStateOf<LocalMediaLibraryScanStats?>(null)
    var dandanplayCacheStatus by mutableStateOf<DandanplayPlaybackUiStatus?>(null)
    var isRefreshingSeriesPosters by mutableStateOf(false)
    var refreshingMetadataMediaIds by mutableStateOf<Set<String>>(emptySet())
    var refreshingMetadataSeriesIds by mutableStateOf<Set<String>>(emptySet())
    var externalAnimeSyncFailures by mutableStateOf(catalogStore.loadExternalAnimeSyncFailures())
    var externalAnimeListEntries by mutableStateOf(catalogStore.loadExternalAnimeListEntries())
    var isExternalAnimeSyncing by mutableStateOf(false)
    var isExternalAnimeReadbackRefreshing by mutableStateOf(false)
}

@Composable
internal fun rememberDesktopShellLibraryState(
    rootRegistry: DesktopLibraryRootRegistry,
    catalogStore: DesktopLibraryCatalogStore,
    legacySelectedLibraryRoot: Path?,
): DesktopShellLibraryState =
    remember(rootRegistry, catalogStore, legacySelectedLibraryRoot) {
        DesktopShellLibraryState(
            rootRegistry = rootRegistry,
            catalogStore = catalogStore,
            legacySelectedLibraryRoot = legacySelectedLibraryRoot,
        )
    }
