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
import app.danmaku.library.android.AndroidOfflineCacheRepository
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
    private var activeOfflineProgressTarget: OfflineProgressTarget? = null
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
        val offlineKey = mediaItem?.mediaId
            ?.takeIf { it.startsWith(OFFLINE_MEDIA_ID_PREFIX) }
            ?.removePrefix(OFFLINE_MEDIA_ID_PREFIX)
        val extras = mediaItem?.mediaMetadata?.extras
        activeOfflineProgressTarget = offlineKey?.let { key ->
            val serverUrl = extras?.getString(OFFLINE_SERVER_URL_EXTRA)
            val mediaId = extras?.getString(OFFLINE_LIBRARY_MEDIA_ID_EXTRA)
            if (serverUrl.isNullOrBlank() || mediaId.isNullOrBlank()) null
            else OfflineProgressTarget(key, serverUrl, mediaId)
        }
        if (activeProgressTarget == null && activeOfflineProgressTarget == null) return
        progressUploadJob = serviceScope.launch {
            while (isActive) {
                delay(PROGRESS_UPLOAD_INTERVAL_MS)
                val snapshot = Media3PlaybackController(player).snapshot()
                checkpointProgress(snapshot)
            }
        }
    }

    private fun checkpointProgress(player: Player) {
        if (activeProgressTarget == null && activeOfflineProgressTarget == null) return
        val snapshot = Media3PlaybackController(player).snapshot()
        serviceScope.launch {
            checkpointProgress(snapshot)
        }
    }

    private suspend fun checkpointProgress(snapshot: PlaybackSnapshot) {
        val target = activeProgressTarget
        val offlineTarget = activeOfflineProgressTarget
        if (target != null) {
            uploadProgress(target, snapshot)
        } else if (offlineTarget != null && snapshot.position.positionMs > 0) {
            progressUploadMutex.withLock {
                withContext(Dispatchers.IO) {
                    AndroidOfflineCacheRepository(applicationContext).savePendingProgress(
                        offlineTarget.cacheKey,
                        offlineTarget.serverUrl,
                        offlineTarget.mediaId,
                        snapshot,
                        System.currentTimeMillis(),
                    )
                }
            }
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

private data class OfflineProgressTarget(
    val cacheKey: String,
    val serverUrl: String,
    val mediaId: String,
)
