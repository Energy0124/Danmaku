package app.danmaku.player.android

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.danmaku.library.android.LanLibraryClient
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.android.lanPlaybackTargetFromStreamUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DanmakuPlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val progressSync = LanPlaybackProgressSync(
        LanLibraryClient(),
        System::currentTimeMillis,
    )
    private var mediaSession: MediaSession? = null
    private var progressUploadJob: Job? = null

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
        val target = mediaItem
            ?.localConfiguration
            ?.uri
            ?.toString()
            ?.let(::lanPlaybackTargetFromStreamUrl)
            ?: return
        progressUploadJob = serviceScope.launch {
            while (isActive) {
                delay(PROGRESS_UPLOAD_INTERVAL_MS)
                val snapshot = Media3PlaybackController(player).snapshot()
                withContext(Dispatchers.IO) {
                    runCatching {
                        progressSync.saveProgress(target, snapshot)
                    }
                }
            }
        }
    }

    companion object {
        private const val PROGRESS_UPLOAD_INTERVAL_MS = 5_000L
    }
}
