package app.danmaku.tv

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import app.danmaku.domain.LibraryItemMetadataStatus
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibraryNextUpItem
import app.danmaku.domain.LibraryNextUpReason
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LibrarySeriesWatchSummary
import app.danmaku.domain.LibraryWatchState
import app.danmaku.domain.LibraryWatchStatus
import app.danmaku.domain.PlaybackProgress
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

@Composable
internal fun PlaybackTrack.buttonLabel(): String =
    if (selected) stringResource(R.string.selected_suffix, label) else label

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

@Composable
internal fun LibraryNextUpItem.nextUpActionLabel(): String =
    when (reason) {
        LibraryNextUpReason.RESUME -> stringResource(R.string.action_resume)
        LibraryNextUpReason.NEXT_EPISODE,
        LibraryNextUpReason.START -> stringResource(R.string.action_play)
    }

@Composable
internal fun LibraryNextUpItem.nextUpLabel(): String =
    when (reason) {
        LibraryNextUpReason.RESUME ->
            stringResource(
                R.string.resume_at,
                progress?.positionMs?.formatPlaybackTime() ?: stringResource(R.string.saved_position),
            )
        LibraryNextUpReason.NEXT_EPISODE -> stringResource(R.string.next_episode)
        LibraryNextUpReason.START -> stringResource(R.string.start_watching)
    }

internal fun String.initials(): String {
    val words = trim()
        .split(' ', '.', '_', '-', '[', ']')
        .filter(String::isNotBlank)
    return words
        .take(2)
        .joinToString(separator = "") { it.first().uppercaseChar().toString() }
        .ifBlank { "?" }
}

internal fun LibrarySeries.episodeLabel(): String =
    if (episodeCount == 1) "1 episode" else "$episodeCount episodes"

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

internal fun LibrarySeriesWatchSummary?.shortProgressLabel(): String =
    if (this == null) {
        "0 watched"
    } else {
        "$watchedCount watched, $inProgressCount watching"
    }

internal fun LibrarySeriesWatchSummary?.progressLabel(): String =
    if (this == null) {
        "0 watched, 0 watching"
    } else {
        "$watchedCount watched, $inProgressCount watching, $newCount new"
    }

@Composable
internal fun LibraryMediaItem.tvMetadataLabel(
    watchStatus: LibraryWatchStatus?,
    isFavorite: Boolean,
): String =
    buildList {
        add(watchStatus.statusLabel())
        animeMetadata?.let { add(stringResource(R.string.matched_anime, it.displayTitle)) }
        if (posterPath != null) {
            add(stringResource(R.string.poster_label))
        }
        metadataStatusLabel(
            loadingLabel = stringResource(R.string.metadata_loading),
            failedLabel = stringResource(R.string.metadata_failed),
        )?.let(::add)
        if (isFavorite) {
            add(stringResource(R.string.action_favorite))
        }
    }.joinToString(" / ")

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

internal fun LibraryWatchStatus?.statusLabel(): String =
    when (this?.state) {
        LibraryWatchState.WATCHED -> "Watched"
        LibraryWatchState.IN_PROGRESS -> {
            val progress = progress
            "In progress" + if (progress == null) {
                ""
            } else {
                " ${progress.positionMs.formatPlaybackTime()} / " +
                    (progress.durationMs?.formatPlaybackTime() ?: "--:--")
            }
        }
        LibraryWatchState.NEW,
        null -> "New"
    }

internal fun PlaybackProgress.progressLabel(): String =
    "${positionMs.formatPlaybackTime()} / ${durationMs?.formatPlaybackTime() ?: "--:--"}"
