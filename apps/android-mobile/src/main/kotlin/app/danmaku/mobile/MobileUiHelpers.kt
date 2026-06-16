package app.danmaku.mobile

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.danmaku.domain.LibraryItemMetadataStatus
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibraryNextUpItem
import app.danmaku.domain.LibraryNextUpReason
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LibrarySeriesWatchSummary
import app.danmaku.domain.LibraryWatchState
import app.danmaku.domain.LibraryWatchStatus
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.PlaybackStatus
import app.danmaku.domain.PlaybackTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

internal enum class PosterImageLoadState {
    IDLE,
    LOADING,
    LOADED,
    FAILED,
}

internal data class PosterImageState(
    val bitmap: ImageBitmap?,
    val state: PosterImageLoadState,
)

internal fun LibraryMediaItem.metadataStatusLabel(
    loadingLabel: String,
    failedLabel: String,
): String? =
    when (metadataStatus) {
        LibraryItemMetadataStatus.LOADING -> loadingLabel
        LibraryItemMetadataStatus.FAILED -> failedLabel
        LibraryItemMetadataStatus.READY -> null
        LibraryItemMetadataStatus.NOT_AVAILABLE -> null
    }

internal fun PlaybackSnapshot.sourceLabel(nowPlaying: LibraryMediaItem?): String =
    when {
        nowPlaying != null -> "${nowPlaying.seriesTitle} · ${nowPlaying.episodeTitle}"
        source is PlaybackSource.LocalFile -> "Local video"
        source is PlaybackSource.RemoteStream -> "LAN stream"
        else -> "Select a video to start watching"
    }

internal fun PlaybackStatus.displayLabel(): String =
    name.lowercase().replaceFirstChar { it.uppercaseChar() }

internal fun PlaybackTrack.buttonLabel(): String =
    if (selected) "$label (selected)" else label

internal fun String.serverDisplayName(): String =
    removePrefix("http://")
        .removePrefix("https://")
        .ifBlank { "No server selected" }

@Composable
internal fun rememberPosterImage(url: String?): PosterImageState {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var state by remember(url) { mutableStateOf(PosterImageLoadState.IDLE) }

    LaunchedEffect(url) {
        bitmap = null
        if (url == null) {
            state = PosterImageLoadState.IDLE
            return@LaunchedEffect
        }
        state = PosterImageLoadState.LOADING
        val loaded = withContext(Dispatchers.IO) {
            loadPosterImage(url)
        }
        bitmap = loaded
        state = if (loaded == null) PosterImageLoadState.FAILED else PosterImageLoadState.LOADED
    }

    return PosterImageState(bitmap, state)
}

private fun loadPosterImage(url: String): ImageBitmap? {
    val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = 3_000
        readTimeout = 5_000
        requestMethod = "GET"
    }
    return try {
        if (connection.responseCode !in 200..299) {
            null
        } else {
            connection.inputStream.use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
        }
    } finally {
        connection.disconnect()
    }
}

internal fun String.encodedQueryValue(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name())

internal fun String.initials(): String {
    val words = trim()
        .split(' ', '.', '_', '-', '[', ']')
        .filter(String::isNotBlank)
    return words
        .take(2)
        .joinToString(separator = "") { it.first().uppercaseChar().toString() }
        .ifBlank { "?" }
}

internal fun LibraryMediaItem.formatSize(): String {
    return sizeBytes.formatSize()
}

internal fun Long.formatSize(): String {
    val mib = toDouble() / (1024.0 * 1024.0)
    val gib = mib / 1024.0
    return if (gib >= 1.0) {
        "${gib.formatOneDecimal()} GB"
    } else {
        "${mib.formatOneDecimal()} MB"
    }
}

internal fun LibrarySeries.displayLabel(watchSummary: LibrarySeriesWatchSummary?): String {
    val episodeLabel = if (episodeCount == 1) "1 ep" else "$episodeCount eps"
    val progressLabel = watchSummary.progressLabel()
    return if (subtitleTrackCount > 0) {
        "$title · $episodeLabel · $progressLabel · $subtitleTrackCount sub"
    } else {
        "$title · $episodeLabel · $progressLabel"
    }
}

internal fun LibrarySeriesWatchSummary?.progressLabel(): String =
    if (this == null) {
        "0 watched"
    } else {
        "${watchedCount} watched · ${inProgressCount} watching · ${newCount} new"
    }

internal fun LibraryNextUpItem.nextUpLabel(): String =
    when (reason) {
        LibraryNextUpReason.RESUME -> "Resume at ${progress?.positionMs?.formatPlaybackTime() ?: "saved position"}"
        LibraryNextUpReason.NEXT_EPISODE -> "Next episode"
        LibraryNextUpReason.START -> "Start watching"
    }

internal fun LibraryNextUpItem.nextUpActionLabel(): String =
    when (reason) {
        LibraryNextUpReason.RESUME -> "Resume"
        LibraryNextUpReason.NEXT_EPISODE,
        LibraryNextUpReason.START -> "Play"
    }

internal fun LibraryWatchStatus?.statusLabel(): String =
    when (this?.state) {
        LibraryWatchState.WATCHED -> "Watched"
        LibraryWatchState.IN_PROGRESS -> {
            val progress = progress
            "In progress" + if (progress == null) {
                ""
            } else {
                " · ${progress.positionMs.formatPlaybackTime()} / " +
                    (progress.durationMs?.formatPlaybackTime() ?: "--:--")
            }
        }
        LibraryWatchState.NEW,
        null -> "New"
    }

internal fun PlaybackProgress.progressLabel(): String =
    "${positionMs.formatPlaybackTime()} / ${durationMs?.formatPlaybackTime() ?: "--:--"}"

private fun Double.formatOneDecimal(): String {
    val scaled = (this * 10).toLong()
    return "${scaled / 10}.${scaled % 10}"
}

internal fun Long.formatPlaybackTime(): String {
    val totalSeconds = this.coerceAtLeast(0) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
