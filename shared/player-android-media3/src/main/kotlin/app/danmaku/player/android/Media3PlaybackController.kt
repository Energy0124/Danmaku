package app.danmaku.player.android

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackController
import app.danmaku.domain.PlaybackPosition
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.PlaybackStatus
import app.danmaku.library.LanPlaybackPreparation
import java.io.File

class Media3PlaybackController(
    val player: Player,
) : PlaybackController {
    private var source: PlaybackSource? = null
    private var hasStartedPlayback = false

    override fun load(source: PlaybackSource) {
        loadMediaItem(source, MediaItem.fromUri(source.toUri()))
    }

    fun load(preparation: LanPlaybackPreparation) {
        loadMediaItem(
            source = preparation.source,
            mediaItem = MediaItem.Builder()
                .setUri(preparation.source.toUri())
                .setSubtitleConfigurations(
                    preparation.subtitles.map { subtitle ->
                        MediaItem.SubtitleConfiguration.Builder(subtitle.source.toUri())
                            .setId(subtitle.track.id)
                            .setLabel(subtitle.track.label)
                            .setMimeType(subtitle.track.mediaType.toMedia3SubtitleMimeType())
                            .build()
                    },
                )
                .build(),
        )
    }

    private fun loadMediaItem(
        source: PlaybackSource,
        mediaItem: MediaItem,
    ) {
        this.source = source
        hasStartedPlayback = false
        player.setMediaItem(mediaItem)
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
        val activeSource = source
            ?: player.currentMediaItem
                ?.localConfiguration
                ?.uri
                ?.toPlaybackSource()
                ?.also { source = it }
        return PlaybackSnapshot(
            status = when {
                playerError != null -> PlaybackStatus.ERROR
                activeSource == null -> PlaybackStatus.IDLE
                player.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.LOADING
                player.playbackState == Player.STATE_READY && player.isPlaying ->
                    PlaybackStatus.PLAYING
                player.playbackState == Player.STATE_READY && hasStartedPlayback ->
                    PlaybackStatus.PAUSED
                player.playbackState == Player.STATE_READY -> PlaybackStatus.READY
                player.playbackState == Player.STATE_ENDED -> PlaybackStatus.ENDED
                else -> PlaybackStatus.LOADING
            },
            source = activeSource,
            position = PlaybackPosition(
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0),
            ),
            playbackRate = player.playbackParameters.speed,
            errorMessage = playerError?.message,
        )
    }

}

private fun PlaybackSource.toUri(): Uri =
    when (this) {
        is PlaybackSource.LocalFile ->
            if (path.contains("://")) Uri.parse(path) else Uri.fromFile(File(path))
        is PlaybackSource.RemoteStream -> Uri.parse(url)
    }

private fun Uri.toPlaybackSource(): PlaybackSource =
    when (scheme) {
        "content", "file" -> PlaybackSource.LocalFile(toString())
        else -> PlaybackSource.RemoteStream(toString())
    }

private fun String.toMedia3SubtitleMimeType(): String =
    when (lowercase()) {
        "text/x-ass" -> MimeTypes.TEXT_SSA
        else -> this
    }
