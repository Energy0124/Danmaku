package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeInfo
import app.danmaku.domain.ExternalAnimeListEntry
import app.danmaku.domain.ExternalAnimeListStatus
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeMappingSource
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeSyncFailure
import app.danmaku.domain.ExternalAnimeTitleSet
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
import kotlinx.serialization.json.Json
import java.nio.file.Path

internal object DesktopLibraryCatalogStoreSchema {
    val CREATE_PLAYBACK_PROGRESS_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS playback_progress (
          media_id TEXT NOT NULL PRIMARY KEY,
          position_ms INTEGER NOT NULL,
          duration_ms INTEGER,
          updated_at_epoch_ms INTEGER NOT NULL
        )
    """.trimIndent()

    val CREATE_APP_SETTING_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS app_setting (
          setting_key TEXT NOT NULL PRIMARY KEY,
          setting_value TEXT NOT NULL,
          updated_at_epoch_ms INTEGER NOT NULL
        )
    """.trimIndent()

    val CREATE_LIBRARY_ROOT_TABLE_SQL = """
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

    val CREATE_LIBRARY_ROOT_MEDIA_ITEM_TABLE_SQL = """
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

    val CREATE_DOWNLOAD_QUEUE_ITEM_TABLE_SQL = """
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

    val CREATE_DANDANPLAY_COMMENT_CACHE_TABLE_SQL = """
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

    val CREATE_EXTERNAL_ANIME_METADATA_CACHE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS external_anime_metadata_cache (
          provider TEXT NOT NULL,
          anime_id INTEGER NOT NULL,
          titles_json TEXT NOT NULL,
          episode_count INTEGER,
          start_year INTEGER,
          image_url TEXT,
          summary TEXT,
          fetched_at_epoch_ms INTEGER NOT NULL,
          PRIMARY KEY(provider, anime_id)
        )
    """.trimIndent()

    val CREATE_EXTERNAL_ANIME_MAPPING_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS external_anime_mapping (
          local_series_id TEXT NOT NULL,
          provider TEXT NOT NULL,
          anime_id INTEGER NOT NULL,
          source TEXT NOT NULL,
          confidence REAL NOT NULL,
          mapped_at_epoch_ms INTEGER NOT NULL,
          PRIMARY KEY(local_series_id, provider)
        )
    """.trimIndent()

    val CREATE_EXTERNAL_ANIME_ITEM_MAPPING_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS external_anime_item_mapping (
          local_media_id TEXT NOT NULL,
          provider TEXT NOT NULL,
          anime_id INTEGER NOT NULL,
          source TEXT NOT NULL,
          confidence REAL NOT NULL,
          mapped_at_epoch_ms INTEGER NOT NULL,
          PRIMARY KEY(local_media_id, provider)
        )
    """.trimIndent()

    val CREATE_EXTERNAL_ANIME_LIST_ENTRY_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS external_anime_list_entry (
          provider TEXT NOT NULL,
          anime_id INTEGER NOT NULL,
          status TEXT,
          watched_episodes INTEGER,
          score INTEGER,
          updated_at_epoch_ms INTEGER,
          PRIMARY KEY(provider, anime_id)
        )
    """.trimIndent()

    val CREATE_EXTERNAL_ANIME_SYNC_FAILURE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS external_anime_sync_failure (
          provider TEXT NOT NULL,
          anime_id INTEGER NOT NULL,
          message TEXT NOT NULL,
          failed_at_epoch_ms INTEGER NOT NULL,
          attempt_count INTEGER NOT NULL,
          retry_after_epoch_ms INTEGER NOT NULL,
          PRIMARY KEY(provider, anime_id)
        )
    """.trimIndent()
}

internal object DesktopLibraryCatalogStoreRowMappers {
    fun storedItem(
        relativePath: String,
        id: String,
        seriesTitle: String,
        episodeTitle: String,
        sizeBytes: Long,
        lastModifiedEpochMs: Long,
        indexedAtEpochMs: Long,
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
                indexedAtEpochMs = indexedAtEpochMs.takeIf { it > 0 } ?: lastModifiedEpochMs,
                subtitles = Json.decodeFromString<List<LibrarySubtitleTrack>>(subtitlesJson),
            ),
            lastModifiedEpochMs = lastModifiedEpochMs,
        )

    fun desktopLibraryRoot(
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

    fun desktopDownloadQueueItem(
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

    fun desktopDandanplayCommentCache(
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

    fun desktopExternalAnimeMetadataCache(
        provider: String,
        animeId: Long,
        titlesJson: String,
        episodeCount: Long?,
        startYear: Long?,
        imageUrl: String?,
        summary: String?,
        fetchedAtEpochMs: Long,
    ): DesktopExternalAnimeMetadataCache =
        DesktopExternalAnimeMetadataCache(
            anime = ExternalAnimeInfo(
                id = ExternalAnimeId(
                    provider = ExternalAnimeProvider.valueOf(provider),
                    value = animeId,
                ),
                titles = Json.decodeFromString<ExternalAnimeTitleSet>(titlesJson),
                episodeCount = episodeCount?.toInt(),
                startYear = startYear?.toInt(),
                imageUrl = imageUrl,
                summary = summary,
            ),
            fetchedAtEpochMs = fetchedAtEpochMs,
        )

    fun externalAnimeMapping(
        localSeriesId: String,
        provider: String,
        animeId: Long,
        source: String,
        confidence: Double,
        mappedAtEpochMs: Long,
    ): ExternalAnimeMapping =
        ExternalAnimeMapping(
            localSeriesId = localSeriesId,
            animeId = ExternalAnimeId(
                provider = ExternalAnimeProvider.valueOf(provider),
                value = animeId,
            ),
            source = ExternalAnimeMappingSource.valueOf(source),
            confidence = confidence,
            mappedAtEpochMs = mappedAtEpochMs,
        )

    fun desktopExternalAnimeItemMapping(
        localMediaId: String,
        provider: String,
        animeId: Long,
        source: String,
        confidence: Double,
        mappedAtEpochMs: Long,
    ): DesktopExternalAnimeItemMapping =
        DesktopExternalAnimeItemMapping(
            localMediaId = localMediaId,
            animeId = ExternalAnimeId(
                provider = ExternalAnimeProvider.valueOf(provider),
                value = animeId,
            ),
            source = ExternalAnimeMappingSource.valueOf(source),
            confidence = confidence,
            mappedAtEpochMs = mappedAtEpochMs,
        )

    fun externalAnimeListEntry(
        provider: String,
        animeId: Long,
        status: String?,
        watchedEpisodes: Long?,
        score: Long?,
        updatedAtEpochMs: Long?,
    ): ExternalAnimeListEntry =
        ExternalAnimeListEntry(
            animeId = ExternalAnimeId(
                provider = ExternalAnimeProvider.valueOf(provider),
                value = animeId,
            ),
            status = status?.let(ExternalAnimeListStatus::valueOf),
            watchedEpisodes = watchedEpisodes?.toInt(),
            score = score?.toInt(),
            updatedAtEpochMs = updatedAtEpochMs,
        )

    fun externalAnimeSyncFailure(
        provider: String,
        animeId: Long,
        message: String,
        failedAtEpochMs: Long,
        attemptCount: Long,
        retryAfterEpochMs: Long,
    ): ExternalAnimeSyncFailure =
        ExternalAnimeSyncFailure(
            animeId = ExternalAnimeId(
                provider = ExternalAnimeProvider.valueOf(provider),
                value = animeId,
            ),
            message = message,
            failedAtEpochMs = failedAtEpochMs,
            attemptCount = attemptCount.toInt(),
            retryAfterEpochMs = retryAfterEpochMs,
        )
}
