package app.danmaku.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class DesktopLibraryRootRegistry(
    private val store: DesktopLibraryCatalogStore,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    fun loadRoots(): List<DesktopLibraryRoot> =
        store.loadLibraryRoots()

    fun addUserSelectedRoot(path: Path): DesktopLibraryRoot =
        addRoot(
            path = path,
            provenance = DesktopLibraryRootProvenance.USER_SELECTED,
            displayName = path.toAbsolutePath().normalize().fileName?.toString()
                ?: path.toAbsolutePath().normalize().toString(),
        )

    fun addAniRssOutputRoot(
        path: Path,
        displayName: String = path.toAbsolutePath().normalize().fileName?.toString()
            ?: path.toAbsolutePath().normalize().toString(),
    ): DesktopLibraryRoot =
        addRoot(
            path = path,
            provenance = DesktopLibraryRootProvenance.ANI_RSS_OUTPUT_FOLDER,
            displayName = displayName,
        )

    fun markAvailable(root: DesktopLibraryRoot): DesktopLibraryRoot =
        root.copy(
            state = DesktopLibraryRootState.AVAILABLE,
            lastScannedAtEpochMs = nowEpochMs(),
            lastError = null,
        ).also(store::saveLibraryRoot)

    fun markMissing(
        root: DesktopLibraryRoot,
        error: String,
    ): DesktopLibraryRoot =
        root.copy(
            state = DesktopLibraryRootState.MISSING,
            lastScannedAtEpochMs = nowEpochMs(),
            lastError = error,
        ).also(store::saveLibraryRoot)

    private fun addRoot(
        path: Path,
        provenance: DesktopLibraryRootProvenance,
        displayName: String,
    ): DesktopLibraryRoot {
        val normalizedPath = path.toAbsolutePath().normalize()
        require(Files.isDirectory(normalizedPath)) {
            "library root must be an existing directory"
        }
        return DesktopLibraryRoot(
            id = rootId(provenance, normalizedPath),
            path = normalizedPath,
            displayName = displayName,
            provenance = provenance,
            state = DesktopLibraryRootState.AVAILABLE,
            addedAtEpochMs = store.loadLibraryRoots()
                .firstOrNull { it.normalizedPath == normalizedPath && it.provenance == provenance }
                ?.addedAtEpochMs
                ?: nowEpochMs(),
        ).also(store::saveLibraryRoot)
    }

    private companion object {
        fun rootId(
            provenance: DesktopLibraryRootProvenance,
            path: Path,
        ): String =
            "${provenance.name.lowercase()}-${sha256(path.toString()).take(24)}"

        fun sha256(value: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(value.toByteArray())
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
