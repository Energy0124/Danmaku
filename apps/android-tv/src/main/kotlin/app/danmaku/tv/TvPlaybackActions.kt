package app.danmaku.tv

import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackCommand
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.LanPlaybackTarget
import app.danmaku.player.android.Media3PlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun playTvLibraryItem(
    controller: Media3PlaybackController,
    progressSync: LanPlaybackProgressSync,
    playbackPreparer: LanPlaybackPreparer,
    baseUrl: String,
    pairingToken: String,
    item: LibraryMediaItem,
    onResumeLookupFailure: (Throwable) -> Unit,
) {
    val target = LanPlaybackTarget(baseUrl, pairingToken, item.id)
    val resumePosition = runCatching {
        withContext(Dispatchers.IO) {
            progressSync.fetchResumePositionMs(target)
        }
    }.onFailure(onResumeLookupFailure)
        .getOrNull()
    val preparation = withContext(Dispatchers.IO) {
        playbackPreparer.prepare(
            baseUrl = target.baseUrl,
            pairingToken = target.pairingToken,
            item = item,
            resumePositionMs = resumePosition,
        )
    }
    controller.load(preparation)
    preparation.resumePositionMs?.let {
        controller.dispatch(PlaybackCommand.SeekTo(it))
    }
    controller.dispatch(PlaybackCommand.Play)
}
