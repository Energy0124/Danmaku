package app.danmaku.desktop

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.danmaku.desktop.db.DesktopLibraryDatabase
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
import app.danmaku.domain.PlaybackProgress
import app.danmaku.server.PlaybackProgressStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

class DesktopLibraryCatalogStore(
    databasePath: Path,
) : AutoCloseable, PlaybackProgressStore, DandanplayCommentCacheStore {
    private val driver: JdbcSqliteDriver
    private val database: DesktopLibraryDatabase

    init {
        databasePath.parent?.let(Files::createDirectories)
        driver = JdbcSqliteDriver(
            url = "jdbc:sqlite:${databasePath.toAbsolutePath().normalize()}",
            schema = DesktopLibraryDatabase.Schema,
        )
        driver.execute(
            identifier = null,
            sql = CREATE_PLAYBACK_PROGRESS_TABLE_SQL,
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = CREATE_APP_SETTING_TABLE_SQL,
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = CREATE_DOWNLOAD_QUEUE_ITEM_TABLE_SQL,
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = CREATE_LIBRARY_ROOT_TABLE_SQL,
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = CREATE_LIBRARY_ROOT_MEDIA_ITEM_TABLE_SQL,
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = CREATE_DANDANPLAY_COMMENT_CACHE_TABLE_SQL,
            parameters = 0,
        )
        addColumnIfMissing("local_media_item", "subtitles_json", "TEXT NOT NULL DEFAULT '[]'")
        addColumnIfMissing("library_root_media_item", "subtitles_json", "TEXT NOT NULL DEFAULT '[]'")
        database = DesktopLibraryDatabase(driver)
    }

    @Synchronized
    fun load(root: Path): IndexedLocalLibrary? {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val metadata = database.libraryCatalogQueries
            .selectMetadata(::StoredLibraryMetadata)
            .executeAsOneOrNull()
            ?.takeIf { Path.of(it.rootPath).toAbsolutePath().normalize() == normalizedRoot }
            ?: return null
        val filesById = linkedMapOf<String, Path>()
        val subtitleFilesById = linkedMapOf<String, Path>()
        val itemMetadata = database.libraryCatalogQueries
            .selectAllItems(::storedItem)
            .executeAsList()
            .mapNotNull { cachedItem ->
                val path = normalizedRoot.resolve(cachedItem.item.relativePath)
                    .normalize()
                    .takeIf { it.startsWith(normalizedRoot) }
                    ?.takeIf(Files::isRegularFile)
                ?: return@mapNotNull null
                val availableItem = cachedItem.copy(
                    item = cachedItem.item.copy(
                        subtitles = cachedItem.item.subtitles.resolveExistingFiles(
                            normalizedRoot,
                            subtitleFilesById,
                        ),
                    ),
                )
                filesById[availableItem.item.id] = path
                availableItem.item.relativePath to availableItem
            }
            .toMap(linkedMapOf())
        return IndexedLocalLibrary(
            catalog = LibraryCatalog(
                rootName = metadata.rootName,
                indexedAtEpochMs = metadata.indexedAtEpochMs,
                items = itemMetadata.values.map(CachedLocalMediaItem::item),
            ),
            filesById = filesById,
            subtitleFilesById = subtitleFilesById,
            fileMetadataByRelativePath = itemMetadata,
        )
    }

    @Synchronized
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
                    Json.encodeToString(item.subtitles),
                )
            }
        }
    }

    @Synchronized
    fun load(root: DesktopLibraryRoot): IndexedLocalLibrary? {
        val storedRoot = loadLibraryRoot(root.id) ?: return null
        val rootPath = storedRoot.normalizedPath
        val filesById = linkedMapOf<String, Path>()
        val subtitleFilesById = linkedMapOf<String, Path>()
        val itemMetadata = database.libraryCatalogQueries
            .selectRootItems(root.id, ::storedItem)
            .executeAsList()
            .mapNotNull { cachedItem ->
                val path = rootPath.resolve(cachedItem.item.relativePath)
                    .normalize()
                    .takeIf { it.startsWith(rootPath) }
                    ?.takeIf(Files::isRegularFile)
                ?: return@mapNotNull null
                val availableItem = cachedItem.copy(
                    item = cachedItem.item.copy(
                        subtitles = cachedItem.item.subtitles.resolveExistingFiles(
                            rootPath,
                            subtitleFilesById,
                        ),
                    ),
                )
                filesById[availableItem.item.id] = path
                availableItem.item.relativePath to availableItem
            }
            .toMap(linkedMapOf())
        return IndexedLocalLibrary(
            catalog = LibraryCatalog(
                rootName = storedRoot.displayName,
                indexedAtEpochMs = storedRoot.lastScannedAtEpochMs ?: storedRoot.addedAtEpochMs,
                items = itemMetadata.values.map(CachedLocalMediaItem::item),
            ),
            filesById = filesById,
            subtitleFilesById = subtitleFilesById,
            fileMetadataByRelativePath = itemMetadata,
        )
    }

    @Synchronized
    fun loadRegisteredLibrary(): IndexedLocalLibrary {
        val filesById = linkedMapOf<String, Path>()
        val subtitleFilesById = linkedMapOf<String, Path>()
        val itemMetadata = linkedMapOf<String, CachedLocalMediaItem>()
        var indexedAtEpochMs = 0L

        loadLibraryRoots()
            .filter { it.state == DesktopLibraryRootState.AVAILABLE }
            .forEach { root ->
                val library = load(root) ?: return@forEach
                indexedAtEpochMs = maxOf(indexedAtEpochMs, library.catalog.indexedAtEpochMs)
                filesById += library.filesById
                subtitleFilesById += library.subtitleFilesById
                library.fileMetadataByRelativePath.forEach { (relativePath, cachedItem) ->
                    val publishedRelativePath = "${root.displayName}/$relativePath"
                    itemMetadata["${root.id}/$relativePath"] = cachedItem.copy(
                        item = cachedItem.item.copy(
                            relativePath = publishedRelativePath,
                            subtitles = cachedItem.item.subtitles.map { subtitle ->
                                subtitle.copy(relativePath = "${root.displayName}/${subtitle.relativePath}")
                            },
                        ),
                    )
                }
            }

        return IndexedLocalLibrary(
            catalog = LibraryCatalog(
                rootName = "Registered Libraries",
                indexedAtEpochMs = indexedAtEpochMs,
                items = itemMetadata.values
                    .map(CachedLocalMediaItem::item)
                    .sortedWith(compareBy(LibraryMediaItem::seriesTitle, LibraryMediaItem::relativePath)),
            ),
            filesById = filesById,
            subtitleFilesById = subtitleFilesById,
            fileMetadataByRelativePath = itemMetadata,
        )
    }

    @Synchronized
    fun replace(root: DesktopLibraryRoot, library: IndexedLocalLibrary) {
        database.transaction {
            saveLibraryRoot(root)
            database.libraryCatalogQueries.deleteRootItems(root.id)
            library.fileMetadataByRelativePath.values.forEach { cachedItem ->
                val item = cachedItem.item
                database.libraryCatalogQueries.insertRootItem(
                    root.id,
                    item.relativePath,
                    item.id,
                    item.seriesTitle,
                    item.episodeTitle,
                    item.sizeBytes,
                    cachedItem.lastModifiedEpochMs,
                    item.mediaType,
                    item.streamPath,
                    Json.encodeToString(item.subtitles),
                )
            }
        }
    }

    @Synchronized
    override fun loadProgress(mediaId: String): PlaybackProgress? =
        database.libraryCatalogQueries
            .selectPlaybackProgress(mediaId, ::PlaybackProgress)
            .executeAsOneOrNull()

    @Synchronized
    fun loadPlaybackProgress(): List<PlaybackProgress> =
        database.libraryCatalogQueries
            .selectAllPlaybackProgress(::PlaybackProgress)
            .executeAsList()

    @Synchronized
    override fun saveProgress(progress: PlaybackProgress) {
        database.libraryCatalogQueries.upsertPlaybackProgress(
            progress.mediaId,
            progress.positionMs,
            progress.durationMs,
            progress.updatedAtEpochMs,
        )
    }

    @Synchronized
    fun loadSetting(key: String): DesktopAppSetting? =
        database.libraryCatalogQueries
            .selectSetting(key, ::DesktopAppSetting)
            .executeAsOneOrNull()

    @Synchronized
    fun loadSettings(): List<DesktopAppSetting> =
        database.libraryCatalogQueries
            .selectAllSettings(::DesktopAppSetting)
            .executeAsList()

    @Synchronized
    fun saveSetting(setting: DesktopAppSetting) {
        database.libraryCatalogQueries.upsertSetting(
            setting.key,
            setting.value,
            setting.updatedAtEpochMs,
        )
    }

    @Synchronized
    fun deleteSetting(key: String) {
        database.libraryCatalogQueries.deleteSetting(key)
    }

    @Synchronized
    fun loadLibraryRoot(id: String): DesktopLibraryRoot? =
        database.libraryCatalogQueries
            .selectLibraryRoot(id, ::desktopLibraryRoot)
            .executeAsOneOrNull()

    @Synchronized
    fun loadLibraryRoots(): List<DesktopLibraryRoot> =
        database.libraryCatalogQueries
            .selectAllLibraryRoots(::desktopLibraryRoot)
            .executeAsList()

    @Synchronized
    fun saveLibraryRoot(root: DesktopLibraryRoot) {
        database.libraryCatalogQueries.upsertLibraryRoot(
            root.id,
            root.normalizedPath.toString(),
            root.displayName,
            root.provenance.name,
            root.state.name,
            root.addedAtEpochMs,
            root.lastScannedAtEpochMs,
            root.lastError,
        )
    }

    @Synchronized
    fun deleteLibraryRoot(id: String) {
        database.libraryCatalogQueries.deleteLibraryRoot(id)
    }

    @Synchronized
    fun loadDownload(id: String): DesktopDownloadQueueItem? =
        database.libraryCatalogQueries
            .selectDownload(id, ::desktopDownloadQueueItem)
            .executeAsOneOrNull()

    @Synchronized
    fun loadDownloads(): List<DesktopDownloadQueueItem> =
        database.libraryCatalogQueries
            .selectAllDownloads(::desktopDownloadQueueItem)
            .executeAsList()

    @Synchronized
    fun saveDownload(item: DesktopDownloadQueueItem) {
        database.libraryCatalogQueries.upsertDownload(
            item.id,
            item.sourceUri,
            item.outputPath,
            item.state,
            item.positionBytes,
            item.totalBytes,
            item.createdAtEpochMs,
            item.updatedAtEpochMs,
            item.failureMessage,
        )
    }

    @Synchronized
    fun deleteDownload(id: String) {
        database.libraryCatalogQueries.deleteDownload(id)
    }

    @Synchronized
    override fun loadDandanplayCommentCache(mediaId: String): DesktopDandanplayCommentCache? =
        database.libraryCatalogQueries
            .selectDandanplayCommentCache(mediaId, ::desktopDandanplayCommentCache)
            .executeAsOneOrNull()

    @Synchronized
    override fun saveDandanplayCommentCache(cache: DesktopDandanplayCommentCache) {
        database.libraryCatalogQueries.upsertDandanplayCommentCache(
            cache.mediaId,
            cache.fileHash,
            cache.fileName,
            cache.fileSizeBytes,
            cache.episodeId,
            cache.animeId,
            cache.animeTitle,
            cache.episodeTitle,
            cache.shiftSeconds,
            cache.commentsJson,
            cache.renderedAssPath,
            cache.fetchedAtEpochMs,
        )
    }

    @Synchronized
    fun deleteDandanplayCommentCache(mediaId: String) {
        database.libraryCatalogQueries.deleteDandanplayCommentCache(mediaId)
    }

    override fun close() {
        driver.close()
    }

    private fun addColumnIfMissing(
        table: String,
        column: String,
        definition: String,
    ) {
        runCatching {
            driver.execute(
                identifier = null,
                sql = "ALTER TABLE $table ADD COLUMN $column $definition",
                parameters = 0,
            )
        }
    }

    companion object {
        private val CREATE_PLAYBACK_PROGRESS_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS playback_progress (
              media_id TEXT NOT NULL PRIMARY KEY,
              position_ms INTEGER NOT NULL,
              duration_ms INTEGER,
              updated_at_epoch_ms INTEGER NOT NULL
            )
        """.trimIndent()

        private val CREATE_APP_SETTING_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS app_setting (
              setting_key TEXT NOT NULL PRIMARY KEY,
              setting_value TEXT NOT NULL,
              updated_at_epoch_ms INTEGER NOT NULL
            )
        """.trimIndent()

        private val CREATE_LIBRARY_ROOT_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS library_root (
              root_id TEXT NOT NULL PRIMARY KEY,
              path TEXT NOT NULL UNIQUE,
              display_name TEXT NOT NULL,
              provenance TEXT NOT NULL,
              state TEXT NOT NULL,
              added_at_epoch_ms INTEGER NOT NULL,
              last_scanned_at_epoch_ms INTEGER,
              last_error TEXT
            )
        """.trimIndent()

        private val CREATE_LIBRARY_ROOT_MEDIA_ITEM_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS library_root_media_item (
              root_id TEXT NOT NULL,
              relative_path TEXT NOT NULL,
              id TEXT NOT NULL UNIQUE,
              series_title TEXT NOT NULL,
              episode_title TEXT NOT NULL,
              size_bytes INTEGER NOT NULL,
              last_modified_epoch_ms INTEGER NOT NULL,
              media_type TEXT NOT NULL,
              stream_path TEXT NOT NULL,
              subtitles_json TEXT NOT NULL DEFAULT '[]',
              PRIMARY KEY(root_id, relative_path)
            )
        """.trimIndent()

        private val CREATE_DOWNLOAD_QUEUE_ITEM_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS download_queue_item (
              id TEXT NOT NULL PRIMARY KEY,
              source_uri TEXT NOT NULL,
              output_path TEXT NOT NULL,
              state TEXT NOT NULL,
              position_bytes INTEGER NOT NULL,
              total_bytes INTEGER,
              created_at_epoch_ms INTEGER NOT NULL,
              updated_at_epoch_ms INTEGER NOT NULL,
              failure_message TEXT
            )
        """.trimIndent()

        private val CREATE_DANDANPLAY_COMMENT_CACHE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS dandanplay_comment_cache (
              media_id TEXT NOT NULL PRIMARY KEY,
              file_hash TEXT NOT NULL,
              file_name TEXT NOT NULL,
              file_size_bytes INTEGER NOT NULL,
              episode_id INTEGER,
              anime_id INTEGER,
              anime_title TEXT,
              episode_title TEXT,
              shift_seconds REAL,
              comments_json TEXT NOT NULL,
              rendered_ass_path TEXT,
              fetched_at_epoch_ms INTEGER NOT NULL
            )
        """.trimIndent()

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
            subtitlesJson: String,
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
                    subtitles = Json.decodeFromString<List<LibrarySubtitleTrack>>(subtitlesJson),
                ),
                lastModifiedEpochMs = lastModifiedEpochMs,
            )

        private fun desktopLibraryRoot(
            id: String,
            path: String,
            displayName: String,
            provenance: String,
            state: String,
            addedAtEpochMs: Long,
            lastScannedAtEpochMs: Long?,
            lastError: String?,
        ): DesktopLibraryRoot =
            DesktopLibraryRoot(
                id = id,
                path = Path.of(path).toAbsolutePath().normalize(),
                displayName = displayName,
                provenance = DesktopLibraryRootProvenance.valueOf(provenance),
                state = DesktopLibraryRootState.valueOf(state),
                addedAtEpochMs = addedAtEpochMs,
                lastScannedAtEpochMs = lastScannedAtEpochMs,
                lastError = lastError,
            )

        private fun desktopDownloadQueueItem(
            id: String,
            sourceUri: String,
            outputPath: String,
            state: String,
            positionBytes: Long,
            totalBytes: Long?,
            createdAtEpochMs: Long,
            updatedAtEpochMs: Long,
            failureMessage: String?,
        ): DesktopDownloadQueueItem =
            DesktopDownloadQueueItem(
                id = id,
                sourceUri = sourceUri,
                outputPath = outputPath,
                state = state,
                positionBytes = positionBytes,
                totalBytes = totalBytes,
                createdAtEpochMs = createdAtEpochMs,
                updatedAtEpochMs = updatedAtEpochMs,
                failureMessage = failureMessage,
            )

        private fun desktopDandanplayCommentCache(
            mediaId: String,
            fileHash: String,
            fileName: String,
            fileSizeBytes: Long,
            episodeId: Long?,
            animeId: Long?,
            animeTitle: String?,
            episodeTitle: String?,
            shiftSeconds: Double?,
            commentsJson: String,
            renderedAssPath: String?,
            fetchedAtEpochMs: Long,
        ): DesktopDandanplayCommentCache =
            DesktopDandanplayCommentCache(
                mediaId = mediaId,
                fileHash = fileHash,
                fileName = fileName,
                fileSizeBytes = fileSizeBytes,
                episodeId = episodeId,
                animeId = animeId,
                animeTitle = animeTitle,
                episodeTitle = episodeTitle,
                shiftSeconds = shiftSeconds,
                commentsJson = commentsJson,
                renderedAssPath = renderedAssPath,
                fetchedAtEpochMs = fetchedAtEpochMs,
            )
    }
}

private fun List<LibrarySubtitleTrack>.resolveExistingFiles(
    root: Path,
    destination: MutableMap<String, Path>,
): List<LibrarySubtitleTrack> =
    mapNotNull { subtitle ->
        val path = root.resolve(subtitle.relativePath)
            .normalize()
            .takeIf { it.startsWith(root) }
            ?.takeIf(Files::isRegularFile)
            ?: return@mapNotNull null
        destination[subtitle.id] = path
        subtitle
    }

data class DesktopLibraryRoot(
    val id: String,
    val path: Path,
    val displayName: String,
    val provenance: DesktopLibraryRootProvenance,
    val state: DesktopLibraryRootState,
    val addedAtEpochMs: Long,
    val lastScannedAtEpochMs: Long? = null,
    val lastError: String? = null,
) {
    val normalizedPath: Path
        get() = path.toAbsolutePath().normalize()

    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(addedAtEpochMs >= 0) { "addedAtEpochMs must not be negative" }
        require(lastScannedAtEpochMs == null || lastScannedAtEpochMs >= 0) {
            "lastScannedAtEpochMs must not be negative"
        }
        require(lastError == null || lastError.isNotBlank()) {
            "lastError must not be blank"
        }
    }
}

enum class DesktopLibraryRootProvenance {
    USER_SELECTED,
    ANI_RSS_OUTPUT_FOLDER,
}

enum class DesktopLibraryRootState {
    AVAILABLE,
    MISSING,
}

data class DesktopAppSetting(
    val key: String,
    val value: String,
    val updatedAtEpochMs: Long,
) {
    init {
        require(key.isNotBlank()) { "key must not be blank" }
        require(updatedAtEpochMs >= 0) { "updatedAtEpochMs must not be negative" }
    }
}

data class DesktopDownloadQueueItem(
    val id: String,
    val sourceUri: String,
    val outputPath: String,
    val state: String,
    val positionBytes: Long,
    val totalBytes: Long?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val failureMessage: String? = null,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(sourceUri.isNotBlank()) { "sourceUri must not be blank" }
        require(outputPath.isNotBlank()) { "outputPath must not be blank" }
        require(state.isNotBlank()) { "state must not be blank" }
        require(positionBytes >= 0) { "positionBytes must not be negative" }
        require(totalBytes == null || totalBytes >= 0) { "totalBytes must not be negative" }
        require(createdAtEpochMs >= 0) { "createdAtEpochMs must not be negative" }
        require(updatedAtEpochMs >= 0) { "updatedAtEpochMs must not be negative" }
    }
}

data class DesktopDandanplayCommentCache(
    val mediaId: String,
    val fileHash: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val episodeId: Long?,
    val animeId: Long?,
    val animeTitle: String?,
    val episodeTitle: String?,
    val shiftSeconds: Double?,
    val commentsJson: String,
    val renderedAssPath: String?,
    val fetchedAtEpochMs: Long,
) {
    init {
        require(mediaId.isNotBlank()) { "mediaId must not be blank" }
        require(fileHash.matches(Regex("[A-Fa-f0-9]{32}"))) { "fileHash must be a 32-character MD5 hex digest" }
        require(fileName.isNotBlank()) { "fileName must not be blank" }
        require(fileSizeBytes >= 0) { "fileSizeBytes must not be negative" }
        require(episodeId == null || episodeId > 0) { "episodeId must be positive" }
        require(animeId == null || animeId > 0) { "animeId must be positive" }
        require(commentsJson.isNotBlank()) { "commentsJson must not be blank" }
        require(fetchedAtEpochMs >= 0) { "fetchedAtEpochMs must not be negative" }
    }
}

private data class StoredLibraryMetadata(
    val rootPath: String,
    val rootName: String,
    val indexedAtEpochMs: Long,
)
