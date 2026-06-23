package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.PlaybackSnapshot

internal enum class TvDestination(
    val labelResId: Int,
) {
    Home(R.string.nav_home),
    Library(R.string.nav_library),
    Search(R.string.nav_search),
    Favorites(R.string.nav_favorites),
    Pc(R.string.nav_pc),
}

@Composable
internal fun TvAppNavigationRail(
    selectedDestination: TvDestination,
    catalog: LibraryCatalog?,
    favoriteCount: Int,
    onSelectDestination: (TvDestination) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(190.dp)
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .background(TvCardColor)
            .padding(16.dp)
            .testTag("tv-app-rail"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Danmaku", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            catalog?.rootName ?: stringResource(R.string.pc_no_connected),
            color = TvMutedText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        TvRailPill(
            if (catalog == null) {
                stringResource(R.string.pc_offline)
            } else {
                stringResource(R.string.pc_ready)
            },
            active = catalog != null,
        )
        TvRailPill(stringResource(R.string.favorites_count, favoriteCount), active = favoriteCount > 0)
        Spacer(modifier = Modifier.height(8.dp))
        TvDestination.entries.forEach { destination ->
            TvRailNavigationItem(
                label = stringResource(destination.labelResId),
                selected = destination == selectedDestination,
                testTag = "tv-destination:${destination.name}",
                onClick = { onSelectDestination(destination) },
            )
        }
    }
}

@Composable
internal fun TvDestinationHeader(
    selectedDestination: TvDestination,
    catalog: LibraryCatalog?,
    snapshot: PlaybackSnapshot,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(TvPanelColor)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(selectedDestination.labelResId),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                catalog?.rootName ?: stringResource(R.string.connect_library_from_pc),
                color = TvMutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TvRailPill(
            stringResource(R.string.player_status, snapshot.status.toString()),
            active = snapshot.source != null,
            modifier = Modifier.width(180.dp),
        )
    }
}

@Composable
internal fun TvRailNavigationItem(
    label: String,
    selected: Boolean = false,
    testTag: String,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(if (compact) 14.dp else 16.dp)
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (compact) Modifier.height(40.dp) else Modifier)
            .tvFocusHalo(shape)
            .testTag(testTag),
        colors = tvButtonColors(selected = selected),
    ) {
        Text(
            label,
            color = if (selected) Color.White else TvMutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun TvRailPill(
    label: String,
    active: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
    compact: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (active) Color(0xFF273747) else TvPanelRaisedColor)
            .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 5.dp else 8.dp),
    ) {
        Text(
            label,
            color = if (active) TvAccentBlue else TvMutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
