package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeMappingSource
import app.danmaku.domain.LibraryAnimeMetadata
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibraryQualityIssue
import app.danmaku.domain.LibraryQualityIssueType

internal data class DesktopLibraryQualityMappingApplyPlan(
    val itemMappings: List<DesktopExternalAnimeItemMapping>,
    val seriesMappings: List<ExternalAnimeMapping>,
) {
    init {
        require(itemMappings.isNotEmpty() || seriesMappings.isNotEmpty()) {
            "apply plan must contain mappings"
        }
    }

    val mappingCount: Int = itemMappings.size + seriesMappings.size
}

internal fun LibraryQualityIssue.libraryQualityMappingApplyPlan(
    catalog: LibraryCatalog,
    mappedAtEpochMs: Long,
): DesktopLibraryQualityMappingApplyPlan? =
    when (type) {
        LibraryQualityIssueType.SPLIT_SERIES_CANDIDATE -> splitSeriesApplyPlan(catalog, mappedAtEpochMs)
        LibraryQualityIssueType.MERGE_SERIES_CANDIDATE -> mergeSeriesApplyPlan(catalog, mappedAtEpochMs)
        else -> null
    }

private fun LibraryQualityIssue.splitSeriesApplyPlan(
    catalog: LibraryCatalog,
    mappedAtEpochMs: Long,
): DesktopLibraryQualityMappingApplyPlan? {
    val affectedItems = affectedItems(catalog)
    val metadataByItem = affectedItems.mapNotNull { item ->
        item.animeMetadata?.let { metadata -> item to metadata }
    }
    if (metadataByItem.size != affectedItems.size) return null
    val animeIds = metadataByItem
        .map { (_, metadata) -> metadata.animeId }
        .distinct()
        .sortedBy(ExternalAnimeId::qualityMappingSortKey)
    if (animeIds.size < 2) return null

    return DesktopLibraryQualityMappingApplyPlan(
        itemMappings = metadataByItem.itemMappings(mappedAtEpochMs),
        seriesMappings = animeIds.map { animeId ->
            animeId.toQualitySeriesMapping(localSeriesId = animeId.metadataSeriesId(), mappedAtEpochMs)
        },
    )
}

private fun LibraryQualityIssue.mergeSeriesApplyPlan(
    catalog: LibraryCatalog,
    mappedAtEpochMs: Long,
): DesktopLibraryQualityMappingApplyPlan? {
    val affectedItems = affectedItems(catalog)
    val metadataByItem = affectedItems.mapNotNull { item ->
        item.animeMetadata?.let { metadata -> item to metadata }
    }
    if (metadataByItem.size != affectedItems.size) return null
    val animeId = metadataByItem
        .map { (_, metadata) -> metadata.animeId }
        .distinct()
        .singleOrNull()
        ?: return null
    val localSeriesTitles = affectedItems
        .map(LibraryMediaItem::seriesTitle)
        .distinct()
        .sorted()
    if (localSeriesTitles.size < 2) return null

    val localSeriesIds = (
        listOf(seriesId, animeId.metadataSeriesId()) +
            localSeriesTitles.map(String::stableLocalSeriesMappingId)
        )
        .distinct()
        .sorted()

    return DesktopLibraryQualityMappingApplyPlan(
        itemMappings = metadataByItem.itemMappings(mappedAtEpochMs),
        seriesMappings = localSeriesIds.map { localSeriesId ->
            animeId.toQualitySeriesMapping(localSeriesId, mappedAtEpochMs)
        },
    )
}

private fun LibraryQualityIssue.affectedItems(catalog: LibraryCatalog): List<LibraryMediaItem> {
    val itemById = catalog.items.associateBy(LibraryMediaItem::id)
    return mediaItemIds.mapNotNull(itemById::get)
}

private fun List<Pair<LibraryMediaItem, LibraryAnimeMetadata>>.itemMappings(
    mappedAtEpochMs: Long,
): List<DesktopExternalAnimeItemMapping> =
    map { (item, metadata) ->
        DesktopExternalAnimeItemMapping(
            localMediaId = item.id,
            animeId = metadata.animeId,
            source = ExternalAnimeMappingSource.MANUAL,
            confidence = 1.0,
            mappedAtEpochMs = mappedAtEpochMs,
        )
    }

private fun ExternalAnimeId.toQualitySeriesMapping(
    localSeriesId: String,
    mappedAtEpochMs: Long,
): ExternalAnimeMapping =
    ExternalAnimeMapping(
        localSeriesId = localSeriesId,
        animeId = this,
        source = ExternalAnimeMappingSource.MANUAL,
        confidence = 1.0,
        mappedAtEpochMs = mappedAtEpochMs,
    )

private fun ExternalAnimeId.metadataSeriesId(): String =
    "anime-${qualityMappingSortKey()}"

private fun ExternalAnimeId.qualityMappingSortKey(): String =
    "${provider.name.lowercase()}-$value"

private fun String.stableLocalSeriesMappingId(): String {
    val normalized = trim()
        .lowercase()
        .map { char ->
            when {
                char.isLetterOrDigit() -> char
                else -> '-'
            }
        }
        .joinToString(separator = "")
        .replace(Regex("-+"), "-")
        .trim('-')
    return normalized.ifBlank { "series" }
}
