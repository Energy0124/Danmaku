package app.danmaku.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.darkColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.danmaku.domain.DanmakuDisplaySettings
import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeMatchCandidate
import app.danmaku.domain.ExternalAnimeMatchQuery
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeMappingSource
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeSyncFailure
import app.danmaku.domain.ExternalAnimeTrackingPlan
import app.danmaku.domain.ExternalAnimeTrackingPlanUpdate
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogQuery
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryItemMetadataStatus
import app.danmaku.domain.LocalDanmakuParser
import app.danmaku.domain.LibraryEpisodeDetail
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibraryNextUpItem
import app.danmaku.domain.LibraryNextUpReason
import app.danmaku.domain.LibraryPlaybackProgressItem
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LibrarySeriesWatchSummary
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.LibraryWatchState
import app.danmaku.domain.LibraryWatchStatus
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackStatus
import app.danmaku.domain.PlaybackTrack
import app.danmaku.domain.PlaybackTrackKind
import app.danmaku.domain.continueWatchingItems
import app.danmaku.domain.displayName
import app.danmaku.domain.episodeDetail
import app.danmaku.domain.externalAnimeTrackingPlan
import app.danmaku.domain.externalAnimeSyncRetryAfterEpochMs
import app.danmaku.domain.filteredItems
import app.danmaku.domain.groupedSeries
import app.danmaku.domain.nextItem
import app.danmaku.domain.nextUpItems
import app.danmaku.domain.previousItem
import app.danmaku.domain.recentlyWatchedItems
import app.danmaku.domain.toPlaybackProgress
import app.danmaku.domain.seriesWatchSummaryById
import app.danmaku.domain.watchStatusByMediaId
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.LanPlaybackPreparation
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.LanPlaybackTarget
import app.danmaku.library.jvm.JvmLanLibraryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Rectangle
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.Window as AwtWindow
import java.awt.datatransfer.StringSelection
import java.net.URI
import kotlin.math.roundToInt
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.jetbrains.skia.Image as SkiaImage

@Composable
internal fun DanmakuDisplaySettingsCard(
    strings: DesktopStrings,
    settings: DanmakuDisplaySettings,
    onSave: (DanmakuDisplaySettings) -> Unit,
) {
    var visible by remember(settings) { mutableStateOf(settings.visible) }
    var opacityText by remember(settings) { mutableStateOf(settings.opacityPercent.toString()) }
    var fontScaleText by remember(settings) { mutableStateOf(settings.fontScalePercent.toString()) }
    var speedText by remember(settings) { mutableStateOf(settings.speedPercent.toString()) }
    var densityText by remember(settings) { mutableStateOf(settings.densityPercent.toString()) }
    var displayAreaText by remember(settings) { mutableStateOf(settings.displayAreaPercent.toString()) }
    var offsetText by remember(settings) { mutableStateOf(settings.offsetMs.toString()) }
    var keywordFiltersText by remember(settings) { mutableStateOf(settings.keywordFilters.joinToString("\n")) }
    var regexFiltersText by remember(settings) { mutableStateOf(settings.regexFilters.joinToString("\n")) }
    val opacityError = integerRangeError(opacityText, 0..100, "%")
    val fontScaleError = integerRangeError(fontScaleText, 50..200, "%")
    val speedError = integerRangeError(speedText, 25..300, "%")
    val densityError = integerRangeError(densityText, 10..200, "%")
    val displayAreaError = integerRangeError(displayAreaText, 10..100, "%")
    val offsetError = longRangeError(offsetText, -3_600_000L..3_600_000L, "ms")
    val draftSettings = if (
        opacityError == null &&
        fontScaleError == null &&
        speedError == null &&
        densityError == null &&
        displayAreaError == null &&
        offsetError == null
    ) {
        DanmakuDisplaySettings(
            visible = visible,
            opacityPercent = opacityText.toInt(),
            fontScalePercent = fontScaleText.toInt(),
            speedPercent = speedText.toInt(),
            densityPercent = densityText.toInt(),
            displayAreaPercent = displayAreaText.toInt(),
            offsetMs = offsetText.toLong(),
            keywordFilters = keywordFiltersText.toFilterEntries(),
            regexFilters = regexFiltersText.toFilterEntries(),
        )
    } else {
        null
    }
    val isDirty = draftSettings != null && draftSettings != settings

    SectionCard(strings.danmakuDisplaySettingsTitle) {
        Text(
            strings.danmakuDisplaySettingsDescription,
            color = DanmakuColors.TextMuted,
        )
        MetadataRow(strings.visibilityLabel, if (settings.visible) strings.shownLabel else strings.hiddenLabel)
        MetadataRow(strings.opacityLabel, "${settings.opacityPercent}%")
        MetadataRow(strings.fontScaleLabel, "${settings.fontScalePercent}%")
        MetadataRow(strings.speedLabel, "${settings.speedPercent}%")
        MetadataRow(strings.densityLabel, "${settings.densityPercent}%")
        MetadataRow(strings.displayAreaLabel, "${settings.displayAreaPercent}%")
        MetadataRow(strings.offsetLabel, strings.offsetMsValueLabel(settings.offsetMs))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { visible = true },
                enabled = !visible,
            ) {
                Text(strings.showDanmakuAction)
            }
            Button(
                onClick = { visible = false },
                enabled = visible,
            ) {
                Text(strings.hideDanmakuAction)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = opacityText,
                onValueChange = { opacityText = it },
                label = { Text(strings.opacityPercentLabel) },
                isError = opacityError != null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = fontScaleText,
                onValueChange = { fontScaleText = it },
                label = { Text(strings.fontScalePercentLabel) },
                isError = fontScaleError != null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = speedText,
                onValueChange = { speedText = it },
                label = { Text(strings.speedPercentLabel) },
                isError = speedError != null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        SettingsValidationText(listOfNotNull(opacityError, fontScaleError, speedError))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = densityText,
                onValueChange = { densityText = it },
                label = { Text(strings.densityPercentLabel) },
                isError = densityError != null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = displayAreaText,
                onValueChange = { displayAreaText = it },
                label = { Text(strings.displayAreaPercentLabel) },
                isError = displayAreaError != null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = offsetText,
                onValueChange = { offsetText = it },
                label = { Text(strings.offsetMsLabel) },
                isError = offsetError != null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        SettingsValidationText(listOfNotNull(densityError, displayAreaError, offsetError))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = keywordFiltersText,
                onValueChange = { keywordFiltersText = it },
                label = { Text(strings.keywordFiltersLabel) },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = regexFiltersText,
                onValueChange = { regexFiltersText = it },
                label = { Text(strings.regexFiltersLabel) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    draftSettings?.let(onSave)
                },
                enabled = isDirty,
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.saveDanmakuDisplayAction)
            }
            Button(
                onClick = {
                    visible = true
                    opacityText = "100"
                    fontScaleText = "100"
                    speedText = "100"
                    densityText = "100"
                    displayAreaText = "100"
                    offsetText = "0"
                    keywordFiltersText = ""
                    regexFiltersText = ""
                },
            ) {
                Text(strings.resetDraftAction)
            }
        }
    }
}

internal fun String.toFilterEntries(): List<String> =
    lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .toList()

@Composable
internal fun SettingsValidationText(errors: List<String>) {
    if (errors.isEmpty()) {
        return
    }
    Text(
        text = errors.joinToString(separator = "\n"),
        color = DanmakuColors.Warning,
    )
}

@Composable
internal fun SettingsConnectionTestStatusRow(
    strings: DesktopStrings,
    label: String,
    status: SettingsConnectionTestStatus,
) {
    val statusLabel = when (status.outcome) {
        SettingsConnectionTestOutcome.TESTING -> strings.testingStatusLabel
        SettingsConnectionTestOutcome.SUCCESS -> strings.okStatusLabel
        SettingsConnectionTestOutcome.FAILURE -> strings.failedStatusLabel
    }
    val color = when (status.outcome) {
        SettingsConnectionTestOutcome.TESTING -> DanmakuColors.Info
        SettingsConnectionTestOutcome.SUCCESS -> DanmakuColors.Good
        SettingsConnectionTestOutcome.FAILURE -> DanmakuColors.Warning
    }
    val icon = when (status.outcome) {
        SettingsConnectionTestOutcome.TESTING -> Icons.Filled.Refresh
        SettingsConnectionTestOutcome.SUCCESS -> Icons.Filled.CheckCircle
        SettingsConnectionTestOutcome.FAILURE -> Icons.Filled.Warning
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = DanmakuColors.TextMuted,
            modifier = Modifier.width(140.dp),
            maxLines = 1,
        )
        StatusPill(
            text = statusLabel,
            icon = icon,
            active = status.outcome != SettingsConnectionTestOutcome.FAILURE,
            color = color,
        )
        Text(
            text = status.detail,
            color = DanmakuColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}


