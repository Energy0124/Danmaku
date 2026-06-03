package app.danmaku.desktop

import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.resumePositionMs
import app.danmaku.server.PlaybackProgressStore
import java.nio.file.Files

data class DesktopLocalPlaybackPreparation(
    val item: LibraryMediaItem,
    val source: PlaybackSource.LocalFile,
    val resumePositionMs: Long?,
)

class DesktopLocalPlaybackPreparer(
    private val progressStore: PlaybackProgressStore,
) {
    fun prepare(
        library: IndexedLocalLibrary,
        item: LibraryMediaItem,
    ): DesktopLocalPlaybackPreparation {
        val file = library.filesById[item.id]
            ?: error("Indexed media file is missing for ${item.id}")
        require(Files.isRegularFile(file)) {
            "Indexed media file no longer exists: ${file.toAbsolutePath().normalize()}"
        }
        return DesktopLocalPlaybackPreparation(
            item = item,
            source = PlaybackSource.LocalFile(file.toAbsolutePath().normalize().toString()),
            resumePositionMs = progressStore
                .loadProgress(item.id)
                ?.resumePositionMs(),
        )
    }
}
