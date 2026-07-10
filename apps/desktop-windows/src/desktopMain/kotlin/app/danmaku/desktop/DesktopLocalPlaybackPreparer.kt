package app.danmaku.desktop

import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.resumePositionMs

fun interface DesktopPlaybackProgressStore {
    fun loadProgress(mediaId: String): PlaybackProgress?
}

internal class InMemoryDesktopPlaybackProgressStore : DesktopPlaybackProgressStore {
    private val progressByMediaId = linkedMapOf<String, PlaybackProgress>()

    override fun loadProgress(mediaId: String): PlaybackProgress? = progressByMediaId[mediaId]

    fun saveProgress(progress: PlaybackProgress) {
        progressByMediaId[progress.mediaId] = progress
    }
}

data class DesktopLocalPlaybackPreparation(
    val item: LibraryMediaItem,
    val source: PlaybackSource.LocalFile,
    val resumePositionMs: Long?,
    val subtitles: List<DesktopPlaybackSubtitle> = emptyList(),
)

class DesktopLocalPlaybackPreparer(
    private val progressStore: DesktopPlaybackProgressStore,
) {
    fun prepare(
        library: IndexedLocalLibrary,
        item: LibraryMediaItem,
    ): DesktopLocalPlaybackPreparation {
        val file = library.requireMediaPath(item)
        return DesktopLocalPlaybackPreparation(
            item = item,
            source = PlaybackSource.LocalFile(file.toAbsolutePath().normalize().toString()),
            resumePositionMs = progressStore
                .loadProgress(item.id)
                ?.resumePositionMs(),
            subtitles = item.subtitles.mapNotNull { subtitle ->
                library.subtitleFilesById[subtitle.id]?.let { path ->
                    DesktopPlaybackSubtitle(
                        source = path.toAbsolutePath().normalize().toString(),
                        label = subtitle.label,
                    )
                }
            },
        )
    }
}
