package app.danmaku.player.android

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackController
import app.danmaku.domain.PlaybackPosition
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.PlaybackStatus
import java.io.File

class Media3PlaybackController(context: Context) : PlaybackController, AutoCloseable {
    val player: Player = ExoPlayer.Builder(context).build()
    private val mediaSession = MediaSession.Builder(context, player).build()

    private var source: PlaybackSource? = null
    private var hasStartedPlayback = false

    override fun load(source: PlaybackSource) {
        this.source = source
        hasStartedPlayback = false
        player.setMediaItem(MediaItem.fromUri(source.toUri()))
        player.prepare()
    }

    override fun dispatch(command: PlaybackCommand) {
        when (command) {
            PlaybackCommand.Play -> {
                hasStartedPlayback = true
                player.play()
            }

            PlaybackCommand.Pause -> player.pause()
            is PlaybackCommand.SeekTo -> player.seekTo(command.positionMs)
            is PlaybackCommand.SetPlaybackRate -> player.setPlaybackSpeed(command.rate)
        }
    }

    override fun snapshot(): PlaybackSnapshot {
        val playerError = player.playerError
        return PlaybackSnapshot(
            status = when {
                playerError != null -> PlaybackStatus.ERROR
                source == null -> PlaybackStatus.IDLE
                player.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.LOADING
                player.playbackState == Player.STATE_READY && player.isPlaying ->
                    PlaybackStatus.PLAYING
                player.playbackState == Player.STATE_READY && hasStartedPlayback ->
                    PlaybackStatus.PAUSED
                player.playbackState == Player.STATE_READY -> PlaybackStatus.READY
                player.playbackState == Player.STATE_ENDED -> PlaybackStatus.ENDED
                else -> PlaybackStatus.LOADING
            },
            source = source,
            position = PlaybackPosition(
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0),
            ),
            playbackRate = player.playbackParameters.speed,
            errorMessage = playerError?.message,
        )
    }

    override fun close() {
        mediaSession.release()
        player.release()
    }
}

private fun PlaybackSource.toUri(): Uri =
    when (this) {
        is PlaybackSource.LocalFile ->
            if (path.contains("://")) Uri.parse(path) else Uri.fromFile(File(path))
        is PlaybackSource.RemoteStream -> Uri.parse(url)
    }
