package app.danmaku.server

import app.danmaku.domain.PlaybackProgress
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

interface PlaybackProgressStore {
    fun loadProgress(mediaId: String): PlaybackProgress?

    fun loadAllProgress(): List<PlaybackProgress>

    fun saveProgress(progress: PlaybackProgress)
}

class InMemoryPlaybackProgressStore : PlaybackProgressStore {
    private val progressByMediaId = ConcurrentHashMap<String, PlaybackProgress>()

    override fun loadProgress(mediaId: String): PlaybackProgress? =
        progressByMediaId[mediaId]

    override fun loadAllProgress(): List<PlaybackProgress> =
        progressByMediaId.values.sortedByDescending(PlaybackProgress::updatedAtEpochMs)

    override fun saveProgress(progress: PlaybackProgress) {
        progressByMediaId[progress.mediaId] = progress
    }
}

class FilePlaybackProgressStore(
    private val file: Path,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    },
) : PlaybackProgressStore {
    private val lock = Any()
    private val progressByMediaId = ConcurrentHashMap<String, PlaybackProgress>()

    init {
        loadSnapshot().forEach { progress ->
            progressByMediaId[progress.mediaId] = progress
        }
    }

    override fun loadProgress(mediaId: String): PlaybackProgress? =
        progressByMediaId[mediaId]

    override fun loadAllProgress(): List<PlaybackProgress> =
        progressByMediaId.values.sortedByDescending(PlaybackProgress::updatedAtEpochMs)

    override fun saveProgress(progress: PlaybackProgress) {
        synchronized(lock) {
            progressByMediaId[progress.mediaId] = progress
            writeSnapshot(progressByMediaId.values.sortedBy(PlaybackProgress::mediaId))
        }
    }

    private fun loadSnapshot(): List<PlaybackProgress> {
        if (!Files.isRegularFile(file)) return emptyList()
        return runCatching {
            json.decodeFromString<List<PlaybackProgress>>(Files.readString(file))
        }.getOrElse { emptyList() }
    }

    private fun writeSnapshot(progress: Collection<PlaybackProgress>) {
        val parent = file.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        val temp = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(
            temp,
            json.encodeToString(progress.toList()),
        )
        runCatching {
            Files.move(
                temp,
                file,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse { error ->
            if (error is AtomicMoveNotSupportedException) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            } else {
                throw error
            }
        }
    }
}
