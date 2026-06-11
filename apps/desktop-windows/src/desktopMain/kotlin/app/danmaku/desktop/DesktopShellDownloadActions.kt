package app.danmaku.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DesktopShellDownloadActions(
    private val scope: CoroutineScope,
    private val catalogStore: DesktopLibraryCatalogStore,
    private val libraryState: DesktopShellLibraryState,
    private val appendDiagnostic: (String, String) -> Unit,
) {
    fun refreshQueue() {
        libraryState.downloadQueueItems = catalogStore.loadDownloads()
        appendDiagnostic("downloads", "Refreshed download queue")
    }

    fun removeQueueItem(item: DesktopDownloadQueueItem) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    catalogStore.deleteDownload(item.id)
                }
            }.onSuccess {
                libraryState.downloadQueueItems = catalogStore.loadDownloads()
                appendDiagnostic("downloads", "Removed download queue item ${item.id}")
            }.onFailure { error ->
                appendDiagnostic("downloads", "Failed to remove download queue item ${item.id}: ${error.message}")
            }
        }
    }

    fun openOutputFolder(item: DesktopDownloadQueueItem) {
        openDownloadOutputFolder(item)
            .onSuccess {
                appendDiagnostic("downloads", "Opened download output folder for ${item.id}")
            }
            .onFailure { error ->
                appendDiagnostic("downloads", "Failed to open download output folder for ${item.id}: ${error.message}")
            }
    }
}
