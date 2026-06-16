package app.danmaku.mobile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.continueWatchingItems
import app.danmaku.domain.nextUpItems
import app.danmaku.domain.recentlyWatchedItems

@Composable
internal fun HomePage(
    contentPadding: PaddingValues,
    catalog: LibraryCatalog?,
    posterEndpoint: LibraryPosterEndpoint? = null,
    playbackProgresses: List<PlaybackProgress>,
    snapshot: PlaybackSnapshot,
    nowPlaying: LibraryMediaItem?,
    onPlay: (LibraryMediaItem) -> Unit,
    onPlayPause: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenLibrary: () -> Unit,
    onShowLibraryItem: (LibraryMediaItem) -> Unit,
    onConnect: () -> Unit,
) {
    val nextUpItems = catalog?.nextUpItems(playbackProgresses, limit = 5).orEmpty()
    val continueWatchingItems = catalog?.continueWatchingItems(playbackProgresses, limit = 5).orEmpty()
    val recentlyWatchedItems = catalog?.recentlyWatchedItems(playbackProgresses, limit = 5).orEmpty()
    val recentlyAddedItems = catalog
        ?.items
        .orEmpty()
        .sortedWith(
            compareByDescending<LibraryMediaItem> { it.indexedAtEpochMs }
                .thenBy { it.seriesTitle }
                .thenBy { it.episodeTitle },
        )
        .take(5)

    PageColumn(contentPadding) {
        item(key = "home-page-header") {
            PageHeader(
                icon = Icons.Filled.Home,
                title = stringResource(R.string.nav_home),
                subtitle = catalog?.let {
                    stringResource(R.string.home_available_episodes, it.items.size)
                } ?: stringResource(R.string.home_connect_library_subtitle),
            )
        }
        if (snapshot.source != null) {
            item(key = "home-mini-player") {
                MiniPlayerBar(
                    snapshot = snapshot,
                    nowPlaying = nowPlaying,
                    onPlayPause = onPlayPause,
                    onOpenPlayer = onOpenPlayer,
                )
            }
        }
        if (catalog == null) {
            item(key = "home-empty-library") {
                EmptyPanel(
                    title = stringResource(R.string.library_empty_connect_title),
                    body = stringResource(R.string.home_library_status_empty),
                    actionLabel = stringResource(R.string.action_connect_library),
                    onAction = onConnect,
                )
            }
        } else {
            if (nextUpItems.isNotEmpty()) {
                item(key = "home-next-up") {
                    NextUpPanel(
                        items = nextUpItems,
                        posterEndpoint = posterEndpoint,
                        onShowDetails = { onOpenLibrary() },
                        onPlay = onPlay,
                    )
                }
            }
            if (continueWatchingItems.isNotEmpty()) {
                item(key = "home-continue-watching") {
                    ProgressRail(
                        title = stringResource(R.string.home_continue_watching),
                        subtitle = stringResource(R.string.library_continue_watching_subtitle),
                        tag = "home-continue-watching",
                        itemTagPrefix = "home-continue",
                        items = continueWatchingItems,
                        posterEndpoint = posterEndpoint,
                        onShowDetails = { onOpenLibrary() },
                        onPlay = onPlay,
                    )
                }
            }
            if (recentlyAddedItems.isNotEmpty()) {
                item(key = "home-recently-added") {
                    RecentlyAddedRail(
                        items = recentlyAddedItems,
                        posterEndpoint = posterEndpoint,
                        onShowDetails = onShowLibraryItem,
                        onPlay = onPlay,
                    )
                }
            }
            if (recentlyWatchedItems.isNotEmpty()) {
                item(key = "home-recently-watched") {
                    ProgressRail(
                        title = stringResource(R.string.home_recently_watched),
                        subtitle = stringResource(R.string.library_recently_watched_subtitle),
                        tag = "home-recently-watched",
                        itemTagPrefix = "home-recent",
                        items = recentlyWatchedItems,
                        posterEndpoint = posterEndpoint,
                        onShowDetails = { onOpenLibrary() },
                        onPlay = onPlay,
                    )
                }
            }
            item(key = "home-library-status") {
                EmptyPanel(
                    title = stringResource(R.string.home_library_status_title),
                    body = stringResource(R.string.home_library_status_connected, catalog.rootName),
                    actionLabel = stringResource(R.string.action_open_library),
                    onAction = onOpenLibrary,
                )
            }
        }
    }
}
