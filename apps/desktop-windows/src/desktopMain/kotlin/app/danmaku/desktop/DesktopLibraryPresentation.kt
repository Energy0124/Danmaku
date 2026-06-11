package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeInfo
import app.danmaku.domain.LibraryAnimeMetadata
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryItemMetadataStatus
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LibraryWatchState
import app.danmaku.domain.LibraryWatchStatus
import app.danmaku.domain.groupedSeries
import app.danmaku.server.PublishedLibrary
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

private val COVER_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

private val COVER_IMAGE_NAMES = listOf(
    "poster",
    "cover",
    "folder",
    "keyvisual",
    "key_visual",
    "key-visual",
    "thumbnail",
    "thumb",
)

internal fun nextPlayableEpisode(
    librarySeries: LibrarySeries,
    watchStatusById: Map<String, LibraryWatchStatus>,
): LibraryMediaItem {
    val items = librarySeries.seasons.flatMap { it.items }
    return items.firstOrNull { watchStatusById[it.id]?.state == LibraryWatchState.IN_PROGRESS }
        ?: items.firstOrNull { watchStatusById[it.id]?.state != LibraryWatchState.WATCHED }
        ?: items.first()
}

internal fun findSeriesCoverImage(
    series: LibrarySeries,
    indexedLibrary: IndexedLocalLibrary?,
): Path? {
    val firstItem = series.seasons.firstOrNull()?.items?.firstOrNull() ?: return null
    val firstMediaPath = indexedLibrary?.filesById?.get(firstItem.id) ?: return null
    return findCoverImageNear(firstMediaPath)
}

internal fun IndexedLocalLibrary.withExternalAnimeMetadata(
    metadataResolver: DesktopAnimeMetadataResolver,
): IndexedLocalLibrary {
    val displayCatalog = catalog.withExternalAnimeMetadata(metadataResolver)
    return if (displayCatalog == catalog) {
        this
    } else {
        copy(catalog = displayCatalog)
    }
}

internal fun LibraryCatalog.withExternalAnimeMetadata(
    metadataResolver: DesktopAnimeMetadataResolver,
): LibraryCatalog {
    var changed = false
    val displayItems = items.map { item ->
        val animeInfo = metadataResolver.cachedAnimeInfoForItem(item)
        val posterPath = metadataResolver.cachedPosterForItem(item)?.let { "/posters/${item.id}" }
        val metadata = animeInfo?.toLibraryAnimeMetadata()
        val status = when {
            metadata != null || posterPath != null -> LibraryItemMetadataStatus.READY
            else -> LibraryItemMetadataStatus.NOT_AVAILABLE
        }
        if (
            item.animeMetadata == metadata &&
            item.posterPath == posterPath &&
            item.metadataStatus == status
        ) {
            item
        } else {
            changed = true
            item.copy(
                animeMetadata = metadata,
                posterPath = posterPath,
                metadataStatus = status,
            )
        }
    }
    return if (changed) copy(items = displayItems) else this
}

internal fun ExternalAnimeInfo.libraryDisplayTitle(): String =
    titles.chinese ?: titles.primary

private fun ExternalAnimeInfo.toLibraryAnimeMetadata(): LibraryAnimeMetadata =
    LibraryAnimeMetadata(
        animeId = id,
        displayTitle = libraryDisplayTitle(),
        primaryTitle = titles.primary,
        chineseTitle = titles.chinese,
        englishTitle = titles.english,
        japaneseTitle = titles.japanese,
        imageUrl = imageUrl,
        episodeCount = episodeCount,
        startYear = startYear,
    )

internal fun loadSeriesPosterById(
    indexedLibrary: IndexedLocalLibrary?,
    metadataResolver: DesktopAnimeMetadataResolver,
): Map<String, Path?> {
    val library = indexedLibrary ?: return emptyMap()
    return library.catalog.groupedSeries().associate { series ->
        series.id to (
            findSeriesCoverImage(series, library)
                ?: metadataResolver.cachedPosterForSeries(series)
            )
    }
}

private fun findCoverImageNear(mediaPath: Path): Path? {
    val directories = sequenceOf(mediaPath.parent, mediaPath.parent?.parent)
        .filterNotNull()
        .distinct()
        .toList()
    return directories.firstNotNullOfOrNull(::findPreferredCoverImage)
}

private fun findPreferredCoverImage(directory: Path): Path? {
    if (!Files.isDirectory(directory)) return null
    val images = Files.list(directory).use { paths ->
        paths
            .filter(Files::isRegularFile)
            .filter { it.fileName.toString().substringAfterLast('.', "").lowercase() in COVER_IMAGE_EXTENSIONS }
            .sorted(compareBy<Path> { coverImageRank(it.fileName.toString()) }.thenBy { it.fileName.toString().lowercase() })
            .toList()
    }
    return images.firstOrNull()
}

private fun coverImageRank(fileName: String): Int {
    val baseName = fileName.substringBeforeLast('.').lowercase()
    return COVER_IMAGE_NAMES.indexOf(baseName).takeIf { it >= 0 } ?: Int.MAX_VALUE
}

internal fun String.initialsForPoster(): String =
    split(Regex("""\s+"""))
        .filter(String::isNotBlank)
        .take(2)
        .joinToString(separator = "") { it.first().uppercaseChar().toString() }
        .ifBlank { take(1).uppercase(Locale.US) }

internal fun IndexedLocalLibrary.toPublishedLibrary(
    metadataResolver: DesktopAnimeMetadataResolver? = null,
): PublishedLibrary {
    val publishedCatalog = metadataResolver?.let { catalog.withExternalAnimeMetadata(it) } ?: catalog
    val posterFilesById = metadataResolver
        ?.let { resolver ->
            publishedCatalog.items
                .mapNotNull { item ->
                    item.posterPath?.let { _ ->
                        resolver.cachedPosterForItem(item)?.let { poster -> item.id to poster }
                    }
                }
                .toMap()
        }
        .orEmpty()
    return PublishedLibrary(
        catalog = publishedCatalog,
        filesById = filesById,
        subtitleFilesById = subtitleFilesById,
        posterFilesById = posterFilesById,
    )
}
