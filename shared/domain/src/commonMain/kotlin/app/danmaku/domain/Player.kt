package app.danmaku.domain

sealed interface PlaybackSource {
    data class LocalFile(val path: String) : PlaybackSource {
        init {
            require(path.isNotBlank()) { "path must not be blank" }
        }
    }

    data class RemoteStream(val url: String) : PlaybackSource {
        init {
            require(url.isNotBlank()) { "url must not be blank" }
        }
    }
}

enum class PlaybackStatus {
    IDLE,
    LOADING,
    READY,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR,
}

enum class PlaybackTrackKind {
    AUDIO,
    SUBTITLE,
}

data class PlaybackTrack(
    val id: String,
    val kind: PlaybackTrackKind,
    val label: String,
    val language: String? = null,
    val selected: Boolean = false,
    val supported: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(label.isNotBlank()) { "label must not be blank" }
        require(language == null || language.isNotBlank()) { "language must not be blank" }
    }
}

data class PlaybackSnapshot(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val source: PlaybackSource? = null,
    val position: PlaybackPosition = PlaybackPosition(positionMs = 0, durationMs = null),
    val playbackRate: Float = 1f,
    val tracks: List<PlaybackTrack> = emptyList(),
    val errorMessage: String? = null,
) {
    init {
        require(playbackRate > 0) { "playbackRate must be positive" }
        require(tracks.map(PlaybackTrack::id).distinct().size == tracks.size) {
            "track IDs must be unique"
        }
        require(status == PlaybackStatus.ERROR || errorMessage == null) {
            "errorMessage requires ERROR status"
        }
    }
}

/**
 * Platform players implement this contract with libmpv, Media3, or another
 * native media engine. The interface intentionally exposes domain types only.
 */
interface PlaybackController {
    fun load(source: PlaybackSource)

    fun dispatch(command: PlaybackCommand)

    fun snapshot(): PlaybackSnapshot
}
