package app.danmaku.desktop

import java.nio.file.Files
import java.nio.file.Path

class DesktopLibraryRootScanner(
    private val store: DesktopLibraryCatalogStore,
    private val registry: DesktopLibraryRootRegistry,
) {
    @Synchronized
    fun importAniRssOutputRoot(
        path: Path,
        displayName: String = path.toAbsolutePath().normalize().fileName?.toString()
            ?: path.toAbsolutePath().normalize().toString(),
    ): DesktopLibraryRootScanResult =
        scan(registry.addAniRssOutputRoot(path, displayName))

    @Synchronized
    fun scan(root: DesktopLibraryRoot): DesktopLibraryRootScanResult {
        if (!Files.isDirectory(root.normalizedPath)) {
            val missingRoot = registry.markMissing(root, MISSING_ROOT_ERROR)
            return DesktopLibraryRootScanResult(
                root = missingRoot,
                indexedLibrary = null,
                publishedLibrary = store.loadRegisteredLibrary(),
            )
        }

        val indexedLibrary = LocalMediaLibraryIndexer.index(
            root = root.normalizedPath,
            cachedItems = store.load(root)
                ?.fileMetadataByRelativePath
                .orEmpty(),
            idNamespace = root.id,
        )
        val availableRoot = registry.markAvailable(root)
        store.replace(availableRoot, indexedLibrary)
        return DesktopLibraryRootScanResult(
            root = availableRoot,
            indexedLibrary = indexedLibrary,
            publishedLibrary = store.loadRegisteredLibrary(),
        )
    }

    @Synchronized
    fun scanAll(): DesktopLibraryScanBatchResult =
        scanRoots(registry.loadRoots())

    @Synchronized
    fun scanAniRssRoots(): DesktopLibraryScanBatchResult =
        scanRoots(
            registry.loadRoots().filter {
                it.provenance == DesktopLibraryRootProvenance.ANI_RSS_OUTPUT_FOLDER
            },
        )

    private fun scanRoots(roots: List<DesktopLibraryRoot>): DesktopLibraryScanBatchResult {
        val results = roots.map(::scan)
        return DesktopLibraryScanBatchResult(
            results = results,
            publishedLibrary = store.loadRegisteredLibrary(),
        )
    }

    companion object {
        const val MISSING_ROOT_ERROR = "Library root folder is not available"
    }
}

data class DesktopLibraryRootScanResult(
    val root: DesktopLibraryRoot,
    val indexedLibrary: IndexedLocalLibrary?,
    val publishedLibrary: IndexedLocalLibrary,
)

data class DesktopLibraryScanBatchResult(
    val results: List<DesktopLibraryRootScanResult>,
    val publishedLibrary: IndexedLocalLibrary,
)
