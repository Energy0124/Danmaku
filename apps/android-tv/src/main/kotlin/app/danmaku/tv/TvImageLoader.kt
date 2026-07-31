package app.danmaku.tv

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import app.danmaku.domain.LibraryMediaItem
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import okio.Path.Companion.toOkioPath

private val LocalTvImageLoader = staticCompositionLocalOf<ImageLoader> {
    error("TV image loader is not installed")
}

internal fun createTvImageLoader(context: Context): ImageLoader =
    ImageLoader.Builder(context.applicationContext)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context.applicationContext, 0.10)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.applicationContext.cacheDir.resolve("tv_posters").toOkioPath())
                .maxSizeBytes(96L * 1024L * 1024L)
                .build()
        }
        .build()

@Composable
internal fun TvImageLoaderProvider(
    imageLoader: ImageLoader,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalTvImageLoader provides imageLoader, content = content)
}

@Composable
internal fun TvPosterImage(
    item: LibraryMediaItem,
    endpoint: LibraryPosterEndpoint?,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val url = endpoint?.posterUrl(item)
    val widthPx = with(LocalDensity.current) { width.roundToPx().coerceAtLeast(1) }
    val heightPx = with(LocalDensity.current) { height.roundToPx().coerceAtLeast(1) }
    Box(
        modifier = modifier
            .size(width, height)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF1C3B58),
                        Color(0xFF281D4C),
                        Color(0xFF111827),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = item.displaySeriesTitle().initials(),
            color = TvContent.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold,
        )
        if (url != null) {
            val cacheKey = "${endpoint.baseUrl.trimEnd('/')}|${item.posterPath}|${item.metadataStatus}"
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .size(widthPx, heightPx)
                    .build(),
                imageLoader = LocalTvImageLoader.current,
                contentDescription = stringResource(
                    R.string.poster_content_description,
                    item.displaySeriesTitle(),
                ),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal fun LibraryMediaItem.displaySeriesTitle(): String =
    animeMetadata?.displayTitle ?: seriesTitle
