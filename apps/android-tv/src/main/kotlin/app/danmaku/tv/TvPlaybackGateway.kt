package app.danmaku.tv

import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.library.LanDanmakuLoader
import app.danmaku.library.LanPlaybackPreparation
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.LanPlaybackTarget
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

internal interface TvPlaybackSession {
    val state: StateFlow<TvSessionUiState>

    suspend fun updateProgresses(
        target: LanPlaybackTarget,
        progresses: List<PlaybackProgress>,
    ): Boolean
}

internal interface TvPlaybackGateway {
    suspend fun prepare(
        target: LanPlaybackTarget,
        item: LibraryMediaItem,
        onResumeLookupFailure: (Throwable) -> Unit,
    ): LanPlaybackPreparation

    suspend fun loadDanmaku(
        target: LanPlaybackTarget,
        forceRefresh: Boolean = false,
    ): TvDanmakuState

    suspend fun saveProgressAndRefresh(
        target: LanPlaybackTarget,
        snapshot: PlaybackSnapshot,
    ): List<PlaybackProgress>
}

internal class LanTvPlaybackGateway(
    private val progressSync: LanPlaybackProgressSync,
    private val playbackPreparer: LanPlaybackPreparer,
    private val danmakuLoader: LanDanmakuLoader,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TvPlaybackGateway {
    override suspend fun prepare(
        target: LanPlaybackTarget,
        item: LibraryMediaItem,
        onResumeLookupFailure: (Throwable) -> Unit,
    ): LanPlaybackPreparation =
        withContext(ioDispatcher) {
            prepareTvLibraryItem(
                progressSync = progressSync,
                playbackPreparer = playbackPreparer,
                target = target,
                item = item,
                onResumeLookupFailure = onResumeLookupFailure,
            )
        }

    override suspend fun loadDanmaku(
        target: LanPlaybackTarget,
        forceRefresh: Boolean,
    ): TvDanmakuState =
        withContext(ioDispatcher) {
            runCatching {
                TvDanmakuState.fromTrack(
                    danmakuLoader.fetchDanmaku(target, forceRefresh = forceRefresh),
                )
            }.getOrElse {
                TvDanmakuState.failed(target.mediaId, it)
            }
        }

    override suspend fun saveProgressAndRefresh(
        target: LanPlaybackTarget,
        snapshot: PlaybackSnapshot,
    ): List<PlaybackProgress> =
        withContext(ioDispatcher) {
            progressSync.saveProgress(target, snapshot)
            progressSync.fetchAllProgress(target.baseUrl, target.pairingToken)
        }
}
