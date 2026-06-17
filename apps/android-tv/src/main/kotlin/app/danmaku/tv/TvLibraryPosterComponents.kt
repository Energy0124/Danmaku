package app.danmaku.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.danmaku.domain.LibraryMediaItem

@Composable
internal fun TvPosterTile(
    item: LibraryMediaItem,
    title: String,
    posterEndpoint: LibraryPosterEndpoint?,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val posterUrl = posterEndpoint?.posterUrl(item)
    val posterImage = rememberPosterImage(posterUrl)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF26313A)),
        contentAlignment = Alignment.Center,
    ) {
        if (posterImage.bitmap != null) {
            Image(
                bitmap = posterImage.bitmap,
                contentDescription = stringResource(R.string.poster_content_description, title),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                title.initials(),
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        if (posterImage.state == PosterImageLoadState.LOADING) {
            TvPosterPill(
                label = stringResource(R.string.poster_loading),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            )
        }
        label?.let {
            TvPosterPill(
                label = it,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
            )
        }
    }
}

@Composable
private fun TvPosterPill(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, color = Color.White)
    }
}
