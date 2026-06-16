package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeInfo
import app.danmaku.domain.ExternalAnimeMappingSource
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySeries
import java.nio.file.Path

class DesktopAnimeMetadataResolver(
    private val catalogStore: DesktopLibraryCatalogStore,
    private val loadConnection: () -> DandanplayConnection,
    private val posterCache: DesktopAnimePosterCache = DesktopAnimePosterCache.default(),
    private val fetchAnimeDetails: (DandanplayConnection, Long) -> ExternalAnimeInfo =
        { connection, animeId -> DandanplayDanmakuClient(connection).fetchAnimeDetails(animeId) },
    private val matchAnimeIdForPath: (DandanplayConnection, Path) -> Long? =
        { connection, path ->
            DandanplayDanmakuClient(connection)
                .match(DandanplayMediaFingerprint.fromPath(path))
                .firstOrNull()
                ?.animeId
        },
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    fun refreshDandanplayMetadataForSeries(
        series: LibrarySeries,
        animeId: Long?,
        forceRefresh: Boolean = false,
    ): Path? {
        val metadata = loadDandanplayMetadata(animeId, forceRefresh) ?: return null
        return fetchPoster(metadata.imageUrl, forceRefresh = forceRefresh)
    }

    fun refreshDandanplayMetadataForAnime(
        animeId: Long?,
        forceRefresh: Boolean = false,
    ): Path? {
        val metadata = loadDandanplayMetadata(animeId, forceRefresh) ?: return null
        return fetchPoster(metadata.imageUrl, forceRefresh = forceRefresh)
    }

    fun refreshDandanplayMetadataForItem(
        item: LibraryMediaItem,
        animeId: Long?,
        forceRefresh: Boolean = false,
    ): Path? {
        val metadata = loadDandanplayMetadata(animeId, forceRefresh) ?: return null
        catalogStore.saveExternalAnimeItemMapping(
            DesktopExternalAnimeItemMapping(
                localMediaId = item.id,
                animeId = metadata.id,
                source = ExternalAnimeMappingSource.AUTO,
                confidence = 1.0,
                mappedAtEpochMs = nowEpochMs(),
            ),
        )
        return fetchPoster(metadata.imageUrl, forceRefresh = forceRefresh)
    }

    fun ensureDandanplayPosterForSeries(
        series: LibrarySeries,
        mediaPathById: Map<String, Path>,
        forceRefresh: Boolean = false,
    ): Path? {
        if (!forceRefresh) {
            cachedPosterForSeries(series)?.let { return it }
        }
        val cachedAnimeId = cachedDandanplayAnimeIdForSeries(series)
        if (cachedAnimeId != null) {
            return refreshDandanplayMetadataForSeries(
                series = series,
                animeId = cachedAnimeId,
                forceRefresh = forceRefresh,
            )
        }

        val mediaItem = series.firstIndexedItem()
        val mediaPath = mediaPathById[mediaItem.id] ?: return null
        val animeId = matchAnimeIdForPath(loadConnection(), mediaPath)
            ?: return null
        return refreshDandanplayMetadataForSeries(
            series = series,
            animeId = animeId,
            forceRefresh = forceRefresh,
        )
    }

    fun cachedPosterForSeries(series: LibrarySeries): Path? =
        cachedAnimeIdsForSeries(series)
            .asSequence()
            .mapNotNull(catalogStore::loadExternalAnimeMetadataCache)
            .mapNotNull { metadata -> posterCache.cachedPath(metadata.anime.imageUrl) }
            .firstOrNull()

    fun cachedPosterForItem(item: LibraryMediaItem): Path? =
        cachedAnimeInfoForItem(item)
            ?.imageUrl
            ?.let(posterCache::cachedPath)

    fun cachedAnimeInfoForSeries(series: LibrarySeries): ExternalAnimeInfo? =
        cachedAnimeIdsForSeries(series)
            .asSequence()
            .mapNotNull(catalogStore::loadExternalAnimeMetadataCache)
            .firstOrNull()
            ?.anime

    fun cachedAnimeInfoForItem(item: LibraryMediaItem): ExternalAnimeInfo? =
        catalogStore.loadExternalAnimeItemMappings(item.id)
            .asSequence()
            .map(DesktopExternalAnimeItemMapping::animeId)
            .filter { it.provider == ExternalAnimeProvider.DANDANPLAY }
            .plus(
                catalogStore.loadDandanplayCommentCache(item.id)
                    ?.animeId
                    ?.takeIf { it > 0 }
                    ?.let { ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, it) }
                    ?.let(::sequenceOf)
                    ?: emptySequence(),
            )
            .distinct()
            .firstOrNull()
            ?.let(catalogStore::loadExternalAnimeMetadataCache)
            ?.anime

    fun cachedDandanplayAnimeIdForItem(item: LibraryMediaItem): Long? =
        catalogStore.loadExternalAnimeItemMappings(item.id)
            .asSequence()
            .map(DesktopExternalAnimeItemMapping::animeId)
            .firstOrNull { it.provider == ExternalAnimeProvider.DANDANPLAY }
            ?.value
            ?: catalogStore.loadDandanplayCommentCache(item.id)
            ?.animeId
            ?.takeIf { it > 0 }

    private fun loadDandanplayMetadata(
        animeId: Long?,
        forceRefresh: Boolean,
    ): ExternalAnimeInfo? {
        val externalAnimeId = animeId
            ?.takeIf { it > 0 }
            ?.let { ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, it) }
            ?: return null

        val cachedMetadata = catalogStore.loadExternalAnimeMetadataCache(externalAnimeId)
        return if (!forceRefresh && cachedMetadata != null) {
            cachedMetadata.anime
        } else {
            fetchAnimeDetails(loadConnection(), externalAnimeId.value)
                .also { anime ->
                    catalogStore.saveExternalAnimeMetadataCache(
                        DesktopExternalAnimeMetadataCache(
                            anime = anime,
                            fetchedAtEpochMs = nowEpochMs(),
                        ),
                    )
                }
        }
    }

    private fun fetchPoster(
        imageUrl: String?,
        forceRefresh: Boolean,
    ): Path? =
        try {
            posterCache.fetch(imageUrl, forceRefresh = forceRefresh)
        } catch (_: DesktopAnimePosterCacheException) {
            null
        }

    private fun cachedDandanplayAnimeIdForSeries(series: LibrarySeries): Long? =
        series.indexedItems()
            .mapNotNull { item ->
                catalogStore.loadDandanplayCommentCache(item.id)
                    ?.animeId
                    ?.takeIf { animeId -> animeId > 0 }
            }
            .groupingBy { it }
            .eachCount()
            .maxWithOrNull(
                compareBy<Map.Entry<Long, Int>> { it.value }
                    .thenBy { it.key },
            )
            ?.key

    private fun cachedAnimeIdsForSeries(series: LibrarySeries): List<ExternalAnimeId> =
        series.indexedItems()
            .flatMap { item ->
                sequenceOf(
                    catalogStore.loadExternalAnimeItemMappings(item.id)
                        .asSequence()
                        .map(DesktopExternalAnimeItemMapping::animeId)
                        .firstOrNull { it.provider == ExternalAnimeProvider.DANDANPLAY },
                    catalogStore.loadDandanplayCommentCache(item.id)
                        ?.animeId
                        ?.takeIf { it > 0 }
                        ?.let { ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, it) },
                ).filterNotNull()
            }
            .distinct()
            .toList()
}

private fun LibrarySeries.firstIndexedItem(): LibraryMediaItem =
    indexedItems()
        .first()

private fun LibrarySeries.indexedItems(): Sequence<LibraryMediaItem> =
    seasons
        .asSequence()
        .flatMap { it.items.asSequence() }
