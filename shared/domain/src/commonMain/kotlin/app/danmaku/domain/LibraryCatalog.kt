package app.danmaku.domain

import kotlinx.serialization.Serializable

@Serializable
data class LibraryCatalog(
    val rootName: String,
    val indexedAtEpochMs: Long,
    val items: List<LibraryMediaItem>,
)

fun LibraryCatalog.previousItem(currentItemId: String): LibraryMediaItem? {
    require(currentItemId.isNotBlank()) { "currentItemId must not be blank" }
    val currentIndex = items.indexOfFirst { it.id == currentItemId }
    return items.getOrNull(currentIndex - 1)
}

fun LibraryCatalog.nextItem(currentItemId: String): LibraryMediaItem? {
    require(currentItemId.isNotBlank()) { "currentItemId must not be blank" }
    val currentIndex = items.indexOfFirst { it.id == currentItemId }
    return if (currentIndex == -1) {
        null
    } else {
        items.getOrNull(currentIndex + 1)
    }
}

@Serializable
data class LanLibraryServerAnnouncement(
    val protocol: String = PROTOCOL,
    val version: Int = VERSION,
    val port: Int,
) {
    init {
        require(protocol == PROTOCOL) { "unsupported discovery protocol" }
        require(version == VERSION) { "unsupported discovery version" }
        require(port in 1..65_535) { "port must be valid" }
    }

    companion object {
        const val PROTOCOL = "danmaku-library"
        const val VERSION = 1
        const val DEFAULT_DISCOVERY_PORT = 8_687
    }
}

@Serializable
data class LibraryMediaItem(
    val id: String,
    val seriesTitle: String,
    val episodeTitle: String,
    val relativePath: String,
    val sizeBytes: Long,
    val mediaType: String,
    val streamPath: String,
    val indexedAtEpochMs: Long = 0,
    val subtitles: List<LibrarySubtitleTrack> = emptyList(),
    val posterPath: String? = null,
    val animeMetadata: LibraryAnimeMetadata? = null,
    val metadataStatus: LibraryItemMetadataStatus = LibraryItemMetadataStatus.NOT_AVAILABLE,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(seriesTitle.isNotBlank()) { "seriesTitle must not be blank" }
        require(episodeTitle.isNotBlank()) { "episodeTitle must not be blank" }
        require(relativePath.isNotBlank()) { "relativePath must not be blank" }
        require(sizeBytes >= 0) { "sizeBytes must not be negative" }
        require(mediaType.isNotBlank()) { "mediaType must not be blank" }
        require(streamPath.startsWith("/")) { "streamPath must be absolute" }
        require(indexedAtEpochMs >= 0) { "indexedAtEpochMs must not be negative" }
        require(posterPath == null || posterPath.startsWith("/")) { "posterPath must be absolute" }
        require(subtitles.map(LibrarySubtitleTrack::id).distinct().size == subtitles.size) {
            "subtitle IDs must be unique"
        }
    }
}

@Serializable
enum class LibraryItemMetadataStatus {
    NOT_AVAILABLE,
    LOADING,
    READY,
    FAILED,
}

@Serializable
data class LibraryAnimeMetadata(
    val animeId: ExternalAnimeId,
    val displayTitle: String,
    val primaryTitle: String,
    val chineseTitle: String? = null,
    val englishTitle: String? = null,
    val japaneseTitle: String? = null,
    val alternateNames: List<String> = emptyList(),
    val externalLinks: List<ExternalAnimeExternalLink> = emptyList(),
    val imageUrl: String? = null,
    val episodeCount: Int? = null,
    val startYear: Int? = null,
) {
    init {
        require(displayTitle.isNotBlank()) { "displayTitle must not be blank" }
        require(primaryTitle.isNotBlank()) { "primaryTitle must not be blank" }
        require(chineseTitle == null || chineseTitle.isNotBlank()) { "chineseTitle must not be blank" }
        require(englishTitle == null || englishTitle.isNotBlank()) { "englishTitle must not be blank" }
        require(japaneseTitle == null || japaneseTitle.isNotBlank()) { "japaneseTitle must not be blank" }
        require(alternateNames.none { it.isBlank() }) { "alternateNames must not contain blank titles" }
        require(externalLinks.distinctBy(ExternalAnimeExternalLink::animeId).size == externalLinks.size) {
            "externalLinks must be unique by anime ID"
        }
        require(imageUrl == null || imageUrl.startsWith("https://")) { "imageUrl must be HTTPS" }
        require(episodeCount == null || episodeCount > 0) { "episodeCount must be positive" }
        require(startYear == null || startYear in 1900..2200) { "startYear must be reasonable" }
    }
}

@Serializable
data class LibrarySubtitleTrack(
    val id: String,
    val label: String,
    val relativePath: String,
    val mediaType: String,
    val streamPath: String,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(label.isNotBlank()) { "label must not be blank" }
        require(relativePath.isNotBlank()) { "relativePath must not be blank" }
        require(mediaType.isNotBlank()) { "mediaType must not be blank" }
        require(streamPath.startsWith("/")) { "streamPath must be absolute" }
    }
}
