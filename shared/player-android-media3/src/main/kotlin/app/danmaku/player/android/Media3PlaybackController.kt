package app.danmaku.player.android

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackController
import app.danmaku.domain.PlaybackPosition
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.PlaybackStatus
import app.danmaku.domain.PlaybackTrack
import app.danmaku.domain.PlaybackTrackKind
import app.danmaku.library.LanPlaybackPreparation
import app.danmaku.library.android.OfflinePlaybackPreparation
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
            startPositionMs = preparation.resumePositionMs,
        )
    }

    fun load(
        preparation: OfflinePlaybackPreparation,
        startPositionMs: Long? = preparation.resumePositionMs,
    ) {
        loadMediaItem(
            source = preparation.source,
            mediaItem = MediaItem.Builder()
                .setMediaId(OFFLINE_MEDIA_ID_PREFIX + preparation.cacheKey)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setExtras(
                            Bundle().apply {
                                putString(OFFLINE_SERVER_URL_EXTRA, preparation.serverUrl)
                                putString(OFFLINE_LIBRARY_MEDIA_ID_EXTRA, preparation.item.id)
                            },
                        )
                        .build(),
                )
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
            startPositionMs = startPositionMs,
        )
    }

    private fun loadMediaItem(
        source: PlaybackSource,
        mediaItem: MediaItem,
        startPositionMs: Long? = null,
    ) {
        this.source = source
        hasStartedPlayback = false
        if (startPositionMs != null && startPositionMs > 0) {
            player.setMediaItem(mediaItem, startPositionMs)
        } else {
            player.setMediaItem(mediaItem)
        }
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
            is PlaybackCommand.SetVolume -> player.volume = command.volumePercent / 100f
            is PlaybackCommand.SelectAudioTrack ->
                selectTrack(PlaybackTrackKind.AUDIO, command.trackId)
            is PlaybackCommand.SelectSubtitleTrack ->
                selectTrack(PlaybackTrackKind.SUBTITLE, command.trackId)
        }
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        source = null
        hasStartedPlayback = false
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
            volumePercent = (player.volume * 100f).toInt().coerceIn(0, 100),
            tracks = currentTrackReferences().map(Media3TrackReference::track),
            errorMessage = playerError?.message,
        )
    }

    private fun selectTrack(
        kind: PlaybackTrackKind,
        trackId: String?,
    ) {
        val trackType = kind.toMedia3TrackType()
        val parameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(trackType)
        if (trackId == null) {
            if (kind == PlaybackTrackKind.SUBTITLE) {
                player.trackSelectionParameters = parameters
                    .setTrackTypeDisabled(trackType, true)
                    .build()
            }
            return
        }
        val reference = currentTrackReferences()
            .firstOrNull { it.track.kind == kind && it.track.id == trackId }
            ?: return
        player.trackSelectionParameters = parameters
            .setTrackTypeDisabled(trackType, false)
            .setOverrideForType(
                TrackSelectionOverride(reference.group.mediaTrackGroup, reference.trackIndex),
            )
            .build()
    }

    private fun currentTrackReferences(): List<Media3TrackReference> =
        player.currentTracks.groups.flatMapIndexed { groupIndex, group ->
            val kind = group.type.toPlaybackTrackKind() ?: return@flatMapIndexed emptyList()
            (0 until group.length).map { trackIndex ->
                val format = group.getTrackFormat(trackIndex)
                val language = format.language?.takeIf(String::isNotBlank)
                Media3TrackReference(
                    track = PlaybackTrack(
                        id = "media3:${kind.name.lowercase()}:$groupIndex:$trackIndex",
                        kind = kind,
                        label = format.label?.takeIf(String::isNotBlank)
                            ?: language
                            ?: "${kind.displayName()} ${trackIndex + 1}",
                        language = language,
                        selected = group.isTrackSelected(trackIndex),
                        supported = group.isTrackSupported(trackIndex),
                    ),
                    group = group,
                    trackIndex = trackIndex,
                )
            }
        }
}

internal const val OFFLINE_MEDIA_ID_PREFIX = "offline-cache:"
internal const val OFFLINE_SERVER_URL_EXTRA = "offline-server-url"
internal const val OFFLINE_LIBRARY_MEDIA_ID_EXTRA = "offline-library-media-id"

private data class Media3TrackReference(
    val track: PlaybackTrack,
    val group: Tracks.Group,
    val trackIndex: Int,
)

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

private fun Int.toPlaybackTrackKind(): PlaybackTrackKind? =
    when (this) {
        C.TRACK_TYPE_AUDIO -> PlaybackTrackKind.AUDIO
        C.TRACK_TYPE_TEXT -> PlaybackTrackKind.SUBTITLE
        else -> null
    }

private fun PlaybackTrackKind.toMedia3TrackType(): Int =
    when (this) {
        PlaybackTrackKind.AUDIO -> C.TRACK_TYPE_AUDIO
        PlaybackTrackKind.SUBTITLE -> C.TRACK_TYPE_TEXT
    }

private fun PlaybackTrackKind.displayName(): String =
    when (this) {
        PlaybackTrackKind.AUDIO -> "Audio"
        PlaybackTrackKind.SUBTITLE -> "Subtitle"
    }
