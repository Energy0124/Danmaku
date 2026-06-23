package app.danmaku.tv

import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackCommand
import app.danmaku.library.LanPlaybackPreparation
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.LanPlaybackTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun prepareTvLibraryItem(
    progressSync: LanPlaybackProgressSync,
    playbackPreparer: LanPlaybackPreparer,
    target: LanPlaybackTarget,
    item: LibraryMediaItem,
    onResumeLookupFailure: (Throwable) -> Unit,
): LanPlaybackPreparation {
    val resumePosition = runCatching {
        withContext(Dispatchers.IO) {
            progressSync.fetchResumePositionMs(target)
        }
    }.onFailure(onResumeLookupFailure)
        .getOrNull()
    return withContext(Dispatchers.IO) {
        playbackPreparer.prepare(
            baseUrl = target.baseUrl,
            pairingToken = target.pairingToken,
            item = item,
            resumePositionMs = resumePosition,
        )
    }
}

internal fun loadPreparedTvLibraryItem(
    controller: TvPlaybackController,
    preparation: LanPlaybackPreparation,
) {
    controller.load(preparation)
    preparation.resumePositionMs?.let {
        controller.dispatch(PlaybackCommand.SeekTo(it))
    }
}

internal fun startLoadedTvLibraryItem(controller: TvPlaybackController) {
    controller.dispatch(PlaybackCommand.Play)
}