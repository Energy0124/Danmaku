package app.danmaku.desktop

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.danmaku.desktop.db.DesktopLibraryDatabase
import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeInfo
import app.danmaku.domain.ExternalAnimeListEntry
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeMappingSource
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeSyncFailure
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
import app.danmaku.domain.LocalAnimeListEntry
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
            sql = DesktopLibraryCatalogStoreSchema.CREATE_PLAYBACK_PROGRESS_TABLE_SQL,
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = DesktopLibraryCatalogStoreSchema.CREATE_APP_SETTING_TABLE_SQL,
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = DesktopLibraryCatalogStoreSchema.CREATE_DOWNLOAD_QUEUE_ITEM_TABLE_SQL,
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = DesktopLibraryCatalogStoreSchema.CREATE_LIBRARY_ROOT_TABLE_SQL,
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = DesktopLibraryCatalogStoreSchema.CREATE_LIBRARY_ROOT_MEDIA_ITEM_TABLE_SQL,
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = DesktopLibraryCatalogStoreSchema.CREATE_DANDANPLAY_COMMENT_CACHE_TABLE_SQL,
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = DesktopLibraryCatalogStoreSchema.CREATE_EXTERNAL_ANIME_METADATA_CACHE_TABLE_SQL,
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = DesktopLibraryCatalogStoreSchema.CREATE_EXTERNAL_ANIME_MAPPING_TABLE_SQL,
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = DesktopLibraryCatalogStoreSchema.CREATE_EXTERNAL_ANIME_ITEM_MAPPING_TABLE_SQL,
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = DesktopLibraryCatalogStoreSchema.CREATE_EXTERNAL_ANIME_LIST_ENTRY_TABLE_SQL,
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = DesktopLibraryCatalogStoreSchema.CREATE_EXTERNAL_ANIME_SYNC_FAILURE_TABLE_SQL,
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = DesktopLibraryCatalogStoreSchema.CREATE_LOCAL_ANIME_LIST_ENTRY_TABLE_SQL,
            parameters = 0,
        )
        driver.execute(
            identifier = null,
            sql = DesktopLibraryCatalogStoreSchema.CREATE_LIBRARY_QUALITY_ISSUE_DECISION_TABLE_SQL,
            parameters = 0,
        )
        addColumnIfMissing("local_media_item", "subtitles_json", "TEXT NOT NULL DEFAULT '[]'")
        addColumnIfMissing("library_root_media_item", "subtitles_json", "TEXT NOT NULL DEFAULT '[]'")
        addColumnIfMissing("local_media_item", "indexed_at_epoch_ms", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing("library_root_media_item", "indexed_at_epoch_ms", "INTEGER NOT NULL DEFAULT 0")
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
            .selectAllItems(DesktopLibraryCatalogStoreRowMappers::storedItem)
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
                    item.indexedAtEpochMs,
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
            .selectRootItems(root.id, DesktopLibraryCatalogStoreRowMappers::storedItem)
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
                    item.indexedAtEpochMs,
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
    override fun loadAllProgress(): List<PlaybackProgress> =
        database.libraryCatalogQueries
            .selectAllPlaybackProgress(::PlaybackProgress)
            .executeAsList()

    fun loadPlaybackProgress(): List<PlaybackProgress> =
        loadAllProgress()

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
    fun loadFavoriteMediaIds(): Set<String> =
        loadSetting(FAVORITE_MEDIA_IDS_SETTING_KEY)
            ?.value
            ?.lineSequence()
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.toSet()
            ?: emptySet()

    @Synchronized
    fun saveFavoriteMediaIds(mediaIds: Set<String>) {
        require(mediaIds.none { it.isBlank() }) { "favorite media ids must not contain blank ids" }
        if (mediaIds.isEmpty()) {
            deleteSetting(FAVORITE_MEDIA_IDS_SETTING_KEY)
        } else {
            saveSetting(
                DesktopAppSetting(
                    key = FAVORITE_MEDIA_IDS_SETTING_KEY,
                    value = mediaIds.sorted().joinToString(separator = "\n"),
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    @Synchronized
    fun loadLibraryRoot(id: String): DesktopLibraryRoot? =
        database.libraryCatalogQueries
            .selectLibraryRoot(id, DesktopLibraryCatalogStoreRowMappers::desktopLibraryRoot)
            .executeAsOneOrNull()

    @Synchronized
    fun loadLibraryRoots(): List<DesktopLibraryRoot> =
        database.libraryCatalogQueries
            .selectAllLibraryRoots(DesktopLibraryCatalogStoreRowMappers::desktopLibraryRoot)
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
        database.libraryCatalogQueries.deleteRootItems(id)
        database.libraryCatalogQueries.deleteLibraryRoot(id)
    }

    @Synchronized
    fun loadDownload(id: String): DesktopDownloadQueueItem? =
        database.libraryCatalogQueries
            .selectDownload(id, DesktopLibraryCatalogStoreRowMappers::desktopDownloadQueueItem)
            .executeAsOneOrNull()

    @Synchronized
    fun loadDownloads(): List<DesktopDownloadQueueItem> =
        database.libraryCatalogQueries
            .selectAllDownloads(DesktopLibraryCatalogStoreRowMappers::desktopDownloadQueueItem)
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
            .selectDandanplayCommentCache(mediaId, DesktopLibraryCatalogStoreRowMappers::desktopDandanplayCommentCache)
            .executeAsOneOrNull()

    @Synchronized
    fun loadDandanplayCommentCaches(): List<DesktopDandanplayCommentCache> =
        database.libraryCatalogQueries
            .selectDandanplayCommentCaches(DesktopLibraryCatalogStoreRowMappers::desktopDandanplayCommentCache)
            .executeAsList()

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
    override fun deleteDandanplayCommentCache(mediaId: String) {
        database.libraryCatalogQueries.deleteDandanplayCommentCache(mediaId)
    }

    @Synchronized
    override fun deleteDandanplayCommentCachesOlderThan(cutoffEpochMs: Long) {
        require(cutoffEpochMs >= 0) { "cutoffEpochMs must not be negative" }
        database.libraryCatalogQueries.deleteDandanplayCommentCachesOlderThan(cutoffEpochMs)
    }

    @Synchronized
    fun loadExternalAnimeMetadataCache(id: ExternalAnimeId): DesktopExternalAnimeMetadataCache? =
        database.libraryCatalogQueries
            .selectExternalAnimeMetadataCache(
                id.provider.name,
                id.value,
                DesktopLibraryCatalogStoreRowMappers::desktopExternalAnimeMetadataCache,
            )
            .executeAsOneOrNull()

    @Synchronized
    fun saveExternalAnimeMetadataCache(cache: DesktopExternalAnimeMetadataCache) {
        database.libraryCatalogQueries.upsertExternalAnimeMetadataCache(
            cache.anime.id.provider.name,
            cache.anime.id.value,
            Json.encodeToString(cache.anime.titles),
            cache.anime.episodeCount?.toLong(),
            cache.anime.startYear?.toLong(),
            cache.anime.imageUrl,
            cache.anime.summary,
            cache.fetchedAtEpochMs,
        )
    }

    @Synchronized
    fun deleteExternalAnimeMetadataCache(id: ExternalAnimeId) {
        database.libraryCatalogQueries.deleteExternalAnimeMetadataCache(id.provider.name, id.value)
    }

    @Synchronized
    fun loadExternalAnimeMappings(localSeriesId: String): List<ExternalAnimeMapping> {
        require(localSeriesId.isNotBlank()) { "localSeriesId must not be blank" }
        return database.libraryCatalogQueries
            .selectExternalAnimeMappings(localSeriesId, DesktopLibraryCatalogStoreRowMappers::externalAnimeMapping)
            .executeAsList()
    }

    @Synchronized
    fun saveExternalAnimeMapping(mapping: ExternalAnimeMapping) {
        database.libraryCatalogQueries.upsertExternalAnimeMapping(
            mapping.localSeriesId,
            mapping.animeId.provider.name,
            mapping.animeId.value,
            mapping.source.name,
            mapping.confidence,
            mapping.mappedAtEpochMs,
        )
    }

    @Synchronized
    fun deleteExternalAnimeMapping(localSeriesId: String, provider: ExternalAnimeProvider) {
        require(localSeriesId.isNotBlank()) { "localSeriesId must not be blank" }
        database.libraryCatalogQueries.deleteExternalAnimeMapping(localSeriesId, provider.name)
    }

    @Synchronized
    fun deleteExternalAnimeMappings(provider: ExternalAnimeProvider, source: ExternalAnimeMappingSource) {
        database.libraryCatalogQueries.deleteExternalAnimeMappingsByProviderSource(provider.name, source.name)
    }

    @Synchronized
    fun loadExternalAnimeItemMappings(localMediaId: String): List<DesktopExternalAnimeItemMapping> {
        require(localMediaId.isNotBlank()) { "localMediaId must not be blank" }
        return database.libraryCatalogQueries
            .selectExternalAnimeItemMappings(localMediaId, DesktopLibraryCatalogStoreRowMappers::desktopExternalAnimeItemMapping)
            .executeAsList()
    }

    @Synchronized
    fun saveExternalAnimeItemMapping(mapping: DesktopExternalAnimeItemMapping) {
        database.libraryCatalogQueries.upsertExternalAnimeItemMapping(
            mapping.localMediaId,
            mapping.animeId.provider.name,
            mapping.animeId.value,
            mapping.source.name,
            mapping.confidence,
            mapping.mappedAtEpochMs,
        )
    }

    @Synchronized
    fun deleteExternalAnimeItemMapping(localMediaId: String, provider: ExternalAnimeProvider) {
        require(localMediaId.isNotBlank()) { "localMediaId must not be blank" }
        database.libraryCatalogQueries.deleteExternalAnimeItemMapping(localMediaId, provider.name)
    }

    @Synchronized
    fun loadExternalAnimeListEntry(id: ExternalAnimeId): ExternalAnimeListEntry? =
        database.libraryCatalogQueries
            .selectExternalAnimeListEntry(
                id.provider.name,
                id.value,
                DesktopLibraryCatalogStoreRowMappers::externalAnimeListEntry,
            )
            .executeAsOneOrNull()

    @Synchronized
    fun loadExternalAnimeListEntries(): List<ExternalAnimeListEntry> =
        database.libraryCatalogQueries
            .selectAllExternalAnimeListEntries(DesktopLibraryCatalogStoreRowMappers::externalAnimeListEntry)
            .executeAsList()

    @Synchronized
    fun saveExternalAnimeListEntry(entry: ExternalAnimeListEntry) {
        database.libraryCatalogQueries.upsertExternalAnimeListEntry(
            entry.animeId.provider.name,
            entry.animeId.value,
            entry.status?.name,
            entry.watchedEpisodes?.toLong(),
            entry.score?.toLong(),
            entry.updatedAtEpochMs,
        )
    }

    @Synchronized
    fun saveExternalAnimeListEntries(entries: List<ExternalAnimeListEntry>) {
        database.libraryCatalogQueries.transaction {
            entries.forEach(::saveExternalAnimeListEntry)
        }
    }

    @Synchronized
    fun deleteExternalAnimeListEntry(id: ExternalAnimeId) {
        database.libraryCatalogQueries.deleteExternalAnimeListEntry(id.provider.name, id.value)
    }

    @Synchronized
    fun deleteExternalAnimeListEntries(ids: Set<ExternalAnimeId>) {
        database.libraryCatalogQueries.transaction {
            ids.forEach(::deleteExternalAnimeListEntry)
        }
    }

    @Synchronized
    fun loadExternalAnimeSyncFailures(): List<ExternalAnimeSyncFailure> =
        database.libraryCatalogQueries
            .selectAllExternalAnimeSyncFailures(DesktopLibraryCatalogStoreRowMappers::externalAnimeSyncFailure)
            .executeAsList()

    @Synchronized
    fun replaceExternalAnimeSyncFailures(failures: List<ExternalAnimeSyncFailure>) {
        database.libraryCatalogQueries.transaction {
            database.libraryCatalogQueries.deleteAllExternalAnimeSyncFailures()
            failures.forEach { failure ->
                database.libraryCatalogQueries.upsertExternalAnimeSyncFailure(
                    failure.animeId.provider.name,
                    failure.animeId.value,
                    failure.message,
                    failure.failedAtEpochMs,
                    failure.attemptCount.toLong(),
                    failure.retryAfterEpochMs,
                )
            }
        }
    }

    @Synchronized
    fun loadLocalAnimeListEntry(localSeriesId: String): LocalAnimeListEntry? {
        require(localSeriesId.isNotBlank()) { "localSeriesId must not be blank" }
        return database.libraryCatalogQueries
            .selectLocalAnimeListEntry(localSeriesId, DesktopLibraryCatalogStoreRowMappers::localAnimeListEntry)
            .executeAsOneOrNull()
    }

    @Synchronized
    fun loadLocalAnimeListEntries(): List<LocalAnimeListEntry> =
        database.libraryCatalogQueries
            .selectAllLocalAnimeListEntries(DesktopLibraryCatalogStoreRowMappers::localAnimeListEntry)
            .executeAsList()

    @Synchronized
    fun saveLocalAnimeListEntry(entry: LocalAnimeListEntry) {
        database.libraryCatalogQueries.upsertLocalAnimeListEntry(
            entry.localSeriesId,
            entry.status.name,
            entry.score?.toLong(),
            entry.notes,
            entry.updatedAtEpochMs,
        )
    }

    @Synchronized
    fun deleteLocalAnimeListEntry(localSeriesId: String) {
        require(localSeriesId.isNotBlank()) { "localSeriesId must not be blank" }
        database.libraryCatalogQueries.deleteLocalAnimeListEntry(localSeriesId)
    }

    @Synchronized
    fun loadLibraryQualityIssueDecision(issueKey: String): DesktopLibraryQualityIssueDecision? {
        require(issueKey.isNotBlank()) { "issueKey must not be blank" }
        return database.libraryCatalogQueries
            .selectLibraryQualityIssueDecision(
                issueKey,
                DesktopLibraryCatalogStoreRowMappers::desktopLibraryQualityIssueDecision,
            )
            .executeAsOneOrNull()
    }

    @Synchronized
    fun loadLibraryQualityIssueDecisions(): List<DesktopLibraryQualityIssueDecision> =
        database.libraryCatalogQueries
            .selectAllLibraryQualityIssueDecisions(
                DesktopLibraryCatalogStoreRowMappers::desktopLibraryQualityIssueDecision,
            )
            .executeAsList()

    @Synchronized
    fun saveLibraryQualityIssueDecision(decision: DesktopLibraryQualityIssueDecision) {
        database.libraryCatalogQueries.upsertLibraryQualityIssueDecision(
            decision.issueKey,
            decision.state.name,
            decision.updatedAtEpochMs,
        )
    }

    @Synchronized
    fun deleteLibraryQualityIssueDecision(issueKey: String) {
        require(issueKey.isNotBlank()) { "issueKey must not be blank" }
        database.libraryCatalogQueries.deleteLibraryQualityIssueDecision(issueKey)
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
        fun default(): DesktopLibraryCatalogStore {
            val appDataRoot = System.getenv("LOCALAPPDATA")
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?: Path.of(System.getProperty("user.home"), ".danmaku")
            return DesktopLibraryCatalogStore(
                appDataRoot.resolve("Danmaku").resolve("library.db"),
            )
        }
    }
}

private const val FAVORITE_MEDIA_IDS_SETTING_KEY = "library.favorite_media_ids"

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

data class DesktopExternalAnimeMetadataCache(
    val anime: ExternalAnimeInfo,
    val fetchedAtEpochMs: Long,
) {
    init {
        require(fetchedAtEpochMs >= 0) { "fetchedAtEpochMs must not be negative" }
    }
}

data class DesktopExternalAnimeItemMapping(
    val localMediaId: String,
    val animeId: ExternalAnimeId,
    val source: ExternalAnimeMappingSource,
    val confidence: Double,
    val mappedAtEpochMs: Long,
) {
    init {
        require(localMediaId.isNotBlank()) { "localMediaId must not be blank" }
        require(confidence in 0.0..1.0) { "confidence must be between 0 and 1" }
        require(mappedAtEpochMs >= 0) { "mappedAtEpochMs must not be negative" }
    }
}

data class DesktopLibraryQualityIssueDecision(
    val issueKey: String,
    val state: DesktopLibraryQualityIssueDecisionState,
    val updatedAtEpochMs: Long,
) {
    init {
        require(issueKey.isNotBlank()) { "issueKey must not be blank" }
        require(updatedAtEpochMs >= 0) { "updatedAtEpochMs must not be negative" }
    }
}

enum class DesktopLibraryQualityIssueDecisionState {
    IGNORED,
    RESOLVED,
}

private data class StoredLibraryMetadata(
    val rootPath: String,
    val rootName: String,
    val indexedAtEpochMs: Long,
)
