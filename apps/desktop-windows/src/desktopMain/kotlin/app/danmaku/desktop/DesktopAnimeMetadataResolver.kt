package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeMappingSource
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.LibrarySeries
import java.nio.file.Path

class DesktopAnimeMetadataResolver(
    private val catalogStore: DesktopLibraryCatalogStore,
    private val loadConnection: () -> DandanplayConnection,
    private val posterCache: DesktopAnimePosterCache = DesktopAnimePosterCache.default(),
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
            DandanplayDanmakuClient(loadConnection()).fetchAnimeDetails(externalAnimeId.value)
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

    fun cachedPosterForSeries(series: LibrarySeries): Path? =
        catalogStore.loadExternalAnimeMappings(series.id)
            .asSequence()
            .filter { it.animeId.provider == ExternalAnimeProvider.DANDANPLAY }
            .mapNotNull { mapping -> catalogStore.loadExternalAnimeMetadataCache(mapping.animeId) }
            .mapNotNull { metadata -> posterCache.cachedPath(metadata.anime.imageUrl) }
            .firstOrNull()
}
