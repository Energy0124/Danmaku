package app.danmaku.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackStatus

internal enum class MobileTab(
    val labelResId: Int,
    val icon: ImageVector,
) {
    Home(R.string.nav_home, Icons.Filled.Home),
    Watch(R.string.nav_watch, Icons.Filled.PlayArrow),
    Library(R.string.nav_library, Icons.Filled.VideoLibrary),
    Connect(R.string.nav_connect, Icons.Filled.Settings),
}

@Composable
internal fun MobileBottomBar(
    selectedTab: MobileTab,
    onTabSelected: (MobileTab) -> Unit,
) {
    NavigationBar(
        containerColor = Color(0xFF15191D),
        tonalElevation = 0.dp,
    ) {
        MobileTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    val tabLabel = stringResource(tab.labelResId)
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tabLabel,
                    )
                },
                label = { Text(stringResource(tab.labelResId)) },
            )
        }
    }
}

@Composable
internal fun PageColumn(
    contentPadding: PaddingValues,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(contentPadding)
            .safeDrawingPadding()
            .testTag("page-column"),
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
internal fun PageHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E2930)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                subtitle,
                color = SubtleText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun MiniPlayerBar(
    snapshot: PlaybackSnapshot,
    nowPlaying: LibraryMediaItem?,
    onPlayPause: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenPlayer)
            .testTag("mini-player"),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF17212A),
        border = BorderStroke(1.dp, Color(0xFF304454)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AccentBlue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (snapshot.status == PlaybackStatus.PLAYING) {
                        Icons.Filled.Pause
                    } else {
                        Icons.Filled.PlayArrow
                    },
                    contentDescription = null,
                    tint = PlayerBlack,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    nowPlaying?.seriesTitle ?: snapshot.sourceLabel(nowPlaying),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    nowPlaying?.episodeTitle
                        ?: "${snapshot.position.positionMs.formatPlaybackTime()} · ${snapshot.status.displayLabel()}",
                    color = SubtleText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (snapshot.status == PlaybackStatus.PLAYING) {
                        Icons.Filled.Pause
                    } else {
                        Icons.Filled.PlayArrow
                    },
                    contentDescription = if (snapshot.status == PlaybackStatus.PLAYING) "Pause" else "Play",
                )
            }
        }
    }
}
