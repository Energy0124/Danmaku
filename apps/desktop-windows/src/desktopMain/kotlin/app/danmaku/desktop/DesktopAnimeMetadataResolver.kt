package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeInfo
import app.danmaku.domain.ExternalAnimeMapping
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
        val externalAnimeId = animeId
            ?.takeIf { it > 0 }
            ?.let { ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, it) }
            ?: return null

        val cachedMetadata = catalogStore.loadExternalAnimeMetadataCache(externalAnimeId)
        val metadata = if (!forceRefresh && cachedMetadata != null) {
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

        catalogStore.saveExternalAnimeMapping(
            ExternalAnimeMapping(
                localSeriesId = series.id,
                animeId = metadata.id,
                source = ExternalAnimeMappingSource.AUTO,
                confidence = 1.0,
                mappedAtEpochMs = nowEpochMs(),
            ),
        )
        return posterCache.fetch(metadata.imageUrl, forceRefresh = forceRefresh)
    }

    fun ensureDandanplayPosterForSeries(
        series: LibrarySeries,
        mediaPathById: Map<String, Path>,
        forceRefresh: Boolean = false,
    ): Path? {
        if (!forceRefresh) {
            cachedPosterForSeries(series)?.let { return it }
        }
        val mappedAnimeId = catalogStore.loadExternalAnimeMappings(series.id)
            .firstOrNull { it.animeId.provider == ExternalAnimeProvider.DANDANPLAY }
            ?.animeId
            ?.value
        if (mappedAnimeId != null) {
            return refreshDandanplayMetadataForSeries(
                series = series,
                animeId = mappedAnimeId,
                forceRefresh = forceRefresh,
            )
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
        catalogStore.loadExternalAnimeMappings(series.id)
            .asSequence()
            .filter { it.animeId.provider == ExternalAnimeProvider.DANDANPLAY }
            .mapNotNull { mapping -> catalogStore.loadExternalAnimeMetadataCache(mapping.animeId) }
            .mapNotNull { metadata -> posterCache.cachedPath(metadata.anime.imageUrl) }
            .firstOrNull()

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
}

private fun LibrarySeries.firstIndexedItem(): LibraryMediaItem =
    indexedItems()
        .first()

private fun LibrarySeries.indexedItems(): Sequence<LibraryMediaItem> =
    seasons
        .asSequence()
        .flatMap { it.items.asSequence() }
