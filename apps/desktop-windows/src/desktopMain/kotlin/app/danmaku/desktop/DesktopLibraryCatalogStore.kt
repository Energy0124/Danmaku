package app.danmaku.desktop

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.danmaku.desktop.db.DesktopLibraryDatabase
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import java.nio.file.Files
import java.nio.file.Path

class DesktopLibraryCatalogStore(
    databasePath: Path,
) : AutoCloseable {
    private val driver: JdbcSqliteDriver
    private val database: DesktopLibraryDatabase

    init {
        databasePath.parent?.let(Files::createDirectories)
        driver = JdbcSqliteDriver(
            url = "jdbc:sqlite:${databasePath.toAbsolutePath().normalize()}",
            schema = DesktopLibraryDatabase.Schema,
        )
        database = DesktopLibraryDatabase(driver)
    }

    fun load(root: Path): IndexedLocalLibrary? {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val metadata = database.libraryCatalogQueries
            .selectMetadata(::StoredLibraryMetadata)
            .executeAsOneOrNull()
            ?.takeIf { Path.of(it.rootPath).toAbsolutePath().normalize() == normalizedRoot }
            ?: return null
        val filesById = linkedMapOf<String, Path>()
        val itemMetadata = database.libraryCatalogQueries
            .selectAllItems(::storedItem)
            .executeAsList()
            .mapNotNull { cachedItem ->
                val path = normalizedRoot.resolve(cachedItem.item.relativePath)
                    .normalize()
                    .takeIf { it.startsWith(normalizedRoot) }
                    ?.takeIf(Files::isRegularFile)
                    ?: return@mapNotNull null
                filesById[cachedItem.item.id] = path
                cachedItem.item.relativePath to cachedItem
            }
            .toMap(linkedMapOf())
        return IndexedLocalLibrary(
            catalog = LibraryCatalog(
                rootName = metadata.rootName,
                indexedAtEpochMs = metadata.indexedAtEpochMs,
                items = itemMetadata.values.map(CachedLocalMediaItem::item),
            ),
            filesById = filesById,
            fileMetadataByRelativePath = itemMetadata,
        )
    }

    fun replace(root: Path, library: IndexedLocalLibrary) {
        val normalizedRoot = root.toAbsolutePath().normalize()
        database.transaction {
            database.libraryCatalogQueries.deleteAllItems()
            database.libraryCatalogQueries.deleteMetadata()
            database.libraryCatalogQueries.insertMetadata(
                normalizedRoot.toString(),
                library.catalog.rootName,
                library.catalog.indexedAtEpochMs,
            )
            library.fileMetadataByRelativePath.values.forEach { cachedItem ->
                val item = cachedItem.item
                database.libraryCatalogQueries.insertItem(
                    item.relativePath,
                    item.id,
                    item.seriesTitle,
                    item.episodeTitle,
                    item.sizeBytes,
                    cachedItem.lastModifiedEpochMs,
                    item.mediaType,
                    item.streamPath,
                )
            }
        }
    }

    override fun close() {
        driver.close()
    }

    companion object {
        fun default(): DesktopLibraryCatalogStore {
            val appDataRoot = System.getenv("LOCALAPPDATA")
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?: Path.of(System.getProperty("user.home"), ".danmaku")
            return DesktopLibraryCatalogStore(
                appDataRoot.resolve("Danmaku").resolve("library.db"),
            )
        }

        private fun storedItem(
            relativePath: String,
            id: String,
            seriesTitle: String,
            episodeTitle: String,
            sizeBytes: Long,
            lastModifiedEpochMs: Long,
            mediaType: String,
            streamPath: String,
        ): CachedLocalMediaItem =
            CachedLocalMediaItem(
                item = LibraryMediaItem(
                    id = id,
                    seriesTitle = seriesTitle,
                    episodeTitle = episodeTitle,
                    relativePath = relativePath,
                    sizeBytes = sizeBytes,
                    mediaType = mediaType,
                    streamPath = streamPath,
                ),
                lastModifiedEpochMs = lastModifiedEpochMs,
            )
    }
}

private data class StoredLibraryMetadata(
    val rootPath: String,
    val rootName: String,
    val indexedAtEpochMs: Long,
)
