package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackTrack
import app.danmaku.domain.PlaybackTrackKind
import app.danmaku.domain.seekTargetBy

@Composable
internal fun TvPlayerPanel(
    controller: TvPlaybackController?,
    snapshot: PlaybackSnapshot,
    playbackError: String?,
    onSeekTo: (Long) -> Unit,
    onSelectAudio: (String) -> Unit,
    onSelectSubtitle: (String?) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(TvPanelRaisedColor)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AndroidView(
            factory = { PlayerView(it) },
            update = { it.player = controller?.androidPlayer },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
        )
        TvSeekControls(snapshot = snapshot, onSeekTo = onSeekTo)
        playbackError?.let {
            Text(stringResource(R.string.playback_error_prefix, it), color = Color(0xFFFCA5A5))
        }
        TrackControls(
            snapshot = snapshot,
            onSelectAudio = onSelectAudio,
            onSelectSubtitle = onSelectSubtitle,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onPlay,
                enabled = snapshot.source != null,
                modifier = Modifier.tvFocusHalo(RoundedCornerShape(18.dp)),
                colors = tvButtonColors(),
            ) {
                Text(stringResource(R.string.action_play))
            }
            Button(
                onClick = onPause,
                enabled = snapshot.source != null,
                modifier = Modifier.tvFocusHalo(RoundedCornerShape(18.dp)),
                colors = tvButtonColors(),
            ) {
                Text(stringResource(R.string.action_pause))
            }
            Button(
                onClick = onVolumeDown,
                enabled = snapshot.source != null && snapshot.volumePercent > 0,
                modifier = Modifier.tvFocusHalo(RoundedCornerShape(18.dp)),
                colors = tvButtonColors(),
            ) {
                Text(stringResource(R.string.action_volume_down))
            }
            Button(
                onClick = onVolumeUp,
                enabled = snapshot.source != null && snapshot.volumePercent < 100,
                modifier = Modifier.tvFocusHalo(RoundedCornerShape(18.dp)),
                colors = tvButtonColors(),
            ) {
                Text(stringResource(R.string.action_volume_up_percent, snapshot.volumePercent))
            }
        }
    }
}

@Composable
private fun TvSeekControls(
    snapshot: PlaybackSnapshot,
    onSeekTo: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(
                R.string.player_position,
                snapshot.position.positionMs.formatPlaybackTime(),
                snapshot.position.durationMs?.formatPlaybackTime() ?: "--:--",
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvSeekButton("-30s", snapshot, onSeekTo, -30_000)
            TvSeekButton("-10s", snapshot, onSeekTo, -10_000)
            TvSeekButton("+10s", snapshot, onSeekTo, 10_000)
            TvSeekButton("+30s", snapshot, onSeekTo, 30_000)
        }
    }
}

@Composable
private fun TvSeekButton(
    label: String,
    snapshot: PlaybackSnapshot,
    onSeekTo: (Long) -> Unit,
    deltaMs: Long,
) {
    Button(
        onClick = { onSeekTo(snapshot.position.seekTargetBy(deltaMs)) },
        enabled = snapshot.source != null,
        colors = tvButtonColors(),
    ) {
        Text(label)
    }
}

@Composable
private fun TrackControls(
    snapshot: PlaybackSnapshot,
    onSelectAudio: (String) -> Unit,
    onSelectSubtitle: (String?) -> Unit,
) {
    val audioTracks = snapshot.tracks.filter { it.kind == PlaybackTrackKind.AUDIO }
    val subtitleTracks = snapshot.tracks.filter { it.kind == PlaybackTrackKind.SUBTITLE }
    if (audioTracks.isNotEmpty()) {
        Text(stringResource(R.string.audio_tracks_title))
        TrackButtons(audioTracks, onSelectAudio)
    }
    if (subtitleTracks.isNotEmpty()) {
        Text(stringResource(R.string.subtitle_tracks_title))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "subtitle-off") {
                Button(
                    onClick = { onSelectSubtitle(null) },
                    enabled = subtitleTracks.any(PlaybackTrack::selected),
                    colors = tvButtonColors(),
                ) {
                    Text(stringResource(R.string.subtitle_off))
                }
            }
            items(subtitleTracks, key = PlaybackTrack::id) { track ->
                Button(
                    onClick = { onSelectSubtitle(track.id) },
                    enabled = track.supported && !track.selected,
                    colors = tvButtonColors(selected = track.selected),
                ) {
                    Text(track.buttonLabel())
                }
            }
        }
    }
}

@Composable
private fun TrackButtons(
    tracks: List<PlaybackTrack>,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(tracks, key = PlaybackTrack::id) { track ->
            Button(
                onClick = { onSelect(track.id) },
                enabled = track.supported && !track.selected,
                colors = tvButtonColors(selected = track.selected),
            ) {
                Text(track.buttonLabel())
            }
        }
    }
}
