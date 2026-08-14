package app.danmaku.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.danmaku.domain.DanmakuDisplaySettings
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackTrack
import app.danmaku.domain.PlaybackTrackKind
import kotlin.math.roundToInt

@Composable
internal fun PlaybackOptionsDialog(
    snapshot: PlaybackSnapshot,
    danmakuSettings: DanmakuDisplaySettings,
    onSetPlaybackRate: (Float) -> Unit,
    onSelectAudio: (String) -> Unit,
    onSelectSubtitle: (String?) -> Unit,
    onUpdateDanmakuSettings: (DanmakuDisplaySettings) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dismissInteractionSource = remember { MutableInteractionSource() }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.48f))
                .clickable(
                    interactionSource = dismissInteractionSource,
                    indication = null,
                    onClick = onDismiss,
                )
                .testTag("playback-options-scrim"),
        ) {
            val panelWidth = minOf(maxWidth * 0.92f, 430.dp)
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(panelWidth)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .testTag("playback-options-panel"),
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                color = Color(0xF2191D21),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.playback_options_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.playback_options_subtitle),
                                color = SubtleText,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("playback-options-close"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.action_close),
                            )
                        }
                    }

                    PlaybackOptionsSection(stringResource(R.string.playback_options_video_section)) {
                        PlaybackRateControls(
                            selectedRate = snapshot.playbackRate,
                            enabled = snapshot.source != null,
                            onSelect = onSetPlaybackRate,
                        )
                        TrackOptions(
                            snapshot = snapshot,
                            onSelectAudio = onSelectAudio,
                            onSelectSubtitle = onSelectSubtitle,
                        )
                    }

                    PlaybackOptionsSection(stringResource(R.string.playback_options_danmaku_section)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.danmaku_visibility_title),
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = if (danmakuSettings.visible) {
                                        stringResource(R.string.danmaku_enabled)
                                    } else {
                                        stringResource(R.string.danmaku_disabled)
                                    },
                                    color = SubtleText,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(
                                checked = danmakuSettings.visible,
                                onCheckedChange = {
                                    onUpdateDanmakuSettings(danmakuSettings.copy(visible = it))
                                },
                                modifier = Modifier.testTag("danmaku-visible-toggle"),
                            )
                        }
                        DanmakuSlider(
                            label = stringResource(
                                R.string.danmaku_opacity_value,
                                danmakuSettings.opacityPercent,
                            ),
                            value = danmakuSettings.opacityPercent.toFloat(),
                            valueRange = 0f..100f,
                            steps = 9,
                            testTag = "danmaku-opacity-slider",
                            onValueChange = { value ->
                                onUpdateDanmakuSettings(
                                    danmakuSettings.copy(opacityPercent = value.roundToInt()),
                                )
                            },
                        )
                        DanmakuSlider(
                            label = stringResource(
                                R.string.danmaku_size_value,
                                danmakuSettings.fontScalePercent,
                            ),
                            value = danmakuSettings.fontScalePercent.toFloat(),
                            valueRange = 50f..200f,
                            steps = 14,
                            testTag = "danmaku-size-slider",
                            onValueChange = { value ->
                                onUpdateDanmakuSettings(
                                    danmakuSettings.copy(fontScalePercent = value.roundToInt()),
                                )
                            },
                        )
                        DanmakuSlider(
                            label = stringResource(
                                R.string.danmaku_speed_value,
                                danmakuSettings.speedPercent,
                            ),
                            value = danmakuSettings.speedPercent.toFloat(),
                            valueRange = 50f..200f,
                            steps = 14,
                            testTag = "danmaku-speed-slider",
                            onValueChange = { value ->
                                onUpdateDanmakuSettings(
                                    danmakuSettings.copy(speedPercent = value.roundToInt()),
                                )
                            },
                        )
                        DanmakuSlider(
                            label = stringResource(
                                R.string.danmaku_density_value,
                                danmakuSettings.densityPercent,
                            ),
                            value = danmakuSettings.densityPercent.toFloat(),
                            valueRange = 10f..200f,
                            steps = 18,
                            testTag = "danmaku-density-slider",
                            onValueChange = { value ->
                                onUpdateDanmakuSettings(
                                    danmakuSettings.copy(densityPercent = value.roundToInt()),
                                )
                            },
                        )
                        DanmakuSlider(
                            label = stringResource(
                                R.string.danmaku_area_value,
                                danmakuSettings.displayAreaPercent,
                            ),
                            value = danmakuSettings.displayAreaPercent.toFloat(),
                            valueRange = 10f..100f,
                            steps = 8,
                            testTag = "danmaku-area-slider",
                            onValueChange = { value ->
                                onUpdateDanmakuSettings(
                                    danmakuSettings.copy(displayAreaPercent = value.roundToInt()),
                                )
                            },
                        )
                        DanmakuTimingControls(
                            offsetMs = danmakuSettings.offsetMs,
                            onOffsetChange = {
                                onUpdateDanmakuSettings(danmakuSettings.copy(offsetMs = it))
                            },
                        )
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                }
            }
        }
    }
}

@Composable
private fun PlaybackOptionsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            color = AccentBlue,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun PlaybackRateControls(
    selectedRate: Float,
    enabled: Boolean,
    onSelect: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.playback_speed_title),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PLAYBACK_RATES.forEach { rate ->
                FilterChip(
                    selected = selectedRate == rate,
                    onClick = { onSelect(rate) },
                    enabled = enabled,
                    label = { Text(stringResource(R.string.playback_speed_value, formatRate(rate))) },
                    modifier = Modifier.testTag("playback-rate:$rate"),
                )
            }
        }
    }
}

@Composable
private fun TrackOptions(
    snapshot: PlaybackSnapshot,
    onSelectAudio: (String) -> Unit,
    onSelectSubtitle: (String?) -> Unit,
) {
    val audioTracks = snapshot.tracks.filter { it.kind == PlaybackTrackKind.AUDIO }
    val subtitleTracks = snapshot.tracks.filter { it.kind == PlaybackTrackKind.SUBTITLE }
    if (audioTracks.isNotEmpty()) {
        OptionTrackGroup(
            title = stringResource(R.string.audio_tracks_title),
            tracks = audioTracks,
            testTagPrefix = "options-audio-track",
            onSelect = onSelectAudio,
        )
    }
    if (subtitleTracks.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.subtitle_tracks_title),
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = subtitleTracks.none(PlaybackTrack::selected),
                    onClick = { onSelectSubtitle(null) },
                    label = { Text(stringResource(R.string.subtitle_off)) },
                    modifier = Modifier.testTag("options-subtitle-off"),
                )
                subtitleTracks.forEach { track ->
                    FilterChip(
                        selected = track.selected,
                        onClick = { onSelectSubtitle(track.id) },
                        enabled = track.supported && !track.selected,
                        label = {
                            Text(
                                text = track.optionLabel(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.testTag("options-subtitle-track:${track.id}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionTrackGroup(
    title: String,
    tracks: List<PlaybackTrack>,
    testTagPrefix: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tracks.forEach { track ->
                FilterChip(
                    selected = track.selected,
                    onClick = { onSelect(track.id) },
                    enabled = track.supported && !track.selected,
                    label = {
                        Text(
                            text = track.optionLabel(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.testTag("$testTagPrefix:${track.id}"),
                )
            }
        }
    }
}

@Composable
private fun DanmakuSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    testTag: String,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
private fun DanmakuTimingControls(
    offsetMs: Long,
    onOffsetChange: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.danmaku_timing_value, offsetMs / 1_000f),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onOffsetChange((offsetMs - OFFSET_STEP_MS).coerceAtLeast(-MAX_OFFSET_MS)) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("danmaku-offset-minus"),
            ) {
                Text(stringResource(R.string.danmaku_timing_earlier))
            }
            OutlinedButton(
                onClick = { onOffsetChange(0L) },
                enabled = offsetMs != 0L,
                modifier = Modifier
                    .weight(1f)
                    .testTag("danmaku-offset-reset"),
            ) {
                Text(stringResource(R.string.action_reset))
            }
            OutlinedButton(
                onClick = { onOffsetChange((offsetMs + OFFSET_STEP_MS).coerceAtMost(MAX_OFFSET_MS)) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("danmaku-offset-plus"),
            ) {
                Text(stringResource(R.string.danmaku_timing_later))
            }
        }
    }
}

private fun PlaybackTrack.optionLabel(): String = label.ifBlank { id }

private fun formatRate(rate: Float): String =
    if (rate == rate.toInt().toFloat()) rate.toInt().toString() else rate.toString()

private val PLAYBACK_RATES = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private const val OFFSET_STEP_MS = 500L
private const val MAX_OFFSET_MS = 60_000L
