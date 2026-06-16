package app.danmaku.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackStatus
import app.danmaku.domain.PlaybackTrack
import app.danmaku.domain.PlaybackTrackKind
import app.danmaku.domain.coerceSeekTarget
import app.danmaku.domain.seekTargetBy
import app.danmaku.player.android.Media3PlaybackController

@Composable
internal fun WatchPage(
    contentPadding: PaddingValues,
    controller: Media3PlaybackController?,
    snapshot: PlaybackSnapshot,
    nowPlaying: LibraryMediaItem?,
    playbackError: String?,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetVolume: (Int) -> Unit,
    onSelectAudio: (String) -> Unit,
    onSelectSubtitle: (String?) -> Unit,
    onBrowseLibrary: () -> Unit,
) {
    PageColumn(contentPadding) {
        item(key = "player") {
            PlayerHome(
                controller = controller,
                snapshot = snapshot,
                nowPlaying = nowPlaying,
                playbackError = playbackError,
                onOpen = onOpen,
                onPlayPause = onPlayPause,
                onSeekTo = onSeekTo,
                onSetVolume = onSetVolume,
                onSelectAudio = onSelectAudio,
                onSelectSubtitle = onSelectSubtitle,
            )
        }
        item(key = "watch-actions") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("watch-library-actions"),
                shape = RoundedCornerShape(18.dp),
                color = PanelColor,
                border = BorderStroke(1.dp, Color(0xFF2B3239)),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.watch_library_playback_title),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.watch_library_playback_body),
                            color = SubtleText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(onClick = onBrowseLibrary) {
                        Text(stringResource(R.string.action_browse))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerHome(
    controller: Media3PlaybackController?,
    snapshot: PlaybackSnapshot,
    nowPlaying: LibraryMediaItem?,
    playbackError: String?,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetVolume: (Int) -> Unit,
    onSelectAudio: (String) -> Unit,
    onSelectSubtitle: (String?) -> Unit,
) {
    Column(
        modifier = Modifier.testTag("watch-player-home"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Danmaku",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    snapshot.sourceLabel(nowPlaying),
                    color = SubtleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusPill(snapshot.status.displayLabel())
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("watch-video-surface")
                .clip(RoundedCornerShape(22.dp))
                .background(PlayerBlack)
                .aspectRatio(16f / 9f),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        useController = false
                    }
                },
                update = { it.player = controller?.player },
                modifier = Modifier.fillMaxSize(),
            )
            if (snapshot.source == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        stringResource(R.string.player_ready_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.player_ready_body),
                        color = SubtleText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        NowPlayingPanel(
            snapshot = snapshot,
            nowPlaying = nowPlaying,
            playbackError = playbackError,
            onOpen = onOpen,
            onPlayPause = onPlayPause,
            onSeekTo = onSeekTo,
            onSetVolume = onSetVolume,
            onSelectAudio = onSelectAudio,
            onSelectSubtitle = onSelectSubtitle,
        )
    }
}

@Composable
private fun NowPlayingPanel(
    snapshot: PlaybackSnapshot,
    nowPlaying: LibraryMediaItem?,
    playbackError: String?,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetVolume: (Int) -> Unit,
    onSelectAudio: (String) -> Unit,
    onSelectSubtitle: (String?) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("now-playing-panel"),
        shape = RoundedCornerShape(20.dp),
        color = PanelColor,
        border = BorderStroke(1.dp, Color(0xFF2B3239)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    nowPlaying?.seriesTitle ?: stringResource(R.string.now_playing_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    nowPlaying?.episodeTitle ?: stringResource(R.string.now_playing_empty_body),
                    color = SubtleText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            PlaybackSeekControls(snapshot = snapshot, onSeekTo = onSeekTo)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onPlayPause,
                    enabled = snapshot.source != null,
                    modifier = Modifier.testTag("watch-play-pause"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentBlue,
                        contentColor = PlayerBlack,
                    ),
                ) {
                    Icon(
                        imageVector = if (snapshot.status == PlaybackStatus.PLAYING) {
                            Icons.Filled.Pause
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (snapshot.status == PlaybackStatus.PLAYING) {
                            stringResource(R.string.action_pause)
                        } else {
                            stringResource(R.string.action_play)
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.testTag("watch-open-video"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_open_video))
                }
                OutlinedButton(
                    onClick = { onSetVolume((snapshot.volumePercent - 10).coerceAtLeast(0)) },
                    enabled = snapshot.source != null && snapshot.volumePercent > 0,
                    modifier = Modifier.testTag("watch-volume-down"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("-10")
                }
                OutlinedButton(
                    onClick = { onSetVolume((snapshot.volumePercent + 10).coerceAtMost(100)) },
                    enabled = snapshot.source != null && snapshot.volumePercent < 100,
                    modifier = Modifier.testTag("watch-volume-up"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("+10")
                }
            }

            Text(
                stringResource(R.string.volume_percent, snapshot.volumePercent),
                color = SubtleText,
                style = MaterialTheme.typography.bodySmall,
            )
            TrackControls(
                snapshot = snapshot,
                onSelectAudio = onSelectAudio,
                onSelectSubtitle = onSelectSubtitle,
            )
            playbackError?.let {
                ErrorText(stringResource(R.string.playback_error_prefix, it))
            }
        }
    }
}

@Composable
private fun PlaybackSeekControls(
    snapshot: PlaybackSnapshot,
    onSeekTo: (Long) -> Unit,
) {
    val durationMs = snapshot.position.durationMs?.takeIf { it > 0 }
    val currentPositionMs = snapshot.position.coerceSeekTarget(snapshot.position.positionMs)
    var sliderPositionMs by remember(snapshot.source, durationMs) {
        mutableStateOf(currentPositionMs.toFloat())
    }
    var isDragging by remember(snapshot.source, durationMs) { mutableStateOf(false) }

    LaunchedEffect(currentPositionMs, durationMs, isDragging) {
        if (!isDragging) {
            sliderPositionMs = currentPositionMs.toFloat()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Slider(
            value = durationMs
                ?.let { sliderPositionMs.coerceIn(0f, it.toFloat()) }
                ?: 0f,
            onValueChange = {
                isDragging = true
                sliderPositionMs = it
            },
            onValueChangeFinished = {
                durationMs?.let {
                    onSeekTo(snapshot.position.coerceSeekTarget(sliderPositionMs.toLong()))
                }
                isDragging = false
            },
            valueRange = 0f..(durationMs ?: 1L).toFloat(),
            enabled = snapshot.source != null && durationMs != null,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                currentPositionMs.formatPlaybackTime(),
                color = SubtleText,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                durationMs?.formatPlaybackTime() ?: "--:--",
                color = SubtleText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SeekButton("-30s", snapshot, onSeekTo, -30_000)
            SeekButton("-10s", snapshot, onSeekTo, -10_000)
            SeekButton("+10s", snapshot, onSeekTo, 10_000)
            SeekButton("+30s", snapshot, onSeekTo, 30_000)
        }
    }
}

@Composable
private fun SeekButton(
    label: String,
    snapshot: PlaybackSnapshot,
    onSeekTo: (Long) -> Unit,
    deltaMs: Long,
) {
    TextButton(
        onClick = { onSeekTo(snapshot.position.seekTargetBy(deltaMs)) },
        enabled = snapshot.source != null,
        modifier = Modifier.testTag("watch-seek:$label"),
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
    if (audioTracks.isNotEmpty() || subtitleTracks.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (audioTracks.isNotEmpty()) {
                Text(
                    stringResource(R.string.audio_tracks_title),
                    color = SubtleText,
                    style = MaterialTheme.typography.labelMedium,
                )
                TrackButtons(audioTracks, onSelectAudio)
            }
            if (subtitleTracks.isNotEmpty()) {
                Text(
                    stringResource(R.string.subtitle_tracks_title),
                    color = SubtleText,
                    style = MaterialTheme.typography.labelMedium,
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "subtitle-off") {
                        FilterChip(
                            selected = subtitleTracks.none(PlaybackTrack::selected),
                            onClick = { onSelectSubtitle(null) },
                            enabled = subtitleTracks.any(PlaybackTrack::selected),
                            label = { Text(stringResource(R.string.subtitle_off)) },
                        )
                    }
                    items(subtitleTracks, key = PlaybackTrack::id) { track ->
                        FilterChip(
                            selected = track.selected,
                            onClick = { onSelectSubtitle(track.id) },
                            enabled = track.supported && !track.selected,
                            label = { Text(track.buttonLabel()) },
                        )
                    }
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tracks, key = PlaybackTrack::id) { track ->
            FilterChip(
                selected = track.selected,
                onClick = { onSelect(track.id) },
                enabled = track.supported && !track.selected,
                label = { Text(track.buttonLabel()) },
            )
        }
    }
}
