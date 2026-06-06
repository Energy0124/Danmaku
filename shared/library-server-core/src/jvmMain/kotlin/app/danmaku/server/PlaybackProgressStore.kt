package app.danmaku.server

import app.danmaku.domain.PlaybackProgress
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
