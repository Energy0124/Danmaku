package app.danmaku.player.android

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.LanPlaybackTarget
import app.danmaku.library.android.LanLibraryClient
import app.danmaku.library.android.lanPlaybackTargetFromStreamUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DanmakuPlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val progressSync = LanPlaybackProgressSync(
        LanLibraryClient(),
        System::currentTimeMillis,
    )
    private var mediaSession: MediaSession? = null
    private var progressUploadJob: Job? = null
    private var activeProgressTarget: LanPlaybackTarget? = null
    private val progressUploadMutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    startProgressUploads(player, mediaItem)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (!isPlaying && player.playbackState == Player.STATE_READY) {
                        checkpointProgress(player)
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    if (
                        reason == Player.DISCONTINUITY_REASON_SEEK ||
                        reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                    ) {
                        checkpointProgress(player)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) checkpointProgress(player)
                }
            },
        )
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo,
    ): MediaSession? =
        mediaSession

    override fun onDestroy() {
        progressUploadJob?.cancel()
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun startProgressUploads(
        player: Player,
        mediaItem: MediaItem?,
    ) {
        progressUploadJob?.cancel()
        activeProgressTarget = mediaItem
            ?.localConfiguration
            ?.uri
            ?.toString()
            ?.let(::lanPlaybackTargetFromStreamUrl)
        val target = activeProgressTarget
            ?: return
        progressUploadJob = serviceScope.launch {
            while (isActive) {
                delay(PROGRESS_UPLOAD_INTERVAL_MS)
                val snapshot = Media3PlaybackController(player).snapshot()
                uploadProgress(target, snapshot)
            }
        }
    }

    private fun checkpointProgress(player: Player) {
        val target = activeProgressTarget ?: return
        val snapshot = Media3PlaybackController(player).snapshot()
        serviceScope.launch {
            uploadProgress(target, snapshot)
        }
    }

    private suspend fun uploadProgress(
        target: LanPlaybackTarget,
        snapshot: PlaybackSnapshot,
    ) {
        if (snapshot.position.positionMs <= 0) return
        progressUploadMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    progressSync.saveProgress(target, snapshot)
                }
            }
        }
    }

    companion object {
        private const val PROGRESS_UPLOAD_INTERVAL_MS = 5_000L
    }
}
