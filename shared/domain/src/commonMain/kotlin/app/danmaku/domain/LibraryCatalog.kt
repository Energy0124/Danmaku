package app.danmaku.domain

import kotlinx.serialization.Serializable

@Serializable
data class LibraryCatalog(
    val rootName: String,
    val indexedAtEpochMs: Long,
    val items: List<LibraryMediaItem>,
)

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
    val subtitles: List<LibrarySubtitleTrack> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(seriesTitle.isNotBlank()) { "seriesTitle must not be blank" }
        require(episodeTitle.isNotBlank()) { "episodeTitle must not be blank" }
        require(relativePath.isNotBlank()) { "relativePath must not be blank" }
        require(sizeBytes >= 0) { "sizeBytes must not be negative" }
        require(mediaType.isNotBlank()) { "mediaType must not be blank" }
        require(streamPath.startsWith("/")) { "streamPath must be absolute" }
        require(subtitles.map(LibrarySubtitleTrack::id).distinct().size == subtitles.size) {
            "subtitle IDs must be unique"
        }
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
